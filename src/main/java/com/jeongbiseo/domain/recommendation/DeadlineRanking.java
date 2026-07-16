package com.jeongbiseo.domain.recommendation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 마감 임박순(deadline 오름차순, null은 뒤) 더하기 동점·null 블록 내부 결정적 타이브레이크를 적용하는 운영 정렬 구현임(정렬 재설계 결정
 * B, 05-sort-total-decisions). 하네스 FLAT_DEADLINE_COMPARATOR(RecommendationRankingHarness
 * 참고 측정)의 운영 승격판이며, tieHash는 MEMBER_ID 없이 sourceId|externalId 2요소로 계산함(무인증 lab 전제,
 * B1-PLAN 3장).
 */
@Component
public class DeadlineRanking implements RecommendationRanking {

	private static final Comparator<MatchResult> COMPARATOR = Comparator
		.comparing(MatchResult::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(DeadlineRanking::tieHash)
		.thenComparing(MatchResult::sourceId, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(MatchResult::externalId, Comparator.nullsLast(Comparator.naturalOrder()));

	@Override
	public Comparator<MatchResult> comparator() {
		return COMPARATOR;
	}

	// 하네스 tieHash(RecommendationRankingHarness.java 370-379행)의 운영 승격판. 키는 MEMBER_ID 없이
	// sourceId|externalId 2요소(무인증 lab 전제, B1-PLAN 3장 설계 결정).
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
