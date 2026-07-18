package com.jeongbiseo.domain.auth.application;

/**
 * 콜백 교환 결과임(내부용). accessToken은 바디로, refreshToken(raw)은 컨트롤러가 쿠키로 세팅함. 서비스는 쿠키를 모르므로 raw
 * 토큰을 그대로 실어 넘김.
 */
public record LoginResult(String accessToken, String refreshToken, boolean isNewMember, boolean onboardingCompleted) {
}
