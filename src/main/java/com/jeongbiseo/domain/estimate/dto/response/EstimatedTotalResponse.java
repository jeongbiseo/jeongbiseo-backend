package com.jeongbiseo.domain.estimate.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예상 총액 카드 응답임(API명세서 19번, count-first). 헤드라인은 건수(totalCount)이고 금액은 현금 확정분(itemCount건)만
 * 보조로 냄. 일시금 현금 총액과 월 지급 현금 총액을 각각 냄(D-C). 강등건은 총액에서 빠지되 totalCount와
 * separateBenefitCount에는 포함됨(D-B).
 *
 * @param totalCount 받을 수 있는 지원금 건수(추천 상위 20건 기준)
 * @param itemCount 일시금 현금 합산에 포함된 지원금 수
 * @param cashTotalMin 일시금 현금 총액 하한(원, itemCount 0이면 null)
 * @param cashTotalMax 일시금 현금 총액 상한(원, itemCount 0이면 null)
 * @param monthlyItemCount 월 지급 현금 합산에 포함된 지원금 수
 * @param monthlyTotalMin 월 지급 현금 총액 하한(원, monthlyItemCount 0이면 null)
 * @param monthlyTotalMax 월 지급 현금 총액 상한(원, monthlyItemCount 0이면 null)
 * @param separateBenefitCount 총액에 포함하지 않은 별도 혜택 수
 * @param currency 통화(KRW)
 * @param isEstimate 추정값 여부(true)
 * @param notice 안내 문구(모집단 라벨 포함)
 */
public record EstimatedTotalResponse(int totalCount, int itemCount,
		@Schema(description = "일시금 현금 총액 하한(원). 합산 대상이 없으면(itemCount 0) 0이 아니라 null임(내역 20번은 같은 경우 0을 냄)",
				nullable = true) Long cashTotalMin,
		@Schema(description = "일시금 현금 총액 상한(원). 합산 대상이 없으면(itemCount 0) 0이 아니라 null임(내역 20번은 같은 경우 0을 냄)",
				nullable = true) Long cashTotalMax,
		int monthlyItemCount,
		@Schema(description = "월 지급 현금 총액 하한(원). 합산 대상이 없으면(monthlyItemCount 0) 0이 아니라 null임(내역 20번은 같은 경우 0을 냄)",
				nullable = true) Long monthlyTotalMin,
		@Schema(description = "월 지급 현금 총액 상한(원). 합산 대상이 없으면(monthlyItemCount 0) 0이 아니라 null임(내역 20번은 같은 경우 0을 냄)",
				nullable = true) Long monthlyTotalMax,
		int separateBenefitCount, String currency, boolean isEstimate, String notice) {

}
