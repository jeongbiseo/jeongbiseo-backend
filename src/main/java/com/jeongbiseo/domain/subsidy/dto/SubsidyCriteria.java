package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;

/**
 * 매칭에 필요한 지원금 조건 스냅샷임(값 객체). Subsidy 엔티티 전체가 아니라 판정과 정렬에 쓰이는 필드만 담음(storage 계층과의 결합을 끊음).
 * SubsidyReader가 반환하고 추천 도메인이 소비함. 추천보다 먼저 이식하는 subsidy 슬라이스에 두어 이슈 단위 컴파일 의존을 정방향으로
 * 둠(PLAN 07-subsidy-recommendation 1장).
 *
 * @param subsidyId 지원금 식별자
 * @param targetAudience 지원 대상 주체 구분
 * @param occupationRestriction 추천 스코프의 직업군 제한
 * @param ageSignal 지원금의 연령 조건 공개 상태
 * @param ageMin 연령 하한(null이면 하한 무제한)
 * @param ageMax 연령 상한(null이면 상한 무제한)
 * @param regionScope 지역 적용 범위
 * @param regionCode REGIONAL일 때의 대상 지역코드(NATIONWIDE는 null 허용)
 * @param employmentSignal 지원금의 고용 조건 공개 상태
 * @param employmentTags 대상 고용상태 CSV(null이면 전체 통과)
 * @param employmentRawCode 고용 조건 원문 코드. 의미를 추측해 매칭에 사용하지 않음
 * @param incomeSignal 지원금의 소득 조건 공개 상태
 * @param incomeThreshold 소득 기준 금액(원). null이면 소득 조건 없음
 * @param householdSignal 지원금의 가구 조건 공개 상태
 * @param householdCondition 가구원 수 조건 자유 문자열(예 "1인 가구", "2인 이상")
 * @param estimatedAmountMin 예상 지원금액 하한
 * @param estimatedAmountMax 예상 지원금액 상한
 * @param monthlyAmount 월 지급액(해당 시)
 * @param paymentType 지급 방식
 * @param deadline 신청 마감일. null이면 상시접수 등 종료일 없음, 정렬 시 nullsLast
 * @param sourceId 원천 소스 식별자(정렬 타이브레이크 키 재료)
 * @param externalId 원천 내 외부 식별자(동일 목적)
 */
public record SubsidyCriteria(Long subsidyId, TargetAudience targetAudience,
		OccupationRestriction occupationRestriction, EligibilitySignal ageSignal, Integer ageMin, Integer ageMax,
		RegionScope regionScope, String regionCode, EligibilitySignal employmentSignal, String employmentTags,
		String employmentRawCode, EligibilitySignal incomeSignal, Long incomeThreshold,
		EligibilitySignal householdSignal, String householdCondition, Long estimatedAmountMin, Long estimatedAmountMax,
		Long monthlyAmount, PaymentType paymentType, LocalDate deadline, String sourceId, String externalId) {

	/**
	 * 신호 컬럼 도입 전 시드·수기 데이터용 생성자임. 신호가 null이면 기존 비교값 기반 판정을 그대로 사용함.
	 */
	public SubsidyCriteria(Long subsidyId, TargetAudience targetAudience, OccupationRestriction occupationRestriction,
			Integer ageMin, Integer ageMax, RegionScope regionScope, String regionCode, String employmentTags,
			Long incomeThreshold, String householdCondition, Long estimatedAmountMin, Long estimatedAmountMax,
			Long monthlyAmount, PaymentType paymentType) {
		this(subsidyId, targetAudience, occupationRestriction, null, ageMin, ageMax, regionScope, regionCode, null,
				employmentTags, null, null, incomeThreshold, null, householdCondition, estimatedAmountMin,
				estimatedAmountMax, monthlyAmount, paymentType, null, null, null);
	}

}
