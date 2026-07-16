package com.jeongbiseo.infra.client.common.dto;

import java.time.LocalDate;

/**
 * 신청기한 자유텍스트를 {@link DeadlineKind} 7분류로 판정한 결과임. FIXED_DATE·DATE_RANGE만 실제 날짜를 채우고 나머지
 * 5종은 startDate·endDate가 전부 null임(그 분류 자체가 "특정 날짜가 없다"는 의미이므로 — 임무 지시 1장).
 *
 * @param kind 7분류 판정 결과
 * @param startDate 시작일(DATE_RANGE만 채움, 그 외 null)
 * @param endDate 종료일(FIXED_DATE는 그 단일 날짜, DATE_RANGE는 범위 종료일. 그 외 null) — 마감 캘린더에서 "이
 * 지원금의 마감일"로 쓸 단일 날짜가 필요하면 이 필드를 우선 참조함
 * @param rawText 원문 신청기한 텍스트
 */
public record ParsedDeadline(DeadlineKind kind, LocalDate startDate, LocalDate endDate, String rawText) {

}
