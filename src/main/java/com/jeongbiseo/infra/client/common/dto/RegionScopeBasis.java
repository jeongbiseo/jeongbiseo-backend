package com.jeongbiseo.infra.client.common.dto;

/**
 * 지역 정보의 근거를 명시함(임무 지시 3장 — "소관기관이 곧 적용 지역이라는 것은 유추이지 선언된 필드가 아니다"). 근거가 셋으로 갈리는 이유는 4종
 * 소스가 지역을 주는 방식이 실제로 다르기 때문임 — 온통청년만 선언된 코드 필드(zipCd)를 주고, gov24는 소관기관명 유추뿐이며,
 * 기업마당·K-Startup은 축약 지역 텍스트만 줌(조사 리포트 2장 G1-지역 행).
 */
public enum RegionScopeBasis {

	// 소스가 지역을 코드 필드로 선언함 — 온통청년 zipCd(법정시군구코드 5자리, 전수 채움 99.3%). 유추가 아니라
	// 선언이므로 이 근거에서만 RegionConfidence.HIGH를 쓸 수 있음(조사 리포트 3장 G5)
	DECLARED_REGION_CODE,

	// 소관기관명 문자열에서 유추함(선언된 지역 필드가 gov24 원문에 없음 — 조사 리포트 3장 G1-지역)
	INFERRED_FROM_AGENCY_NAME,

	// 지역 텍스트 필드에서 유추함 — 기업마당 jrsdInsttNm(부처명이 섞임), K-Startup supt_regin("경북" 같은
	// 축약형). 코드가 아니라 텍스트라 법정동코드 조인이 곧바로 되지 않음(조사 리포트 2장 G1-지역 행)
	INFERRED_FROM_REGION_TEXT,

	// 지역을 유추하지 못함(RegionLevel.NATIONAL과 짝) — 근거 자체가 없다는 뜻이라 유추 실패와 구분함
	NOT_APPLICABLE

}
