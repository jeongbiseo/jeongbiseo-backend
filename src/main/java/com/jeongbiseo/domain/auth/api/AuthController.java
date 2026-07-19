package com.jeongbiseo.domain.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "소셜 로그인(코드 교환), 토큰 재발급, 로그아웃")
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
	@Operation(summary = "소셜 로그인(코드 교환)",
			description = "프론트가 소유한 인가 흐름에서 받은 code를 IdP 토큰과 교환해 로그인함. 첫 로그인은 자동 가입임. "
					+ "경로의 provider는 kakao 또는 google이며 대소문자 무관임. "
					+ "액세스 토큰은 응답 바디로, 리프레시 토큰은 HttpOnly 쿠키(Set-Cookie refreshToken)로 나감.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 성공. 리프레시 토큰을 HttpOnly 쿠키로 심음",
					useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "지원하지 않는 provider(VALID400_0) 또는 요청 바디 검증 실패(VALID400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"),
							@ExampleObject(name = "VALID400_1",
									value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"codeVerifier\":\"code_verifier는 필수예요\"}}") })),
			@ApiResponse(responseCode = "401",
					description = "소셜 로그인 실패(AUTH401_1). 만료·위조 code, IdP 토큰 교환 실패, 자동가입 경합 패배 후 재조회 실패를 사유 비노출로 통합함",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_1",
							value = "{\"isSuccess\":false,\"code\":\"AUTH401_1\",\"message\":\"소셜 로그인에 실패했어요, 다시 시도해주세요\",\"result\":null}"))) })
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
	@Operation(summary = "토큰 재발급",
			description = "요청 바디 없음. 리프레시 토큰은 refreshToken 쿠키로만 받음. 성공 시 새 리프레시 토큰으로 회전해 다시 쿠키로 심음. "
					+ "쿠키가 HttpOnly라 Swagger UI에서는 브라우저 제약으로 직접 시험 호출이 되지 않음.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "재발급 성공. 회전된 새 리프레시 토큰을 쿠키로 심음",
					useReturnTypeSchema = true),
			@ApiResponse(responseCode = "401",
					description = "재로그인 필요(AUTH401_2). 쿠키 미제공·공백, 만료, 미존재, 재사용, 동시 재발급 경합 패배를 통합함. "
							+ "재사용과 경합 패배는 회전 실패로 같은 401이 되며 이긴 토큰은 유효하게 유지됨(설계 D9 단순 거부)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AUTH401_2",
							value = "{\"isSuccess\":false,\"code\":\"AUTH401_2\",\"message\":\"다시 로그인해주세요\",\"result\":null}"))) })
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
	@Operation(summary = "로그아웃",
			description = "서버의 리프레시 토큰 행을 지우고 쿠키를 Max-Age 0으로 삭제함. 회원 식별은 FixedMemberResolver 고정 회원임(배포 N).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그아웃 성공. 리프레시 쿠키를 삭제함", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 enforcement Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))) })
	@PostMapping("/logout")
	public CustomResponse<String> logOut(HttpServletResponse response) {
		this.authService.processLogout(this.memberResolver.resolveMemberId());
		this.cookieUtils.deleteRefreshTokenCookie(response);
		return CustomResponse.ok("로그아웃 성공");
	}

}
