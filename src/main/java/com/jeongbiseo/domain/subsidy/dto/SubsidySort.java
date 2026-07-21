package com.jeongbiseo.domain.subsidy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 검색 정렬 옵션임(API명세서 13번 searchSubsidies의 sort 파라미터). 미지정이면 현행 id 오름차순을 유지해 하위호환을 지킴.
 * 금액순(AMOUNT)은 비현금 지원금이 금액을 갖지 않아 제외했고, 전체 탭 "추천순"은 LLM 추천 완성 전까지 sort 미지정(기본 순서)으로 대응함.
 */
// 허용값 밖 문자열은 enum 바인딩 실패로 VALID400_0을 유발함(SubsidyCategory 파라미터와 동일 관용).
@Schema(description = """
		지원금 검색 정렬 기준.
		DEADLINE: 마감일 임박순(마감일 미상은 항상 뒤) / NAME: 지원금명 가나다순.
		미지정이면 등록 순서(id 오름차순).""")
public enum SubsidySort {

	DEADLINE, NAME

}
