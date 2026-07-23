package com.jeongbiseo.domain.recommendation;

/**
 * 추천 결과의 판단·산정 불가 사유 코드와 고정 안내 문구임. 이 사유가 붙어도 매칭에서는 통과하며, 사용자 정보·지원금 조건·금액 정보 중 어느 쪽이
 * 부족한지 구분함(DISCUSS.md 3.4).
 */
public enum EligibilityReason {

	INCOME_MISSING("소득 정보가 없어 지원금을 산정할 수 없어요"), HOUSEHOLD_UNDETERMINED("가구원 수 정보가 없어 지원금을 산정할 수 없어요"),
	AMOUNT_INFO_MISSING("지원 금액 정보가 공개되지 않아 산정할 수 없어요"), PAYMENT_TYPE_UNCONFIRMED("지급 방식을 확인할 수 없어 합산하지 않았어요"),
	AGE_CONDITION_DETAILS_MISSING("지원금에 연령 제한이 있지만 비교할 세부 기준이 없어 판단할 수 없어요"),
	AGE_CONDITION_UNKNOWN("지원금의 연령 조건이 공개되지 않아 판단할 수 없어요"),
	INCOME_CONDITION_DETAILS_MISSING("지원금에 소득 제한이 있지만 비교할 세부 기준이 없어 판단할 수 없어요"),
	INCOME_CONDITION_UNKNOWN("지원금의 소득 조건이 공개되지 않아 판단할 수 없어요"),
	HOUSEHOLD_CONDITION_DETAILS_MISSING("지원금에 가구 제한이 있지만 비교할 세부 기준이 없어 판단할 수 없어요"),
	HOUSEHOLD_CONDITION_UNKNOWN("지원금의 가구 조건이 공개되지 않아 판단할 수 없어요"),
	EMPLOYMENT_CONDITION_DETAILS_MISSING("지원금에 고용 제한이 있지만 비교할 세부 기준이 없어 판단할 수 없어요"),
	EMPLOYMENT_CONDITION_UNKNOWN("지원금의 고용 조건이 공개되지 않아 판단할 수 없어요");

	// ponytail: 고정 문구. LLM 없음(화면정의서 diff 3.1 결정 1번)
	private final String message;

	EligibilityReason(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	// 자격 축(연령·고용·소득·가구) 불확실 사유인지 반환함. 금액 축 사유는 false임("추가 확인 필요 조건 수"
	// 집계 재료).
	public boolean qualificationUncertainty() {
		return this != AMOUNT_INFO_MISSING && this != PAYMENT_TYPE_UNCONFIRMED;
	}

}
