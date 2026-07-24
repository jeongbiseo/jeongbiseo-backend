package com.jeongbiseo.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.recommendation.RecommendationPolicy.ConditionOutcome;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;

/**
 * RecommendationPolicy 단위 테스트임(순수 JUnit, 스프링 컨텍스트 없음). QA.md 2.1 순서 2번에서 14번까지 대응함.
 * BDD.md "추천 조회와 매칭 경계" 그리고 "산정불가 판정" 기능과 1대1 대응함.
 *
 * <p>
 * 핵심 불변식: 소득과 가구가 미입력이어도 matched는 true이고 uncomputableReasons에 사유가 붙음
 * (matchIncome_passesWithReason_whenBracketNull,
 * matchHousehold_passesWithReason_whenSizeNull). "탈락"이 아니라 "산정불가"임(DISCUSS.md 3.4).
 * </p>
 */
class RecommendationPolicyTest {

	private static final long AMOUNT_MIN = 100_000L;

	private static final long AMOUNT_MAX = 300_000L;

	private RecommendationPolicy policy;

	@BeforeEach
	void setUp() {
		policy = new RecommendationPolicy();
	}

	@Test
	void inScope_excludesBusinessAndPrimaryIndustryOnly() {
		SubsidyCriteria business = scopeCriteria(TargetAudience.BUSINESS, OccupationRestriction.NONE);
		SubsidyCriteria primaryIndustry = scopeCriteria(TargetAudience.PERSONAL,
				OccupationRestriction.PRIMARY_INDUSTRY_ONLY);

		assertThat(policy.inScope(business)).isFalse();
		assertThat(policy.inScope(primaryIndustry)).isFalse();
	}

	@Test
	void inScope_includesPersonalMixedAndUnknown() {
		assertThat(policy.inScope(scopeCriteria(TargetAudience.PERSONAL, OccupationRestriction.NONE))).isTrue();
		assertThat(policy.inScope(scopeCriteria(TargetAudience.MIXED, OccupationRestriction.NONE))).isTrue();
		assertThat(policy.inScope(scopeCriteria(TargetAudience.UNKNOWN, OccupationRestriction.NONE))).isTrue();
	}

	private static SubsidyCriteria scopeCriteria(TargetAudience targetAudience,
			OccupationRestriction occupationRestriction) {
		return new SubsidyCriteria(1L, targetAudience, occupationRestriction, null, null, RegionScope.NATIONWIDE, null,
				null, null, null, 100_000L, 100_000L, null, PaymentType.CASH);
	}

	// NATIONWIDE에 ageMin 19, ageMax 34만 고정하고 나머지 조건은 호출부에서 채우는 테스트 전용
	// 빌더임. 3개 테스트가 공유하는 SubsidyCriteria 생성 반복을 줄임(checkstyle 120자 제한 대응 겸함).
	private static SubsidyCriteria nationwideCriteria(long subsidyId, Long amountMin, Long amountMax,
			PaymentType paymentType) {
		return new SubsidyCriteria(subsidyId, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, amountMin, amountMax, null, paymentType);
	}

	// ---- matchAge ----

	@Test
	void matchAge_passes_whenAgeEqualsMin() {
		assertThat(policy.matchAge(19, 19, 34)).isTrue();
	}

	@Test
	void matchAge_passes_whenAgeEqualsMax() {
		assertThat(policy.matchAge(34, 19, 34)).isTrue();
	}

	@Test
	void matchAge_fails_whenAgeBelowMinOrAboveMax() {
		assertThat(policy.matchAge(18, 19, 34)).isFalse();
		assertThat(policy.matchAge(35, 19, 34)).isFalse();
	}

	@Test
	void matchAge_passes_whenBoundNull() {
		// ageMax가 null이면 상한 무제한(BDD "ageMax가 null인 지원금은 상한 제한이 없다")
		assertThat(policy.matchAge(60, 19, null)).isTrue();
		// ageMin이 null이면 하한 무제한(대칭 경계)
		assertThat(policy.matchAge(5, null, 34)).isTrue();
	}

	// ---- regionDemoted (D6) ----

	@Test
	void regionDemoted_alwaysFalse_whenNationwide() {
		// NATIONWIDE는 regionCodes가 없으므로 유효 지역코드 집합이 비어 강등 자체가 불가함(D6 조건2 불충족)
		SubsidyCriteria criteria = regionCriteria(RegionScope.NATIONWIDE, null, null);

		assertThat(policy.regionDemoted(criteria, "11620")).isFalse();
	}

