package com.jeongbiseo.infra.client.gov24.dto;

/**
 * JA 소득 플래그와 원문("중위소득 N%" 언급)의 일치 여부임(임무 지시 4장). 기존 감사 테스트(Gov24JaFieldParserTest의
 * flagVsOriginalTextAudit_*, 64건 중 14건 불일치 = 21.88%)가 리포트로만 남기던 것을 레코드 단위 필드로 승격함.
 */
public enum IncomeConsistencyStatus {

	// 원문 언급 상한을 커버하는 JA 구간이 전부 Y임 — 일치. 스냅샷 실측 50건
	CONSISTENT,

	// 원문 언급 상한을 커버하지 못하는 JA 구간이 하나 이상 null임 — 불일치(과소 커버 또는 플래그 구조 소실). 이
	// 불일치는 파서 버그가 아니라 gov24 원문 데이터의 성질임(SNAPSHOT_META.md 플래그 대 원문 대조 감사 참조). 스냅샷
	// 실측 14건
	CONFLICT,

	// 원문에 "중위소득 N%" 언급 자체가 없어 대조 불가능(JA 플래그만으로는 원문과의 일치 여부를 판단할 수 없음). 스냅샷
	// 실측 1,033건
	NO_TEXT_EVIDENCE

}
