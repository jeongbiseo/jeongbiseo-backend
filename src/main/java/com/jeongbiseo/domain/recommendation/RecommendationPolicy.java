package com.jeongbiseo.domain.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;

/**
 * 5조건 매칭 판정의 단일 정본임(도메인 서비스, JPA 비의존 순수 자바). 이 클래스 밖에서 매칭 조건 분기를 만들지 않음(DISCUSS.md 3.3,
 * QA.md 3.2 체크리스트).
 *
 * <p>
 * "매칭 탈락"과 "산정불가"는 별개 축임. 소득과 가구는 미입력이어도 탈락시키지 않고 통과시키되 EligibilityReason을 붙여 산정불가로
 * 강등함(DISCUSS.md 3.4 핵심 규칙).
 * </p>
 */
public final class RecommendationPolicy {

	// householdCondition "N인 가구" 정확 일치 형식(예 "1인 가구")
	private static final Pattern HOUSEHOLD_EXACT = Pattern.compile("^(\\d+)인 가구$");

	// householdCondition "N인 이상" 형식(예 "2인 이상")
	private static final Pattern HOUSEHOLD_AT_LEAST = Pattern.compile("^(\\d+)인 이상$");

	/**
	 * 지원금이 제품 추천 스코프 안에 있는지 판정함. 매칭 조건 판정과 별개 축이며, 대상 근거가 불명확한 UNKNOWN은 추천 도메인 불변식에 따라
	 * 통과시킴.
	 * @param criteria 지원금 조건 스냅샷
	 * @return 기업 대상 또는 1차산업 전용이 아니면 true
	 */
	public boolean inScope(SubsidyCriteria criteria) {
		return criteria.targetAudience() != TargetAudience.BUSINESS
				&& criteria.occupationRestriction() != OccupationRestriction.PRIMARY_INDUSTRY_ONLY;
	}

	/**
	 * 신청자 프로필과 지원금 조건 1건을 5조건으로 판정함.
	 * @param profile 신청자 프로필
	 * @param criteria 지원금 조건 스냅샷
	 * @return 매칭 결과(산정불가 사유 포함)
	 */
	public MatchResult evaluate(ApplicantProfile profile, SubsidyCriteria criteria) {
		ConditionOutcome ageOutcome = matchAge(profile.age(), criteria.ageSignal(), criteria.ageMin(),
				criteria.ageMax());
		boolean regionOk = matchRegion(criteria.regionCode(), criteria.regionScope(), profile.regionCode());
		ConditionOutcome employmentOutcome = matchEmployment(profile.employmentStatus(), criteria.employmentSignal(),
				criteria.employmentTags());
		ConditionOutcome incomeOutcome = matchIncome(profile.incomeBracket(), criteria.incomeSignal(),
				criteria.incomeThreshold());
		ConditionOutcome houseOutcome = matchHousehold(profile.householdSize(), criteria.householdSignal(),
				criteria.householdCondition());

		boolean matched = ageOutcome.passed() && regionOk && employmentOutcome.passed() && incomeOutcome.passed()
				&& houseOutcome.passed();

		List<EligibilityReason> reasons = new ArrayList<>();
		addReason(reasons, ageOutcome);
		addReason(reasons, employmentOutcome);
		addReason(reasons, incomeOutcome);
		addReason(reasons, houseOutcome);
		if (amountInfoMissing(criteria)) {
			reasons.add(EligibilityReason.AMOUNT_INFO_MISSING);
		}

		int score = matchScore(ageOutcome.passed(), regionOk, employmentOutcome.passed(), incomeOutcome.passed(),
				houseOutcome.passed());

		return new MatchResult(criteria.subsidyId(), matched, score, List.copyOf(reasons), criteria.deadline(),
				criteria.sourceId(), criteria.externalId());
	}

	/**
	 * 연령 조건을 판정함. min 또는 max가 null이면 그 방향은 무제한임.
	 */
	boolean matchAge(int age, Integer min, Integer max) {
		if (min != null && age < min) {
			return false;
		}
		return max == null || age <= max;
	}

	/**
	 * 연령 신호를 우선 판정함. 신호가 없는 기존 행은 min/max 비교를 그대로 사용함.
	 */
	ConditionOutcome matchAge(int age, EligibilitySignal signal, Integer min, Integer max) {
		if (signal == null) {
			return new ConditionOutcome(matchAge(age, min, max), null);
		}
		return switch (signal) {
			case UNRESTRICTED -> new ConditionOutcome(true, null);
			case UNKNOWN -> new ConditionOutcome(true, EligibilityReason.AGE_CONDITION_UNKNOWN);
			case RESTRICTED ->
				min == null && max == null ? new ConditionOutcome(true, EligibilityReason.AGE_CONDITION_DETAILS_MISSING)
						: new ConditionOutcome(matchAge(age, min, max), null);
		};
	}

