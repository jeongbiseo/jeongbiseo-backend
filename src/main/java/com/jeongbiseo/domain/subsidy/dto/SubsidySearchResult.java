package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;

/**
 * 지원금 검색 결과 한 건임(API명세서 §13 searchSubsidies). SubsidyRepository.search가 JPQL constructor
 * expression으로 직접 채워 반환함(엔티티 전체를 노출하지 않음).
 *
 * @param subsidyId 지원금 id
 * @param name 지원금명
 * @param agency 소관기관(null 허용)
 * @param category 지원금 분류(null 허용)
 * @param deadline 마감일(null이면 상시)
 */
public record SubsidySearchResult(Long subsidyId, String name, String agency, SubsidyCategory category,
		LocalDate deadline) {
}
