package com.jeongbiseo.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 소셜 로그인 성공 시 반환 바디임. refreshToken은 바디가 아니라 HttpOnly 쿠키로 나가므로 여기 담지 않고, accessToken과 회원
 * 상태만 실음.
 */
public record SocialCallbackResponse(String accessToken, @JsonProperty("isNewMember") boolean isNewMember,
		@JsonProperty("onboardingCompleted") boolean onboardingCompleted) {
}
