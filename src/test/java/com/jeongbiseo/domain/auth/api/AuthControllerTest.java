package com.jeongbiseo.domain.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.auth.application.AuthService;
import com.jeongbiseo.domain.auth.dto.SocialCallbackResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 서비스는 목이라 토큰 실발급은 검증하지 않고, 경로·메소드·응답
 * 봉투·상태 계약(302 리다이렉트와 200 envelope)만 고정함. logout은 회원 주입을 FixedMemberResolver로 받으므로 실빈을
 * import함.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void socialAuthorize_인가URL로_302_리다이렉트한다() throws Exception {
		given(authService.getAuthorizeUrl("kakao")).willReturn("https://kauth.kakao.com/oauth/authorize?state=s");

		mockMvc.perform(get("/api/v1/auth/kakao"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("https://kauth.kakao.com/oauth/authorize?state=s"));
	}

	@Test
	void socialCallback_발급된_토큰을_200_envelope으로_반환한다() throws Exception {
		given(authService.handleCallback(eq("kakao"), any(), any()))
			.willReturn(new SocialCallbackResponse("access", "refresh", "Bearer", true, false));

		mockMvc.perform(get("/api/v1/auth/kakao/callback").param("code", "c").param("state", "s"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.result.accessToken").value("access"))
			.andExpect(jsonPath("$.result.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.result.isNewMember").value(true));
	}

	@Test
	void logOut_고정회원의_리프레시를_지우고_200을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("로그아웃 성공"));

		verify(authService).processLogout(1L);
	}

	@Test
	void refreshToken_회전된_토큰을_200으로_반환한다() throws Exception {
		given(authService.rotateToken("old"))
			.willReturn(new SocialCallbackResponse("newAccess", "newRefresh", "Bearer", false, true));

		mockMvc
			.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"old\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.accessToken").value("newAccess"));
	}

	@Test
	void refreshToken_리프레시_토큰이_비면_400을_반환한다() throws Exception {
		mockMvc
			.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"\"}"))
			.andExpect(status().isBadRequest());
	}

}
