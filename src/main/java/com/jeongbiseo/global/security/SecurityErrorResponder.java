package com.jeongbiseo.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.jeongbiseo.global.apiPayload.code.BaseErrorCode;
import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;

/**
 * 시큐리티 필터 계층의 인증·인가 실패를 CustomResponse 봉투로 반환함(AUTH-W001). 미인증은 COMMON401, 인가 거부는
 * COMMON403임. 이 실패는 DispatcherServlet 앞에서
 * 나므로 @RestControllerAdvice(GlobalExceptionHandler)가 잡지 못해, 여기서 직접 상태·본문을 기록함.
 * EntryPoint와 AccessDeniedHandler를 한 클래스로 겸함.
 */
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, CommonErrorCode.UNAUTHENTICATED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, CommonErrorCode.ACCESS_DENIED);
	}

	// ponytail: 봉투가 고정 4필드이고 필터 계층엔 주입된 ObjectMapper가 없어(Boot 4의 Jackson 2/3 이중 클래스패스
	// 모호성도 회피) 문자열로 직접 조립함. code·message는 enum 상수라 따옴표·역슬래시가 없어 이스케이프가 불필요함.
	private static void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
			.write("{\"isSuccess\":false,\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
					+ errorCode.getMessage() + "\",\"result\":null}");
	}

}
