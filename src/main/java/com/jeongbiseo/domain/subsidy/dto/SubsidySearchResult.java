package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 검색 결과 한 건임(API명세서 13번 searchSubsidies). SubsidyRepository.search가 JPQL constructor
 * expression으로 직접 채워 반환함(엔티티 전체를 노출하지 않음).
 *
 * @param subsidyId 지원금 id
 * @param name 지원금명
 * @param agency 소관기관(null 허용)
 * @param category 지원금 분류(null 허용)
 * @param deadline 마감일(null이면 상시)
 * @param estimatedAmountMin 예상 금액 하한(null이면 산정 불가). 단일 금액이면 min·max가 같음
 * @param estimatedAmountMax 예상 금액 상한(null이면 산정 불가)
 */
public record SubsidySearchResult(Long subsidyId, String name,
		@Schema(description = "소관기관. 원천 데이터에 기관명이 없으면 null임", nullable = true) String agency,
		@Schema(nullable = true) SubsidyCategory category,
		@Schema(description = "마감일. 상시 모집이거나 마감일이 없는 유형이면 null임", nullable = true) LocalDate deadline,
		@Schema(description = "예상 금액 하한. 산정 불가면 null(정렬은 미제공, 표시용). 단일 금액이면 max와 같음",
				nullable = true) Long estimatedAmountMin,
		@Schema(description = "예상 금액 상한. 산정 불가면 null", nullable = true) Long estimatedAmountMax) {
}
