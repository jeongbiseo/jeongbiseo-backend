package com.jeongbiseo.domain.auth.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.auth.client.OAuthClient;
import com.jeongbiseo.domain.auth.client.OAuthUserInfo;
import com.jeongbiseo.domain.auth.entity.Auth;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.domain.auth.entity.RefreshToken;
import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.auth.repository.RefreshTokenRepository;
import com.jeongbiseo.domain.consent.service.TermConsentService;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.exception.AuthErrorCode;
import com.jeongbiseo.global.security.jwt.JwtProvider;

/**
 * 소셜 인증 서비스임(소셜인증-전환설계 정본). 콜백 교환(자동가입 더하기 JWT 발급), 리프레시 회전, 로그아웃을 담당함. state 서버검증은 방식 B
 * 전환으로 제거했고, raw 리프레시 토큰은 컨트롤러가 쿠키로 세팅하도록 결과 record로 넘김(서비스는 쿠키를 모름).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private static final int REFRESH_TOKEN_BYTES = 32; // 256비트, 설계 D7

	private final List<OAuthClient> oauthClients;

	private final JwtProvider jwtProvider;

	private final AuthRepository authRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	private final AuthMemberProvisioner memberProvisioner;

	private final TermConsentService termConsentService;

	private final Clock clock;

	private final long refreshTokenExpirationDays;

	private final long refreshTokenGraceSeconds;

	public AuthService(List<OAuthClient> oauthClients, JwtProvider jwtProvider, AuthRepository authRepository,
			RefreshTokenRepository refreshTokenRepository, AuthMemberProvisioner memberProvisioner,
			TermConsentService termConsentService, Clock clock,
			@Value("${app.auth.refresh.expiration-days:14}") long refreshTokenExpirationDays,
			@Value("${app.auth.refresh.grace-seconds:30}") long refreshTokenGraceSeconds) {
		// provider()를 생성 시점에 한 번만 불러 Map을 미리 만들지 않음(테스트에서 @MockitoBean으로 대체된
		// OAuthClient는 provider() 스텁이 실제 호출 시점에야 걸리므로, 지연 조회라야 Spring 컨텍스트
		// 기동 시점의 미스텁 null 충돌을 피함).
		this.oauthClients = oauthClients;
		this.jwtProvider = jwtProvider;
		this.authRepository = authRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.memberProvisioner = memberProvisioner;
		this.termConsentService = termConsentService;
		this.clock = clock;
		this.refreshTokenExpirationDays = refreshTokenExpirationDays;
		this.refreshTokenGraceSeconds = refreshTokenGraceSeconds;
	}

	/**
	 * login(콜백 교환): IdP 토큰 교환(PKCE code_verifier 포함), 자동가입(또는 기존 회원 조회), JWT와 리프레시 발급.
	 * raw 리프레시 토큰은 결과에 담아 컨트롤러가 쿠키로 세팅함.
	 */
	@Transactional
	public LoginResult handleCallback(String provider, String code, String codeVerifier, String redirectUri) {
		Provider providerEnum = resolveProviderEnum(provider);
		if (code == null || code.isBlank()) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
		}

		OAuthUserInfo userInfo = resolveClient(providerEnum).exchange(code, codeVerifier, redirectUri);
		ProvisionResult provisioned = provisionMember(providerEnum, userInfo);
		Member member = provisioned.member();

		// 매 로그인마다 누락된 필수 약관을 멱등하게 보강함(add-missing). 별도 트랜잭션(REQUIRES_NEW)이라 로그인 흐름과
		// 독립적으로 커밋되고, 동시 첫 로그인 승자·패자가 같은 회원에 대해 경쟁 삽입해 (member_id, term_type) UNIQUE가
		// 충돌해도 그 콜백만 롤백되지 로그인 전체를 막지 않음. 충돌은 항목당 1건 멱등이라 삼키며, 양쪽이 다 롤백돼 누락되면
		// 다음 로그인이 복구함. 버그 기간에 이미 가입한 회원의 백필도 이 무조건 호출로 로그인 시 점진 복구됨. 이 연결이 없어
		// 마이페이지 약관이 "동의 내역 없음"으로 나오던 버그를 고침.
		try {
			this.termConsentService.ensureRequiredConsents(member);
		}
		catch (DataIntegrityViolationException e) {
			// 동시 첫 로그인 경쟁 삽입 충돌. 멱등 보강이라 무시함(누락 시 다음 로그인이 보강).
		}

		String accessToken = this.jwtProvider.issueAccessToken(member.getId());
		String rawRefreshToken = issueRefreshToken(member);

		return new LoginResult(accessToken, rawRefreshToken, provisioned.newMember(), member.isOnboardingCompleted());
	}

	/**
	 * reissue: 이전 리프레시 해시를 조건으로 원자적 회전(설계 D9). 회전에 실패하면 유예 경로로 한 번 더 판정하고, 그마저 아니면
	 * AUTH401_2. 회전 성공 시에만 새 raw 리프레시 토큰을 결과에 담아 컨트롤러가 쿠키로 세팅함(유예 경로는 null이라 쿠키를 건드리지
	 * 않음).
	 *
	 * 트랜잭션을 열지 않음(NOT_SUPPORTED). 회전 UPDATE와 그 뒤 조회가 한 트랜잭션에 묶이면 MySQL REPEATABLE READ
	 * 스냅샷 때문에 경쟁에서 진 요청이 이긴 요청의 prev 해시를 못 보고 유예가 무력화됨(RefreshTokenRepository 주석 참조).
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ReissueResult reissue(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
		}
		String oldHash = sha256Hex(rawRefreshToken);
		LocalDateTime now = LocalDateTime.now(this.clock);

		// 회원 식별은 회전 전에 확보함. 회전 후 새 해시로 다시 찾으면 그 사이 다른 기기 로그인이 행을 덮어써 못 찾는 경쟁 창이 생김.
		Optional<Long> ownerId = this.refreshTokenRepository.findMemberIdByTokenHash(oldHash);

		String newRawToken = generateOpaqueToken();
		String newHash = sha256Hex(newRawToken);
		LocalDateTime newExpiresAt = now.plusDays(this.refreshTokenExpirationDays);
		int updated = this.refreshTokenRepository.rotateByTokenHash(oldHash, newHash, newExpiresAt, now);
		if (updated == 1) {
			Long memberId = ownerId.orElseThrow(() -> new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED));
			return new ReissueResult(this.jwtProvider.issueAccessToken(memberId), newRawToken);
		}

		// 유예 경로: 방금 회전된 직전 해시로 들어온 요청임(프론트 중복 발사의 패자). 두 경우가 여기로 모임 — 승자가 이미 커밋해
		// 위 조회부터 못 찾은 경우와, 조회는 됐지만 UPDATE가 승자 커밋 뒤에 0행이 된 경우. 액세스 토큰만 재발급하고 쿠키는 이긴
		// 쪽이 심은 것을 그대로 둠. 유예창을 벗어났거나 아예 모르는 토큰이면 재로그인 요구임.
		LocalDateTime graceThreshold = now.minusSeconds(this.refreshTokenGraceSeconds);
		Long graceMemberId = this.refreshTokenRepository.findMemberIdByPrevTokenHash(oldHash, graceThreshold, now)
			.orElseThrow(() -> new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED));
		return new ReissueResult(this.jwtProvider.issueAccessToken(graceMemberId), null);
	}

	/**
	 * logOut: 저장된 리프레시 토큰 행을 삭제함(설계 D3, 회원당 1행. 행이 없어도 멱등하게 no-op).
	 */
	@Transactional
	public void processLogout(Long memberId) {
		this.refreshTokenRepository.deleteByMemberId(memberId);
	}

	private ProvisionResult provisionMember(Provider providerEnum, OAuthUserInfo userInfo) {
		Optional<Auth> existing = this.authRepository.findByProviderAndProviderIdWithMember(providerEnum,
				userInfo.providerId());
		if (existing.isPresent()) {
			return new ProvisionResult(existing.get().getMember(), false);
		}
		try {
			Member created = this.memberProvisioner.createMemberWithAuth(providerEnum, userInfo.providerId(),
					userInfo.email(), userInfo.name());
			return new ProvisionResult(created, true);
		}
		catch (DataIntegrityViolationException e) {
			// 동시 첫 로그인 레이스: UNIQUE 충돌로 REQUIRES_NEW 트랜잭션만 롤백됨. 이겨서 먼저 커밋된 행을
			// 재조회함(설계 11장).
			Member winner = this.authRepository
				.findByProviderAndProviderIdWithMember(providerEnum, userInfo.providerId())
				.map(Auth::getMember)
				.orElseThrow(() -> new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED, e));
			return new ProvisionResult(winner, false);
		}
	}

	private String issueRefreshToken(Member member) {
		String rawToken = generateOpaqueToken();
		String tokenHash = sha256Hex(rawToken);
		LocalDateTime expiresAt = LocalDateTime.now(this.clock).plusDays(this.refreshTokenExpirationDays);

		this.refreshTokenRepository.findByMemberId(member.getId()).ifPresentOrElse(existing -> {
			existing.rotate(tokenHash, expiresAt);
		}, () -> this.refreshTokenRepository
			.save(RefreshToken.builder().member(member).tokenHash(tokenHash).expiresAt(expiresAt).build()));

		return rawToken;
	}

	private OAuthClient resolveClient(Provider providerEnum) {
		return this.oauthClients.stream()
			.filter(client -> client.provider() == providerEnum)
			.findFirst()
			.orElseThrow(() -> new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER));
	}

	private Provider resolveProviderEnum(String provider) {
		if (provider == null) {
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		try {
			return Provider.valueOf(provider.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER, e);
		}
	}

	private static String generateOpaqueToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없음", e);
		}
	}

	private record ProvisionResult(Member member, boolean newMember) {
	}

}
