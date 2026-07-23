package com.jeongbiseo.global.security;

import com.jeongbiseo.support.MySqlContainerSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.global.security.jwt.JwtProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 강제화 후 경로·메소드별 인증 요구 매트릭스를 실제 시큐리티 필터로 관통 검증함(AUTH-W001, api-versioning 3절). 슬라이스
 * 테스트는 addFilters=false라 이 매트릭스를 잡지 못하므로, 필터를 켠 @SpringBootTest로 공개·인증필요·선택인증 세 갈래를 고정함.
 * Testcontainers MySQL이 필요함(Docker).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuthEnforcementIntegrationTest extends MySqlContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	void 공개_엔드포인트는_무토큰이어도_200이다() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies/categories")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/regions")).andExpect(status().isOk());
	}

	@Test
	void 인증필요_엔드포인트는_무토큰이면_401_COMMON401이다() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON401"));
		mockMvc.perform(get("/api/v1/calendar"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401"));
		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401"));
	}

	@Test
	void 인증필요_엔드포인트는_유효토큰이면_통과한다() throws Exception {
		String token = jwtProvider.issueAccessToken(1L);

		// 검색은 회원 데이터에 의존하지 않으므로 유효 토큰만으로 200(빈 결과)이 나옴 — 필터가 인증을 통과시켰다는 증거임
		mockMvc.perform(get("/api/v1/subsidies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"));
	}

	@Test
	void 선택인증_상세는_무토큰이어도_401이_아니다() throws Exception {
		// 존재하지 않는 id라 404(SUBSIDY404_1)가 나옴 — 401이 아니라는 것이 핵심(익명 접근 허용, isFavorite=false)
		mockMvc.perform(get("/api/v1/subsidies/999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SUBSIDY404_1"));
	}

}
