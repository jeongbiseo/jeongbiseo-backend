package com.jeongbiseo.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생성된 OpenAPI 문서의 보안 계약을 고정함. 어노테이션 설정이라 컴파일로는 깨짐이 드러나지 않고 운영 Swagger를 눈으로 봐야 발견되므로 회귀
 * 테스트로 잡음.
 *
 * 고정하는 것은 두 가지임. 글로벌 Bearer 요구가 살아 있어야 Authorize에 넣은 토큰이 실제 요청에 실리고, 토큰을 발급받기 전에 부르는
 * 엔드포인트는 빈 security로 요구가 해제돼 있어야 문서를 읽는 쪽이 계약을 오독하지 않음.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OpenApiSecurityContractTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 글로벌_bearer_요구와_스킴이_문서에_있다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.security[0].bearerAuth").isArray());
	}

	@Test
	void 토큰_발급_전에_부르는_엔드포인트는_요구가_해제돼_있다() throws Exception {
		// 빈 배열이어야 함. 배열 자체가 없으면 글로벌 요구를 상속해 자물쇠가 붙음
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.['/api/v1/auth/{provider}'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths.['/api/v1/auth/reissue'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths.['/api/v1/regions'].get.security").isEmpty());
	}

	@Test
	void 인증이_필요한_엔드포인트는_글로벌_요구를_상속한다() throws Exception {
		// 개별 security 키가 없어야 글로벌 요구가 적용됨
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths.['/api/v1/auth/logout'].post.security").doesNotExist())
			.andExpect(jsonPath("$.paths.['/api/v1/calendar'].get.security").doesNotExist());
	}

}
