package com.jeongbiseo.domain.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 카테고리임. API명세서 공통 enum 절에서 7개 값으로 확정함.
 */
// 라벨 정본은 API명세서 SubsidyCategory 절임.
@Schema(description = """
		지원금 카테고리.
		YOUTH: 청년 / HOUSING: 주거 / EMPLOYMENT: 고용 / EDUCATION: 교육
		STARTUP: 창업 / WELFARE: 복지 / ETC: 기타""")
public enum SubsidyCategory {

	YOUTH("청년"), HOUSING("주거"), EMPLOYMENT("고용"), EDUCATION("교육"), STARTUP("창업"), WELFARE("복지"), ETC("기타");

	private final String label;

	SubsidyCategory(String label) {
		this.label = label;
	}

	// 화면 필터 칩 표시용 한국어 라벨임(getSubsidyCategories 응답).
	public String getLabel() {
		return this.label;
	}

}
