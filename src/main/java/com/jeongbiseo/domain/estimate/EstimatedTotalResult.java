package com.jeongbiseo.domain.estimate;

import java.util.List;

import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 예상 총액 계산 결과임(값 객체). 일시금 현금과 월 지급 현금을 각각 계산해 분리한 두 합계와, 총액에 못 넣은 별도 혜택 목록을 담음(D-C 각각
 * 계산). 강등건은 별도 혜택으로 들어가 총액에서 빠지되 totalCount에는 포함됨(D-B).
 *
 * @param oneTimeItems 일시금 현금 합산 포함 항목
 * @param monthlyItems 월 지급 현금 합산 포함 항목(amountMin/Max에 월 지급액)
 * @param separateItems 총액 미포함 별도 혜택과 사유
 * @param cashTotalMin 일시금 현금 총액 하한(원)
 * @param cashTotalMax 일시금 현금 총액 상한(원)
 * @param monthlyTotalMin 월 지급 현금 총액 하한(원, 월액 단순 합)
 * @param monthlyTotalMax 월 지급 현금 총액 상한(원, 월액 단순 합)
 */
public record EstimatedTotalResult(List<IncludedItem> oneTimeItems, List<IncludedItem> monthlyItems,
		List<SeparateItem> separateItems, long cashTotalMin, long cashTotalMax, long monthlyTotalMin,
		long monthlyTotalMax) {

	/**
	 * 모집단 전체 건수(일시금 포함 더하기 월 지급 포함 더하기 별도 혜택)를 반환함. count-first 카드의 헤드라인 N임.
	 * @return 총 건수
	 */
	public int totalCount() {
		return oneTimeItems.size() + monthlyItems.size() + separateItems.size();
	}

	/**
	 * 예상 총액에 합산된 지원금과 금액임. 일시금은 amountMin/Max에 예상 금액, 월 지급은 amountMin/Max에 월 지급액을 담음.
	 *
	 * @param subsidyId 지원금 식별자
	 * @param name 지원금명
	 * @param amountMin 금액 하한(원)
	 * @param amountMax 금액 상한(원)
	 */
	public record IncludedItem(Long subsidyId, String name, long amountMin, long amountMax) {

	}

	/**
	 * 총액에 못 넣은 별도 혜택과 사유임.
	 *
	 * @param subsidyId 지원금 식별자
	 * @param name 지원금명
	 * @param paymentType 지급 방식(미확인이면 null)
	 * @param reason 제외 사유
	 * @param note 사용자 안내 문구
	 */
	public record SeparateItem(Long subsidyId, String name, PaymentType paymentType, EstimateExclusionReason reason,
			String note) {

	}

}
