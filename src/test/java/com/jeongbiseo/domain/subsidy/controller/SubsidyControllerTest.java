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
import com.jeongbiseo.domain.favorite.service.FavoriteService;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;
import com.jeongbiseo.global.apiPayload.code.FavoriteErrorCode;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	@MockitoBean
	private FavoriteService favoriteService;

	@MockitoBean
	private FixedMemberResolver memberResolver;

	@Test
	void searchSubsidies_기본파라미터로_200과_페이지응답을_반환한다() throws Exception {
		SubsidySearchResult result = new SubsidySearchResult(1L, "청년월세지원", "국토교통부", SubsidyCategory.YOUTH,
				LocalDate.of(2026, 8, 1), 200000L, 500000L);
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/subsidies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.content[0].subsidyId").value(1))
			.andExpect(jsonPath("$.result.content[0].name").value("청년월세지원"))
			.andExpect(jsonPath("$.result.content[0].estimatedAmountMin").value(200000))
			.andExpect(jsonPath("$.result.content[0].estimatedAmountMax").value(500000))
			.andExpect(jsonPath("$.result.page").value(0))
			.andExpect(jsonPath("$.result.size").value(20))
			.andExpect(jsonPath("$.result.totalElements").value(1));
	}

	@Test
	void getFavorites_200과_관심목록_totalCount를_반환한다() throws Exception {
		// /favorites가 /{subsidyId}(getSubsidyDetail)로 새지 않고 getFavorites로 매핑되는지도 함께 고정함.
		given(memberResolver.resolveMemberId()).willReturn(1L);
		given(favoriteService.getFavorites(1L)).willReturn(List.of(new SubsidySearchResult(10L, "청년 월세 특별지원", "국토교통부",
				SubsidyCategory.YOUTH, LocalDate.of(2026, 8, 1), 200000L, 200000L)));

		mockMvc.perform(get("/api/v1/subsidies/favorites"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.content[0].subsidyId").value(10))
			.andExpect(jsonPath("$.result.totalCount").value(1));
	}

	@Test
	void searchSubsidies_keyword와_category가_서비스로_전달된다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mockMvc.perform(get("/api/v1/subsidies").param("keyword", "청년").param("category", "YOUTH"))
			.andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService)
			.search(eq("청년"), eq(SubsidyCategory.YOUTH), any(), anyBoolean(), any());
	}

	@Test
	void searchSubsidies_sort_미지정이면_null과_id정렬_Pageable을_넘긴다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(any(), any(), eq(null), anyBoolean(), captor.capture());
		// 미지정 경로는 id 오름차순을 Pageable에 실어 하위호환 응답을 유지함
		assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
	}

	@Test
	void searchSubsidies_sort_DEADLINE이면_enum과_비정렬_Pageable을_넘긴다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies").param("sort", "DEADLINE")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService)
			.search(any(), any(), eq(com.jeongbiseo.domain.subsidy.dto.SubsidySort.DEADLINE), anyBoolean(),
					captor.capture());
		// 정렬은 전용 쿼리 order by에 있으므로 Pageable엔 정렬이 없어야 함(이중 부여 방지)
		assertThat(captor.getValue().getSort().isSorted()).isFalse();
	}

	@Test
	void searchSubsidies_includeClosed_쿼리파라미터가_서비스로_전달되고_미지정이면_false가_기본이다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ArgumentCaptor<Boolean> includeClosedCaptor = ArgumentCaptor.forClass(Boolean.class);

		mockMvc.perform(get("/api/v1/subsidies").param("includeClosed", "true")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/subsidies")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService, org.mockito.Mockito.times(2))
			.search(any(), any(), any(), includeClosedCaptor.capture(), any());
		// 첫 호출(includeClosed=true 지정)과 두 번째 호출(미지정 -- 기본 false) 순서대로 값이 실림
		assertThat(includeClosedCaptor.getAllValues()).containsExactly(true, false);
	}

	@Test
	void searchSubsidies_sort가_잘못된값이면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/subsidies").param("sort", "WRONG"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void searchSubsidies_size는_상한클램프되고_id_오름차순_정렬을_고정한다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies").param("size", "500")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(any(), any(), any(), anyBoolean(), captor.capture());
		assertThat(captor.getValue().getPageSize()).isEqualTo(100);
		Sort.Order idOrder = captor.getValue().getSort().getOrderFor("id");
		assertThat(idOrder).isNotNull();
		assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void searchSubsidies_size가_0이하면_기본20으로_보정한다() throws Exception {
		given(subsidyService.search(any(), any(), any(), anyBoolean(), any()))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		mockMvc.perform(get("/api/v1/subsidies").param("size", "0")).andExpect(status().isOk());

		org.mockito.Mockito.verify(subsidyService).search(any(), any(), any(), anyBoolean(), captor.capture());
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
				"https://example.com", false, null);
		given(memberResolver.resolveMemberId()).willReturn(1L);
		given(subsidyService.getDetail(1L, 1L)).willReturn(response);

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
		given(subsidyService.getDetail(anyLong(), any()))
			.willThrow(new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND));

		mockMvc.perform(get("/api/v1/subsidies/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SUBSIDY404_1"));
	}

	@Test
	void addFavorite_정상이면_subsidyId와_favorited_true를_반환한다() throws Exception {
		given(memberResolver.resolveMemberId()).willReturn(1L);

		mockMvc.perform(post("/api/v1/subsidies/10/favorite"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.subsidyId").value(10))
			.andExpect(jsonPath("$.result.favorited").value(true));

		verify(favoriteService).add(1L, 10L);
	}

	@Test
	void removeFavorite_정상이면_subsidyId와_favorited_false를_반환한다() throws Exception {
		given(memberResolver.resolveMemberId()).willReturn(1L);

		mockMvc.perform(delete("/api/v1/subsidies/10/favorite"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.subsidyId").value(10))
			.andExpect(jsonPath("$.result.favorited").value(false));

		verify(favoriteService).remove(1L, 10L);
	}

	@Test
	void addFavorite_중복이면_409_FAVORITE409_1() throws Exception {
		given(memberResolver.resolveMemberId()).willReturn(1L);
		org.mockito.Mockito.doThrow(new CustomException(FavoriteErrorCode.FAVORITE_ALREADY_EXISTS))
			.when(favoriteService)
			.add(1L, 10L);

		mockMvc.perform(post("/api/v1/subsidies/10/favorite"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FAVORITE409_1"));
	}

	@Test
	void removeFavorite_미등록이면_404_FAVORITE404_1() throws Exception {
		given(memberResolver.resolveMemberId()).willReturn(1L);
		org.mockito.Mockito.doThrow(new CustomException(FavoriteErrorCode.FAVORITE_NOT_FOUND))
			.when(favoriteService)
			.remove(1L, 10L);

		mockMvc.perform(delete("/api/v1/subsidies/10/favorite"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FAVORITE404_1"));
	}

}
