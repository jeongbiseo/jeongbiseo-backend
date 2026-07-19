package com.jeongbiseo.domain.calendar.dto;

import java.time.LocalDate;

/**
 * 캘린더 날짜별 그룹 내부에 담길 개별 지원금 항목 DTO임. 필드명은 API명세서 18번 계약을 따르며, 코드베이스 공통 관례이기도
 * 함(SubsidySummary·EstimatedTotalResult 등이 모두 name·deadline을 씀).
 */
public record CalendarDayElement(Long subsidyId, String name, LocalDate deadline, long dDay) {
}