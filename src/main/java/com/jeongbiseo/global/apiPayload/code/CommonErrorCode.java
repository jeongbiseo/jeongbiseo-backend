package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드임(API명세서 "공통 에러코드" 절). 최종 안전망(Exception fallback)이 쓰는 COMMON500, 매핑되지 않은 경로 전용
 * COMMON404, 그리고 인증 강제화(AUTH-W001)로 시큐리티 필터가 던지는 COMMON401·COMMON403을 둠. COMMON401·403은
 * DispatcherServlet 앞 필터 계층에서 SecurityErrorResponder가 사용함(GlobalExceptionHandler가 잡지 못하는
 * 계층).
 */
public enum CommonErrorCode implements BaseErrorCode {

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다"),

	// 인증 없이 보호 엔드포인트를 부를 때 시큐리티 EntryPoint가 반환함(API명세서 각주 COMMON401 정합). 프론트는 이 401을
	// 받아 reissue를 트리거함.
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다"),

	// 인증됐으나 권한이 없을 때 AccessDeniedHandler가 반환함. 현재 역할 분기가 없어 실제로는 거의 발생하지 않지만, 보호 계층의
	// 응답 봉투 계약을 완결하기 위해 둠.
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMON403", "접근 권한이 없습니다"),

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
