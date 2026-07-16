package com.jeongbiseo.domain.region.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RegionController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). RegionCatalog 고정 참조 데이터만 다뤄 DB에
 * 의존하지 않음. 인증 불필요 엔드포인트라 시큐리티 필터는 끔(addFilters = false).
 */
@WebMvcTest(RegionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getRegions_sido_미지정_시_시도목록만_채운다() throws Exception {
		mockMvc.perform(get("/api/v1/regions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.sidoList").value(hasItem("서울특별시")))
			.andExpect(jsonPath("$.result.sido").value(nullValue()))
			.andExpect(jsonPath("$.result.sigunguList").value(nullValue()));
	}

	@Test
	void getRegions_sido_지정_시_시군구목록만_채운다() throws Exception {
		mockMvc.perform(get("/api/v1/regions").param("sido", "서울특별시"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.sido").value("서울특별시"))
			.andExpect(jsonPath("$.result.sidoList").value(nullValue()))
			.andExpect(jsonPath("$.result.sigunguList", hasSize(2)))
			.andExpect(jsonPath("$.result.sigunguList[*].name", hasItems("강남구", "관악구")));
	}

	@Test
	void getRegions_등록되지_않은_sido면_빈_시군구목록을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/regions").param("sido", "제주특별자치도"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.sido").value("제주특별자치도"))
			.andExpect(jsonPath("$.result.sigunguList", hasSize(0)));
	}

}