	/**
	 * 지역 조건을 판정함. scope가 NATIONWIDE면 항상 통과, REGIONAL이면 code와 target 일치 여부로 판정함.
	 */
	boolean matchRegion(String code, RegionScope scope, String target) {
		if (scope == RegionScope.NATIONWIDE) {
			return true;
		}
		return code != null && code.equals(target);
	}

	/**
	 * 고용상태 조건을 판정함. tagsCsv가 null이면 전체 통과, 아니면 CSV에 s가 포함돼야 통과함.
	 */
	boolean matchEmployment(EmploymentStatus s, String tagsCsv) {
		if (tagsCsv == null) {
			return true;
		}
		for (String tag : tagsCsv.split(",")) {
			if (s.name().equals(tag.trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 고용 신호를 우선 판정함. employmentRawCode는 코드 정의가 확인되지 않아 이 판정에 전달하지 않음.
	 */
	ConditionOutcome matchEmployment(EmploymentStatus status, EligibilitySignal signal, String tagsCsv) {
		if (signal == null) {
			return new ConditionOutcome(matchEmployment(status, tagsCsv), null);
		}
		return switch (signal) {
			case UNRESTRICTED -> new ConditionOutcome(true, null);
			case UNKNOWN -> new ConditionOutcome(true, EligibilityReason.EMPLOYMENT_CONDITION_UNKNOWN);
			case RESTRICTED -> tagsCsv == null || tagsCsv.isBlank()
					? new ConditionOutcome(true, EligibilityReason.EMPLOYMENT_CONDITION_DETAILS_MISSING)
					: new ConditionOutcome(matchEmployment(status, tagsCsv), null);
		};
	}

	/**
	 * 소득 조건을 판정함. bracket이 null이면 통과이되 INCOME_MISSING 사유를 함께 반환함(산정불가). bracket이 있고
	 * incomeThreshold가 null이면 소득 조건 자체가 없으므로 무조건 통과임.
	 */
	// ponytail: threshold를 월 소득 상한(원)으로 보고, 구간 하한이 threshold를 초과할 때만 탈락함(보수적으로
	// 통과 쪽). 계약이 프로필(구간)과 지원금(금액) 비교 규칙을 정의하지 않아 만든 가정이며, 상한은 이 부등호
	// 규칙 하나로 고정함. 대안은 규칙 확정 시 bracketLowerBoundWon 매핑을 교체하는
	// 것뿐임(DISCUSS.md 6장 가정 1번, 회의 실증 자료로 가져갈 것).
	ConditionOutcome matchIncome(IncomeBracket bracket, Long incomeThreshold) {
		if (bracket == null) {
			return new ConditionOutcome(true, EligibilityReason.INCOME_MISSING);
		}
		if (incomeThreshold == null) {
			return new ConditionOutcome(true, null);
		}
		boolean passed = bracketLowerBoundWon(bracket) <= incomeThreshold;
		return new ConditionOutcome(passed, null);
	}

	/**
	 * 소득 신호를 우선 판정함. 제한형인데 비교 금액이 없으면 지원금 쪽 세부 기준 부재 사유로 통과함.
	 */
	ConditionOutcome matchIncome(IncomeBracket bracket, EligibilitySignal signal, Long incomeThreshold) {
		if (signal == null) {
			return matchIncome(bracket, incomeThreshold);
		}
		return switch (signal) {
			case UNRESTRICTED -> new ConditionOutcome(true, null);
			case UNKNOWN -> new ConditionOutcome(true, EligibilityReason.INCOME_CONDITION_UNKNOWN);
			case RESTRICTED ->
				incomeThreshold == null ? new ConditionOutcome(true, EligibilityReason.INCOME_CONDITION_DETAILS_MISSING)
						: matchIncome(bracket, incomeThreshold);
		};
	}

	/**
	 * 가구 조건을 판정함. householdSize가 null이면 통과이되 HOUSEHOLD_UNDETERMINED 사유를 함께 반환함(산정불가).
	 */
	// ponytail: householdCondition은 "N인 가구"(정확 일치)와 "N인 이상" 2형식만 파싱함. 그 외 형식
	// (예 "1인 이하", "2인~4인")은 판정 불가로 통과 처리하며 지원금 쪽 세부 기준 부재 사유를 붙임. 상한은 이
	// 2형식이고, 새 형식이 나오면 정규식을 추가하는 것이 대안임(DISCUSS.md 6장 가정 2번).
	ConditionOutcome matchHousehold(Integer householdSize, String householdCondition) {
		if (householdSize == null) {
			return new ConditionOutcome(true, EligibilityReason.HOUSEHOLD_UNDETERMINED);
		}
		if (householdCondition == null) {
			return new ConditionOutcome(true, null);
		}
		Matcher exact = HOUSEHOLD_EXACT.matcher(householdCondition);
		if (exact.matches()) {
			int required = Integer.parseInt(exact.group(1));
			return new ConditionOutcome(householdSize == required, null);
		}
		Matcher atLeast = HOUSEHOLD_AT_LEAST.matcher(householdCondition);
		if (atLeast.matches()) {
			int required = Integer.parseInt(atLeast.group(1));
			return new ConditionOutcome(householdSize >= required, null);
		}
		return new ConditionOutcome(true, EligibilityReason.HOUSEHOLD_CONDITION_DETAILS_MISSING);
	}

	/**
	 * 가구 신호를 우선 판정함. 제한형인데 비교 조건이 없으면 지원금 쪽 세부 기준 부재 사유로 통과함.
	 */
	ConditionOutcome matchHousehold(Integer householdSize, EligibilitySignal signal, String householdCondition) {
		if (signal == null) {
			return matchHousehold(householdSize, householdCondition);
		}
		return switch (signal) {
			case UNRESTRICTED -> new ConditionOutcome(true, null);
			case UNKNOWN -> new ConditionOutcome(true, EligibilityReason.HOUSEHOLD_CONDITION_UNKNOWN);
			case RESTRICTED -> householdCondition == null || householdCondition.isBlank()
					? new ConditionOutcome(true, EligibilityReason.HOUSEHOLD_CONDITION_DETAILS_MISSING)
					: matchHousehold(householdSize, householdCondition);
		};
	}

	/**
	 * 금액 정보 전무(3필드 전부 null) 또는 paymentType UNKNOWN이면 산정불가임.
	 */
	private static boolean amountInfoMissing(SubsidyCriteria criteria) {
		boolean noAmountFields = criteria.estimatedAmountMin() == null && criteria.estimatedAmountMax() == null
				&& criteria.monthlyAmount() == null;
		return noAmountFields || criteria.paymentType() == PaymentType.UNKNOWN;
	}

	private static void addReason(List<EligibilityReason> reasons, ConditionOutcome outcome) {
		if (outcome.reason() != null) {
			reasons.add(outcome.reason());
		}
	}

	// ponytail: matchScore 산식은 계약에 산식이 없어 5조건 중 통과 개수의 단순 가중합(각 1점,
	// 0에서 5점)으로 가정함(DISCUSS.md 6장 가정 3번). 동점 처리(subsidyId 오름차순 등)는 이 값을 쓰는
	// 상위 계층(추천 서비스, W3)의 책임이고 이 메서드는 결정적 값만 보장함. 상한은 5점 고정이며,
	// 대안(완결도 가중치 등)은 산식 확정 시 이 메서드를 교체하는 것임
	private static int matchScore(boolean ageOk, boolean regionOk, boolean employmentOk, boolean incomeOk,
			boolean householdOk) {
		int score = 0;
		score += ageOk ? 1 : 0;
		score += regionOk ? 1 : 0;
		score += employmentOk ? 1 : 0;
		score += incomeOk ? 1 : 0;
		score += householdOk ? 1 : 0;
		return score;
	}

	/**
	 * 소득구간의 하한 금액(원)임. bracketLowerBoundWon 매핑 자체가 matchIncome의 ponytail 가정임.
	 */
	private static long bracketLowerBoundWon(IncomeBracket bracket) {
		return switch (bracket) {
			case UNDER_200 -> 0L;
			case FROM_200_TO_300 -> 2_000_000L;
			case FROM_300_TO_400 -> 3_000_000L;
			case FROM_400_TO_600 -> 4_000_000L;
			case OVER_600 -> 6_000_000L;
		};
	}

	/**
	 * 자격조건 한 축의 판정 결과임(통과 여부와 판단·산정 불가 사유를 함께 반환).
	 *
	 * @param passed 매칭 통과 여부(미입력이어도 항상 true)
	 * @param reason 산정불가 사유(산정 가능하면 null)
	 */
	record ConditionOutcome(boolean passed, EligibilityReason reason) {

	}

}
