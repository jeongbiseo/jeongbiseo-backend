package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 도메인 에러코드가 구현하는 계약임. CustomException과 GlobalExceptionHandler가 이 인터페이스로 에러코드를 다룸(API명세서
 * "공통 에러코드" 절).
 */
public interface BaseErrorCode {

	HttpStatus getHttpStatus();

	String getCode();

	String getMessage();

}
