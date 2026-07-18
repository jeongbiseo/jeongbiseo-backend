package com.jeongbiseo.domain.auth.application;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthService 계약 테스트임. 지금 통과하는 것은 회귀 가드이고, @Disabled 표시된 것은 팀원 구현 목표(구현 완료 시 어노테이션 제거하면
 * 초록). 실제 JWT 서명·OAuth 토큰 교환은 OAuth 키와 IdP 응답 목이 필요해 여기서 검증하지 않음(설계 D7, 팀원 몫).
 */
class AuthServiceTest {

	private final AuthService authService = new AuthService(Clock.system(ZoneId.of("Asia/Seoul")));

	@Test
	void 알수없는_provider는_VALID400_0을_던진다() {
		assertThatThrownBy(() -> authService.getAuthorizeUrl("naver")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");
	}

	@Test
	void socialCallback은_code나_state가_없으면_AUTH401_1을_던진다() {
		assertThatThrownBy(() -> authService.handleCallback("kakao", null, "someState"))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");

		assertThatThrownBy(() -> authService.handleCallback("kakao", "someCode", "  "))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void refreshToken은_빈_토큰이면_AUTH401_2를_던진다() {
		assertThatThrownBy(() -> authService.rotateToken("  ")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_2");
	}

	@Test
	void getAuthorizeUrl은_provider별_인가_URL과_state를_담는다() {
		assertThat(authService.getAuthorizeUrl("kakao")).contains("kauth.kakao.com").contains("state=");
		assertThat(authService.getAuthorizeUrl("google")).contains("accounts.google.com").contains("state=");
	}

	@Test
	void socialCallback은_유효한_요청에_Bearer_토큰_응답을_반환한다() {
		SocialCallbackResponse result = authService.handleCallback("kakao", "code", "state");

		assertThat(result.tokenType()).isEqualTo("Bearer");
		assertThat(result.accessToken()).isNotBlank();
		assertThat(result.refreshToken()).isNotBlank();
	}

	@Test
	void refreshToken은_유효한_토큰에_회전된_쌍을_반환한다() {
		SocialCallbackResponse result = authService.rotateToken("someRefresh");

		assertThat(result.tokenType()).isEqualTo("Bearer");
		assertThat(result.accessToken()).isNotBlank();
		assertThat(result.refreshToken()).isNotBlank();
	}

	@Test
	void processLogout은_예외없이_수행된다() {
		authService.processLogout(1L);
	}

	@Disabled("팀원 구현 목표: 인가 URL의 client_id·redirect_uri를 설정(OAuth 프로퍼티)에서 읽어야 함. DUMMY 플레이스홀더 제거 후 활성화.")
	@Test
	void getAuthorizeUrl은_DUMMY_플레이스홀더를_남기지_않는다() {
		assertThat(authService.getAuthorizeUrl("kakao")).doesNotContain("DUMMY");
		assertThat(authService.getAuthorizeUrl("google")).doesNotContain("DUMMY");
	}

	@Disabled("팀원 구현 목표: 리프레시 토큰은 SecureRandom 256비트 랜덤이어야 함(설계 D7). UUID(122비트) 대체 구현 후 활성화.")
	@Test
	void 발급된_리프레시_토큰은_UUID_형식이_아니다() {
		// UUID.toString()은 하이픈 4개(8-4-4-4-12) 형식임. 이 형식이면 UUID 그대로라는 신호.
		String refresh = authService.handleCallback("kakao", "code", "state").refreshToken();
		assertThat(refresh).doesNotMatch("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}

}