	@Test
	void regionDemoted_false_whenUserSidoPrefixMatchesCriteriaCode() {
		// exact 일치가 아니어도 시도 prefix(앞 2자리)가 같으면 강등 안 함(D5)
		SubsidyCriteria criteria = regionCriteria(RegionScope.REGIONAL, "11680", null);

		assertThat(policy.regionDemoted(criteria, "11620")).isFalse();
	}

	@Test
	void regionDemoted_true_whenUserSidoPrefixDiffers() {
		// 강남(11680, 시도 11) 지원금에 세종(36110, 시도 36) 신청자는 시도 prefix가 달라 강등됨
		SubsidyCriteria criteria = regionCriteria(RegionScope.REGIONAL, "11680", null);

		assertThat(policy.regionDemoted(criteria, "36110")).isTrue();
	}

	@Test
	void regionDemoted_fails_whenRegionalCodeMissing() {
		// REGIONAL인데 regionCodes와 regionCode 둘 다 없으면 유효 지역코드 집합이 비어 강등 없이 노출됨(D6 조건2, 동작
		// 반전
		// 박제)
		SubsidyCriteria criteria = regionCriteria(RegionScope.REGIONAL, null, null);

		assertThat(policy.regionDemoted(criteria, "11620")).isFalse();
	}

	@Test
	void regionDemoted_usesRegionCodesCsv_overSingleRegionCode() {
		// regionCodes CSV가 있으면 그 집합을 우선 사용함(D4). 관악(11620)이 CSV 안에 있으므로 강등 안 함
		SubsidyCriteria criteria = regionCriteria(RegionScope.REGIONAL, "11680", "11680,11620");

		assertThat(policy.regionDemoted(criteria, "11620")).isFalse();
	}

	private static SubsidyCriteria regionCriteria(RegionScope regionScope, String regionCode, String regionCodes) {
		return new SubsidyCriteria(1L, TargetAudience.PERSONAL, OccupationRestriction.NONE, null, 19, 34, regionScope,
				regionCode, null, null, null, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH,
				null, null, null, regionCodes);
	}

	// ---- matchEmployment ----

	@Test
	void matchEmployment_passesAll_whenTagsNull() {
		assertThat(policy.matchEmployment(EmploymentStatus.JOB_SEEKING, null)).isTrue();
		// TC-DEMO-013: OTHER도 tags null이면 통과
		assertThat(policy.matchEmployment(EmploymentStatus.OTHER, null)).isTrue();
	}

	@Test
	void matchEmployment_checksCsvContainment() {
		assertThat(policy.matchEmployment(EmploymentStatus.EMPLOYED, "EMPLOYED,STUDENT")).isTrue();
		// BDD "employmentTags CSV에 신청자 고용상태가 없으면 탈락"
		assertThat(policy.matchEmployment(EmploymentStatus.JOB_SEEKING, "EMPLOYED,STUDENT")).isFalse();
	}

	@Test
	void matchEmployment_trimsWhitespaceInCsvTags() {
		// CSV 값 사이에 공백이 섞여도(예 파서가 ", "로 join) trim() 처리로 정상 매칭돼야 함
		assertThat(policy.matchEmployment(EmploymentStatus.STUDENT, " EMPLOYED , STUDENT ")).isTrue();
	}

	// ---- matchIncome / matchHousehold : 핵심 불변식(미입력은 탈락이 아니라 산정불가) ----

	@Test
	void matchIncome_passesWithReason_whenBracketNull() {
		ConditionOutcome outcome = policy.matchIncome(null, 3_000_000L);

		assertThat(outcome.passed()).isTrue();
		assertThat(outcome.reason()).isEqualTo(EligibilityReason.INCOME_MISSING);
	}

	@Test
	void matchIncome_passesForAllBrackets_whenThresholdAtOrAboveLowerBound() {
		// bracketLowerBoundWon 5개 소득구간 전부를 짚음(소득 비교 가정의 심장, ponytail 매핑 자체를 검증)
		assertThat(policy.matchIncome(IncomeBracket.UNDER_200, 0L).passed()).isTrue();
		assertThat(policy.matchIncome(IncomeBracket.FROM_200_TO_300, 2_000_000L).passed()).isTrue();
		assertThat(policy.matchIncome(IncomeBracket.FROM_300_TO_400, 3_000_000L).passed()).isTrue();
		assertThat(policy.matchIncome(IncomeBracket.FROM_400_TO_600, 4_000_000L).passed()).isTrue();
		assertThat(policy.matchIncome(IncomeBracket.OVER_600, 6_000_000L).passed()).isTrue();
	}

