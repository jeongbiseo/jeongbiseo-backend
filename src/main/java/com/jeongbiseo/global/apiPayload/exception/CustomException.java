package com.jeongbiseo.global.apiPayload.exception;

import com.jeongbiseo.global.apiPayload.code.BaseErrorCode;

/**
 * 도메인 실패를 BaseErrorCode로 감싸 전달하는 런타임 예외임. GlobalExceptionHandler가 이 타입을 CustomResponse 실패
 * 응답으로 변환함. serialVersionUID는 spotbugs-exclude.xml의 SE_NO_SERIALVERSIONUID 전역 제외로 생략함(팀
 * 관례).
 */
public class CustomException extends RuntimeException {

	private final transient BaseErrorCode errorCode;

	public CustomException(BaseErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	/**
	 * 원인 예외를 보존하는 오버로드임. 예기치 못한 하위 예외를 도메인 에러코드로 감쌀 때 원인 스택이 서버 로그에서 유실되지 않게 함.
	 */
	public CustomException(BaseErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	public BaseErrorCode getErrorCode() {
		return errorCode;
	}

}
