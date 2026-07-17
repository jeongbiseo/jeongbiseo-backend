package com.jeongbiseo.infra.client.gov24.dto;

/**
 * 소득 조건 신호({@link com.jeongbiseo.domain.common.enums.EligibilitySignal})의 출처임(임무 지시 4장 —
 * "incomeSignal만 저장하면 위험하다"는 지적에 따른 근거 추가). JA 플래그만 있고 원문에 "중위소득 N%" 언급이 없으면 대조 자체가
 * 불가능하므로 출처를 구분함.
 */
public enum IncomeSignalSource {

	// supportConditions JA0201~JA0205 플래그만 근거임(원문에 "중위소득 N%" 언급 없음, 또는 플래그도 원문도
	// 둘 다 없어 UNKNOWN인 기본 상태 포함). 스냅샷 실측 1,033건
	JA_FLAGS,

	// 원문(지원대상·선정기준)에 "중위소득 N%" 언급은 있으나 JA 플래그는 전부 null(공식 supportConditions
	// 응답에 신호가 없음). 스냅샷 실측 1건(B55307700005 소상공인 무료법률구조 — SNAPSHOT_META.md 기록 사례)
	TEXT,

	// JA 플래그와 원문 언급이 둘 다 있어 대조가 가능함. 스냅샷 실측 63건
	BOTH

}