	@Test
	void matchIncome_fails_whenBracketLowerBoundExceedsThreshold() {
		// 구간 하한(600만원 이상 -> 600만원)이 지원금 소득 상한(300만원)을 초과하면 탈락함
		ConditionOutcome outcome = policy.matchIncome(IncomeBracket.OVER_600, 3_000_000L);

		assertThat(outcome.passed()).isFalse();
		assertThat(outcome.reason()).isNull();
	}

	@Test
	void matchHousehold_passesWithReason_whenSizeNull() {
		ConditionOutcome outcome = policy.matchHousehold(null, "1인 가구");

		assertThat(outcome.passed()).isTrue();
		assertThat(outcome.reason()).isEqualTo(EligibilityReason.HOUSEHOLD_UNDETERMINED);
	}

	@Test
	void matchHousehold_passes_whenConditionMissing_andSizePresent() {
		// householdCondition 자체가 없으면(지원금이 가구 조건을 안 둠) 가구원 수와 무관하게 통과이고 사유도 없음
		ConditionOutcome outcome = policy.matchHousehold(2, null);

		assertThat(outcome.passed()).isTrue();
		assertThat(outcome.reason()).isNull();
	}

	@Test
	void matchHousehold_exactFormat_matchesOnlyExactSize() {
		// "N인 가구" 정확 일치 형식임. 일치와 불일치 둘 다 짚음
		assertThat(policy.matchHousehold(1, "1인 가구").passed()).isTrue();
		assertThat(policy.matchHousehold(2, "1인 가구").passed()).isFalse();
	}

	@Test
	void matchHousehold_atLeastFormat_matchesSizeAtOrAboveThreshold() {
		// "N인 이상" 형식임. 상한 이상 통과, 미만 탈락 둘 다 짚음
		assertThat(policy.matchHousehold(3, "2인 이상").passed()).isTrue();
		assertThat(policy.matchHousehold(1, "2인 이상").passed()).isFalse();
	}

	@Test
	void matchHousehold_undetermined_whenConditionFormatUnrecognized() {
		// 지원금 쪽 조건 형식을 해석할 수 없는 경우이므로 사용자 미입력 사유와 구분함
		ConditionOutcome outcome = policy.matchHousehold(3, "2인~4인");

		assertThat(outcome.passed()).isTrue();
		assertThat(outcome.reason()).isEqualTo(EligibilityReason.HOUSEHOLD_CONDITION_DETAILS_MISSING);
	}

