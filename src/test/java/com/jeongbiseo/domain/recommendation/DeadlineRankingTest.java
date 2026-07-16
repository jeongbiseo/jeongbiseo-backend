package com.jeongbiseo.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * DeadlineRanking 단위 테스트임(순수 JUnit). 마감 임박순(nullsLast), null·동점 블록 내부 tieHash 결정성, 입력 순서
 * 무관성(정렬 안정성이 아니라 비교자 자체의 결정성)을 검증함(05-sort-total-decisions IMPL-PLAN 3.4).
 */
class DeadlineRankingTest {

	private final DeadlineRanking ranking = new DeadlineRanking();

	@Test
	void comparator_ordersByDeadlineAscending_nullsLast() {
		MatchResult early = matchResult(1L, LocalDate.of(2026, 7, 20), "gov24", "A");
		MatchResult later = matchResult(2L, LocalDate.of(2026, 8, 1), "gov24", "B");
		MatchResult none = matchResult(3L, null, "gov24", "C");

		List<MatchResult> sorted = sort(List.of(none, later, early));

		assertThat(sorted).extracting(MatchResult::subsidyId).containsExactly(1L, 2L, 3L);
	}

	@Test
	void comparator_ordersNullDeadlineBlock_byTieHash() {
		MatchResult a = matchResult(1L, null, "gov24", "AAA");
		MatchResult b = matchResult(2L, null, "youthcenter", "BBB");
		MatchResult c = matchResult(3L, null, "gov24", "ZZZ");

		List<MatchResult> input = List.of(a, b, c);
		List<Long> expectedOrder = input.stream()
			.sorted(Comparator.comparing(DeadlineRankingTest::tieHash))
			.map(MatchResult::subsidyId)
			.toList();

		List<MatchResult> sorted = sort(input);

		assertThat(sorted).extracting(MatchResult::subsidyId).containsExactlyElementsOf(expectedOrder);
	}

	@Test
	void comparator_ordersTiedDeadlineBlock_byTieHash() {
		LocalDate sameDeadline = LocalDate.of(2026, 7, 20);
		MatchResult a = matchResult(1L, sameDeadline, "gov24", "AAA");
		MatchResult b = matchResult(2L, sameDeadline, "youthcenter", "BBB");
		MatchResult c = matchResult(3L, sameDeadline, "gov24", "ZZZ");

		List<MatchResult> input = List.of(a, b, c);
		List<Long> expectedOrder = input.stream()
			.sorted(Comparator.comparing(DeadlineRankingTest::tieHash))
			.map(MatchResult::subsidyId)
			.toList();

		List<MatchResult> sorted = sort(input);

		assertThat(sorted).extracting(MatchResult::subsidyId).containsExactlyElementsOf(expectedOrder);
	}

	@Test
	void comparator_isDeterministic_regardlessOfInputOrder() {
		MatchResult a = matchResult(1L, LocalDate.of(2026, 7, 20), "gov24", "A");
		MatchResult b = matchResult(2L, LocalDate.of(2026, 8, 1), "gov24", "B");
		MatchResult c = matchResult(3L, null, "gov24", "C");

		List<MatchResult> forward = sort(List.of(a, b, c));
		List<MatchResult> reversed = sort(List.of(c, b, a));

		assertThat(forward).extracting(MatchResult::subsidyId)
			.containsExactlyElementsOf(reversed.stream().map(MatchResult::subsidyId).toList());
	}

	private List<MatchResult> sort(List<MatchResult> input) {
		List<MatchResult> mutable = new ArrayList<>(input);
		mutable.sort(this.ranking.comparator());
		return mutable;
	}

	private static MatchResult matchResult(long subsidyId, LocalDate deadline, String sourceId, String externalId) {
		return new MatchResult(subsidyId, true, 5, List.of(), deadline, sourceId, externalId);
	}

	// 운영 DeadlineRanking.tieHash(private)의 독립 재현임 — 하드코딩 hex 금지, 같은 알고리즘을 테스트에서
	// 재계산해 기대 순서를 도출함(IMPL-PLAN 3.4 절 지시).
	private static String tieHash(MatchResult result) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String key = result.sourceId() + "|" + result.externalId();
			return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없음", e);
		}
	}

}
