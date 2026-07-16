package com.jeongbiseo.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 회원 도메인 에러코드임(API명세서 "공통 에러코드" 절). 회원 미존재(MEMBER404_1)와 탈퇴 계정 접근(MEMBER400_1) 2건임.
 * MEMBER400_1은 소셜 콜백의 탈퇴 회원 재로그인(AUTH-172)이 정본 발원지이나, 활성 회원 조회 공통 헬퍼(MemberReader)가 탈퇴
 * 회원을 걸러낼 때도 씀.
 */
public enum MemberErrorCode implements BaseErrorCode {

	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "회원이 존재하지 않습니다"),
	MEMBER_DELETED(HttpStatus.BAD_REQUEST, "MEMBER400_1", "탈퇴된 계정이에요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	MemberErrorCode(HttpStatus httpStatus, String code, String message) {
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
