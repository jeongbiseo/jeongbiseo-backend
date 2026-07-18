package com.jeongbiseo.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 카카오 토큰 교환(oauth/token) 응답 DTO임. access_token만 사용함(사용자 정보 조회에 필요). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
}
