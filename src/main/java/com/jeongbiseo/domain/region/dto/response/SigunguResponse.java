package com.jeongbiseo.domain.region.dto.response;

/**
 * 시군구 1건 응답임(API명세서 11번, result.sigunguList 항목).
 *
 * @param code 지역 코드
 * @param name 시군구 이름
 */
public record SigunguResponse(String code, String name) {

}
