package com.jeongbiseo.domain.calendar.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 마감 캘린더 최종 응답 DTO
 */
public record CalendarResponse(int year, int month, Map<LocalDate, List<CalendarDayElement>> days) {
}