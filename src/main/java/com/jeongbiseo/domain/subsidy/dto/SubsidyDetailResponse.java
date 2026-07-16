package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 지원금 상세 응답임(API명세서 15번 getSubsidyDetail, 신규로 lab에 대응 코드 없음). eligibilityText는 원문 null을
 * 그대로 반환함(프론트가 "정보 없음" 등으로 치환, 백엔드가 임의 문구로 치환하지 않음). isFavorite은 즐겨찾기 도메인이 아직 없어 항상
 * false임(이연). record boolean 컴포넌트는 is-접두어 스트리핑으로 "favorite"로 나갈 수 있어
 * `@JsonProperty("isFavorite")`로 필드명을 명시 고정함(CustomResponse.isSuccess 선례).
 *
 * @param subsidyId 지원금 id
 * @param name 지원금명
 * @param agency 소관기관(null 허용)
 * @param eligibilityText 자격조건 원문(null 허용, 치환하지 않음)
 * @param deadline 마감일(null이면 상시)
 * @param dDay 마감까지 남은 일수(deadline null이면 null)
 * @param estimatedAmountMin 예상 최소 금액(null 허용)
 * @param estimatedAmountMax 예상 최대 금액(null 허용)
 * @param paymentType 지급 방식 문자열(null 허용)
 * @param category 지원금 분류 문자열(null 허용)
 * @param description 상세 설명(null 허용)
 * @param externalUrl 외부 원문 링크(null 허용)
 * @param isFavorite 즐겨찾기 여부(즐겨찾기 이연으로 항상 false)
 */
public record SubsidyDetailResponse(Long subsidyId, String name, String agency, String eligibilityText,
		LocalDate deadline, Integer dDay, Long estimatedAmountMin, Long estimatedAmountMax, String paymentType,
		String category, String description, String externalUrl, @JsonProperty("isFavorite") boolean isFavorite) {
}
