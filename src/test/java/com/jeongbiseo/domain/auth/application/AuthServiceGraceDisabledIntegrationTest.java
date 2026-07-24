package com.jeongbiseo.domain.auth.application;

import com.jeongbiseo.support.MySqlContainerSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jeongbiseo.domain.auth.client.GoogleOAuthClient;
import com.jeongbiseo.domain.auth.client.KakaoOAuthClient;
import com.jeongbiseo.domain.auth.client.OAuthUserInfo;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 유예창을 벗어난 재사용이 실제 MySQL에서 401이 되는지 고정하는 통합 테스트임(Docker 필요). 시계를 돌리는 대신 유예창을 0초로 두어 회전
 * 직후의 재사용도 창 밖이 되게 함 — prev_rotated_at 비교 술어가 MySQL에서 의도대로 동작하는지 검증하는 것이 목적임.
 */
@SpringBootTest
@TestPropertySource(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "app.auth.refresh.grace-seconds=0" })
class AuthServiceGraceDisabledIntegrationTest extends MySqlContainerSupport {

	@Autowired
	private AuthService authService;

	@MockitoBean
	private KakaoOAuthClient kakaoOAuthClient;

	@MockitoBean
	private GoogleOAuthClient googleOAuthClient;

	@BeforeEach
	void stubProviders() {
		given(this.kakaoOAuthClient.provider()).willReturn(Provider.KAKAO);
		given(this.googleOAuthClient.provider()).willReturn(Provider.GOOGLE);
	}

	@Test
	void 유예창_밖의_구_리프레시_재사용은_AUTH401_2다() {
		given(this.kakaoOAuthClient.exchange(any(), any(), any()))
			.willReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-uid-grace-off", "graceoff@example.com", "테스터"));
		LoginResult issued = this.authService.handleCallback("kakao", "code-1", "verifier-1", "https://front/callback");
		this.authService.reissue(issued.refreshToken());

		assertThatThrownBy(() -> this.authService.reissue(issued.refreshToken())).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

}
