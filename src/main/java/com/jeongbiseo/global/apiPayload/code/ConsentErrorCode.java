package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 약관 도메인 에러코드임. 현재 약관 버전이 term_version에 등록되지 않은 상태(시더 미실행 등 서버 구성 문제)를 도메인 오류 계약으로 표현함.
 * IllegalStateException으로 던지면 전역 핸들러의 최종 안전망을 우회할 수 있어 CustomException+ErrorCode로 통일함.
 * 약관은 아직 엔드포인트가 없어 명세서 공통 에러코드 표에는 없으며, 소셜 인증 연결 시 노출 경로가 생기면 등재함.
 */
public enum ConsentErrorCode implements BaseErrorCode {

	TERM_VERSION_NOT_REGISTERED(HttpStatus.INTERNAL_SERVER_ERROR, "CONSENT500_1", "약관 버전이 등록되지 않았어요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	ConsentErrorCode(HttpStatus httpStatus, String code, String message) {
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
