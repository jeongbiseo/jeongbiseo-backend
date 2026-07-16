package com.jeongbiseo.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.jeongbiseo.global.apiPayload.code.BaseErrorCode;

/**
 * 정비서 API 표준 응답 봉투임(API명세서 "응답 envelope" 절). JSON 필드 순서를 isSuccess, code, message,
 * result로 고정함. 템플릿의 ApiResponse 대신 이 타입만 사용함(AGENTS.md 코드 규칙). isSuccess에 명시적으로
 * `@JsonProperty`를 붙여, record 컴포넌트명과 무관하게 JSON 필드명이 정확히 "isSuccess"로 나가게 고정함(자바빈 is-접두어
 * 스트리핑 관례와 혼동 방지).
 *
 * @param <T> 결과 데이터 타입
 * @param isSuccess 성공 여부
 * @param code 성공은 HTTP 숫자만("200", "201"), 실패는 도메인 prefix 형식
 * @param message 응답 메시지
 * @param result 결과 데이터(성공 시 API별 데이터, 실패 시 null 또는 VALID400_1의 필드 오류맵)
 */
@JsonPropertyOrder({ "isSuccess", "code", "message", "result" })
public record CustomResponse<T>(@JsonProperty("isSuccess") boolean isSuccess, @JsonProperty("code") String code,
		@JsonProperty("message") String message, @JsonProperty("result") T result) {

	private static final String SUCCESS_MESSAGE_OK = "OK";

	private static final String SUCCESS_MESSAGE_CREATED = "Created";

	/**
	 * 200 OK 성공 응답을 만듦.
	 */
	public static <T> CustomResponse<T> ok(T result) {
		return new CustomResponse<>(true, "200", SUCCESS_MESSAGE_OK, result);
	}

	/**
	 * 201 Created 성공 응답을 만듦.
	 */
	public static <T> CustomResponse<T> created(T result) {
		return new CustomResponse<>(true, "201", SUCCESS_MESSAGE_CREATED, result);
	}

	/**
	 * 실패 응답을 만듦(result 없음).
	 */
	public static CustomResponse<Void> fail(BaseErrorCode errorCode) {
		return fail(errorCode, null);
	}

	/**
	 * 실패 응답을 만듦(result에 부가 정보 포함, 예 VALID400_1의 필드 오류맵).
	 */
	public static <T> CustomResponse<T> fail(BaseErrorCode errorCode, T result) {
		return new CustomResponse<>(false, errorCode.getCode(), errorCode.getMessage(), result);
	}

}
