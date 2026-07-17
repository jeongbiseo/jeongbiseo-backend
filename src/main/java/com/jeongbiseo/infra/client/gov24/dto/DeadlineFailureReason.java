package com.jeongbiseo.infra.client.gov24.dto;

/**
 * 신청기한 자유텍스트 파싱 실패 사유임. Gov24Parser가 LocalDate로 못 바꾼 원문을 유형별로 분류해 PoC 리포트에 씀(PLAN.md 3장
 * W4 절, 미결-05 apply_start_date 컬럼 도입 여부의 판단 근거).
 */
public enum DeadlineFailureReason {

	// "상시신청" — 특정 마감일 없이 상시 접수함
	ALWAYS_OPEN,

	// "예산 소진 시까지" — 마감일이 예산 상황에 달려 있어 고정 날짜가 없음
	BUDGET_EXHAUSTION,

	// "...규정에 따름" — 마감일이 이 응답 밖의 별도 규정을 참조함
	EXTERNAL_REGULATION_REFERENCE,

	// 위 세 유형에 안 걸리고 알려진 날짜 패턴(범위 형식, 절대 연월일 형식)도 없는 나머지 전부
	UNRECOGNIZED_FORMAT

}
