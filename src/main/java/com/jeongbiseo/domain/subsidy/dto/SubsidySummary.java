package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

/**
 * 추천 응답 조립에 쓰는 지원금 표시 정보임(값 객체). SubsidyCriteria(매칭 조건 스냅샷)와 분리해 표시용 필드만 담음(storage 타입이
 * domain 밖으로 새지 않게 함).
 *
 * @param subsidyId 지원금 식별자
 * @param name 지원금명
 * @param agency 담당 기관
 * @param deadline 신청 마감일(상시 또는 미정이면 null)
 * @param eligibilitySummary 자격요건 요약(원문 eligibilityText)
 * @param estimatedAmountMin 예상 수령액 하한(원, 미제공 시 null)
 * @param estimatedAmountMax 예상 수령액 상한(원, 미제공 시 null)
 */
public record SubsidySummary(Long subsidyId, String name, String agency, LocalDate deadline, String eligibilitySummary,
		Long estimatedAmountMin, Long estimatedAmountMax) {

}
