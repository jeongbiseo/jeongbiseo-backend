package com.jeongbiseo.domain.recommendation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 추천 리스트 조회 응답임(API명세서 14번). 매칭 0건이면 items가 빈 배열이며 이는 에러가 아니라 정상 응답임(REC-321).
 *
 * @param items 추천 지원금 배열(요청 limit만큼, 기본 3·상한 20, 마감 임박순, 기수령 제외)
 * @param dataUpdatedAt 지원금 데이터 갱신 시각
 */
public record RecommendationResponse(List<RecommendationItemResponse> items, LocalDateTime dataUpdatedAt) {

}
