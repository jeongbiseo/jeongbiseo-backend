package com.jeongbiseo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.recommendation.ApplicantProfile;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.subsidy.SubsidyReader;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.global.apiPayload.code.RecommendationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * RecommendationService 단위 테스트임(순수 JUnit, SubsidyReader는 테스트 더블로 대체, 스프링 컨텍스트 없음).
 * RecommendationPolicy 판정 자체는 RecommendationPolicyTest가 정본이고, 이 클래스는 판정 앞뒤(기수령 제외,
 * REC500_1 래핑, limit 정규화, 정렬과 상한)만 검증함.
 */
class RecommendationServiceTest {

	private static final LocalDate AS_OF = LocalDate.of(2026, 7, 14);

	private static final ApplicantProfile APPLICANT = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
			IncomeBracket.UNDER_200, 1);

	// 신청자(만 27세, 관악구)와 항상 매칭되는 지원금 조건 빌더임(NATIONWIDE, 연령 19~34, 조건 전무)
	private static SubsidyCriteria matchingCriteria(long subsidyId) {
		return new SubsidyCriteria(subsidyId, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, 100_000L, 300_000L, null, PaymentType.CASH);
	}

	private static List<SubsidyCriteria> matchingCriteriaList(int count) {
		return IntStream.rangeClosed(1, count).mapToObj(RecommendationServiceTest::matchingCriteria).toList();
	}

	@Test
	void recommend_wrapsUnexpectedError_asRecommendationServerError() {
		// BDD "매칭 0건은 빈 배열의 정상 응답, REC500_1은 예기치 못한 오류일 때만": findCandidates 자체가 실패하는
		// 경로(REC500_1 유발)를 짚음. 0건(정상)과 서버 오류(비정상)를 구분하는 것이 이 테스트의 목적임
		SubsidyReader failingReader = new StubSubsidyReader(null, true);
		RecommendationService service = new RecommendationService(failingReader);

		assertThatThrownBy(() -> service.recommend(APPLICANT, Set.of(), AS_OF, null))
			.isInstanceOf(CustomException.class)
			.extracting(thrown -> ((CustomException) thrown).getErrorCode())
			.isEqualTo(RecommendationErrorCode.RECOMMENDATION_SERVER_ERROR);
	}

	@Test
	void recommend_preservesCause_whenWrappingUnexpectedError() {
		// REC500_1로 감싸도 원인 예외가 유실되지 않아야 서버 로그에서 실제 실패 지점을 추적할 수 있음(P1 회귀 고정)
		SubsidyReader failingReader = new StubSubsidyReader(null, true);
		RecommendationService service = new RecommendationService(failingReader);

		assertThatThrownBy(() -> service.recommend(APPLICANT, Set.of(), AS_OF, null))
			.isInstanceOf(CustomException.class)
			.hasCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage("추천 후보 조회 중 예기치 못한 오류(테스트 더블)");
	}

	@Test
	void recommend_usesDefaultLimit_whenLimitNull() {
		// normalizeLimit: null -> DEFAULT_LIMIT(3). 후보 7건 중 3건만 노출돼야 기본값이 실제로 적용된 것임
		SubsidyReader reader = new StubSubsidyReader(matchingCriteriaList(7), false);
		RecommendationService service = new RecommendationService(reader);

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, null);

		assertThat(items).hasSize(RecommendationService.DEFAULT_LIMIT);
	}

	@Test
	void recommend_fallsBackToDefaultLimit_whenLimitZeroOrNegative() {
		// 0 이하 거부는 컨트롤러가 VALID400_0으로 담당하고, 서비스는 방어적으로 DEFAULT_LIMIT(3)로 보정함(음수가
		// .limit()에 닿아 터지지
		// 않게). 5가 아니라 3건이 나와야 방어 보정이 동작한 것임
		SubsidyReader reader = new StubSubsidyReader(matchingCriteriaList(7), false);
		RecommendationService service = new RecommendationService(reader);

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, 0);

		assertThat(items).hasSize(RecommendationService.DEFAULT_LIMIT);
	}

	@Test
	void recommend_clampsToMaxLimit_whenLimitExceedsMax() {
		// normalizeLimit: limit > MAX_LIMIT(20) -> 20으로 클램프. 후보 25건에서 20건만 나와야 함
		SubsidyReader reader = new StubSubsidyReader(matchingCriteriaList(25), false);
		RecommendationService service = new RecommendationService(reader);

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, 100);

		assertThat(items).hasSize(RecommendationService.MAX_LIMIT);
	}

	@Test
	void recommend_usesGivenLimit_whenWithinRange() {
		// normalizeLimit: 1..MAX_LIMIT 범위 안이면 입력값을 그대로 씀(보정하지 않는 경로)
		SubsidyReader reader = new StubSubsidyReader(matchingCriteriaList(7), false);
		RecommendationService service = new RecommendationService(reader);

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, 5);

		assertThat(items).hasSize(5);
		assertThat(items).extracting(item -> item.summary().subsidyId()).containsExactly(1L, 2L, 3L, 4L, 5L);
	}

	@Test
	void recommend_appliesScopeFilterBeforeMatching() {
		List<SubsidyCriteria> candidates = List.of(matchingCriteria(1L),
				matchingCriteria(2L, TargetAudience.BUSINESS, OccupationRestriction.NONE),
				matchingCriteria(3L, TargetAudience.MIXED, OccupationRestriction.NONE),
				matchingCriteria(4L, TargetAudience.UNKNOWN, OccupationRestriction.NONE),
				matchingCriteria(5L, TargetAudience.PERSONAL, OccupationRestriction.PRIMARY_INDUSTRY_ONLY));
		RecommendationService service = new RecommendationService(new StubSubsidyReader(candidates, false));

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, 5);

		assertThat(items).extracting(item -> item.summary().subsidyId()).containsExactly(1L, 3L, 4L);
	}

	private static SubsidyCriteria matchingCriteria(long subsidyId, TargetAudience targetAudience,
			OccupationRestriction occupationRestriction) {
		return new SubsidyCriteria(subsidyId, targetAudience, occupationRestriction, 19, 34, RegionScope.NATIONWIDE,
				null, null, null, null, 100_000L, 300_000L, null, PaymentType.CASH);
	}

	// 마감순 정렬과 소스 다양성 re-rank(DeadlineRanking · SourceDiversityReranker) 통합 시나리오임
	// (IMPL-PLAN 3.4절 3번).
	@Test
	void recommend_sortsByDeadlineAscending_andGuaranteesSourceDiversity() {
		List<SubsidyCriteria> candidates = List.of(deadlineCriteria(1L, LocalDate.of(2026, 7, 18), "youthcenter", "Y1"),
				deadlineCriteria(2L, LocalDate.of(2026, 7, 19), "youthcenter", "Y2"),
				deadlineCriteria(3L, LocalDate.of(2026, 7, 25), "youthcenter", "Y3"),
				deadlineCriteria(4L, null, "gov24", "G1"), deadlineCriteria(5L, null, "gov24", "G2"),
				deadlineCriteria(6L, null, "gov24", "G3"));
		RecommendationService service = new RecommendationService(new StubSubsidyReader(candidates, false));

		List<RecommendationItem> items = service.recommend(APPLICANT, Set.of(), AS_OF, 3);

		assertThat(items).hasSize(3);
		List<LocalDate> nonNullDeadlines = items.stream()
			.map(item -> item.matchResult().deadline())
			.filter(java.util.Objects::nonNull)
			.toList();
		assertThat(nonNullDeadlines).isSorted();
		assertThat(items).extracting(item -> item.matchResult().sourceId()).contains("gov24", "youthcenter");
		List<Boolean> nullFlags = items.stream().map(item -> item.matchResult().deadline() == null).toList();
		for (int i = 0; i < nullFlags.size() - 1; i++) {
			if (nullFlags.get(i)) {
				assertThat(nullFlags.get(i + 1)).isTrue();
			}
		}
	}

	private static SubsidyCriteria deadlineCriteria(long subsidyId, LocalDate deadline, String sourceId,
			String externalId) {
		return new SubsidyCriteria(subsidyId, TargetAudience.PERSONAL, OccupationRestriction.NONE, null, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, null, null, null, null, 100_000L, 300_000L, null,
				PaymentType.CASH, deadline, sourceId, externalId, null);
	}

	// SubsidyReader 테스트 더블임. throwOnFindCandidates가 true면 findCandidates에서
	// RuntimeException을 던져 REC500_1 경로를 재현하고, 아니면 candidates를 그대로 반환함. findSummaries는
	// 요청받은 id로 최소한의 표시 정보를 즉석 조립함(도메인 판정과 무관한 표시용 필드라 값 자체는 임의임). 팀 SubsidyReader는
	// 3메서드만 두므로(검색은 순위 4 소관이라 이 인터페이스 밖) lab의 search 메서드 오버라이드는 이식하지 않음.
	private static final class StubSubsidyReader implements SubsidyReader {

		private final List<SubsidyCriteria> candidates;

		private final boolean throwOnFindCandidates;

		StubSubsidyReader(List<SubsidyCriteria> candidates, boolean throwOnFindCandidates) {
			this.candidates = candidates;
			this.throwOnFindCandidates = throwOnFindCandidates;
		}

		@Override
		public List<SubsidyCriteria> findCandidates(LocalDate asOf) {
			if (throwOnFindCandidates) {
				throw new IllegalStateException("추천 후보 조회 중 예기치 못한 오류(테스트 더블)");
			}
			return candidates;
		}

		@Override
		public List<SubsidySummary> findSummaries(List<Long> subsidyIds) {
			return subsidyIds.stream()
				.map(id -> new SubsidySummary(id, "지원금" + id, "테스트기관", null, "요약", 100_000L, 300_000L))
				.toList();
		}

		@Override
		public LocalDateTime findLatestDataUpdatedAt() {
			return null;
		}

	}

}
