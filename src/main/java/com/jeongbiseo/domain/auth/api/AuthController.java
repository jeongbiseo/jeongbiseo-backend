package com.jeongbiseo.domain.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.jeongbiseo.domain.auth.application.AuthService;
import com.jeongbiseo.domain.auth.application.LoginResult;
import com.jeongbiseo.domain.auth.application.ReissueResult;
import com.jeongbiseo.domain.auth.dto.ReissueResponse;
import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.domain.auth.dto.SocialLoginRequest;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;
import com.jeongbiseo.global.security.exception.AuthErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.utils.CookieUtils;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final FixedMemberResolver memberResolver;

	private final CookieUtils cookieUtils;

	/**
	 * login: POST /api/v1/auth/{provider} 프론트 콜백 페이지가 code·code_verifier·redirectUri를
	 * 전달함. accessToken은 바디로, 리프레시 토큰은 HttpOnly 쿠키로 나감.
	 */
	@PostMapping("/{provider}")
	public CustomResponse<SocialCallbackResponse> login(@PathVariable("provider") String provider,
			@Valid @RequestBody SocialLoginRequest request, HttpServletResponse response) {
		LoginResult result = this.authService.handleCallback(provider, request.code(), request.codeVerifier(),
				request.redirectUri());
		this.cookieUtils.addRefreshTokenCookie(response, result.refreshToken());
		return CustomResponse
			.ok(new SocialCallbackResponse(result.accessToken(), result.isNewMember(), result.onboardingCompleted()));
	}

	/**
	 * reissue: POST /api/v1/auth/reissue 리프레시 토큰은 쿠키로 전달됨. 없으면 AUTH401_2. 새 리프레시 토큰을 쿠키로
	 * 회전함.
	 */
	@PostMapping("/reissue")
	public CustomResponse<ReissueResponse> reissue(
			@CookieValue(name = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
		}
		ReissueResult result = this.authService.reissue(refreshToken);
		this.cookieUtils.addRefreshTokenCookie(response, result.refreshToken());
		return CustomResponse.ok(new ReissueResponse(result.accessToken()));
	}

	/**
	 * logOut: POST /api/v1/auth/logout 인증 필수(Bearer). 회원 식별은 전 도메인 단일 패턴인
	 * FixedMemberResolver로 주입함(설계 D5). 리프레시 쿠키를 삭제함.
	 */
	@PostMapping("/logout")
	public CustomResponse<String> logOut(HttpServletResponse response) {
		this.authService.processLogout(this.memberResolver.resolveMemberId());
		this.cookieUtils.deleteRefreshTokenCookie(response);
		return CustomResponse.ok("로그아웃 성공");
	}

}
