package com.jeongbiseo.domain.recommendation.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 추천 리스트 항목 응답임(API명세서 14번). estimatedAmountMin과 estimatedAmountMax는 미제공 시 null임
 * (REC-312). uncomputable과 uncomputableReasons는 화면 231913의 "산정불가" 배지를 위해 demo가 계약에 추가한 확장
 * 필드임(DISCUSS.md 3.4 핵심 규칙, 하위호환 필드 추가이므로 API명세서 4장 버전 증가 기준의 "v1 유지" 해당, v2 신설 대상 아님).
 *
 * @param subsidyId 지원금 ID
 * @param name 지원금명
 * @param agency 담당 기관
 * @param deadline 신청 마감일
 * @param dDay 마감까지 남은 일수
 * @param eligibilitySummary 자격요건 요약
 * @param estimatedAmountMin 예상 수령액 하한(원, 미제공 시 null)
 * @param estimatedAmountMax 예상 수령액 상한(원, 미제공 시 null)
 * @param paymentType 지급 유형(확장 필드, 2026-07-20 프론트 요청)
 * @param matchScore 적합도 점수
 * @param uncomputable 산정불가 여부(확장 필드)
 * @param uncomputableReasons 산정불가 사유 안내 문구 목록(확장 필드, 비어있으면 산정 가능)
 * @param confirmedMatchCount 확인된 개인 조건 수(확장 필드, 0에서 4)
 * @param unverifiedConditionCount 추가 확인 필요 조건 수(확장 필드, 0에서 4)
 * @param confirmedAgeRange 확인된 대상 연령 범위(확장 필드). 연령이 확정됐을 때만 값이 있고 아니면 null임
 */
public record RecommendationItemResponse(Long subsidyId, String name, String agency,
		@Schema(description = "신청 마감일. 상시 모집이거나 마감일이 없는 유형이면 null임", nullable = true) LocalDate deadline,
		@Schema(description = "마감까지 남은 일수. deadline이 null이면 계산하지 않고 null임", nullable = true) Integer dDay,
		String eligibilitySummary,
		@Schema(description = "예상 수령액 하한(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMin,
		@Schema(description = "예상 수령액 상한(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMax,
		@Schema(description = "지급 유형. 비현금과 UNKNOWN도 추천에 노출되므로 배지·금액 표기 분기에 씀") PaymentType paymentType,
		Integer matchScore, boolean uncomputable, List<String> uncomputableReasons,
		@Schema(description = "확인된 개인 조건 수(0에서 4). 지원금이 명시한 제한을 사용자 정보로 통과 확인한 축의 개수임. "
				+ "지역은 별도 축이라 포함하지 않음. matchScore와 달리 제약없음·불명은 세지 않음이라 백분율·5점 표기에 쓰지 말 것") int confirmedMatchCount,
		@Schema(description = "추가 확인 필요 조건 수(0에서 4). 자격 축 중 조건 미공개·세부기준 부재·사용자 정보 미입력으로 "
				+ "판단 보류된 축의 개수임(금액 산정불가는 포함하지 않음)") int unverifiedConditionCount,
		@Schema(description = "공고의 대상 연령 범위. 연령 조건이 확정됐을 때만 값이 있고 아니면 null임. "
				+ "사용자 나이가 아니라 공고 조건임. 한쪽만 개방이면 그쪽만 null", nullable = true) ConfirmedAgeRange confirmedAgeRange) {

	/** 공고의 대상 연령 범위임(추천 이유 표시용). 각 경계는 개방이면 null임. */
	public record ConfirmedAgeRange(@Schema(nullable = true) Integer minAge, @Schema(nullable = true) Integer maxAge) {

	}

}
