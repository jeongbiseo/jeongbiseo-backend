package com.jeongbiseo.infra.client.gov24.dto;

import java.time.LocalDate;

/**
 * 신청기한 자유텍스트 1건의 파싱 결과임. 성공하면 deadline에 값이 있고 failureReason은 null, 실패하면 반대임(PLAN.md 3장 W4
 * 절 완료 조건 — 성공하면 파싱, 실패하면 사유와 함께 실패 기록).
 *
 * @param rawText 원문 신청기한 텍스트
 * @param parsed 파싱 성공 여부
 * @param deadline 파싱된 마감일(실패 시 null)
 * @param failureReason 실패 사유(성공 시 null)
 */
public record DeadlineParseResult(String rawText, boolean parsed, LocalDate deadline,
		DeadlineFailureReason failureReason) {

	/**
	 * 파싱 성공 결과를 생성함.
	 * @param rawText 원문 신청기한 텍스트
	 * @param deadline 파싱된 마감일
	 * @return 성공 결과
	 */
	public static DeadlineParseResult success(String rawText, LocalDate deadline) {
		return new DeadlineParseResult(rawText, true, deadline, null);
	}

	/**
	 * 파싱 실패 결과를 생성함.
	 * @param rawText 원문 신청기한 텍스트
	 * @param reason 실패 사유
	 * @return 실패 결과
	 */
	public static DeadlineParseResult failure(String rawText, DeadlineFailureReason reason) {
		return new DeadlineParseResult(rawText, false, null, reason);
	}

}
