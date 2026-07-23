package com.jeongbiseo.domain.auth.api;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.auth.application.AuthService;
import com.jeongbiseo.domain.auth.application.LoginResult;
import com.jeongbiseo.domain.auth.application.ReissueResult;
import com.jeongbiseo.global.security.FixedMemberResolver;
import com.jeongbiseo.global.utils.CookieUtils;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 서비스는 목이라 토큰 실발급은 검증하지 않고, 경로·메소드·응답
 * 봉투·상태 계약(200 envelope 더하기 refreshToken 쿠키 I/O)만 고정함. 방식 B 전환으로 인가 리다이렉트는 사라지고 로그인은 POST
 * 바디 교환이 됨. logout은 회원 주입을 FixedMemberResolver로 받고, 쿠키 세팅·삭제는 실빈 CookieUtils로 하므로 둘 다
 * import함.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ FixedMemberResolver.class, CookieUtils.class })
@org.junit.jupiter.api.extension.ExtendWith(com.jeongbiseo.support.FixedMemberContextExtension.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void login_발급된_토큰을_200_envelope과_리프레시_쿠키로_반환한다() throws Exception {
		given(authService.handleCallback(eq("kakao"), any(), any(), any()))
			.willReturn(new LoginResult("access", "raw-refresh", true, false));

		mockMvc
			.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"c\",\"codeVerifier\":\"v\",\"redirectUri\":\"https://front/callback\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.result.accessToken").value("access"))
			.andExpect(jsonPath("$.result.isNewMember").value(true))
			.andExpect(jsonPath("$.result.onboardingCompleted").value(false))
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=raw-refresh")))
			.andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
	}

	@Test
	void login_필수_바디_필드가_비면_400을_반환한다() throws Exception {
		mockMvc
			.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"\",\"codeVerifier\":\"v\",\"redirectUri\":\"r\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void reissue_쿠키의_리프레시로_회전하고_새_액세스와_쿠키를_반환한다() throws Exception {
		given(authService.reissue("old-raw")).willReturn(new ReissueResult("newAccess", "new-raw"));

		mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refreshToken", "old-raw")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.accessToken").value("newAccess"))
			.andExpect(header().string("Set-Cookie", containsString("refreshToken=new-raw")));

		verify(authService).reissue("old-raw");
	}

	@Test
	void reissue_리프레시_쿠키가_없으면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_2"));
	}

	@Test
	void logOut_고정회원의_리프레시를_지우고_쿠키를_삭제하며_200을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("로그아웃 성공"))
			.andExpect(cookie().maxAge("refreshToken", 0));

		verify(authService).processLogout(1L);
	}

}
