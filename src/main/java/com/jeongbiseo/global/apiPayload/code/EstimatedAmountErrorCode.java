package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 예상 총액 도메인 에러코드임(API명세서 19번 FAIL 2). 예상 금액 계산 도중 예기치 못한 오류가 나면 이 코드로 감쌈. 메시지는 명세서 기존 문구와
 * 동일하게 맞춤(springdoc 정합).
 */
public enum EstimatedAmountErrorCode implements BaseErrorCode {

	ESTIMATED_AMOUNT_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AMT500_1", "예상 금액을 계산하지 못했어요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	EstimatedAmountErrorCode(HttpStatus httpStatus, String code, String message) {
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
