package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 추천 도메인 에러코드임(API명세서 "공통 에러코드" 절). 추천 계산 도중 예기치 못한 오류가 나면 이 코드로 감싸 던짐. 매칭 0건(정상)과 서버
 * 오류(비정상)를 구분함(REC-321).
 */
public enum RecommendationErrorCode implements BaseErrorCode {

	RECOMMENDATION_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "REC500_1", "추천을 불러오지 못했어요, 잠시 후 다시 시도해주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	RecommendationErrorCode(HttpStatus httpStatus, String code, String message) {
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
