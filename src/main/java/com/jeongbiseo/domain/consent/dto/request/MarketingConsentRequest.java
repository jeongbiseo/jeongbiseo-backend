package com.jeongbiseo.domain.consent.dto.request;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마케팅 수신 동의 변경 요청 본문임(operationId updateMarketingConsent). 토글이지만 서버는 현재 값을 뒤집지 않고 프론트가 보낸
 * 목표 상태로 멱등하게 설정함 — 더블탭·재시도로 상태가 뒤집히는 것을 막기 위함임. Boolean 래퍼로 두어 누락을 @NotNull로
 * 잡음(primitive면 누락이 false로 흡수됨).
 *
 * @param agreed 설정할 마케팅 수신 동의 여부(필수)
 */
public record MarketingConsentRequest(
		@Schema(description = "설정할 마케팅 수신 동의 여부", example = "true") @NotNull(message = "동의 여부는 필수예요") Boolean agreed) {

}
