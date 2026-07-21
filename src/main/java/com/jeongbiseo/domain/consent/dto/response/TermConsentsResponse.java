package com.jeongbiseo.domain.consent.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마이페이지 약관 화면 조회 응답임(operationId getMyTermConsents). 표시 약관(서비스 이용약관·개인정보 처리방침)의 동의 상태 목록과
 * 마케팅 정보 수신 동의 상태를 함께 반환함. 표시 약관은 화면에 노출하는 2종만 담고 만 14세 이상 확인은 화면 미표시라 제외함(기획 확정,
 * 2026-07-21).
 *
 * @param terms 표시 약관 동의 목록
 * @param marketingConsent 마케팅 수신 동의 여부
 * @param marketingConsentUpdatedAt 마케팅 동의 최종 변경 시각(변경 이력 없으면 null)
 */
public record TermConsentsResponse(@Schema(description = "표시 약관 동의 목록(서비스 이용약관·개인정보 처리방침)") List<TermConsentItem> terms,
		@Schema(description = "마케팅 정보 수신 동의 여부") boolean marketingConsent,
		@Schema(description = "마케팅 동의 최종 변경 시각. 변경 이력이 없으면 null",
				nullable = true) LocalDateTime marketingConsentUpdatedAt) {

}
