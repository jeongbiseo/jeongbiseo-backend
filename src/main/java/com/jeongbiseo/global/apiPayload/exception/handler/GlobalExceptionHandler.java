package com.jeongbiseo.global.apiPayload.exception.handler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 전역 예외 처리기임. CustomException은 담긴 BaseErrorCode 그대로, `@Valid` 검증 실패와 본문 역직렬화 실패는
 * VALID400_1 더하기 필드별 안내 문구로 변환함(API명세서 "응답 envelope" 절, VALID400_1은 result에 필드 오류맵을 담음).
 * 위 핸들러가 못 잡는 나머지 예외는 handleUnexpected가 COMMON500 봉투로 감싸 최종 안전망 역할을 함(예상 못한 예외도 Spring 기본
 * 에러 바디가 아니라 반드시 CustomResponse 봉투로 나가야 함).
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
	 * 요청 본문을 DTO로 읽지 못할 때 VALID400_1로 변환함. 원인 예외는 Boot 4가 쓰는 Jackson 3(`tools.jackson`)
	 * 타입이어야 함 — 클래스패스에 Jackson 2(`com.fasterxml.jackson`)도 transitive로 남아 있어 그쪽을 검사하면
	 * 컴파일은 되지만 분기가 영영 타지 않음(2026-07-20 실측). enum 필드에 계약 밖 문자열이 오면(예 incomeBracket에
	 * "200~300만원") Jackson이 InvalidFormatException을 던지는데, 이는 바인딩 단계라 `@Valid`까지 도달하지 못해
	 * 최종 안전망이 500으로 감싸던 경로였음. 프론트가 매핑을 틀렸을 때 "잘못된 값"이 아니라 "서버 오류"로 보여 원인 추적이 막히므로 400
	 * 계약으로 되돌림. result 형태는 `@Valid` 실패(handleValidation)와 같은 필드명 대 메시지 맵으로 맞춰 프론트 파싱 경로를
	 * 하나로 유지함. Jackson 원본 메시지는 내부 클래스명을 흘리므로 응답에 담지 않고 허용값 목록만 실음.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<CustomResponse<Map<String, String>>> handleNotReadable(HttpMessageNotReadableException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		if (e.getCause() instanceof InvalidFormatException cause) {
			Class<?> targetType = cause.getTargetType();
			String message = (targetType != null && targetType.isEnum())
					? "허용값이 아니에요. 허용: " + allowedValuesOf(targetType) : "형식이 올바르지 않아요";
			fieldErrors.put(fieldPathOf(cause), message);
		}
		else {
			log.warn("요청 본문을 읽지 못함", e);
			fieldErrors.put("body", "요청 본문을 해석할 수 없어요");
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(CustomResponse.fail(ValidationErrorCode.INVALID_DTO_FIELD, fieldErrors));
	}

	/**
	 * 역직렬화에 실패한 필드 경로를 점 표기로 만듦(중첩이면 부모.자식). 경로를 못 얻으면 body로 대체함.
	 */
	// ponytail: 배열 인덱스는 경로에 넣지 않음. 현재 계약의 배열 요청은 subsidyIds(List<Long>) 하나뿐이라 필드명만으로 충분함.
	// 중첩 컬렉션 DTO가 생기면 Reference.getIndex()를 대괄호 표기로 더하는 것이 대안임.
	private static String fieldPathOf(InvalidFormatException cause) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : cause.getPath()) {
			if (reference.getPropertyName() == null) {
				continue;
			}
			if (path.length() > 0) {
				path.append('.');
			}
			path.append(reference.getPropertyName());
		}
		return path.length() == 0 ? "body" : path.toString();
	}

	/** enum 허용값 목록을 문자열로 만듦(계약 밖 값을 보낸 클라이언트가 정정할 수 있게 응답에 실음). */
	private static String allowedValuesOf(Class<?> enumType) {
		return Arrays.stream(enumType.getEnumConstants()).map(String::valueOf).collect(Collectors.joining(", "));
	}

	/**
	 * 위 핸들러가 담당하지 않는 예기치 못한 예외의 최종 안전망임. DB 제약 위반(DataIntegrityViolationException), 매핑되지
	 * 않은 경로(NoResourceFoundException) 등 어떤 예외든 COMMON500 봉투로 감싸 응답 envelope 계약을 예외 종류와
	 * 무관하게 보장함. 매핑되지 않은 경로도 500으로 뭉뚱그려지는 것은 알려진 단순화임(ponytail: 계약에 있는 경로만 다루므로 404 대 405
	 * 대 500을 세분화하는 핸들러를 두지 않음. 세분화가 필요해지면 이 핸들러 앞에 전용 핸들러를 추가하는 것이 대안임). 응답에는 스택트레이스를 담지
	 * 않되(정보 노출 방지) 서버 로그에는 남겨 원인 추적이 가능하게 함.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<CustomResponse<Void>> handleUnexpected(Exception e) {
		log.error("예기치 못한 예외를 COMMON500으로 감쌈", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(CustomResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR));
	}

}
