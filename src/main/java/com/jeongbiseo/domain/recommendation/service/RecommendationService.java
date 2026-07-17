package com.jeongbiseo.domain.recommendation.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jeongbiseo.domain.estimate.EstimateCandidate;
import com.jeongbiseo.domain.recommendation.ApplicantProfile;
import com.jeongbiseo.domain.recommendation.DeadlineRanking;
import com.jeongbiseo.domain.recommendation.MatchResult;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.recommendation.RecommendationPolicy;
import com.jeongbiseo.domain.recommendation.RecommendationRanking;
import com.jeongbiseo.domain.recommendation.SourceDiversityReranker;
import com.jeongbiseo.domain.subsidy.SubsidyReader;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.global.apiPayload.code.RecommendationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 추천 파이프라인 전체를 조율하는 도메인 서비스임: 후보 조회, 기수령 제외, RecommendationPolicy 적용, 정렬, 소스 다양성 re-rank,
 * limit 적용(PLAN.md 3장 W3 절). 매칭 4조건과 지역 강등 판정 자체는 RecommendationPolicy에 위임하고, 정렬은
 * RecommendationRanking에 위임하며, 이 서비스는 그 앞뒤(후보 수집, 필터, 표시 정보 결합)만 담당함. 매칭 조건 분기를 여기서 다시 쓰지
 * 않음.
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
	// ("JPA 비의존 순수 자바" 설계를 유지하며 컨테이너 의존을 강제하지 않음).
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
	 * 신청자 프로필로 추천 리스트를 계산함. 기수령 지원금은 후보에서 먼저 제외하고, 매칭 통과분을 RecommendationRanking(현행
	 * DeadlineRanking, 마감 임박순 nullsLast + 동점 tieHash)으로 정렬한 뒤 소스 다양성 re-rank를 적용해 최대
	 * limit건 반환함. 매칭 0건이면 빈 리스트를 반환함(REC-321, 에러 아님). 계산 도중 예기치 못한 오류가 나면 REC500_1로 감싸
	 * 던짐(추천 0건과 서버 오류를 구분).
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

	/**
	 * 예상 총액 분류에 필요한 후보 목록을 계산함. recommend와 동일한 선택 로직(inScope·매칭·정렬·리랭크·상한)을 공유하되, 표시용
	 * RecommendationItem이 아니라 분류에 필요한 필드를 담은 EstimateCandidate로 반환함.
	 * paymentType·targetAudience ·월 지급액은 SubsidySummary에 없고 SubsidyCriteria에만 있어
	 * criteria를 살려 옮김. 예외는 감싸지 않고 그대로 올려 호출부(EstimatedAmountService)가 AMT500_1로 감싸게 함(추천의
	 * REC500_1과 도메인 분리).
	 * @param applicant 신청자 프로필
	 * @param receivedSubsidyIds 기수령 지원금 id 목록(후보에서 제외)
	 * @param asOf 신청 가능 여부를 판정할 기준일
	 * @param limit 노출 상한(예상 총액 모집단 캡)
	 * @return 예상 총액 후보 목록(정렬·상한 적용)
	 */
	public List<EstimateCandidate> estimateCandidates(ApplicantProfile applicant, Set<Long> receivedSubsidyIds,
			LocalDate asOf, Integer limit) {
		List<Selected> selected = select(applicant, receivedSubsidyIds, asOf, limit);
		if (selected.isEmpty()) {
			return List.of();
		}
		List<Long> ids = selected.stream().map(item -> item.result().subsidyId()).toList();
		List<SubsidySummary> summaries = subsidyReader.findSummaries(ids);
		return selected.stream().map(item -> toCandidate(item, summaries)).toList();
	}

	private List<RecommendationItem> doRecommend(ApplicantProfile applicant, Set<Long> receivedSubsidyIds,
			LocalDate asOf, Integer limit) {
		List<Selected> selected = select(applicant, receivedSubsidyIds, asOf, limit);
		if (selected.isEmpty()) {
			return List.of();
		}

		List<Long> matchedIds = selected.stream().map(item -> item.result().subsidyId()).toList();
		List<SubsidySummary> summaries = subsidyReader.findSummaries(matchedIds);
		return selected.stream().map(item -> toItem(item.result(), summaries)).toList();
	}

	// 매칭 통과 후보를 정렬·리랭크·상한 적용해 고르는 공유 선택 로직임(recommend와 estimateCandidates가 함께 씀).
	// 반환은 criteria와 매칭 결과를 함께 담은 Selected라 표시(summary)와 분류(criteria) 어느 쪽으로도 매핑됨.
	private List<Selected> select(ApplicantProfile applicant, Set<Long> receivedSubsidyIds, LocalDate asOf,
			Integer limit) {
		int effectiveLimit = normalizeLimit(limit);
		List<Selected> ranked = subsidyReader.findCandidates(asOf)
			.stream()
			.filter(criteria -> !receivedSubsidyIds.contains(criteria.subsidyId()))
			.filter(policy::inScope)
			.map(criteria -> new Selected(criteria, policy.evaluate(applicant, criteria)))
			.filter(item -> item.result().matched())
			.sorted(Comparator.comparing(Selected::result, ranking.comparator()))
			.toList();

		// 되매핑은 MatchResult 인스턴스 동일성으로 함. rerank가 subList 더하기 교체라 동일 인스턴스를 반환하므로
		// IdentityHashMap 키가 무가정으로 정확함(subsidyId toMap의 중복 시 실패 모드 전이를 피함).
		Map<MatchResult, Selected> byResult = new IdentityHashMap<>();
		for (Selected item : ranked) {
			byResult.put(item.result(), item);
		}
		List<MatchResult> rerankedResults = reranker.rerank(ranked.stream().map(Selected::result).toList(),
				effectiveLimit);
		return rerankedResults.stream().map(byResult::get).toList();
	}

	private static EstimateCandidate toCandidate(Selected selected, List<SubsidySummary> summaries) {
		SubsidyCriteria criteria = selected.criteria();
		SubsidySummary summary = summaries.stream()
			.filter(candidate -> candidate.subsidyId().equals(criteria.subsidyId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("지원금 표시 정보를 찾을 수 없어요: " + criteria.subsidyId()));
		return new EstimateCandidate(criteria.subsidyId(), summary.name(), criteria.paymentType(),
				criteria.targetAudience(), criteria.estimatedAmountMin(), criteria.estimatedAmountMax(),
				criteria.monthlyAmount(), selected.result().regionDemoted());
	}

	private static RecommendationItem toItem(MatchResult result, List<SubsidySummary> summaries) {
		SubsidySummary summary = summaries.stream()
			.filter(candidate -> candidate.subsidyId().equals(result.subsidyId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("지원금 표시 정보를 찾을 수 없어요: " + result.subsidyId()));
		return new RecommendationItem(summary, result);
	}

	// ponytail: 서비스 직접 호출 경로(테스트) 방어용 보정임. HTTP 검증은 컨트롤러 소관임.
	private static int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	// 선택 단계에서 criteria(분류·표시 재료)와 매칭 결과를 함께 나르는 내부 값 객체임. 정렬은 result 기준으로 함.
	private record Selected(SubsidyCriteria criteria, MatchResult result) {

	}

}
