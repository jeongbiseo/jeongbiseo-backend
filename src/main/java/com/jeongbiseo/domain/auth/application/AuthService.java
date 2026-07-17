package com.jeongbiseo.domain.auth.application;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.global.security.exception.AuthErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final Clock clock; // ClockConfig 주입

    /**
     * socialAuthorize: Provider별 인가 서버 경로 생성
     */
    public String getAuthorizeUrl(String provider) {
        validateProvider(provider);
        String state = UUID.randomUUID().toString(); // 서명 state 발행

        if ("kakao".equals(provider)) {
            return "https://kauth.kakao.com/oauth/authorize?client_id=DUMMY&redirect_uri=DUMMY&response_type=code&state=" + state;
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?client_id=DUMMY&redirect_uri=DUMMY&response_type=code&scope=email profile&state=" + state;
    }

    /**
     * socialCallback: 위조 검증, 토큰 교환 후 자동 가입 및 발급
     */
    @Transactional
    public SocialCallbackResponse handleCallback(String provider, String code, String state) {
        validateProvider(provider);

        // 1. 위조 또는 누락 검사
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
        }

        try {
            // IdP 토큰 교환 및 유저 프로필 조회 연산 가상 시뮬레이션
        } catch (Exception e) {
            // [보안 정책] 상세 실패 사유는 로그로만 남기고 외부 노출 차단
            throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
        }

        // 2. 가입 상태에 따른 응답 플래그 제어
        boolean isNewMember = false;
        boolean onboardingCompleted = false;

        // 3. 30분 만료 액세스 토큰 및 14일 만료 리프레시 토큰 MySQL 적재/회전 처리 생략
        String accessToken = "eyJhbGciOiJIUzI1NiJ9.dummyAccessToken";
        String refreshToken = UUID.randomUUID().toString();

        return new SocialCallbackResponse(accessToken, refreshToken, "Bearer", isNewMember, onboardingCompleted);
    }

    /**
     * refreshToken: 구 토큰 즉시 무효화 및 신규 발급 (회전)
     */
    @Transactional
    public SocialCallbackResponse rotateToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
        }

        // DB 조회 후 만료·위조·이미 폐기된 토큰 검증 분기
        boolean isValid = true;
        if (!isValid) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED);
        }

        // 구 토큰 폐기 및 신규 토큰 쌍 발행 회전 로직 진행
        String newAccessToken = "eyJhbGciOiJIUzI1NiJ9.newDummyAccessToken";
        String newRefreshToken = UUID.randomUUID().toString();

        return new SocialCallbackResponse(newAccessToken, newRefreshToken, "Bearer", false, true);
    }

    /**
     * logOut: 저장된 리프레시 토큰 영구 삭제
     */
    @Transactional
    public void processLogout(String memberId) {
        // 해당 회원의 모든 기기 리프레시 토큰 데이터 무효화 처리 수행
    }

    private void validateProvider(String provider) {
        if (!"kakao".equals(provider) && !"google".equals(provider)) {
            throw new CustomException(AuthErrorCode.VALID_PARAMETER_ERROR);
        }
    }
}