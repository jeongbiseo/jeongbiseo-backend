package com.jeongbiseo.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * socialCallback 성공 시 반환 포맷
 */
public record SocialCallbackResponse(String accessToken, String refreshToken, String tokenType,
		@JsonProperty("isNewMember") boolean isNewMember,
		@JsonProperty("onboardingCompleted") boolean onboardingCompleted) {
}