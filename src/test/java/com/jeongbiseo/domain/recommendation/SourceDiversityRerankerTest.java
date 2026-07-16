package com.jeongbiseo.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * SourceDiversityReranker 단위 테스트임(순수 JUnit). 정렬된 상위 limit 창 안에 소스별 최소 1건을 보장하는 교체 규칙을
 * 검증함(05-sort-total-decisions IMPL-PLAN 3.4절 (가)~(사) 7종 시나리오). SourceDiversityReranker가
 * package-private이라 이 테스트도 같은 패키지(com.jeongbiseo.domain.recommendation)에 둠.
 */
class SourceDiversityRerankerTest {

	private final SourceDiversityReranker reranker = new SourceDiversityReranker();

	@Test
	void rerank_promotesMissingSource_intoWindow_whenOutsideLimit() {
		// (가) gov24 후보 1건이 창 밖(youthcenter 3건 뒤)에 있으면 상위 limit에 승격되고 크기는 불변함
		List<MatchResult> ranked = List.of(mr(1L, "youthcenter", "Y1"), mr(2L, "youthcenter", "Y2"),
				mr(3L, "youthcenter", "Y3"), mr(4L, "gov24", "G1"));

		List<MatchResult> result = this.reranker.rerank(ranked, 3);

		assertThat(result).hasSize(3);
		assertThat(result).extracting(MatchResult::sourceId).contains("gov24");
	}

	@Test
	void rerank_keepsInputUnchanged_whenBothSourcesAlreadyInWindow() {
		// (나) 이미 두 소스가 창 안이면 무교체(입력 순서 그대로)
		List<MatchResult> ranked = List.of(mr(1L, "gov24", "G1"), mr(2L, "youthcenter", "Y1"), mr(3L, "gov24", "G2"));

		List<MatchResult> result = this.reranker.rerank(ranked, 3);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void rerank_doesNotReplace_whenLimitIsOne() {
		// (다) limit 1이면 1자리에 다양성 강제가 불가하므로 교체 안 함
		List<MatchResult> ranked = List.of(mr(1L, "youthcenter", "Y1"), mr(2L, "gov24", "G1"));

		List<MatchResult> result = this.reranker.rerank(ranked, 1);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L);
	}

	@Test
	void rerank_doesNotReplace_whenOnlySingleSourcePresent() {
		// (라) 단일 소스만 존재하면 교체 대상 자체가 없어 무교체
		List<MatchResult> ranked = List.of(mr(1L, "gov24", "G1"), mr(2L, "gov24", "G2"), mr(3L, "gov24", "G3"));

		List<MatchResult> result = this.reranker.rerank(ranked, 2);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L, 2L);
	}

	@Test
	void rerank_evictsLowestRankedItem_ofSourceWithAtLeastTwoInWindow() {
		// (마) 희생자는 소스 2건 이상 보유한 소스 중 창 안 최하위 항목
		List<MatchResult> ranked = List.of(mr(1L, "youthcenter", "Y1"), mr(2L, "youthcenter", "Y2"),
				mr(3L, "youthcenter", "Y3"), mr(4L, "gov24", "G1"));

		List<MatchResult> result = this.reranker.rerank(ranked, 3);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L, 2L, 4L);
	}

	@Test
	void rerank_treatsNullSourceId_asNonPromotableAndNonEvictable() {
		// (바) sourceId null 항목은 다양성 축에서 제외되되 자리는 유지(승격 대상도 희생 대상도 아님)
		List<MatchResult> ranked = List.of(mr(1L, null, "N1"), mr(2L, "youthcenter", "Y1"), mr(3L, "youthcenter", "Y2"),
				mr(4L, "gov24", "G1"));

		List<MatchResult> result = this.reranker.rerank(ranked, 3);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L, 2L, 4L);
	}

	@Test
	void rerank_returnsAllMatched_whenFewerThanLimit() {
		// (사) matched가 limit보다 적으면 전량 반환
		List<MatchResult> ranked = List.of(mr(1L, "gov24", "G1"), mr(2L, "youthcenter", "Y1"));

		List<MatchResult> result = this.reranker.rerank(ranked, 5);

		assertThat(result).extracting(MatchResult::subsidyId).containsExactly(1L, 2L);
	}

	private static MatchResult mr(long subsidyId, String sourceId, String externalId) {
		return new MatchResult(subsidyId, true, 5, List.of(), LocalDate.of(2026, 7, 20), sourceId, externalId);
	}

}
