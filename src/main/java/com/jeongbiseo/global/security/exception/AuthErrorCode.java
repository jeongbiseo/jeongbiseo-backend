package com.jeongbiseo.global.security.exception;

import org.springframework.http.HttpStatus;
import com.jeongbiseo.global.common.BaseErrorCode; // 프로젝트 공용 인터페이스

/**
 * 도메인 통합 인증 에러코드 참조표 반영 enum
 */
public enum AuthErrorCode implements BaseErrorCode {

    VALID_PARAMETER_ERROR("VALID400_0", HttpStatus.BAD_REQUEST, "잘못된 파라미터 입니다."),
    SOCIAL_LOGIN_FAILED("AUTH401_1", HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했어요, 다시 시도해주세요."),
    REFRESH_TOKEN_FAILED("AUTH401_2", HttpStatus.UNAUTHORIZED, "다시 로그인해주세요."),
    AUTHENTICATION_REQUIRED("COMMON401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    AuthErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}