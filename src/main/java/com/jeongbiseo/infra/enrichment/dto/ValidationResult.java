package com.jeongbiseo.infra.enrichment.dto;

/**
 * 보강 결과 검증 판정임. 통과분만 활성 저장하고, 거부분은 사유와 함께 지표로만 남김(배치 설계 6장).
 *
 * @param accepted 활성 저장 대상인지 여부
 * @param value 통과한 보강 값. 거부면 null
 * @param reason 거부 사유. 통과면 null
 * @param detail 거부 상세(로그·지표용). 통과면 null
 */
public record ValidationResult(boolean accepted, AmountEnrichment value, RejectionReason reason, String detail) {

	public static ValidationResult accept(AmountEnrichment value) {
		return new ValidationResult(true, value, null, null);
	}

	public static ValidationResult reject(RejectionReason reason, String detail) {
		return new ValidationResult(false, null, reason, detail);
	}

}
