package com.jeongbiseo.domain.auth.api;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.jeongbiseo.domain.auth.application.AuthService;
import com.jeongbiseo.domain.auth.dto.RefreshRequest;
import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final FixedMemberResolver memberResolver;

	/**
	 * socialAuthorize: GET /api/v1/auth/{provider} 명세서 상 302 인가 리다이렉트를 처리하는 유일한 봉투 비대상 예외
	 * 메서드
	 */
	@GetMapping("/{provider}")
	public void socialAuthorize(@PathVariable("provider") String provider, HttpServletResponse response)
			throws IOException {
		String authorizeUrl = authService.getAuthorizeUrl(provider);
		response.sendRedirect(authorizeUrl);
	}

	/**
	 * socialCallback: GET /api/v1/auth/{provider}/callback
	 */
	@GetMapping("/{provider}/callback")
	public ResponseEntity<CustomResponse<SocialCallbackResponse>> socialCallback(
			@PathVariable("provider") String provider, @RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state", required = false) String state) {
		SocialCallbackResponse result = authService.handleCallback(provider, code, state);
		return ResponseEntity.ok(CustomResponse.ok(result));
	}

	/**
	 * logOut: POST /api/v1/auth/logout 인증 필수(Bearer). 회원 식별은 전 도메인 단일 패턴인
	 * FixedMemberResolver로 주입함(설계 D5). 배포 N에서는 고정 회원 1을, 파괴 스위치 배포에서 resolver 내부만
	 * SecurityContext 조회로 갈아끼우면 이 메서드는 무변경으로 실제 로그인 회원을 받음.
	 */
	@PostMapping("/logout")
	public ResponseEntity<CustomResponse<String>> logOut() {
		authService.processLogout(memberResolver.resolveMemberId());
		return ResponseEntity.ok(CustomResponse.ok("로그아웃 성공"));
	}

	/**
	 * refreshToken: POST /api/v1/auth/refresh 자격 토큰 자체가 바디로 전달되므로 헤더 무인증(불필요) 처리 대상 API
	 */
	@PostMapping("/refresh")
	public ResponseEntity<CustomResponse<SocialCallbackResponse>> refreshToken(
			@Valid @RequestBody RefreshRequest request) {
		SocialCallbackResponse result = authService.rotateToken(request.refreshToken());
		return ResponseEntity.ok(CustomResponse.ok(result));
	}

}