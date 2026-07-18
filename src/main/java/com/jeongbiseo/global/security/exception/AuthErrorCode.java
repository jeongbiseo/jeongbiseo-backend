package com.jeongbiseo.global.security.exception;

import org.springframework.http.HttpStatus;

import com.jeongbiseo.global.apiPayload.code.BaseErrorCode;

/**
 * 인증 도메인 에러코드임(소셜인증-전환설계 4장). 설계가 명시한 AUTH401_1(소셜 실패, 사유 비노출 통합)과 AUTH401_2(재로그인)만 둠.
 * VALID400_0은 ValidationErrorCode.INVALID_QUERY_PARAMETER가, COMMON401은 CommonErrorCode가
 * 정본이라 여기서 중복 정의하지 않음.
 */
public enum AuthErrorCode implements BaseErrorCode {

	SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_1", "소셜 로그인에 실패했어요, 다시 시도해주세요."),
	REFRESH_TOKEN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_2", "다시 로그인해주세요.");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	AuthErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

	@Override
	public String getCode() {
		return this.code;
	}

	@Override
	public String getMessage() {
		return this.message;
	}

}
