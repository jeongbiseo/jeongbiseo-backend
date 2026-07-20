package com.jeongbiseo.global.apiPayload.exception.handler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.region.controller.RegionController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매핑되지 않은 경로가 500이 아니라 404 COMMON404 봉투로 나가는지 확인하는 웹 슬라이스 테스트임. 슬라이스 컨텍스트는 목 빈 없이 뜨는
 * RegionController를 빌려 구성하고, 실제 검증 대상은 GlobalExceptionHandler.handleNoResource임.
 */
@WebMvcTest(RegionController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoResourceFoundHandlingTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 매핑되지_않은_경로는_COMMON404_봉투로_응답한다() throws Exception {
		mockMvc.perform(get("/api/v1/nonexistent"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON404"))
			.andExpect(jsonPath("$.message").value("요청하신 경로를 찾을 수 없습니다"))
			.andExpect(jsonPath("$.result").value(nullValue()));
	}

	@Test
	void 루트_경로도_COMMON404_봉투로_응답한다() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON404"));
	}

}
