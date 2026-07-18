package com.jeongbiseo.global.utils;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CookieUtils 단위 테스트임. 리프레시 토큰 쿠키를 심는(HttpOnly·Secure·경로·만료·SameSite) 속성과, 삭제 시 값
 * 비움·MaxAge 0으로 브라우저가 지우게 하는 계약을 고정함.
 */
class CookieUtilsTest {

	private static final long EXPIRATION_DAYS = 14;

	private final CookieUtils cookieUtils = new CookieUtils(EXPIRATION_DAYS, "None");

	@Test
	void addRefreshTokenCookie는_HttpOnly_Secure_속성으로_토큰을_심는다() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		cookieUtils.addRefreshTokenCookie(response, "raw-refresh-token");

		Cookie cookie = response.getCookie(CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
		assertThat(cookie).isNotNull();
		assertThat(cookie.getValue()).isEqualTo("raw-refresh-token");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getMaxAge()).isEqualTo((int) (EXPIRATION_DAYS * 24 * 60 * 60));
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
	}

	@Test
	void deleteRefreshTokenCookie는_MaxAge_0으로_쿠키를_만료시킨다() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		cookieUtils.deleteRefreshTokenCookie(response);

		Cookie cookie = response.getCookie(CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
		assertThat(cookie).isNotNull();
		assertThat(cookie.getMaxAge()).isZero();
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/");
	}

}
