package com.jeongbiseo.domain.recommendation;

import java.time.LocalDate;
import java.util.List;

/**
 * 신청자와 지원금 1건 사이의 매칭 판정 결과임(값 객체).
 *
 * @param subsidyId 지원금 식별자
 * @param matched 5조건 매칭 통과 여부(산정불가와 별개 축, 미입력은 matched = true)
 * @param matchScore 정렬용 점수
 * @param uncomputableReasons 산정불가 사유 목록(비어있으면 산정 가능)
 * @param deadline 신청 마감일(criteria에서 옮김, 정렬 재료)
 * @param sourceId 원천 소스 식별자(criteria에서 옮김, 정렬 타이브레이크 재료)
 * @param externalId 원천 내 외부 식별자(criteria에서 옮김, 정렬 타이브레이크 재료)
 */
public record MatchResult(Long subsidyId, boolean matched, int matchScore, List<EligibilityReason> uncomputableReasons,
		LocalDate deadline, String sourceId, String externalId) {

	public boolean uncomputable() {
		return !uncomputableReasons.isEmpty();
	}

}
