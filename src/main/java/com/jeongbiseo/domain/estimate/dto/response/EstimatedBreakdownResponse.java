package com.jeongbiseo.domain.estimate.dto.response;

import java.util.List;

import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 예상 총액 내역 응답임(API명세서 20번). 일시금 현금 합산 대상(items)과 월 지급 현금(monthlyItems), 총액에 못 넣은 별도
 * 혜택(separateBenefits)을 각각 분리해 냄(D-C). 항목 클릭 시 지원금 상세(15번)로 이동함.
 *
 * @param cashTotalMin 일시금 현금 총액 하한(원)
 * @param cashTotalMax 일시금 현금 총액 상한(원)
 * @param monthlyTotalMin 월 지급 현금 총액 하한(원)
 * @param monthlyTotalMax 월 지급 현금 총액 상한(원)
 * @param currency 통화(KRW)
 * @param isEstimate 추정값 여부(true)
 * @param items 일시금 현금 합산 대상 목록
 * @param monthlyItems 월 지급 현금 목록
 * @param separateBenefits 총액 미포함 별도 혜택 목록
 */
public record EstimatedBreakdownResponse(Long cashTotalMin, Long cashTotalMax, Long monthlyTotalMin,
		Long monthlyTotalMax, String currency, boolean isEstimate, List<CashItem> items, List<MonthlyItem> monthlyItems,
		List<SeparateBenefit> separateBenefits) {

	/**
	 * 일시금 현금 합산 항목임.
	 *
	 * @param subsidyId 지원금 식별자
	 * @param name 지원금명
	 * @param amountMin 예상 수령액 하한(원)
	 * @param amountMax 예상 수령액 상한(원)
	 * @param paymentType 지급 방식(CASH)
	 * @param includedInTotal 총액 합산 포함 여부(true)
	 */
	public record CashItem(Long subsidyId, String name, Long amountMin, Long amountMax, PaymentType paymentType,
			boolean includedInTotal) {

	}

	/**
	 * 월 지급 현금 항목임.
	 *
	 * @param subsidyId 지원금 식별자
	 * @param name 지원금명
	 * @param monthlyAmountMin 월 지급액 하한(원)
	 * @param monthlyAmountMax 월 지급액 상한(원)
	 * @param paymentType 지급 방식(MONTHLY)
	 */
	public record MonthlyItem(Long subsidyId, String name, Long monthlyAmountMin, Long monthlyAmountMax,
			PaymentType paymentType) {

	}

	/**
	 * 총액에 못 넣은 별도 혜택 항목임.
	 *
	 * @param subsidyId 지원금 식별자
	 * @param name 지원금명
	 * @param paymentType 지급 방식(미확인이면 UNKNOWN)
	 * @param note 총액 미포함 사유
	 */
	public record SeparateBenefit(Long subsidyId, String name, PaymentType paymentType, String note) {

	}

}
