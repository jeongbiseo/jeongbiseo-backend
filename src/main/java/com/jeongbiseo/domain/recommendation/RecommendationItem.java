package com.jeongbiseo.domain.recommendation;

import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;

/**
 * 추천 리스트 항목임(표시 정보와 매칭 결과를 결합한 값 객체). API 응답 DTO로 변환하기 전 도메인 산출물임(PLAN.md 3장 W3 절).
 *
 * @param summary 지원금 표시 정보
 * @param matchResult 매칭 판정 결과(matchScore, 산정불가 사유 포함)
 */
public record RecommendationItem(SubsidySummary summary, MatchResult matchResult) {

}
