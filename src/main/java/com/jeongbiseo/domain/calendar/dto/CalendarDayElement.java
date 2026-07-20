package com.jeongbiseo.domain.calendar.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 캘린더 날짜별 그룹 내부에 담길 개별 지원금 항목 DTO임. 필드명은 API명세서 18번 계약을 따르며, 코드베이스 공통 관례이기도
 * 함(SubsidySummary·EstimatedTotalResult 등이 모두 name·deadline을 씀).
 */
public record CalendarDayElement(@Schema(description = "지원금 ID", example = "101") Long subsidyId,
		@Schema(description = "지원금명", example = "청년월세 특별지원") String name,
		@Schema(description = "신청 마감일(YYYY-MM-DD)", example = "2026-07-25") LocalDate deadline,
		@Schema(description = "마감까지 남은 일수. 지난 마감은 조회 대상에서 제외되므로 0 이상만 반환함", example = "5") long dDay) {
}