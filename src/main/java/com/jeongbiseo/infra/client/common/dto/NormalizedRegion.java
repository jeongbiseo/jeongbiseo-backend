package com.jeongbiseo.infra.client.common.dto;

import java.util.List;

/**
 * 4종 소스 공통 지역 정보임. gov24 전용 {@code ParsedRegion}(소관기관명 유추, 코드 없음)을 일반화한 것으로, 차이는 <b>코드
 * 목록을 담는다</b>는 점 하나임 — 온통청년만 지역을 코드로 주기 때문임.
 *
 * <p>
 * <b>regionCodes를 단수가 아니라 목록으로 둔 이유</b>: 온통청년 zipCd는 콤마 구분 다중 코드이고, 전수 2,648건 분포가 단일
 * 29.6%(783건) / 2에서 9개 28.2%(746건) / <b>10개 이상 41.6%(1,101건)</b>임(조사 리포트 3장 G5). 10개 이상이
 * 최대 버킷이라 단일 컬럼으로 받으면 다수 레코드에서 지역 정보가 잘림. 파서 단계에서까지 잘라 버리면 복구가 불가능하므로 여기서는 전부 보존하고, DB 저장
 * 구조(단일 컬럼 유지 대 조인 테이블 승격)는 회의 결정에 맡김.
 *
 * <p>
 * 코드 체계는 <b>법정동코드 시군구 5자리</b>로 확정함(조사 리포트 3장 G5). zipCd 5자리에 "00000"을 붙이면 code.go.kr
 * 법정동코드 10자리와 직접 조인됨(표본 10건 실측). 2026-07-01 행정구역 개편(전남광주통합특별시, 인천 신설 4구) 반영본을 쓸 것.
 *
 * @param regionCodes 법정동코드 시군구 5자리 목록. <b>선언된 코드가 있을 때만</b> 채움(온통청년). 코드를 안 주는 소스는 빈
 * 목록이며, sidoName·sigunguName에서 코드를 <b>역산해 채우지 말 것</b> — 없는 코드를 지어내는 것임
 * @param sidoName 시도명(유추 또는 선언). 지역을 못 잡으면 null
 * @param sigunguName 시군구명. 시군구까지 못 잡으면 null
 * @param regionLevel 지역 판정 단계(시군구 / 시도 / 전국·유추불가)
 * @param scopeBasis 지역 정보의 근거 — 이 값이 DECLARED_REGION_CODE인지 INFERRED_*인지에 따라 하드 필터 사용 여부가
 * 갈림
 * @param confidence 신뢰도. DECLARED_REGION_CODE에서만 HIGH를 씀
 */
public record NormalizedRegion(List<String> regionCodes, String sidoName, String sigunguName, RegionLevel regionLevel,
		RegionScopeBasis scopeBasis, RegionConfidence confidence) {

	/**
	 * 지역 근거가 전혀 없는 상태임(중앙부처 소관 등).
	 * @return 전국·유추불가 지역
	 */
	public static NormalizedRegion national() {
		return new NormalizedRegion(List.of(), null, null, RegionLevel.NATIONAL, RegionScopeBasis.NOT_APPLICABLE,
				RegionConfidence.LOW);
	}

}
