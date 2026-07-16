package com.jeongbiseo.domain.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정렬된 매칭 결과의 상위 limit 창 안에 등장 소스별 최소 1건을 보장함(B-4 은폐 방지). 레일(확실성 tier) 축이 백로그로 빠져 교체 규칙만
 * 남김. 입력 정렬 순서를 신뢰하고 재정렬하지 않음. 스프링 빈이 아니라 RecommendationService가 직접 생성하는 순수 자바 헬퍼임. lab은
 * 같은 패키지라 package-private이었으나, 팀 레포는 서비스가 service 서브패키지로 분리돼 public으로 노출함(노출 정책 축, DI 축과
 * 분리 유지).
 */
public final class SourceDiversityReranker {

	/**
	 * @param ranked 비교자로 이미 정렬된 매칭 결과 전체
	 * @param limit 노출 상한(normalizeLimit 통과값)
	 * @return 크기 min(limit, ranked.size())의 노출 목록, ranked 내 상대 순서 유지
	 */
	public List<MatchResult> rerank(List<MatchResult> ranked, int limit) {
		int windowSize = Math.min(limit, ranked.size());
		List<MatchResult> selected = new ArrayList<>(ranked.subList(0, windowSize));
		if (selected.size() < 2) {
			return selected;
		}

		Map<MatchResult, Integer> rankIndex = new IdentityHashMap<>();
		for (int index = 0; index < ranked.size(); index++) {
			rankIndex.put(ranked.get(index), index);
		}

		Set<String> visitedSources = new LinkedHashSet<>();
		for (MatchResult candidate : ranked) {
			String sourceId = candidate.sourceId();
			if (sourceId == null || !visitedSources.add(sourceId)) {
				continue;
			}
			boolean alreadyExposed = selected.stream().anyMatch(item -> sourceId.equals(item.sourceId()));
			if (alreadyExposed) {
				continue;
			}
			MatchResult victim = findVictim(selected, sourceId);
			if (victim != null) {
				selected.remove(victim);
				selected.add(candidate);
			}
		}

		selected.sort(Comparator.comparingInt(rankIndex::get));
		return List.copyOf(selected);
	}

	// selected를 뒤에서 앞으로 훑어 "자기 소스가 selected에 2건 이상 남는" 첫 항목(즉 어떤 소스도 0건으로
	// 만들지 않는 최하위)을 찾음. null sourceId 항목과 승격 대상과 같은 소스인 항목은 희생 대상에서 제외함.
	private static MatchResult findVictim(List<MatchResult> selected, String promotingSourceId) {
		for (int index = selected.size() - 1; index >= 0; index--) {
			MatchResult candidate = selected.get(index);
			String candidateSourceId = candidate.sourceId();
			if (candidateSourceId == null || promotingSourceId.equals(candidateSourceId)) {
				continue;
			}
			long countInSelected = selected.stream().filter(item -> candidateSourceId.equals(item.sourceId())).count();
			if (countInSelected >= 2) {
				return candidate;
			}
		}
		return null;
	}

}
