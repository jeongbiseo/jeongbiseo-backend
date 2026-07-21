package com.jeongbiseo.domain.consent.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마케팅 수신 동의 변경 응답임(operationId updateMarketingConsent). 변경 후의 동의 여부와 변경 시각을 반환해 프론트 토글이
 * 서버 확정 상태로 동기화하게 함.
 *
 * @param agreed 변경된 마케팅 동의 여부
 * @param updatedAt 변경 시각
 */
public record MarketingConsentResponse(@Schema(description = "변경된 마케팅 동의 여부") boolean agreed,
		@Schema(description = "변경 시각") LocalDateTime updatedAt) {

}
