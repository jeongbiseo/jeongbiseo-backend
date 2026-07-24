package com.jeongbiseo.global.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtAuthenticationFilter의 인증 세팅 계약을 고정함(AUTH-W001). 유효 토큰이면 principal=memberId 인증을 심고,
 * 무토큰·만료·위조·비Bearer면 인증을 심지 않고(익명) 체인을 계속함. 조용한 특정 회원 대입이 없음을 고정함.
 */
class JwtAuthenticationFilterTest {

	private static final String SECRET = "test-secret-key-for-jwt-authentication-filter-32bytes-plus";

	private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 유효한_Bearer_토큰이면_principal에_memberId를_심는다() throws Exception {
		JwtProvider provider = jwtProvider(NOW);
		MockFilterChain chain = doFilterWith(provider, "Bearer " + provider.issueAccessToken(42L));

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(42L);
		// 필터는 인증만 심고 요청을 계속 흘려보냄
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void 만료된_토큰이면_인증을_심지_않는다() throws Exception {
		String expired = jwtProvider(NOW).issueAccessToken(42L);
		// 발급 31분 뒤 시계로 검증(TTL 30분)
		doFilterWith(jwtProvider(NOW.plus(Duration.ofMinutes(31))), "Bearer " + expired);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void 위조된_토큰이면_인증을_심지_않는다() throws Exception {
		doFilterWith(jwtProvider(NOW), "Bearer not-a-real-jwt");

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void Authorization_헤더가_없으면_인증을_심지_않는다() throws Exception {
		doFilterWith(jwtProvider(NOW), null);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void Bearer_접두가_아니면_인증을_심지_않는다() throws Exception {
		doFilterWith(jwtProvider(NOW), "Basic dXNlcjpwYXNz");

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private static JwtProvider jwtProvider(Instant at) {
		return new JwtProvider(Clock.fixed(at, ZoneId.of("Asia/Seoul")), SECRET, 30L);
	}

	private static MockFilterChain doFilterWith(JwtProvider provider, String authorizationHeader) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (authorizationHeader != null) {
			request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
		}
		MockFilterChain chain = new MockFilterChain();
		new JwtAuthenticationFilter(provider).doFilter(request, new MockHttpServletResponse(), chain);
		return chain;
	}

}
