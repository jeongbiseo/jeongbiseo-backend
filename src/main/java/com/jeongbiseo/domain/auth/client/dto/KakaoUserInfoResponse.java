package com.jeongbiseo.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보(v2/user/me) 응답 DTO임. 이메일은 동의항목 미제공 시 kakao_account 자체가 없거나 email이 null일 수
 * 있음(kakao-oauth-setup 스킬의 비즈 앱 전환 함정). 표시용 이름은 실명(name)이 아니라 profile.nickname을 씀 — 실명은
 * 별도 권한이 필요하고, 참고 레포(LikeLion14th)도 카카오는 닉네임만 받아 회원 이름으로 저장함.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record KakaoAccount(String email, Profile profile) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Profile(String nickname) {
	}

}
