package com.jeongbiseo.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.exception.AuthErrorCode;
import com.jeongbiseo.global.security.jwt.JwtProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * FixedMemberResolver의 회원 식별 계약을 고정함. 무헤더면 고정 회원 폴백(배포 N 백도어, 무토큰 데모와 슬라이스 테스트 전제), 유효
 * 토큰이면 그 토큰의 회원, 토큰이 있으나 무효면 AUTH401_2임.
 */
class FixedMemberResolverTest {

	private static final String SECRET = "test-secret-key-for-fixed-member-resolver-32bytes-plus";

	private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void Authorization_헤더가_없으면_고정회원_1로_폴백한다() {
		bindRequest(null);

		assertThat(resolver(jwtProvider(NOW)).resolveMemberId()).isEqualTo(1L);
	}

	// 요청 컨텍스트 자체가 없는 호출(비웹 경로)도 폴백이어야 함. 폴백이 깨지면 무토큰 데모가 전부 죽음.
	@Test
	void 요청_컨텍스트가_없으면_고정회원_1로_폴백한다() {
		assertThat(resolver(jwtProvider(NOW)).resolveMemberId()).isEqualTo(1L);
	}

	@Test
	void 유효한_Bearer_토큰이면_토큰의_회원id를_반환한다() {
		JwtProvider jwtProvider = jwtProvider(NOW);
		bindRequest("Bearer " + jwtProvider.issueAccessToken(42L));

		assertThat(resolver(jwtProvider).resolveMemberId()).isEqualTo(42L);
	}

	// 조용한 폴백을 금지하는 핵심 단언임. 만료 토큰이 1L로 떨어지면 (가) 서버가 401을 주지 않아 프론트 reissue가 영영 트리거되지 않고
	// (나)
	// 만료 토큰의 회원 탈퇴가 고정 회원 1을 지워 데모 픽스처가 무너짐. 이 단언을 지우지 말 것.
	@Test
	void 만료된_토큰이면_폴백하지_않고_AUTH401_2를_던진다() {
		String expiredToken = jwtProvider(NOW).issueAccessToken(42L);
		bindRequest("Bearer " + expiredToken);
		// 발급 시점보다 31분 뒤 시계로 검증함(액세스 토큰 TTL 기본 30분)
		JwtProvider laterProvider = jwtProvider(NOW.plus(Duration.ofMinutes(31)));

		assertThatThrownBy(() -> resolver(laterProvider).resolveMemberId()).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode())
			.isEqualTo(AuthErrorCode.REFRESH_TOKEN_FAILED);
	}

	@Test
	void 위조된_토큰이면_AUTH401_2를_던진다() {
		bindRequest("Bearer not-a-real-jwt");

		assertThatThrownBy(() -> resolver(jwtProvider(NOW)).resolveMemberId()).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode())
			.isEqualTo(AuthErrorCode.REFRESH_TOKEN_FAILED);
	}

	// Bearer 접두가 아닌 헤더는 액세스 토큰이 아니라고 보고 폴백함(Basic 등).
	@Test
	void Bearer_접두가_아니면_고정회원_1로_폴백한다() {
		bindRequest("Basic dXNlcjpwYXNz");

		assertThat(resolver(jwtProvider(NOW)).resolveMemberId()).isEqualTo(1L);
	}

	private static JwtProvider jwtProvider(Instant at) {
		return new JwtProvider(Clock.fixed(at, ZoneId.of("Asia/Seoul")), SECRET, 30L);
	}

	@SuppressWarnings("unchecked")
	private static FixedMemberResolver resolver(JwtProvider jwtProvider) {
		ObjectProvider<JwtProvider> provider = mock(ObjectProvider.class);
		given(provider.getIfAvailable()).willReturn(jwtProvider);
		return new FixedMemberResolver(provider);
	}

	private static void bindRequest(String authorizationHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (authorizationHeader != null) {
			request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
		}
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

}
