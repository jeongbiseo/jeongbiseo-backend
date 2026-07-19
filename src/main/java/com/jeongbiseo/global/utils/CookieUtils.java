package com.jeongbiseo.global.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 HttpOnly 쿠키로 내리고 지우는 유틸임. INYRO 운영 검증 패턴을 각색함. 리프레시 토큰은 JS에서 읽지 못하게 HttpOnly로
 * 두고, HTTPS 전용(Secure)에 루트 경로로 심어 프론트 콜백·재발급·로그아웃 흐름에서 브라우저가 자동으로 실어 보내게 함. SameSite는 크로스
 * 사이트 배포(프론트와 백엔드 도메인 분리)를 지원하려 프로퍼티로 뺌.
 */
@Component
public class CookieUtils {

	/** 리프레시 토큰 쿠키명임. 재발급·로그아웃 흐름이 같은 이름으로 읽고 지움. */
	public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

	/** 리프레시 토큰 만료일수임(일 단위). 쿠키 max-age를 초로 환산할 때 씀. */
	private final long refreshExpirationDays;

	/** 쿠키 SameSite 속성값임. 크로스 사이트 전송이 필요하면 None, 동일 사이트면 Lax 등으로 지정함. */
	private final String sameSite;

	public CookieUtils(@Value("${app.auth.refresh.expiration-days:14}") long refreshExpirationDays,
			@Value("${app.auth.cookie.same-site:None}") String sameSite) {
		this.refreshExpirationDays = refreshExpirationDays;
		this.sameSite = sameSite;
	}

	/** 리프레시 토큰을 HttpOnly·Secure 쿠키로 응답에 실음. max-age는 만료일수를 초로 환산함. */
	public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
		cookie.setHttpOnly(true);
		cookie.setSecure(true);
		cookie.setPath("/");
		cookie.setMaxAge((int) (refreshExpirationDays * 24 * 60 * 60));
		cookie.setAttribute("SameSite", this.sameSite);
		response.addCookie(cookie);
	}

	/** 리프레시 토큰 쿠키를 즉시 만료시킴. 심을 때와 같은 이름·속성에 값 null·max-age 0으로 덮어써 브라우저가 지우게 함. */
	public void deleteRefreshTokenCookie(HttpServletResponse response) {
		Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
		cookie.setHttpOnly(true);
		cookie.setSecure(true);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		cookie.setAttribute("SameSite", this.sameSite);
		response.addCookie(cookie);
	}

}
