package com.jeongbiseo.domain.recommendation;

import java.time.LocalDate;
import java.util.List;

/**
 * 신청자와 지원금 1건 사이의 매칭 판정 결과임(값 객체).
 *
 * @param subsidyId 지원금 식별자
 * @param regionDemoted 지역 불일치로 강등됐는지 여부(탈락이 아니라 정렬 후순위, matched와 별개 축)
 * @param matched 연령·고용·소득·가구 4조건 매칭 통과 여부(산정불가와 별개 축, 미입력은 matched = true)
 * @param matchScore 표시용 점수(연령·지역·고용·소득·가구 5축 통과 개수, 제약없음·불명도 통과로 집계됨)
 * @param confirmedMatchCount 지역을 뺀 4축 중 RESTRICTED 세부기준을 사용자 정보로 통과 확인한 개수(0에서 4, 배지·조건부
 * 타이브레이크 재료)
 * @param uncomputableReasons 산정불가 사유 목록(비어있으면 산정 가능)
 * @param deadline 신청 마감일(criteria에서 옮김, 정렬 재료)
 * @param sourceId 원천 소스 식별자(criteria에서 옮김, 정렬 타이브레이크 재료)
 * @param externalId 원천 내 외부 식별자(criteria에서 옮김, 정렬 타이브레이크 재료)
 */
public record MatchResult(Long subsidyId, boolean regionDemoted, boolean matched, int matchScore,
		int confirmedMatchCount, List<EligibilityReason> uncomputableReasons, LocalDate deadline, String sourceId,
		String externalId) {

	public boolean uncomputable() {
		return !uncomputableReasons.isEmpty();
	}

}
