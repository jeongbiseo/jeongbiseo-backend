package com.jeongbiseo.domain.recommendation;

import java.util.Comparator;

/**
 * 매칭 통과 후보의 노출 순서를 정하는 전략임. RecommendationService는 정렬 기준을 직접 박지 않고 이 전략에 위임함. 정렬
 * 재설계(HANDOFF 9.B-5, 미결 대기)가 확정되면 구현만 교체함. 필터·스코프 판정은 RecommendationPolicy가 정본이고, 이
 * 인터페이스는 순서만 다룸.
 */
public interface RecommendationRanking {

	Comparator<MatchResult> comparator();

}
