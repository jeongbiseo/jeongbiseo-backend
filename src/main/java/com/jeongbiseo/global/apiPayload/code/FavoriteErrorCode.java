package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 관심 등록 도메인 에러코드임(API명세서 16번과 17번).
 */
public enum FavoriteErrorCode implements BaseErrorCode {

	FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAVORITE409_1", "이미 관심 등록한 지원금이에요"),
	FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE404_1", "관심 등록되지 않은 지원금이에요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	FavoriteErrorCode(HttpStatus httpStatus, String code, String message) {
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
