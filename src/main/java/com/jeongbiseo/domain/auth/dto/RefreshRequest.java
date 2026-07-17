package com.jeongbiseo.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 리프레시 토큰 바디 검증용 DTO
 */
public record RefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수예요") String refreshToken
) {
}