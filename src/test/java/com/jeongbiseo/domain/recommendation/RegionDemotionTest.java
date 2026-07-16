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
		// 강남(11680, 시도 11)만 담은 다중지역 붕괴 지원금(NATIONWIDE+regionCodes)에 세종(36110, 시도 36) 신청자
		// — 시도 prefix가 달라 강등됨. 종전 동작(탈락)과 대비: 종전엔 이 조합이 후보에서 제거됐음. 진짜 전국(아래 c3)과의 대비쌍
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
	void c3_진짜_전국은_불일치_신청자여도_강등하지_않는다() {
		// 반대 방향 회귀(a와 대비쌍): regionCodes가 비어있는 진짜 전국은 regionScope=NATIONWIDE라도 시도가 안 맞는
		// 신청자에게 강등되지 않음. a의 붕괴 지원금(regionCodes 있음)만 강등되고 진짜 전국은 안 되는 것을 박제해,
		// CodeRabbit이 우려한 "전국이 강등되는" 시나리오가 불가능함을 테스트로 증명함
		SubsidyCriteria criteria = nationwideCriteria();
		ApplicantProfile applicant = applicant("36110");

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

	// 실제 적재 상태를 미러함: 다중 지역은 단일 컬럼에 안 담겨 regionScope=NATIONWIDE·regionCode=null로 붕괴되고
	// regionCodes CSV만 채워짐. regionCode를 null로 둬 폴백이 아니라 CSV 경로만 타는지 고립 검증하고,
	// NATIONWIDE인데도
	// regionCodes가 있으면 강등된다는 의미론을 박제함(CodeRabbit이 제안한 REGIONAL 가드 회귀를 이 테스트가 차단함).
	private static SubsidyCriteria regionalCriteria(String regionCodes) {
		return new SubsidyCriteria(1L, TargetAudience.PERSONAL, OccupationRestriction.NONE, null, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX, null,
				PaymentType.CASH, null, null, null, regionCodes);
	}

	private static SubsidyCriteria nationwideCriteria() {
		return new SubsidyCriteria(2L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH);
	}

	private static ApplicantProfile applicant(String regionCode) {
		return new ApplicantProfile(27, regionCode, EmploymentStatus.JOB_SEEKING, IncomeBracket.UNDER_200, 1);
	}

}
