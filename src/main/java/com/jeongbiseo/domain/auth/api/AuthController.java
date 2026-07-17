package com.jeongbiseo.domain.auth.api;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.jeongbiseo.domain.auth.application.AuthService;
import com.jeongbiseo.domain.auth.dto.RefreshRequest;
import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.global.apiPayload.CustomResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * socialAuthorize: GET /api/v1/auth/{provider}
     * 명세서 상 302 인가 리다이렉트를 처리하는 유일한 봉투 비대상 예외 메서드
     */
    @GetMapping("/{provider}")
    public void socialAuthorize(
            @PathVariable("provider") String provider,
            HttpServletResponse response
    ) throws IOException {
        String authorizeUrl = authService.getAuthorizeUrl(provider);
        response.sendRedirect(authorizeUrl);
    }

    /**
     * socialCallback: GET /api/v1/auth/{provider}/callback
     */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<CustomResponse<SocialCallbackResponse>> socialCallback(
            @PathVariable("provider") String provider,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state
    ) {
        SocialCallbackResponse result = authService.handleCallback(provider, code, state);
        return ResponseEntity.ok(CustomResponse.ok(result));
    }

    /**
     * logOut: POST /api/v1/auth/logout
     * Bearer 토큰 주입 검증이 필요한 인증 봉투 대상 API
     */
    @PostMapping("/logout")
    public ResponseEntity<CustomResponse<String>> logOut(
            @RequestAttribute("memberId") String memberId // FixedMemberResolver 또는 JWT 인터셉터 공급 가정
    ) {
        authService.processLogout(memberId);
        return ResponseEntity.ok(CustomResponse.ok("로그아웃 성공"));
    }

    /**
     * refreshToken: POST /api/v1/auth/refresh
     * 자격 토큰 자체가 바디로 전달되므로 헤더 무인증(불필요) 처리 대상 API
     */
    @PostMapping("/refresh")
    public ResponseEntity<CustomResponse<SocialCallbackResponse>> refreshToken(
            @Valid @RequestBody RefreshRequest request
    ) {
        SocialCallbackResponse result = authService.rotateToken(request.refreshToken());
        return ResponseEntity.ok(CustomResponse.ok(result));
    }
}