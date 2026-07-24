package com.jeongbiseo.domain.subsidy.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 상세 응답임(API명세서 15번 getSubsidyDetail, 신규로 lab에 대응 코드 없음). eligibilityText는 원문 null을
 * 그대로 반환함(프론트가 "정보 없음" 등으로 치환, 백엔드가 임의 문구로 치환하지 않음). isFavorite은 로그인 회원의 관심 등록 여부를 반영하고
 * 비로그인이면 false임. record boolean 컴포넌트는 is-접두어 스트리핑으로 "favorite"로 나갈 수 있어
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
 * @param isFavorite 관심 등록 여부(비로그인이면 false)
 * @param aiExplanation AI 금액 해석(등급 1). 검증 통과분이 없으면 null이며 프론트는 기존 산정불가 배지를 유지함
 */
public record SubsidyDetailResponse(@Schema(description = "지원금 ID", example = "101") Long subsidyId,
		@Schema(description = "지원금명", example = "청년월세지원") String name,
		@Schema(description = "소관기관. 원천 데이터에 기관명이 없으면 null임", nullable = true) String agency,
		@Schema(description = "자격조건 원문. 원천에 없으면 null이며 백엔드가 임의 문구로 치환하지 않음", nullable = true) String eligibilityText,
		@Schema(description = "마감일. 상시 모집이거나 마감일이 없는 유형이면 null임", nullable = true) LocalDate deadline,
		@Schema(description = "마감까지 남은 일수. deadline이 null이면 계산하지 않고 null임", nullable = true) Integer dDay,
		@Schema(description = "예상 최소 금액(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMin,
		@Schema(description = "예상 최대 금액(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMax,
		PaymentType paymentType, @Schema(nullable = true) SubsidyCategory category,
		@Schema(description = "상세 설명. 원천에 설명이 없으면 null임", nullable = true) String description,
		@Schema(description = "외부 원문 링크. 원천에 링크가 없으면 null임", nullable = true) String externalUrl,
		@Schema(description = "관심 등록 여부. 로그인 회원의 등록 여부를 반영하고 비로그인·만료 토큰이면 false임(선택 인증 엔드포인트)",
				example = "false") @JsonProperty("isFavorite") boolean isFavorite,
		@Schema(description = "AI 금액 해석. 공고 원문을 LLM이 읽어 구조화하고 검증기를 통과한 결과만 실림. "
				+ "통과분이 없으면 null이며 그때는 기존 금액 필드와 산정불가 배지를 그대로 쓰면 됨. "
				+ "값과 함께 근거 문장(evidence)이 오므로 사용자가 원문과 대조할 수 있게 함께 표시할 것",
				nullable = true) AiExplanation aiExplanation) {
}
