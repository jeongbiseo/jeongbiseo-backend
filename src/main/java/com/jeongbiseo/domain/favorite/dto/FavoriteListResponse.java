package com.jeongbiseo.domain.favorite.dto;

import java.util.List;

import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관심 목록 조회 응답임(API명세서 getFavorites). 아이템은 검색 결과와 동일한 SubsidySearchResult를 재사용해 프론트가 같은 카드
 * 컴포넌트로 렌더하게 함. totalCount는 마이페이지 "즐겨찾기 N건" 카운트용임.
 *
 * @param content 최근 등록순 관심 지원금 목록
 * @param totalCount 관심 지원금 총 개수
 */
public record FavoriteListResponse(List<SubsidySearchResult> content,
		@Schema(description = "관심 지원금 총 개수", example = "8") int totalCount) {

	public static FavoriteListResponse from(List<SubsidySearchResult> content) {
		return new FavoriteListResponse(content, content.size());
	}

}
