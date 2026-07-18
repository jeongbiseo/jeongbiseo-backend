package com.jeongbiseo.domain.auth.client;

import com.jeongbiseo.domain.auth.entity.Provider;

/**
 * provider별 code 대 프로필 교환을 다루는 인터페이스임(설계 4장). 인가 URL·PKCE·state는 프론트가 소유하므로 클라이언트는 code
 * 교환만 담당함. AuthService는 provider()로 알맞은 구현을 골라 씀.
 */
public interface OAuthClient {

	Provider provider();

	/**
	 * 인가 code를 IdP 토큰과 교환하고 프로필을 조회함. PKCE code_verifier와 프론트가 준 redirect_uri를 함께 보냄. 실패
	 * 사유는 구분하지 않고 AUTH401_1로 통합함(설계 2장).
	 */
	OAuthUserInfo exchange(String code, String codeVerifier, String redirectUri);

}
