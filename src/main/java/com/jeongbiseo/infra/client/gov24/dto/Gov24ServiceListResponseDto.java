package com.jeongbiseo.infra.client.gov24.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보조금24 serviceList·serviceDetail 응답 최상위 래퍼임. 두 엔드포인트가 동일한 {page, perPage, totalCount,
 * currentCount, matchCount, data} 구조라 하나의 DTO로 겸용함.
 *
 * @param totalCount 전체 서비스 수
 * @param data 서비스 항목 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Gov24ServiceListResponseDto(@JsonProperty("totalCount") Integer totalCount,
		@JsonProperty("data") List<Gov24ServiceItemDto> data) {

}
