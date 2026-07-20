package com.jeongbiseo.domain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관심 등록·해제 결과 응답임(API명세서 16번과 17번).
 */
public record FavoriteResponse(@Schema(description = "지원금 ID", example = "101") Long subsidyId,
		@Schema(description = "관심 등록 여부. 등록이면 true, 해제면 false임", example = "true") boolean favorited) {
}
