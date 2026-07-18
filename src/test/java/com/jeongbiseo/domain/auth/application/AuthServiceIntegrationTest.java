package com.jeongbiseo.domain.auth.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.auth.client.GoogleOAuthClient;
import com.jeongbiseo.domain.auth.client.KakaoOAuthClient;
import com.jeongbiseo.domain.auth.client.OAuthUserInfo;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.auth.repository.RefreshTokenRepository;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.member.service.MemberService;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.jwt.JwtProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * AuthService 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL, Docker 필요). 방식 B 전환으로
 * state 서버검증은 사라졌고, OAuthClient는 @MockitoBean으로 가짜 프로필을 반환해 실제 IdP 호출 없이 콜백 1회의 자동가입·JWT
 * 발급·리프레시 저장, 재콜백의 기존 회원 로그인, 리프레시 회전과 재사용 거부, 로그아웃, Model A 탈퇴 후처리(auth·refresh 삭제 더하기
 * 재로그인 신규가입)를 종단으로 고정함.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuthServiceIntegrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	@Autowired
	private AuthService authService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private AuthRepository authRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private KakaoOAuthClient kakaoOAuthClient;

	@MockitoBean
	private GoogleOAuthClient googleOAuthClient;

	// AuthService는 provider()를 매 호출마다 지연 조회한다(AuthService 생성자 주석 참고). 이 스텁이 없으면
	// @MockitoBean 기본 응답(null)이 실제 provider와 일치하지 않아 VALID400_0으로 잘못 거절됨.
	@BeforeEach
	void stubProviders() {
		given(this.kakaoOAuthClient.provider()).willReturn(Provider.KAKAO);
		given(this.googleOAuthClient.provider()).willReturn(Provider.GOOGLE);
	}

	// 테스트가 @Transactional이 아니라 실제로 커밋되므로(REQUIRES_NEW 자동가입 가시성 확보), 매 테스트 뒤 FK 순서로 정리함.
	@AfterEach
	void cleanUp() {
		this.refreshTokenRepository.deleteAll();
		this.authRepository.deleteAll();
		this.memberRepository.deleteAll();
	}

	@Test
	void 콜백_1회로_회원과_auth가_생성되고_JWT와_리프레시가_발급된다() {
		given(kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-1", "user1@example.com"));

		LoginResult result = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");

		assertThat(result.isNewMember()).isTrue();
		assertThat(result.onboardingCompleted()).isFalse();
		assertThat(result.refreshToken()).isNotBlank();
		Long memberId = this.jwtProvider.parseMemberId(result.accessToken());
		assertThat(this.memberRepository.findById(memberId)).isPresent();
		assertThat(this.authRepository.findByProviderAndProviderIdWithMember(Provider.KAKAO, "kakao-uid-1"))
			.isPresent();
		assertThat(this.refreshTokenRepository.findByMemberId(memberId)).isPresent();
	}

	@Test
	void 같은_providerId로_재콜백하면_기존_회원으로_로그인된다() {
		given(kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-2", "user2@example.com"));
		LoginResult first = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");

		LoginResult second = this.authService.handleCallback("kakao", "code-2", "verifier-2", "https://front/callback");

		assertThat(second.isNewMember()).isFalse();
		assertThat(this.jwtProvider.parseMemberId(second.accessToken()))
			.isEqualTo(this.jwtProvider.parseMemberId(first.accessToken()));
	}

	@Test
	void reissue는_새_쌍을_주고_구_리프레시_재사용은_거부한다() {
		given(kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-3", "user3@example.com"));
		LoginResult issued = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");

		ReissueResult rotated = this.authService.reissue(issued.refreshToken());

		assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());
		assertThat(this.jwtProvider.parseMemberId(rotated.accessToken()))
			.isEqualTo(this.jwtProvider.parseMemberId(issued.accessToken()));
		assertThatThrownBy(() -> this.authService.reissue(issued.refreshToken())).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void processLogout은_리프레시_행을_삭제한다() {
		given(kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-4", "user4@example.com"));
		LoginResult issued = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");
		Long memberId = this.jwtProvider.parseMemberId(issued.accessToken());

		this.authService.processLogout(memberId);

		assertThat(this.refreshTokenRepository.findByMemberId(memberId)).isEmpty();
	}

	@Test
	void 탈퇴하면_auth와_리프레시가_삭제되고_재로그인은_신규가입이_된다() {
		given(kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-5", "user5@example.com"));
		LoginResult issued = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");
		Long memberId = this.jwtProvider.parseMemberId(issued.accessToken());

		this.memberService.delete(memberId, null);

		assertThat(this.authRepository.findByProviderAndProviderIdWithMember(Provider.KAKAO, "kakao-uid-5")).isEmpty();
		assertThat(this.refreshTokenRepository.findByMemberId(memberId)).isEmpty();

		LoginResult rejoin = this.authService.handleCallback("kakao", "code-6", "verifier-6", "https://front/callback");

		assertThat(rejoin.isNewMember()).isTrue();
		assertThat(this.jwtProvider.parseMemberId(rejoin.accessToken())).isNotEqualTo(memberId);
	}

}
