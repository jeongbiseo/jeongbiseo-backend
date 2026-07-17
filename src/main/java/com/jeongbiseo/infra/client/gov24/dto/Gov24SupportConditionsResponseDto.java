package com.jeongbiseo.infra.client.gov24.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보조금24 supportConditions 응답 최상위 래퍼임.
 *
 * @param totalCount 전체 지원조건 수
 * @param data 지원조건 항목 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Gov24SupportConditionsResponseDto(@JsonProperty("totalCount") Integer totalCount,
		@JsonProperty("data") List<Gov24SupportConditionDto> data) {

}
