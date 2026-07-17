package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 온보딩 도메인 에러코드임(API명세서 "공통 에러코드" 절). 온보딩 미완료 조회·수정(ONB404_1)과 재제출(ONB409_1) 2건임.
 */
public enum OnboardingErrorCode implements BaseErrorCode {

	ONBOARDING_NOT_FOUND(HttpStatus.NOT_FOUND, "ONB404_1", "온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요"),
	ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "ONB409_1", "이미 온보딩을 완료했어요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	OnboardingErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}

}
