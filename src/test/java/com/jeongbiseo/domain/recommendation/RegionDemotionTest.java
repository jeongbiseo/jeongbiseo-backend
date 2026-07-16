package com.jeongbiseo.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;

/**
 * 지역 강등 랭킹 회귀 재현 테스트임(순수 JUnit, RecommendationPolicy 더하기 DeadlineRanking 조합,
 * 09-region-demotion PLAN 5장 ABC). 종전엔 REGIONAL 지역 불일치가 후보에서 탈락했으나, 이제는 강등(뒤로 밀되 노출 유지)으로
 * 바뀐 것을 정면으로 대비함.
 */
class RegionDemotionTest {

	private static final long AMOUNT_MIN = 100_000L;

	private static final long AMOUNT_MAX = 300_000L;

	private final RecommendationPolicy policy = new RecommendationPolicy();

	private final DeadlineRanking ranking = new DeadlineRanking();

	@Test
	void a_강등되나_노출은_유지된다() {
		// 강남(11680, 시도 11) 지원금에 세종(36110, 시도 36) 신청자 — 시도 prefix가 달라 강등됨. 종전 동작(탈락)과 대비:
		// 종전엔 이 조합이 후보에서 제거됐음
		SubsidyCriteria criteria = regionalCriteria("11680");
		ApplicantProfile applicant = applicant("36110");

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.regionDemoted()).isTrue();
		assertThat(result.matched()).isTrue();
		// matchScore는 5축 유지라 강등건은 지역 1점을 뺀 4점으로 표시됨(D3)
		assertThat(result.matchScore()).isEqualTo(4);
	}

	@Test
	void b_시도_prefix가_일치하면_정상_노출된다() {
		// 관악(11620)과 강남(11680)은 exact 일치는 아니지만 시도 prefix "11"이 같아 강등 안 함
		SubsidyCriteria criteria = regionalCriteria("11680");
		ApplicantProfile applicant = applicant("11620");

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.regionDemoted()).isFalse();
	}

	@Test
	void c1_지원금_유효_지역코드가_없으면_강등하지_않는다() {
		// regionCodes null, regionScope NATIONWIDE라 유효 지역코드 집합이 비어 강등 판정 불가(D6 조건2)
		SubsidyCriteria criteria = nationwideCriteria();
		ApplicantProfile applicant = applicant("11620");

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.regionDemoted()).isFalse();
	}

	@Test
	void c2_사용자_지역코드가_없으면_강등하지_않는다() {
		// 사용자 regionCode가 null이면 판정 불가라 강등하지 않음(누락-안전 처리, D6 조건1)
		SubsidyCriteria criteria = regionalCriteria("11680");
		ApplicantProfile applicant = applicant(null);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.regionDemoted()).isFalse();
	}

	@Test
	void 정렬_강등건은_마감이_임박해도_비강등건보다_뒤에_온다() {
		MatchResult demoted = new MatchResult(1L, true, true, 5, List.of(), LocalDate.of(2026, 7, 18), "gov24", "D1");
		MatchResult notDemoted = new MatchResult(2L, false, true, 5, List.of(), LocalDate.of(2026, 8, 1), "gov24",
				"N1");

		List<MatchResult> sorted = new ArrayList<>(List.of(demoted, notDemoted));
		sorted.sort(this.ranking.comparator());

		assertThat(sorted).extracting(MatchResult::subsidyId).containsExactly(2L, 1L);
	}

	private static SubsidyCriteria regionalCriteria(String regionCode) {
		return new SubsidyCriteria(1L, TargetAudience.PERSONAL, OccupationRestriction.NONE, null, 19, 34,
				RegionScope.REGIONAL, regionCode, null, null, null, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX,
				null, PaymentType.CASH, null, null, null, regionCode);
	}

	private static SubsidyCriteria nationwideCriteria() {
		return new SubsidyCriteria(2L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH);
	}

	private static ApplicantProfile applicant(String regionCode) {
		return new ApplicantProfile(27, regionCode, EmploymentStatus.JOB_SEEKING, IncomeBracket.UNDER_200, 1);
	}

}