	@Test
	void signalsUnknown_passWithSupportConditionReasons() {
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.UNKNOWN, null, null, EligibilitySignal.UNKNOWN,
				null, "0013011", EligibilitySignal.UNKNOWN, null, EligibilitySignal.UNKNOWN, null);

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).containsExactly(EligibilityReason.AGE_CONDITION_UNKNOWN,
				EligibilityReason.EMPLOYMENT_CONDITION_UNKNOWN, EligibilityReason.INCOME_CONDITION_UNKNOWN,
				EligibilityReason.HOUSEHOLD_CONDITION_UNKNOWN);
		// UNKNOWN은 통과이되 확인은 아님 — confirmedMatchCount에 안 셈
		assertThat(result.confirmedMatchCount()).isZero();
	}

	@Test
	void signalsUnrestricted_passWithoutReasons_evenWhenLegacyValuesWouldReject() {
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.UNRESTRICTED, 60, 70,
				EligibilitySignal.UNRESTRICTED, "EMPLOYED", "0013011", EligibilitySignal.UNRESTRICTED, 0L,
				EligibilitySignal.UNRESTRICTED, "3인 가구");

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).isEmpty();
		// UNRESTRICTED(제약 없음)는 통과이되 확인은 아님 — confirmedMatchCount에 안 셈
		assertThat(result.confirmedMatchCount()).isZero();
		// 연령이 60~70으로 존재해도 UNRESTRICTED라 확정이 아니므로 연령 범위를 싣지 않음(확정일 때만 싣는다는 보장)
		assertThat(result.confirmedAgeMin()).isNull();
		assertThat(result.confirmedAgeMax()).isNull();
	}

	@Test
	void signalsRestrictedWithoutComparableValues_passWithAxisSpecificReasons() {
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.RESTRICTED, null, null,
				EligibilitySignal.RESTRICTED, null, "0013011", EligibilitySignal.RESTRICTED, null,
				EligibilitySignal.RESTRICTED, null);

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).containsExactly(EligibilityReason.AGE_CONDITION_DETAILS_MISSING,
				EligibilityReason.EMPLOYMENT_CONDITION_DETAILS_MISSING,
				EligibilityReason.INCOME_CONDITION_DETAILS_MISSING,
				EligibilityReason.HOUSEHOLD_CONDITION_DETAILS_MISSING);
		// RESTRICTED이나 세부기준이 없어 판단 보류(DETAILS_MISSING) — 확인이 아니므로 안 셈
		assertThat(result.confirmedMatchCount()).isZero();
		assertThat(result.confirmedAgeMin()).isNull();
		assertThat(result.confirmedAgeMax()).isNull();
	}

	@Test
	void confirmedAgeRange_carriesOnlyLowerBound_whenAgeMaxOpen() {
		// 연령 RESTRICTED에 하한만 있고 상한 개방(ageMax null)인 확정 케이스 — 하한만 실리고 상한은 null
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.RESTRICTED, 65, null,
				EligibilitySignal.UNRESTRICTED, null, "0013011", EligibilitySignal.UNRESTRICTED, null,
				EligibilitySignal.UNRESTRICTED, null);
		ApplicantProfile senior = new ApplicantProfile(70, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(senior, criteria);

		assertThat(result.confirmedMatchCount()).isEqualTo(1);
		assertThat(result.confirmedAgeMin()).isEqualTo(65);
		assertThat(result.confirmedAgeMax()).isNull();
	}

	@Test
	void signalsRestrictedWithComparableValues_useExistingMatchingRules() {
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.RESTRICTED, 60, 70,
				EligibilitySignal.RESTRICTED, "EMPLOYED", "0013010", EligibilitySignal.RESTRICTED, 0L,
				EligibilitySignal.RESTRICTED, "3인 가구");

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.matched()).isFalse();
		assertThat(result.matchScore()).isEqualTo(2);
	}

	@Test
	void confirmedMatchCount_countsFourRestrictedConfirmedAxes_excludingRegion() {
		// 4축 전부 RESTRICTED 세부기준 있고 사용자 정보로 통과 확인 — 확인 4. 지역은 별도 축이라 상한이 5가 아니라 4임
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.RESTRICTED, 19, 34,
				EligibilitySignal.RESTRICTED, "JOB_SEEKING", "0013011", EligibilitySignal.RESTRICTED, 2_000_000L,
				EligibilitySignal.RESTRICTED, "1인 가구");

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.confirmedMatchCount()).isEqualTo(4);
		// AGE가 확정됐으므로 공고의 대상 연령 범위를 실음
		assertThat(result.confirmedAgeMin()).isEqualTo(19);
		assertThat(result.confirmedAgeMax()).isEqualTo(34);
	}

	@Test
	void confirmedMatchCount_excludesUserInfoMissingPass() {
		// 연령만 RESTRICTED 확인(1), 소득은 RESTRICTED 세부기준 있으나 신청자 소득 null이라 INCOME_MISSING으로 통과
		// —
		// 확인 아님. 고용·가구는 UNRESTRICTED라 확인 아님. 총 확인 1
		SubsidyCriteria criteria = eligibilityCriteria(EligibilitySignal.RESTRICTED, 19, 34,
				EligibilitySignal.UNRESTRICTED, null, "0013011", EligibilitySignal.RESTRICTED, 2_000_000L,
				EligibilitySignal.UNRESTRICTED, null);
		ApplicantProfile incomeUnknown = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING, null, 1);

		MatchResult result = policy.evaluate(incomeUnknown, criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).contains(EligibilityReason.INCOME_MISSING);
		assertThat(result.confirmedMatchCount()).isEqualTo(1);
		// 확인된 축은 연령뿐이고, 확인된 연령 범위가 공고 조건 그대로 실림(운영 데이터의 실질 케이스)
		assertThat(result.confirmedAgeMin()).isEqualTo(19);
		assertThat(result.confirmedAgeMax()).isEqualTo(34);
	}

	@Test
	void qualificationUncertainty_trueForQualificationAxis_falseForAmount() {
		// 자격 축 사유는 "추가 확인 필요"에 셈, 금액 축 사유는 제외함
		assertThat(EligibilityReason.INCOME_MISSING.qualificationUncertainty()).isTrue();
		assertThat(EligibilityReason.AMOUNT_INFO_MISSING.qualificationUncertainty()).isFalse();
		assertThat(EligibilityReason.PAYMENT_TYPE_UNCONFIRMED.qualificationUncertainty()).isFalse();
	}

	private static SubsidyCriteria eligibilityCriteria(EligibilitySignal ageSignal, Integer ageMin, Integer ageMax,
			EligibilitySignal employmentSignal, String employmentTags, String employmentRawCode,
			EligibilitySignal incomeSignal, Long incomeThreshold, EligibilitySignal householdSignal,
			String householdCondition) {
		return new SubsidyCriteria(100L, TargetAudience.PERSONAL, OccupationRestriction.NONE, ageSignal, ageMin, ageMax,
				RegionScope.NATIONWIDE, null, employmentSignal, employmentTags, employmentRawCode, incomeSignal,
				incomeThreshold, householdSignal, householdCondition, AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH,
				null, null, null, null);
	}

	private static ApplicantProfile applicant() {
		return new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING, IncomeBracket.UNDER_200, 1);
	}

	// ---- evaluate : amountInfoMissing 경계(3필드 부분 입력, paymentType UNKNOWN이되 금액 있음) ----

	@Test
	void matchResult_computable_whenOnlyAmountMaxProvided() {
		// estimatedAmountMin만 없고 estimatedAmountMax는 있으면 "3필드 전부 null"이 아니므로 산정 가능함
		SubsidyCriteria criteria = nationwideCriteria(20L, null, AMOUNT_MAX, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.uncomputable()).isFalse();
	}

	@Test
	void matchResult_computable_whenMonthlyAmountProvidedInsteadOfRange() {
		// estimatedAmountMin과 Max는 없어도 monthlyAmount(월 지급액)가 있으면 산정 가능함
		SubsidyCriteria criteria = new SubsidyCriteria(21L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, null, null, null, 500_000L, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.uncomputable()).isFalse();
	}

	@Test
	void matchResult_uncomputable_whenPaymentTypeUnknown_despiteAmountsPresent() {
		// 금액 범위는 공개돼 있어도 paymentType이 UNKNOWN이면(지급 방식 미확정) 산정불가로 강등됨
		SubsidyCriteria criteria = nationwideCriteria(22L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.UNKNOWN);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.uncomputable()).isTrue();
		assertThat(result.uncomputableReasons()).containsExactly(EligibilityReason.PAYMENT_TYPE_UNCONFIRMED);
	}

	// ---- evaluate : 지역 강등 재확인 더하기 나머지 4조건 중 하나만 개별 탈락(matched AND 체인의 각 분기 짚기) ----

	@Test
	void matchResult_isRegionDemoted_whenSidoPrefixMismatch_butOtherConditionsPass() {
		// 강등 반전(09-region-demotion D1, D2): 종전엔 REGIONAL 지역 불일치가 탈락이었으나 이제는 강등이며 노출은 유지됨.
		// 강남(11680, 시도 11) 지원금에 세종(36110, 시도 36) 신청자, 나머지 4조건은 통과
		SubsidyCriteria criteria = new SubsidyCriteria(23L, TargetAudience.PERSONAL, OccupationRestriction.NONE, null,
				19, 34, RegionScope.REGIONAL, "11680", null, null, null, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX,
				null, PaymentType.CASH, null, null, null, "11680");
		ApplicantProfile applicant = new ApplicantProfile(27, "36110", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.regionDemoted()).isTrue();
	}

	@Test
	void matchResult_failsOnEmploymentMismatch_whenOtherConditionsPass() {
		// TC-DEMO-014: employmentTags "EMPLOYED,STUDENT"에 JOB_SEEKING 신청자, 나머지 4조건은 통과
		SubsidyCriteria criteria = new SubsidyCriteria(24L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, "EMPLOYED,STUDENT", null, null, AMOUNT_MIN, AMOUNT_MAX, null,
				PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isFalse();
	}

	@Test
	void matchResult_failsOnIncomeMismatch_whenOtherConditionsPass() {
		// incomeThreshold 300만원인 지원금에 OVER_600(하한 600만원) 신청자, 나머지 4조건은 통과
		SubsidyCriteria criteria = new SubsidyCriteria(25L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, 3_000_000L, null, AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.OVER_600, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isFalse();
	}

	@Test
	void matchResult_failsOnHouseholdMismatch_whenOtherConditionsPass() {
		// householdCondition "1인 가구"에 가구원 수 2명 신청자, 나머지 4조건은 통과
		SubsidyCriteria criteria = new SubsidyCriteria(26L, TargetAudience.PERSONAL, OccupationRestriction.NONE, 19, 34,
				RegionScope.NATIONWIDE, null, null, null, "1인 가구", AMOUNT_MIN, AMOUNT_MAX, null, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 2);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isFalse();
	}

	// ---- evaluate : 산정불가 사유가 evaluate() 조립 경로에서도 붙는지(reasons.add 분기) ----

	@Test
	void matchResult_includesIncomeMissingReason_whenApplicantIncomeBracketNull() {
		SubsidyCriteria criteria = nationwideCriteria(27L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING, null, 1);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).containsExactly(EligibilityReason.INCOME_MISSING);
	}

	@Test
	void matchResult_includesHouseholdUndeterminedReason_whenApplicantHouseholdSizeNull() {
		SubsidyCriteria criteria = nationwideCriteria(28L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.CASH);
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, null);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).containsExactly(EligibilityReason.HOUSEHOLD_UNDETERMINED);
	}

	// ---- evaluate 종합 판정 ----

	@Test
	void matchResult_marksUncomputable_whenAmountInfoMissing() {
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);
		// 금액 필드 전무 더하기 paymentType UNKNOWN, 나머지 4조건은 통과하는 조건
		SubsidyCriteria criteria = nationwideCriteria(1L, null, null, PaymentType.UNKNOWN);

		MatchResult result = policy.evaluate(applicant, criteria);

		assertThat(result.matched()).isTrue();
		assertThat(result.uncomputableReasons()).contains(EligibilityReason.AMOUNT_INFO_MISSING);
		assertThat(result.uncomputableReasons()).doesNotContain(EligibilityReason.PAYMENT_TYPE_UNCONFIRMED);
		assertThat(result.uncomputable()).isTrue();
	}

	@Test
	void matchResult_matchedOnlyWhenAllFourConditionsPass() {
		SubsidyCriteria criteria = nationwideCriteria(2L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.CASH);

		ApplicantProfile matching = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);
		MatchResult matchingResult = policy.evaluate(matching, criteria);
		assertThat(matchingResult.matched()).isTrue();
		assertThat(matchingResult.uncomputableReasons()).isEmpty();

		// 연령 조건 하나만 탈락(18세, ageMin 19 미만). 4조건 중 하나라도 탈락하면 종합 탈락
		ApplicantProfile tooYoung = new ApplicantProfile(18, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);
		MatchResult failingResult = policy.evaluate(tooYoung, criteria);
		assertThat(failingResult.matched()).isFalse();
	}

	// ---- evaluate : deadline·sourceId·externalId 배관(정렬 재설계 B-1 재료 이동) ----

	@Test
	void evaluate_carriesDeadlineAndSourceKeysToMatchResult() {
		SubsidyCriteria criteria = new SubsidyCriteria(30L, TargetAudience.PERSONAL, OccupationRestriction.NONE, null,
				19, 34, RegionScope.NATIONWIDE, null, null, null, null, null, null, null, null, AMOUNT_MIN, AMOUNT_MAX,
				null, PaymentType.CASH, java.time.LocalDate.of(2026, 8, 31), "gov24", "EXT-30", null);

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.deadline()).isEqualTo(java.time.LocalDate.of(2026, 8, 31));
		assertThat(result.sourceId()).isEqualTo("gov24");
		assertThat(result.externalId()).isEqualTo("EXT-30");
	}

	@Test
	void evaluate_carriesNullDeadlineAndSourceKeys_whenConvenienceConstructorUsed() {
		SubsidyCriteria criteria = nationwideCriteria(31L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.CASH);

		MatchResult result = policy.evaluate(applicant(), criteria);

		assertThat(result.deadline()).isNull();
		assertThat(result.sourceId()).isNull();
		assertThat(result.externalId()).isNull();
	}

	@Test
	void matchScore_ordersDeterministically() {
		// ponytail: matchScore 산식은 단순 가중합 가정(DISCUSS.md 6장). 이 테스트는 산식 값이 아니라
		// "같은 입력이면 항상 같은 점수"(비무작위, 정렬 안정성의 전제조건)만 검증함.
		ApplicantProfile applicant = new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING,
				IncomeBracket.UNDER_200, 1);
		SubsidyCriteria criteria = nationwideCriteria(3L, AMOUNT_MIN, AMOUNT_MAX, PaymentType.CASH);

		MatchResult first = policy.evaluate(applicant, criteria);
		MatchResult second = policy.evaluate(applicant, criteria);

		assertThat(first.matchScore()).isEqualTo(second.matchScore());
	}

}
