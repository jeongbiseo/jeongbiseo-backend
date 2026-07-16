package com.jeongbiseo.domain.common.enums;

/**
 * 지원금 지급 방식임. UNKNOWN은 원문에 지급 방식이 명시되지 않은 경우이며 금액 필드 전무와 함께 산정불가(AMOUNT_INFO_MISSING) 판정의
 * 조건이 됨.
 */
public enum PaymentType {

	CASH, MONTHLY, VOUCHER, IN_KIND, REDUCTION, UNKNOWN

}
