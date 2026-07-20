package com.jeongbiseo.domain.calendar.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 캘린더의 날짜별 그룹 DTO임(API명세서 18번 result.days 원소). 날짜를 객체 키로 쓰지 않고 배열 원소로 두는 이유는 두 가지임. JSON
 * 객체는 키 순서가 무보장이라 마감일 오름차순 정렬이 계약으로 성립하지 않고, 동적 키 객체는 springdoc이 additionalProperties로 뭉개
 * API 문서에 date·items 스키마가 드러나지 않음.
 */
public record CalendarDay(@Schema(description = "마감 날짜(YYYY-MM-DD)", example = "2026-07-25") LocalDate date,
		@Schema(description = "해당 날짜에 마감하는 관심 지원금 배열") List<CalendarDayElement> items) {
}
