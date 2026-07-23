package com.jeongbiseo.domain.estimate.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.estimate.EstimateExclusionReason;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.IncludedItem;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.SeparateItem;
import com.jeongbiseo.domain.estimate.service.EstimatedAmountService;
import com.jeongbiseo.global.apiPayload.code.EstimatedAmountErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EstimatedAmountController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 오케스트레이션은
 * EstimatedAmountService를 목으로 대체해 컨트롤러의 카드·내역 DTO 매핑과 AMT500_1 예외 응답만 검증함. 실제 계산·배관은 계산기
 * 단위 테스트와 통합 테스트가 담당함.
 */
@WebMvcTest(EstimatedAmountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
@org.junit.jupiter.api.extension.ExtendWith(com.jeongbiseo.support.FixedMemberContextExtension.class)
class EstimatedAmountControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EstimatedAmountService estimatedAmountService;

	private static EstimatedTotalResult sampleResult() {
		List<IncludedItem> oneTime = List.of(new IncludedItem(1L, "청년월세 특별지원", 100_000L, 300_000L));
		List<IncludedItem> monthly = List.of(new IncludedItem(2L, "청년 월세 월지급", 500_000L, 500_000L));
		List<SeparateItem> separate = List.of(
				new SeparateItem(3L, "국민내일배움카드", PaymentType.VOUCHER, EstimateExclusionReason.NON_CASH,
						EstimateExclusionReason.NON_CASH.note()),
				new SeparateItem(4L, "타지역 전용 현금", PaymentType.CASH, EstimateExclusionReason.REGION_UNVERIFIED,
						EstimateExclusionReason.REGION_UNVERIFIED.note()));
		return new EstimatedTotalResult(oneTime, monthly, separate, 100_000L, 300_000L, 500_000L, 500_000L);
	}

	@Test
	void getEstimatedTotal_정상이면_countfirst_카드를_반환한다() throws Exception {
		given(estimatedAmountService.getEstimatedTotal(anyLong())).willReturn(sampleResult());

		mockMvc.perform(get("/api/v1/estimated-total"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.totalCount").value(4))
			.andExpect(jsonPath("$.result.itemCount").value(1))
			.andExpect(jsonPath("$.result.cashTotalMin").value(100000))
			.andExpect(jsonPath("$.result.cashTotalMax").value(300000))
			.andExpect(jsonPath("$.result.monthlyItemCount").value(1))
			.andExpect(jsonPath("$.result.monthlyTotalMin").value(500000))
			.andExpect(jsonPath("$.result.separateBenefitCount").value(2))
			.andExpect(jsonPath("$.result.currency").value("KRW"))
			.andExpect(jsonPath("$.result.isEstimate").value(true))
			.andExpect(jsonPath("$.result.notice").value("추천 상위 20건 중 현금으로 확정된 지원금만 더한 예상 금액이에요"));
	}

	@Test
	void getEstimatedTotal_금액확정이_없으면_총액은_null이고_정보부족_문구를_준다() throws Exception {
		EstimatedTotalResult empty = new EstimatedTotalResult(List.of(), List.of(), List.of(new SeparateItem(9L, "바우처",
				PaymentType.VOUCHER, EstimateExclusionReason.NON_CASH, EstimateExclusionReason.NON_CASH.note())), 0L,
				0L, 0L, 0L);
		given(estimatedAmountService.getEstimatedTotal(anyLong())).willReturn(empty);

		mockMvc.perform(get("/api/v1/estimated-total"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.itemCount").value(0))
			.andExpect(jsonPath("$.result.cashTotalMin").value(nullValue()))
			.andExpect(jsonPath("$.result.cashTotalMax").value(nullValue()))
			.andExpect(jsonPath("$.result.separateBenefitCount").value(1))
			.andExpect(jsonPath("$.result.notice").value("추천 상위 20건 중 금액이 확정된 지원금이 아직 없어요"));
	}

	@Test
	void getEstimatedBreakdown_현금과_월지급과_별도혜택을_분리해_반환한다() throws Exception {
		given(estimatedAmountService.getEstimatedTotal(anyLong())).willReturn(sampleResult());

		mockMvc.perform(get("/api/v1/estimated-total/breakdown"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.items[0].subsidyId").value(1))
			.andExpect(jsonPath("$.result.items[0].includedInTotal").value(true))
			.andExpect(jsonPath("$.result.items[0].paymentType").value("CASH"))
			.andExpect(jsonPath("$.result.monthlyItems[0].subsidyId").value(2))
			.andExpect(jsonPath("$.result.monthlyItems[0].paymentType").value("MONTHLY"))
			.andExpect(jsonPath("$.result.separateBenefits[1].paymentType").value("CASH"))
			.andExpect(jsonPath("$.result.separateBenefits[1].note")
				.value(EstimateExclusionReason.REGION_UNVERIFIED.note()));
	}

	@Test
	void getEstimatedTotal_서버오류면_AMT500_1을_반환한다() throws Exception {
		given(estimatedAmountService.getEstimatedTotal(anyLong()))
			.willThrow(new CustomException(EstimatedAmountErrorCode.ESTIMATED_AMOUNT_SERVER_ERROR));

		mockMvc.perform(get("/api/v1/estimated-total"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("AMT500_1"));
	}

}
