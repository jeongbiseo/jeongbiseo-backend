package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 요청 검증 실패 에러코드임(API명세서 "공통 에러코드" 절). `@Valid` DTO 필드 검증 실패(VALID400_1)와
 * Query/PathVariable 검증 실패(VALID400_0)를 다룸. VALID400_0은 추천 limit처럼 0 이하이거나 정수로 파싱되지 않는 쿼리
 * 파라미터에 씀(HANDOFF 2.B-14, 개수는 프론트가 limit로 결정하되 잘못된 값은 400으로 거절).
 */
public enum ValidationErrorCode implements BaseErrorCode {

	INVALID_QUERY_PARAMETER(HttpStatus.BAD_REQUEST, "VALID400_0", "잘못된 파라미터 입니다."),
	INVALID_DTO_FIELD(HttpStatus.BAD_REQUEST, "VALID400_1", "잘못된 DTO 필드입니다.");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	ValidationErrorCode(HttpStatus httpStatus, String code, String message) {
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
