package com.jeongbiseo.domain.estimate;

import java.util.ArrayList;
import java.util.List;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.IncludedItem;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.SeparateItem;

/**
 * 예상 총액을 분류하는 순수 도메인 계산기임(스프링 빈 아님, RecommendationPolicy와 같은 관용). 팀 레포에는 lab의 AmountKind
 * enum이 없어 현금 확정성을 PaymentType 더하기 null 검사로 재유도함(PLAN D3). 각 후보는 first-match로 정확히 한
 * 버킷(일시금·월 지급·별도)에 배치되어 이중 계상이 없음.
 */
public final class EstimatedTotalCalculator {

	/**
	 * 후보 목록을 일시금 현금·월 지급 현금·별도 혜택으로 분류하고 각 총액을 합산함.
	 * @param candidates 추천 inScope에서 고른 후보(상위 노출분)
	 * @return 분류 결과와 두 총액
	 */
	public EstimatedTotalResult calculate(List<EstimateCandidate> candidates) {
		List<IncludedItem> oneTimeItems = new ArrayList<>();
		List<IncludedItem> monthlyItems = new ArrayList<>();
		List<SeparateItem> separateItems = new ArrayList<>();
		long cashTotalMin = 0L;
		long cashTotalMax = 0L;
		long monthlyTotalMin = 0L;
		long monthlyTotalMax = 0L;

		for (EstimateCandidate candidate : candidates) {
			EstimateExclusionReason reason = exclusionReason(candidate);
			if (reason != null) {
				separateItems.add(new SeparateItem(candidate.subsidyId(), candidate.name(), candidate.paymentType(),
						reason, reason.note()));
				continue;
			}

			if (candidate.paymentType() == PaymentType.CASH) {
				long min = candidate.estimatedAmountMin();
				long max = candidate.estimatedAmountMax();
				oneTimeItems.add(new IncludedItem(candidate.subsidyId(), candidate.name(), min, max));
				cashTotalMin += min;
				cashTotalMax += max;
			}
			else {
				long monthly = candidate.monthlyAmount();
				monthlyItems.add(new IncludedItem(candidate.subsidyId(), candidate.name(), monthly, monthly));
				monthlyTotalMin += monthly;
				monthlyTotalMax += monthly;
			}
		}

		return new EstimatedTotalResult(List.copyOf(oneTimeItems), List.copyOf(monthlyItems),
				List.copyOf(separateItems), cashTotalMin, cashTotalMax, monthlyTotalMin, monthlyTotalMax);
	}

	// 분류 우선순위(위에서 아래로 첫 매칭). null 반환이면 합산 포함(호출부에서 CASH·MONTHLY로 다시 가름).
	// paymentType 비교는 == 라 null이어도 안전하게 마지막 기본 분기(PAYMENT_TYPE_UNKNOWN)로 떨어짐.
	private static EstimateExclusionReason exclusionReason(EstimateCandidate candidate) {
		if (candidate.regionDemoted()) {
			return EstimateExclusionReason.REGION_UNVERIFIED;
		}
		if (candidate.targetAudience() == TargetAudience.BUSINESS) {
			return EstimateExclusionReason.BUSINESS;
		}
		if (candidate.targetAudience() == TargetAudience.MIXED) {
			return EstimateExclusionReason.MIXED;
		}
		if (candidate.targetAudience() == TargetAudience.UNKNOWN) {
			return EstimateExclusionReason.UNKNOWN_AUDIENCE;
		}
		if (candidate.paymentType() == PaymentType.CASH) {
			boolean amountConfirmed = candidate.estimatedAmountMin() != null && candidate.estimatedAmountMax() != null;
			return amountConfirmed ? null : EstimateExclusionReason.AMOUNT_MISSING;
		}
		if (candidate.paymentType() == PaymentType.MONTHLY) {
			// ponytail: MONTHLY인데 monthlyAmount가 null이고 min/max만 있어도 월 버킷에 안 넣음. 월액과
			// 총지급액은 의미가 달라 min/max를 월 합계에 넣으면 오합산임(PLAN L3 의도).
			return candidate.monthlyAmount() != null ? null : EstimateExclusionReason.AMOUNT_MISSING;
		}
		if (candidate.paymentType() == PaymentType.VOUCHER || candidate.paymentType() == PaymentType.IN_KIND
				|| candidate.paymentType() == PaymentType.REDUCTION) {
			return EstimateExclusionReason.NON_CASH;
		}
		// paymentType == UNKNOWN 또는 null(레거시·시드 경로 방어). 무배치 항목 0 보장(PLAN M2).
		return EstimateExclusionReason.PAYMENT_TYPE_UNKNOWN;
	}

}
