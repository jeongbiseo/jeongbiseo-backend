package com.jeongbiseo.domain.estimate;

/**
 * 예상 총액 합산 제외 사유와 사용자 안내 문구의 정본임. 모집단이 추천 inScope라 BUSINESS와 PRIMARY_INDUSTRY_ONLY는 실제로는
 * 오지 않으나, 계산기를 입력 가정에 묶지 않으려 방어적으로 유지함(PLAN 3.A).
 */
public enum EstimateExclusionReason {

	REGION_UNVERIFIED("거주 지역이 확인되지 않아 총액에서 제외했어요"),

	MIXED("개인과 사업자 대상이 함께 있어 총액에서 제외했어요"),

	UNKNOWN_AUDIENCE("지원 대상을 확인하지 못해 총액에서 제외했어요"),

	PAYMENT_TYPE_UNKNOWN("지급 방식을 확인하지 못해 총액에서 제외했어요"),

	NON_CASH("현금성 지원이 아니라 총액에서 제외했어요"),

	AMOUNT_MISSING("금액을 확인할 수 없어 총액에서 제외했어요"),

	BUSINESS("기업·사업자 대상 지원사업이라 총액에서 제외했어요"),

	PRIMARY_INDUSTRY_ONLY("농림축수산업 종사자 전용이라 총액에서 제외했어요");

	private final String note;

	EstimateExclusionReason(String note) {
		this.note = note;
	}

	/**
	 * 제외 사유에 대응하는 고정 안내 문구를 반환함.
	 * @return 사용자 안내 문구
	 */
	public String note() {
		return this.note;
	}

}
