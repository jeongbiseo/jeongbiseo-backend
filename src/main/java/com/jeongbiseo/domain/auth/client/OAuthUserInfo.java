package com.jeongbiseo.domain.auth.client;

import com.jeongbiseo.domain.auth.entity.Provider;

/**
 * OAuth 교환(code에서 프로필까지)으로 얻는 최소 사용자 정보임. email은 동의항목 미제공 시 null일 수 있음(특히 카카오는 비즈 앱 전환이
 * 이메일 동의의 선행 조건). name은 화면 표시용 이름이며 실명이 아님 — 구글은 계정 표시명(name), 카카오는 프로필 닉네임을 실어 옴. 둘 다
 * 미제공이면 null임.
 */
public record OAuthUserInfo(Provider provider, String providerId, String email, String name) {
}
