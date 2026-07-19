package com.jeongbiseo.domain.auth.dto;

/**
 * reissue(토큰 재발급) 성공 시 반환 바디임. 새 리프레시 토큰은 쿠키로 나가므로 accessToken만 실음.
 */
public record ReissueResponse(String accessToken) {
}
