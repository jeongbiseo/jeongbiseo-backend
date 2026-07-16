package com.jeongbiseo.infra.client.common.dto;

/**
 * 지역 유추의 신뢰도임. gov24 소관기관명 유추는 선언된 필드가 아니므로 신뢰도를 낮게 잡으라는 임무 지시(3장)에 따라 HIGH를 쓰지 않음 —
 * SIGUNGU도 자치법규 교차검증에서 94.32%(465/493건, 시도 또는 시군구명 일치 기준)로 준수한 근거가 있지만 "유추"라는 한계는 남으므로
 * MEDIUM에 그침.
 *
 * <p>
 * HIGH는 4종 소스 통합 회차(2026-07-12)에 추가함 — 온통청년 zipCd가 <b>선언된 코드 필드</b>라 gov24 유추와 같은 등급에 둘 수
 * 없기 때문임(전수 2,648건 코드 인스턴스 122,789개 전부 5자리, code.go.kr 법정동코드 10자리와 직접 조인 확인 — 조사 리포트 3장
 * G5). 즉 HIGH를 안 쓴 것은 원칙이 아니라 <b>그때까지 선언된 지역 필드를 주는 소스가 없었기 때문</b>이었음.
 */
public enum RegionConfidence {

	// 소스가 법정시군구코드를 선언 필드로 줌(RegionScopeBasis.DECLARED_REGION_CODE와 짝). 유추가 아니므로
	// 하드 지역 필터에 쓸 수 있는 유일한 등급임 — 온통청년 전용
	HIGH,

	// 시도와 시군구 2단계까지 유추되고 자치법규 교차검증 일치율이 높음(94.32%). RegionLevel.SIGUNGU에서만 씀
	MEDIUM,

	// 시도만 유추됐거나(RegionLevel.SIDO), 중앙부처 등으로 유추가 성립하지 않음(RegionLevel.NATIONAL — "등"
	// 안에 소관기관명에 공백이 없어 유추가 안 된 실제 지역 기관이 섞여 있을 수 있음, 임무 지시 3장)
	LOW

}
