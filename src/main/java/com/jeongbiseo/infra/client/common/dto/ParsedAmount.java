package com.jeongbiseo.infra.client.common.dto;

import java.util.List;

/**
 * 지원내용 원문에서 뽑은 금액 정보임(임무 지시 2장). amountCandidates는 원문에 등장한 순서 그대로이고 원 단위(1원=1)임 —
 * "만원"·"천원"·"억원" 단위 표기는 파서가 실제 원화 값으로 환산함(예 "100만원" -> 1,000,000).
 *
 * @param amountKind 금액 표현의 성격 3+1분류
 * @param amountCandidates 원문에서 뽑은 모든 금액 후보(원 단위, 등장 순서). NONE이면 빈 리스트
 * @param minAmount amountCandidates 중 최솟값(NONE이면 null)
 * @param maxAmount amountCandidates 중 최댓값(NONE이면 null)
 * @param amountUnit 첫 금액 후보 근처에서 관찰된 단위(가구·인·회·월 중 하나, 아무 단서도 없으면 기본값 "원". NONE이면 null) —
 * 관측 기반 참고값이지 모든 후보에 공통 적용되는 보장은 아님(ponytail: 레코드 1개당 단위 1개로 단순화)
 * @param conditionSummary CONDITIONAL일 때만 채우는 원문 발췌(조건 표현이 걸린 금액 앞뒤 문맥, 200자 이내). 그 외
 * null
 * @param parseStatus 추출 시도 성공 여부
 */
public record ParsedAmount(AmountKind amountKind, List<Long> amountCandidates, Long minAmount, Long maxAmount,
		String amountUnit, String conditionSummary, AmountParseStatus parseStatus) {

}
