package com.jeongbiseo.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보(v2/user/me) 응답 DTO임. 이메일은 동의항목 미제공 시 kakao_account 자체가 없거나 email이 null일 수
 * 있음(kakao-oauth-setup 스킬의 비즈 앱 전환 함정).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record KakaoAccount(String email) {
	}

}
