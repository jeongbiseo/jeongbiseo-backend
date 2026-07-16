package com.jeongbiseo.domain.recommendation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jeongbiseo.domain.recommendation.ApplicantProfile;
import com.jeongbiseo.domain.recommendation.DeadlineRanking;
import com.jeongbiseo.domain.recommendation.MatchResult;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.recommendation.RecommendationPolicy;
import com.jeongbiseo.domain.recommendation.RecommendationRanking;
import com.jeongbiseo.domain.recommendation.SourceDiversityReranker;
import com.jeongbiseo.domain.subsidy.SubsidyReader;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.global.apiPayload.code.RecommendationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 추천 파이프라인 전체를 조율하는 도메인 서비스임: 후보 조회, 기수령 제외, RecommendationPolicy 적용, 정렬, 소스 다양성 re-rank,
 * limit 적용(PLAN.md 3장 W3 절). 5조건 판정 자체는 RecommendationPolicy에 위임하고, 정렬은
 * RecommendationRanking에 위임하며, 이 서비스는 그 앞뒤(후보 수집, 필터, 표시 정보 결합)만 담당함 — 매칭 조건 분기를 여기서 다시
 * 쓰지 않음.
 */
@Service
public class RecommendationService {

	/**
	 * 노출 개수 성능 상한임(API명세서 14번). 초과 요청은 이 값으로 클램프함. 개수 자체는 프론트가 limit로 결정함(HANDOFF
	 * 2.B-14).
	 */
	public static final int MAX_LIMIT = 20;

	/** limit 미지정 시 노출 개수 기본값임(화면 기본 3, API명세서 14번). */
	public static final int DEFAULT_LIMIT = 3;

	private final SubsidyReader subsidyReader;

	private final RecommendationRanking ranking;

	// RecommendationPolicy는 상태 없는 순수 도메인 서비스라 스프링 빈으로 등록하지 않고 여기서 직접 만듦
	// (W1 절 "JPA 비의존 순수 자바" 설계를 유지 — 컨테이너 의존을 강제하지 않음).
	private final RecommendationPolicy policy = new RecommendationPolicy();

	// SourceDiversityReranker는 정렬 전략(교체 가능 축)이 아니라 노출 정책(고정 축)이라 RecommendationRanking DI
	// 축과 분리하고, 스프링 빈이 아닌 순수 자바로 여기서 직접 만듦(policy와 같은 설계 관용).
	private final SourceDiversityReranker reranker = new SourceDiversityReranker();

	// 테스트·실측 하네스용 편의 생성자임(현행 정렬 기본 구현을 주입). 스프링은 아래 2인자 생성자로 빈을 주입함.
	public RecommendationService(SubsidyReader subsidyReader) {
		this(subsidyReader, new DeadlineRanking());
	}

	@Autowired
	public RecommendationService(SubsidyReader subsidyReader, RecommendationRanking ranking) {
		this.subsidyReader = subsidyReader;
		this.ranking = ranking;
	}

	/**
	 * 신청자 프로필로 추천 리스트를 계산함. 기수령 지원금은 후보에서 먼저 제외하고, 매칭 통과분만 점수 내림차순(동점은 subsidyId 오름차순)으로
	 * 정렬해 최대 limit건 반환함. 매칭 0건이면 빈 리스트를 반환함(REC-321, 에러 아님). 계산 도중 예기치 못한 오류가 나면
	 * REC500_1로 감싸 던짐(추천 0건과 서버 오류를 구분, BDD.md "추천 0건과 기수령 제외 필터").
	 * @param applicant 신청자 프로필
	 * @param receivedSubsidyIds 기수령 지원금 id 목록(추천 후보에서 제외)
	 * @param asOf 신청 가능 여부를 판정할 기준일
	 * @param limit 노출 개수(null이면 DEFAULT_LIMIT, MAX_LIMIT 초과는 MAX_LIMIT로 클램프). 0 이하 거부는
	 * 컨트롤러의 HTTP 검증(VALID400_0)이 담당하며, 서비스는 방어적으로 DEFAULT_LIMIT로 보정함
	 * @return 추천 항목 목록(표시 정보 더하기 매칭 결과)
	 */
	public List<RecommendationItem> recommend(ApplicantProfile applicant, Set<Long> receivedSubsidyIds, LocalDate asOf,
			Integer limit) {
		try {
			return doRecommend(applicant, receivedSubsidyIds, asOf, limit);
		}
		catch (RuntimeException e) {
			throw new CustomException(RecommendationErrorCode.RECOMMENDATION_SERVER_ERROR, e);
		}
	}

	private List<RecommendationItem> doRecommend(ApplicantProfile applicant, Set<Long> receivedSubsidyIds,
			LocalDate asOf, Integer limit) {
		int effectiveLimit = normalizeLimit(limit);

		List<MatchResult> ranked = subsidyReader.findCandidates(asOf)
			.stream()
			.filter(criteria -> !receivedSubsidyIds.contains(criteria.subsidyId()))
			.filter(policy::inScope)
			.map(criteria -> policy.evaluate(applicant, criteria))
			.filter(MatchResult::matched)
			.sorted(ranking.comparator())
			.toList();
		List<MatchResult> matched = reranker.rerank(ranked, effectiveLimit);

		if (matched.isEmpty()) {
			return List.of();
		}

		List<Long> matchedIds = matched.stream().map(MatchResult::subsidyId).toList();
		List<SubsidySummary> summaries = subsidyReader.findSummaries(matchedIds);
		return matched.stream().map(result -> toItem(result, summaries)).toList();
	}

	private static RecommendationItem toItem(MatchResult result, List<SubsidySummary> summaries) {
		SubsidySummary summary = summaries.stream()
			.filter(candidate -> candidate.subsidyId().equals(result.subsidyId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("지원금 표시 정보를 찾을 수 없어요: " + result.subsidyId()));
		return new RecommendationItem(summary, result);
	}

	// ponytail: null이면 DEFAULT_LIMIT(3), MAX_LIMIT(20) 초과는 클램프. 0 이하는 컨트롤러가 VALID400_0으로
	// 먼저
	// 막지만, 서비스를 직접 부르는 경로(테스트·실측 하네스)를 위해 방어적으로 DEFAULT_LIMIT로 보정함(음수가 .limit()에 닿아 터지지
	// 않게).
	private static int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
