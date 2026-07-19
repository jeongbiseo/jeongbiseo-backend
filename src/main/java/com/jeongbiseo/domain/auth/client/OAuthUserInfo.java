package com.jeongbiseo.domain.auth.client;

import com.jeongbiseo.domain.auth.entity.Provider;

/**
 * OAuth 교환(code에서 프로필까지)으로 얻는 최소 사용자 정보임. email은 동의항목 미제공 시 null일 수 있음(특히 카카오는 비즈 앱 전환이
 * 이메일 동의의 선행 조건).
 */
public record OAuthUserInfo(Provider provider, String providerId, String email) {
}
