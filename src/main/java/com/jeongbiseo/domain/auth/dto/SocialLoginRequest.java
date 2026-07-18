package com.jeongbiseo.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 콜백 교환 바디 검증용 DTO임. 프론트 콜백 페이지가 IdP 쿼리의 code와 PKCE code_verifier, 그리고 인가 때 쓴
 * redirectUri를 전달함.
 */
public record SocialLoginRequest(@NotBlank(message = "인가 코드는 필수예요") String code,
		@NotBlank(message = "code_verifier는 필수예요") String codeVerifier,
		@NotBlank(message = "redirectUri는 필수예요") String redirectUri) {
}
