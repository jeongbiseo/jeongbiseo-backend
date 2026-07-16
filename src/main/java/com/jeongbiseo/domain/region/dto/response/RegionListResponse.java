package com.jeongbiseo.domain.region.dto.response;

import java.util.List;

/**
 * 거주지 목록 조회 응답임(API명세서 11번, operationId getRegions). sido 지정 여부에 따라 해당하는 필드만 채우고 나머지는
 * null로 둠.
 *
 * <p>
 * sido 미지정 시: sidoList만 채움(시 또는 도 목록). sido 지정 시: sido와 sigunguList만 채움(해당 시군구 목록).
 * </p>
 *
 * @param sidoList 시 또는 도 목록(sido 미지정 조회일 때만 채움)
 * @param sido 조회한 시 또는 도(sido 지정 조회일 때만 채움)
 * @param sigunguList 시군구 목록(sido 지정 조회일 때만 채움)
 */
public record RegionListResponse(List<String> sidoList, String sido, List<SigunguResponse> sigunguList) {

}
