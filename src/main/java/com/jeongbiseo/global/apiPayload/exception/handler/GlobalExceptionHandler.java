package com.jeongbiseo.global.apiPayload.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 전역 예외 처리기임. CustomException은 담긴 BaseErrorCode 그대로, `@Valid` 검증 실패는 VALID400_1 더하기 필드별
 * 안내 문구로 변환함(API명세서 "응답 envelope" 절, VALID400_1은 result에 필드 오류맵을 담음). 위 핸들러가 못 잡는 나머지 예외는
 * handleUnexpected가 COMMON500 봉투로 감싸 최종 안전망 역할을 함(예상 못한 예외도 Spring 기본 에러 바디가 아니라 반드시
 * CustomResponse 봉투로 나가야 함).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<CustomResponse<Void>> handleCustomException(CustomException e) {
		var errorCode = e.getErrorCode();
		// cause가 있는 경우는 예기치 못한 하위 예외를 감싼 것이라 원인 추적용으로 스택을 남김(응답에는 담지 않음).
		if (e.getCause() != null) {
			log.error("CustomException 원인 예외: code={}", errorCode.getCode(), e);
		}
		return ResponseEntity.status(errorCode.getHttpStatus()).body(CustomResponse.fail(errorCode));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<CustomResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(CustomResponse.fail(ValidationErrorCode.INVALID_DTO_FIELD, fieldErrors));
	}

	/**
	 * 쿼리·경로 파라미터가 선언 타입으로 변환되지 않을 때(예 추천 limit에 `?limit=abc`) VALID400_0으로 변환함. 최종 안전망보다
	 * 먼저 잡아 500이 아니라 400 계약으로 나가게 함(HANDOFF 2.B-14).
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<CustomResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(CustomResponse.fail(ValidationErrorCode.INVALID_QUERY_PARAMETER));
	}

	/**
	 * 위 핸들러가 담당하지 않는 예기치 못한 예외의 최종 안전망임. 깨진 JSON 본문(HttpMessageNotReadableException), DB
	 * 제약 위반(DataIntegrityViolationException), 매핑되지 않은 경로(NoResourceFoundException) 등 어떤
	 * 예외든 COMMON500 봉투로 감싸 응답 envelope 계약을 예외 종류와 무관하게 보장함. 매핑되지 않은 경로도 500으로 뭉뚱그려지는 것은
	 * 알려진 단순화임(ponytail: 계약에 있는 경로만 다루므로 404 대 405 대 500을 세분화하는 핸들러를 두지 않음. 세분화가 필요해지면 이
	 * 핸들러 앞에 전용 핸들러를 추가하는 것이 대안임). 응답에는 스택트레이스를 담지 않되(정보 노출 방지) 서버 로그에는 남겨 원인 추적이 가능하게 함.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<CustomResponse<Void>> handleUnexpected(Exception e) {
		log.error("예기치 못한 예외를 COMMON500으로 감쌈", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(CustomResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR));
	}

}
