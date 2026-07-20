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
 */
public record RecommendationItemResponse(Long subsidyId, String name, String agency,
		@Schema(description = "신청 마감일. 상시 모집이거나 마감일이 없는 유형이면 null임", nullable = true) LocalDate deadline,
		@Schema(description = "마감까지 남은 일수. deadline이 null이면 계산하지 않고 null임", nullable = true) Integer dDay,
		String eligibilitySummary,
		@Schema(description = "예상 수령액 하한(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMin,
		@Schema(description = "예상 수령액 상한(원). 원천에 금액 정보가 없으면 null임", nullable = true) Long estimatedAmountMax,
		@Schema(description = "지급 유형. 비현금과 UNKNOWN도 추천에 노출되므로 배지·금액 표기 분기에 씀") PaymentType paymentType,
		Integer matchScore, boolean uncomputable, List<String> uncomputableReasons) {

}
