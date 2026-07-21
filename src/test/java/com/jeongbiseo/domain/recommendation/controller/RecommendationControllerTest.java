package com.jeongbiseo.domain.recommendation.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.recommendation.EligibilityReason;
import com.jeongbiseo.domain.recommendation.MatchResult;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService.RecommendationView;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RecommendationController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 프로필 조회·추천 계산 오케스트레이션은
 * RecommendationQueryService를 목으로 대체해 컨트롤러의 limit HTTP 검증과 뷰(RecommendationView)에서
 * 응답(RecommendationResponse)으로의 변환만 검증함. 온보딩 미완료(ONB404_1) 등 서비스가 만드는 예외 경로와 실제 DB를 태우는
 * 회귀(기본값 3, 20 클램프, 기수령 제외, 마감 필터, 스코프 필터)는 RecommendationScopeIntegrationTest가 담당함.
 */
@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
class RecommendationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RecommendationQueryService recommendationQueryService;

	@Test
	void getRecommendations_limit0이면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/recommendations").param("limit", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void getRecommendations_limit음수이면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/recommendations").param("limit", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void getRecommendations_limit정수아니면_400_VALID400_0() throws Exception {
		mockMvc.perform(get("/api/v1/recommendations").param("limit", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_0"));
	}

	@Test
	void getRecommendations_정상이면_200과_항목_매핑을_반환한다() throws Exception {
		LocalDate asOf = LocalDate.of(2026, 7, 16);
		SubsidySummary summary = new SubsidySummary(1L, "청년월세지원", "국토교통부", asOf.plusDays(10), "만 19~34세 청년", 100_000L,
				200_000L, PaymentType.CASH);
		MatchResult matchResult = new MatchResult(1L, false, true, 5, List.of(EligibilityReason.INCOME_MISSING),
				asOf.plusDays(10), "gov24", "EXT-1");
		RecommendationItem item = new RecommendationItem(summary, matchResult);
		RecommendationView view = new RecommendationView(List.of(item), asOf, LocalDateTime.of(2026, 7, 15, 12, 0));
		given(recommendationQueryService.getRecommendations(anyLong(), any(), anyBoolean())).willReturn(view);

		mockMvc.perform(get("/api/v1/recommendations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.items[0].subsidyId").value(1))
			.andExpect(jsonPath("$.result.items[0].name").value("청년월세지원"))
			.andExpect(jsonPath("$.result.items[0].dDay").value(10))
			.andExpect(jsonPath("$.result.items[0].paymentType").value("CASH"))
			.andExpect(jsonPath("$.result.items[0].matchScore").value(5))
			.andExpect(jsonPath("$.result.items[0].uncomputable").value(true))
			.andExpect(jsonPath("$.result.items[0].uncomputableReasons[0]")
				.value(EligibilityReason.INCOME_MISSING.getMessage()));
	}

	@Test
	void getRecommendations_includeReceived_생략하면_기본true를_서비스로_넘긴다() throws Exception {
		LocalDate asOf = LocalDate.of(2026, 7, 16);
		RecommendationView view = new RecommendationView(List.of(), asOf, null);
		given(recommendationQueryService.getRecommendations(anyLong(), any(), anyBoolean())).willReturn(view);

		mockMvc.perform(get("/api/v1/recommendations")).andExpect(status().isOk());

		verify(recommendationQueryService).getRecommendations(anyLong(), any(), eq(true));
	}

	@Test
	void getRecommendations_includeReceived_false면_그대로_서비스로_넘긴다() throws Exception {
		LocalDate asOf = LocalDate.of(2026, 7, 16);
		RecommendationView view = new RecommendationView(List.of(), asOf, null);
		given(recommendationQueryService.getRecommendations(anyLong(), any(), anyBoolean())).willReturn(view);

		mockMvc.perform(get("/api/v1/recommendations").param("includeReceived", "false")).andExpect(status().isOk());

		verify(recommendationQueryService).getRecommendations(anyLong(), any(), eq(false));
	}

	@Test
	void getRecommendations_dDay는_deadline이_null이면_null이다() throws Exception {
		LocalDate asOf = LocalDate.of(2026, 7, 16);
		SubsidySummary summary = new SubsidySummary(2L, "상시접수 지원금", "고용노동부", null, "제한 없음", null, null,
				PaymentType.VOUCHER);
		MatchResult matchResult = new MatchResult(2L, false, true, 3, List.of(), null, "gov24", "EXT-2");
		RecommendationItem item = new RecommendationItem(summary, matchResult);
		RecommendationView view = new RecommendationView(List.of(item), asOf, null);
		given(recommendationQueryService.getRecommendations(anyLong(), any(), anyBoolean())).willReturn(view);

		mockMvc.perform(get("/api/v1/recommendations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.items[0].dDay").doesNotExist())
			// 비현금 유형도 추천에 노출되므로 배지 분기용 값이 그대로 실려야 함
			.andExpect(jsonPath("$.result.items[0].paymentType").value("VOUCHER"));
	}

}
