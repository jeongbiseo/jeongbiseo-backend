package com.jeongbiseo.domain.calendar.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마감 캘린더 최종 응답 DTO임. days는 날짜를 키로 하는 객체가 아니라 배열이며 마감일 오름차순임(API명세서 18번). 관심 등록이 없거나 캘린더
 * 대상이 0건이면 에러가 아니라 빈 배열을 반환함.
 */
public record CalendarResponse(@Schema(description = "조회 연도", example = "2026") int year,
		@Schema(description = "조회 월", example = "7") int month,
		@Schema(description = "마감일별 그룹. 마감일 오름차순이며 대상이 0건이면 빈 배열임") List<CalendarDay> days) {
}