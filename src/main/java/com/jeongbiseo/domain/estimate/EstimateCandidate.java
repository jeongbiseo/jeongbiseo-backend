package com.jeongbiseo.domain.estimate;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.infra.client.common.dto.AmountKind;

/**
 * 예상 총액 분류에 필요한 후보 스냅샷임(값 객체). 추천 파이프라인이 고른 matched inScope 항목을 EstimatedTotalCalculator가
 * 소비할 최소 필드만 담음. 표시용 SubsidySummary(name만)와 매칭용 SubsidyCriteria(paymentType 등)에 흩어진 값을 한
 * 곳으로 모아 계산기가 storage 타입에 의존하지 않게 함.
 *
 * @param subsidyId 지원금 식별자
 * @param name 지원금명
 * @param paymentType 지급 방식(null이면 미확인으로 분류함)
 * @param targetAudience 지원 대상 주체 구분
 * @param amountKind 금액 표현 성격. 과거 수기·시드 행은 null일 수 있음
 * @param estimatedAmountMin 예상 지원금액 하한(원, 미제공 시 null)
 * @param estimatedAmountMax 예상 지원금액 상한(원, 미제공 시 null)
 * @param monthlyAmount 월 지급액(원, 해당 없거나 미제공 시 null)
 * @param regionDemoted 지역 불일치 강등 여부(강등건은 총액에서 제외, 건수에는 포함)
 */
public record EstimateCandidate(Long subsidyId, String name, PaymentType paymentType, TargetAudience targetAudience,
		AmountKind amountKind, Long estimatedAmountMin, Long estimatedAmountMax, Long monthlyAmount,
		boolean regionDemoted) {

	/** amountKind 영속화 전 생성자와의 호환용임. 금액 성격을 모르면 null로 둠. */
	public EstimateCandidate(Long subsidyId, String name, PaymentType paymentType, TargetAudience targetAudience,
			Long estimatedAmountMin, Long estimatedAmountMax, Long monthlyAmount, boolean regionDemoted) {
		this(subsidyId, name, paymentType, targetAudience, null, estimatedAmountMin, estimatedAmountMax, monthlyAmount,
				regionDemoted);
	}

}
