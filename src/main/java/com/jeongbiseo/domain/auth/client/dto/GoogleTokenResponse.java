package com.jeongbiseo.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 구글 토큰 교환 응답 DTO임. 사용자 정보는 별도 호출 없이 id_token(JWT) payload에서 추출함. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(@JsonProperty("access_token") String accessToken,
		@JsonProperty("id_token") String idToken) {
}
