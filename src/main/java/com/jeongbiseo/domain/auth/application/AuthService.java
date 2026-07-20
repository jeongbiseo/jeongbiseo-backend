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
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.auth.client.OAuthClient;
import com.jeongbiseo.domain.auth.client.OAuthUserInfo;
import com.jeongbiseo.domain.auth.entity.Auth;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.domain.auth.entity.RefreshToken;
import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.auth.repository.RefreshTokenRepository;
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

	private final Clock clock;

	private final long refreshTokenExpirationDays;

	public AuthService(List<OAuthClient> oauthClients, JwtProvider jwtProvider, AuthRepository authRepository,
			RefreshTokenRepository refreshTokenRepository, AuthMemberProvisioner memberProvisioner, Clock clock,
			@Value("${app.auth.refresh.expiration-days:14}") long refreshTokenExpirationDays) {
		// provider()를 생성 시점에 한 번만 불러 Map을 미리 만들지 않음(테스트에서 @MockitoBean으로 대체된
		// OAuthClient는 provider() 스텁이 실제 호출 시점에야 걸리므로, 지연 조회라야 Spring 컨텍스트
		// 기동 시점의 미스텁 null 충돌을 피함).
		this.oauthClients = oauthClients;
		this.jwtProvider = jwtProvider;
		this.authRepository = authRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.memberProvisioner = memberProvisioner;
		this.clock = clock;
		this.refreshTokenExpirationDays = refreshTokenExpirationDays;
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

		String accessToken = this.jwtProvider.issueAccessToken(member.getId());
		String rawRefreshToken = issueRefreshToken(member);

		return new LoginResult(accessToken, rawRefreshToken, provisioned.newMember(), member.isOnboardingCompleted());
	}

	/**
	 * reissue: 이전 리프레시 해시를 조건으로 원자적 회전(설계 D9). 회전에 지거나 만료·미존재·미제공이면 AUTH401_2. 새 raw 리프레시
	 * 토큰은 결과에 담아 컨트롤러가 쿠키로 세팅함.
	 */
	@Transactional
	public ReissueResult reissue(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
		}
		String oldHash = sha256Hex(rawRefreshToken);
		RefreshToken existing = this.refreshTokenRepository.findByTokenHash(oldHash)
			.orElseThrow(() -> new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED));
		if (existing.getExpiresAt().isBefore(LocalDateTime.now(this.clock))) {
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
		}

		String newRawToken = generateOpaqueToken();
		String newHash = sha256Hex(newRawToken);
		LocalDateTime newExpiresAt = LocalDateTime.now(this.clock).plusDays(this.refreshTokenExpirationDays);
		int updated = this.refreshTokenRepository.rotateByTokenHash(oldHash, newHash, newExpiresAt);
		if (updated != 1) {
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
		}

		Long memberId = existing.getMember().getId();
		String newAccessToken = this.jwtProvider.issueAccessToken(memberId);
		return new ReissueResult(newAccessToken, newRawToken);
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
