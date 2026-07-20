package com.jeongbiseo.domain.subsidy.controller;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SubsidyController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). SubsidyService는 목이고, 검색 파라미터
 * 전달·기본값, VALID400_0(음수 page·잘못된 category·비정수 page/subsidyId), 상세 매핑(isFavorite 필드명 고정,
 * M3), SUBSIDY404_1(서비스 mock throw)을 고정함.
 */
@WebMvcTest(SubsidyController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubsidyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SubsidyService subsidyService;

	@Test
	void searchSubsidies_기본파라미터로_200과_페이지응답을_반환한다() throws Exception {
		SubsidySearchResult result = new SubsidySearchResult(1L, "청년월세지원", "국토교통부", SubsidyCategory.YOUTH,
				LocalDate.of(2026, 8, 1));
		given(subsidyService.search(any(), any(), any()))
			.willReturn(new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/subsidies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.content[0].subsidyId").value(1))
			.andExpect(jsonPath("$.result.content[0].name").value("청년월세지원"))
			.andExpect(jsonPath("$.result.page").value(0))
			.andExpect(jsonPath("$.result.size").value(20))
			.andExpect(jsonPath("$.result.totalElements").value(1));
	}

	@Test
	void searchSubsidies_keyword와_category가_서비스로_전달된다() throws Exception {
		given(subsidyService.search(any(), any(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mockMvc.perform(get("/api/v1/subsidies").param("keyword", "청년").param("category", "YOUTH"))
			.andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(eq("청년"), eq(SubsidyCategory.YOUTH), any());
	}

	@Test
	void searchSubsidies_size는_상한클램프되고_id_오름차순_정렬을_고정한다() throws Exception {
		given(subsidyService.search(any(), any(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies").param("size", "500")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(any(), any(), captor.capture());
		assertThat(captor.getValue().getPageSize()).isEqualTo(100);
		Sort.Order idOrder = captor.getValue().getSort().getOrderFor("id");
		assertThat(idOrder).isNotNull();
		assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void searchSubsidies_size가_0이하면_기본20으로_보정한다() throws Exception {
		given(subsidyService.search(any(), any(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies").param("size", "0")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(any(), any(), captor.capture());
		assertThat(captor.getValue().getPageSize()).isEqualTo(20);
	}

	@Test
	void searchSubsidies_page가_음수면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies").param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void searchSubsidies_page가_정수아니면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void searchSubsidies_category가_잘못된값이면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies").param("category", "WRONG"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void getSubsidyDetail_정상이면_200과_상세매핑을_반환한다() throws Exception {
		SubsidyDetailResponse response = new SubsidyDetailResponse(1L, "청년월세지원", "국토교통부", "만 19~34세 무주택 청년",
				LocalDate.of(2026, 8, 1), 15, 100_000L, 200_000L, PaymentType.CASH, SubsidyCategory.YOUTH, "설명",
				"https://example.com", false);
		given(subsidyService.getDetail(1L)).willReturn(response);

		mockMvc.perform(get("/api/v1/subsidies/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.subsidyId").value(1))
			.andExpect(jsonPath("$.result.dDay").value(15))
			.andExpect(jsonPath("$.result.isFavorite").value(false));
	}

	@Test
	void getSubsidyDetail_subsidyId가_정수아니면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void getSubsidyDetail_없는id면_404_SUBSIDY404_1() throws Exception {
		given(subsidyService.getDetail(anyLong())).willThrow(new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND));

		mockMvc.perform(get("/api/v1/subsidies/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SUBSIDY404_1"));
	}

}
