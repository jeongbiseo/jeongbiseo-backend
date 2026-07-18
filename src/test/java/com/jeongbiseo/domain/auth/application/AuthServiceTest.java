package com.jeongbiseo.domain.auth.application;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import com.jeongbiseo.domain.auth.client.OAuthClient;
import com.jeongbiseo.domain.auth.client.OAuthUserInfo;
import com.jeongbiseo.domain.auth.entity.Auth;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.domain.auth.entity.RefreshToken;
import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.auth.repository.RefreshTokenRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.jwt.JwtProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 단위 테스트임(Mockito). 방식 B 전환으로 state 서버검증은 사라졌고, DB·IdP 없이 검증 가능한 분기(provider
 * 검증, 코드 누락, 리프레시 빈값·미존재·만료·회전경쟁, 동시 첫 로그인 재조회)를 고정함. DB를 실제로 쓰는 자동가입·회전·탈퇴 종단 흐름은
 * AuthServiceIntegrationTest가 담당함.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	private static final String JWT_SECRET = "unit-test-only-jwt-secret-key-must-be-at-least-32-bytes-long-A";

	@Mock
	private OAuthClient kakaoClient;

	@Mock
	private OAuthClient googleClient;

	@Mock
	private AuthRepository authRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private AuthMemberProvisioner memberProvisioner;

	private JwtProvider jwtProvider;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		given(kakaoClient.provider()).willReturn(Provider.KAKAO);
		given(googleClient.provider()).willReturn(Provider.GOOGLE);
		this.jwtProvider = new JwtProvider(FIXED_CLOCK, JWT_SECRET, 30);
		this.authService = new AuthService(List.of(kakaoClient, googleClient), jwtProvider, authRepository,
				refreshTokenRepository, memberProvisioner, FIXED_CLOCK, 14);
	}

	@Test
	void handleCallback은_알수없는_provider면_VALID400_0을_던진다() {
		assertThatThrownBy(() -> authService.handleCallback("naver", "code", "verifier", "redirect"))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");
	}

	@Test
	void handleCallback은_code가_없으면_AUTH401_1을_던진다() {
		assertThatThrownBy(() -> authService.handleCallback("kakao", null, "verifier", "redirect"))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");

		assertThatThrownBy(() -> authService.handleCallback("kakao", "  ", "verifier", "redirect"))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void handleCallback은_기존_auth가_있으면_기존_회원으로_로그인한다() {
		given(kakaoClient.exchange("code", "verifier", "redirect"))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "uid-1", "e@example.com"));
		Member existingMember = Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
		setId(existingMember, 7L);
		Auth existingAuth = Auth.builder().provider(Provider.KAKAO).providerId("uid-1").member(existingMember).build();
		given(authRepository.findByProviderAndProviderIdWithMember(Provider.KAKAO, "uid-1"))
			.willReturn(Optional.of(existingAuth));
		given(refreshTokenRepository.findByMemberId(7L)).willReturn(Optional.empty());

		LoginResult result = authService.handleCallback("kakao", "code", "verifier", "redirect");

		assertThat(result.isNewMember()).isFalse();
		assertThat(result.onboardingCompleted()).isTrue();
		assertThat(result.refreshToken()).isNotBlank();
		assertThat(jwtProvider.parseMemberId(result.accessToken())).isEqualTo(7L);
		verify(memberProvisioner, never()).createMemberWithAuth(any(), anyString(), any());
	}

	@Test
	void handleCallback은_동시_첫로그인_유니크충돌시_기존_auth를_재조회한다() {
		given(kakaoClient.exchange("code", "verifier", "redirect"))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "uid-2", "e@example.com"));
		given(authRepository.findByProviderAndProviderIdWithMember(Provider.KAKAO, "uid-2"))
			.willReturn(Optional.empty(), Optional.of(winnerAuth("uid-2")));
		given(memberProvisioner.createMemberWithAuth(eq(Provider.KAKAO), eq("uid-2"), any()))
			.willThrow(new DataIntegrityViolationException("duplicate"));
		given(refreshTokenRepository.findByMemberId(9L)).willReturn(Optional.empty());

		LoginResult result = authService.handleCallback("kakao", "code", "verifier", "redirect");

		assertThat(result.isNewMember()).isFalse();
		assertThat(jwtProvider.parseMemberId(result.accessToken())).isEqualTo(9L);
	}

	@Test
	void reissue는_빈_토큰이면_AUTH401_2를_던진다() {
		assertThatThrownBy(() -> authService.reissue("  ")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void reissue는_존재하지_않는_해시면_AUTH401_2를_던진다() {
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.reissue("unknown-refresh-token")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void reissue는_만료된_토큰이면_AUTH401_2를_던진다() {
		Member member = Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
		setId(member, 3L);
		RefreshToken expired = RefreshToken.builder()
			.member(member)
			.tokenHash("hash")
			.expiresAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
			.build();
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expired));

		assertThatThrownBy(() -> authService.reissue("expired-refresh-token")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void reissue는_원자적_회전이_경쟁에서_지면_AUTH401_2를_던진다() {
		Member member = Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
		setId(member, 5L);
		RefreshToken valid = RefreshToken.builder()
			.member(member)
			.tokenHash("hash")
			.expiresAt(LocalDateTime.now(FIXED_CLOCK).plusDays(1))
			.build();
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(valid));
		given(refreshTokenRepository.rotateByTokenHash(anyString(), anyString(), any())).willReturn(0);

		assertThatThrownBy(() -> authService.reissue("some-refresh-token")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void reissue는_회전에_성공하면_새_액세스와_새_리프레시를_반환한다() {
		Member member = Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
		setId(member, 11L);
		RefreshToken valid = RefreshToken.builder()
			.member(member)
			.tokenHash("hash")
			.expiresAt(LocalDateTime.now(FIXED_CLOCK).plusDays(1))
			.build();
		given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(valid));
		given(refreshTokenRepository.rotateByTokenHash(anyString(), anyString(), any())).willReturn(1);

		ReissueResult result = authService.reissue("some-refresh-token");

		assertThat(result.refreshToken()).isNotEqualTo("some-refresh-token").isNotBlank();
		assertThat(jwtProvider.parseMemberId(result.accessToken())).isEqualTo(11L);
	}

	@Test
	void processLogout은_리프레시_저장소_삭제를_호출한다() {
		authService.processLogout(1L);

		verify(refreshTokenRepository).deleteByMemberId(1L);
	}

	private static Auth winnerAuth(String providerId) {
		Member winner = Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build();
		setId(winner, 9L);
		return Auth.builder().provider(Provider.KAKAO).providerId(providerId).member(winner).build();
	}

	// 테스트 전용 리플렉션 헬퍼: Member.id는 @GeneratedValue(IDENTITY)라 빌더로 설정 불가하고 실제 저장 없이는
	// 채워지지 않으므로, 순수 Mockito 단위 테스트에서 memberId 단정을 위해 리플렉션으로 주입함.
	private static void setId(Member member, Long id) {
		try {
			Field idField = Member.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(member, id);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

}
