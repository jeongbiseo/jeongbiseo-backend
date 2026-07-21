package com.jeongbiseo.domain.consent.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.jeongbiseo.domain.consent.TermType;

/**
 * 마이페이지 표시 약관 1건의 동의 상태임(getMyTermConsents 응답 요소). 약관 본문·버전은 싣지 않고 화면이 필요로 하는 동의 여부와 동의
 * 시각만 담음(기획 확정, 2026-07-21). 동의 이력이 없으면 agreed는 false, agreedAt은 null임.
 *
 * @param type 약관 항목 코드
 * @param label 약관 표시명
 * @param agreed 동의 여부
 * @param agreedAt 동의 시각(미동의면 null)
 */
public record TermConsentItem(@Schema(description = "약관 항목 코드", example = "SERVICE") TermType type,
		@Schema(description = "약관 표시명", example = "서비스 이용약관") String label,
		@Schema(description = "동의 여부. 동의 이력이 없으면 false") boolean agreed,
		@Schema(description = "동의 시각. 미동의면 null", nullable = true) LocalDateTime agreedAt) {

	/** 동의 시각으로부터 항목을 만듦. agreedAt이 null이면 미동의로 판정함. */
	public static TermConsentItem of(TermType type, LocalDateTime agreedAt) {
		return new TermConsentItem(type, type.label(), agreedAt != null, agreedAt);
	}

}
