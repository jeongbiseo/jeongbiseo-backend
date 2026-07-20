package com.jeongbiseo.domain.favorite.dto;

/**
 * 관심 등록·해제 결과 응답임(API명세서 16번과 17번).
 */
public record FavoriteResponse(Long subsidyId, boolean favorited) {
}
