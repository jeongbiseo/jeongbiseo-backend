package com.jeongbiseo.infra.client.gov24.dto;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.domain.common.enums.TargetAudience;

import java.time.LocalDateTime;

import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 보조금24 원문 하나를 파싱한 최종 결과임. serviceList·serviceDetail 필드와 supportConditions의 연령·소득·가구 조건을
 * 서비스ID로 합쳐 만듦. SubsidyCriteria로 곧장 못 만드는 이유는 지역·고용 조건이 gov24 원문에 구조화 필드로 없고(선정기준 자유텍스트
 * 안에 섞여 있음) 이 PoC의 검증 범위가 신청기한 파싱과 JA 필드 판독으로 좁혀져 있기 때문임(PLAN.md 3장 W4 절,
 * 외부API-부족분-조사-2026-07-12.md 3장 G1).
 *
 * <p>
 * incomeSignal·householdSignal은 원문 JA 플래그를 그대로 읽어 3분류로 정규화한 신호임 — 매칭 하드 필터로 쓸지는 회의 안건 22번
 * 결정 사항이라 여기서는 배제 판단을 내리지 않음. 적대 검증에서 이 플래그가 선정기준 원문과 어긋나는 사례(대조 표본 불일치)가 실측 확인됐으므로, 하드
 * 배제에 쓸 때는 원문 대조 없이 신뢰하지 말 것(신뢰도 한계는 {@link EligibilitySignal} 참조).
 *
 * @param serviceId 서비스ID
 * @param serviceName 서비스명
 * @param agency 소관기관명(Subsidy.agency 매핑 대상)
 * @param description 지원내용 원문(Subsidy.description 매핑 대상)
 * @param eligibilityText 지원대상 원문에 선정기준 원문을 이어붙인 자격조건 텍스트(Subsidy.eligibilityText 매핑 대상).
 * 선정기준은 채움률이 9.75%뿐이라 있을 때만 덧붙임(Gov24Parser 참조)
 * @param categoryRawText serviceList의 서비스분야 원문. SubsidyCategory로 매핑하지 않으며 원문이 없으면 null
 * @param ageMin 대상연령 시작(supportConditions 매칭 없으면 null)
 * @param ageMax 대상연령 종료(supportConditions 매칭 없으면 null)
 * @param incomeSignal 소득 조건 3분류 신호(supportConditions 매칭 없으면 UNKNOWN)
 * @param householdSignal 가구 조건 3분류 신호(supportConditions 매칭 없으면 UNKNOWN)
 * @param paymentType 지원유형 원문을 매핑한 PaymentType(매핑표에 없는 값이나 "||" 콤보는 UNKNOWN — Gov24Parser
 * 참조)
 * @param paymentTypeRawText 지원유형 원문
 * @param externalUrl 온라인신청사이트URL(Subsidy.externalUrl 매핑 대상). 채움률 18.23%라 나머지는 null
 * @param dataUpdatedAt 수정일시를 LocalDateTime으로 파싱한 값(Subsidy.dataUpdatedAt 매핑 대상). 원문이 날짜만
 * 있으면 자정(00:00)으로 채움
 * @param applicationMethod 신청방법 키워드 분류 플래그. 스키마 컬럼이 없어 DB 매핑 대상이 아님(회의 결정 사항)
 * @param requiredDocumentsText 구비서류 원문("해당없음"이면 null로 정규화). 스키마 컬럼이 없어 DB 매핑 대상이 아님(회의 결정
 * 사항)
 * @param deadline 신청기한 파싱 결과(기존 성공/실패 이분법 — 후속 임무 이전부터 있던 필드, 하위호환 유지)
 * @param parsedDeadline 신청기한 7분류 판정 결과(후속 임무 1장 — DeadlineKind, 상시접수를 실패로 취급하지 않음)
 * @param amount 지원내용 원문에서 뽑은 금액 정보(후속 임무 2장 — AmountKind, MULTIPLE과 CONDITIONAL 구분)
 * @param region 소관기관명에서 유추한 지역 정보(후속 임무 3장 — 법정동코드 없음, 유추 근거와 낮은 신뢰도 명시)
 * @param incomeSignalSource 소득 조건 신호의 출처(JA 플래그·원문 언급·둘 다, 후속 임무 4장)
 * @param incomeConsistencyStatus JA 소득 플래그와 원문의 일치 여부(후속 임무 4장 — 기존 감사 테스트 결과를 필드로 승격)
 * @param incomeTextEvidence 원문에서 뽑은 "중위소득 N%" 근거 문구(없으면 null)
 */
public record ParsedSubsidyResult(String serviceId, String serviceName, String agency, String description,
		String eligibilityText, String categoryRawText, Integer ageMin, Integer ageMax, EligibilitySignal incomeSignal,
		EligibilitySignal householdSignal, PaymentType paymentType, String paymentTypeRawText, String externalUrl,
		LocalDateTime dataUpdatedAt, Gov24ApplicationMethodFlags applicationMethod, String requiredDocumentsText,
		DeadlineParseResult deadline, ParsedDeadline parsedDeadline, ParsedAmount amount, ParsedRegion region,
		IncomeSignalSource incomeSignalSource, IncomeConsistencyStatus incomeConsistencyStatus,
		String incomeTextEvidence, TargetAudience targetAudience, OccupationRestriction occupationRestriction) {

}
