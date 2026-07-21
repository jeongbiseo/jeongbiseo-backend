package com.jeongbiseo.domain.subsidy.dto;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 카테고리 목록 항목임(API명세서 12번 getSubsidyCategories). 화면 필터 칩 1개에 대응하며 code로 검색(13번)의
 * category 파라미터를 채움.
 *
 * @param code SubsidyCategory enum 값
 * @param label 화면 표시 한국어 라벨
 */
public record SubsidyCategoryResponse(@Schema(description = "카테고리 코드", example = "YOUTH") String code,
		@Schema(description = "카테고리 라벨", example = "청년") String label) {

	public static SubsidyCategoryResponse from(SubsidyCategory category) {
		return new SubsidyCategoryResponse(category.name(), category.getLabel());
	}

}
