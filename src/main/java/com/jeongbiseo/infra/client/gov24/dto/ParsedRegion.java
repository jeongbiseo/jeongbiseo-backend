package com.jeongbiseo.infra.client.gov24.dto;

import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;

/**
 * 소관기관명에서 유추한 지역 정보임(임무 지시 3장). 법정동코드는 아직 없음 — sidoName·sigunguName은 한글 명칭만 담고, 코드 매핑은 회의
 * 안건 17번 결정 이후 별도 필드로 추가함(TODO, 없는 코드를 지어내지 않음).
 *
 * @param sidoName 시도명(RegionLevel.NATIONAL이면 null)
 * @param sigunguName 시군구명(RegionLevel.SIGUNGU에서만 채움, 그 외 null)
 * @param regionLevel 유추 단계(시군구/시도/전국·유추불가)
 * @param scopeBasis 지역 정보의 근거(소관기관명 유추 또는 해당없음)
 * @param confidence 유추 신뢰도(선언된 필드가 아니므로 HIGH를 쓰지 않음)
 */
public record ParsedRegion(String sidoName, String sigunguName, RegionLevel regionLevel, RegionScopeBasis scopeBasis,
		RegionConfidence confidence) {

}
