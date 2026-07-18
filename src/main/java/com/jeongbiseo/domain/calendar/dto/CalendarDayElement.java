package com.jeongbiseo.domain.calendar.dto;

import java.time.LocalDate;

/**
 * 캘린더 날짜별 그룹 내부에 담길 개별 지원금 항목 DTO
 */
public record CalendarDayElement(Long subsidyId, String title, LocalDate deadlineDate, long dDay) {
}