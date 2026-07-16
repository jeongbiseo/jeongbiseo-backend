package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 지원금 도메인 에러코드임(API명세서 §13·§15). subsidyId가 존재하지 않는 지원금 상세 조회나 setReceivedSubsidies 존재 검증
 * 실패 시 이 코드로 통일해 던짐.
 */
public enum SubsidyErrorCode implements BaseErrorCode {

	SUBSIDY_NOT_FOUND(HttpStatus.NOT_FOUND, "SUBSIDY404_1", "해당 지원금 정보를 찾을 수 없어요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	SubsidyErrorCode(HttpStatus httpStatus, String code, String message) {
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
