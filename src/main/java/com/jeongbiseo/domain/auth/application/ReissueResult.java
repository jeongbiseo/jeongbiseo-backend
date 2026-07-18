package com.jeongbiseo.domain.auth.application;

/**
 * 리프레시 회전 결과임(내부용). accessToken은 바디로, refreshToken(회전된 raw)은 컨트롤러가 쿠키로 세팅함.
 */
public record ReissueResult(String accessToken, String refreshToken) {
}
