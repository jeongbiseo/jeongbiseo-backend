package com.jeongbiseo.domain.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 지급 방식임. UNKNOWN은 원문에 지급 방식이 명시되지 않은 경우이며, 금액이 있더라도 지급 방식 미확정 산정불가
 * (PAYMENT_TYPE_UNCONFIRMED) 판정의 조건이 됨.
 */
// 라벨 정본은 API명세서 PaymentType 절이고 버킷 분류 정본은 EstimatedTotalCalculator.exclusionReason임.
// 응답 DTO가 이 enum을 타입 그대로 노출해야 이 설명과 허용값이 /v3/api-docs에 실림(String으로 평탄화하면 스키마에서 사라짐).
@Schema(description = """
		지급유형. 예상 총액에서 CASH는 일시금 총액에, MONTHLY는 월 지급 총액에 넣고 나머지는 별도 혜택으로 분류함.
		CASH: 현금성(금액이 확정된 건만 합산하고 금액 미상이면 별도 혜택)
		MONTHLY: 월지급(월액이 있는 건만 월 합계에 넣음)
		VOUCHER: 바우처 / IN_KIND: 현물 / REDUCTION: 감면 (셋 다 비현금이라 별도 혜택)
		UNKNOWN: 미상(외부 소스가 지급유형을 판정해 주지 못한 상태). 화면에는 "미상"으로 표시하고 별도 혜택으로 분류함""")
public enum PaymentType {

	CASH, MONTHLY, VOUCHER, IN_KIND, REDUCTION, UNKNOWN

}
