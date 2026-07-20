package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드임(API명세서 "공통 에러코드" 절). 최종 안전망(Exception fallback)이 쓰는 COMMON500과 매핑되지 않은 경로 전용
 * COMMON404 2건만 둠. COMMON400/401/403/405는 각 도메인 에러코드나 `@Valid` 검증(VALID400_1)이 덮으므로 실제로
 * 필요해질 때 도메인과 함께 추가함.
 */
public enum CommonErrorCode implements BaseErrorCode {

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다"),

	// 매핑되지 않은 경로 전용임. 도메인 자원 404(SUBSIDY404_1 등)와 구분함 — 이쪽은 "그런 API가 없음"이고
	// 도메인 404는 "API는 있으나 그 id의 자원이 없음"임.
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청하신 경로를 찾을 수 없습니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	CommonErrorCode(HttpStatus httpStatus, String code, String message) {
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
