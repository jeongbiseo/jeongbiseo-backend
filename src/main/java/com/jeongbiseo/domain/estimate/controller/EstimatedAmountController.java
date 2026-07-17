package com.jeongbiseo.domain.estimate.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.IncludedItem;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.SeparateItem;
import com.jeongbiseo.domain.estimate.dto.response.EstimatedBreakdownResponse;
import com.jeongbiseo.domain.estimate.dto.response.EstimatedBreakdownResponse.CashItem;
import com.jeongbiseo.domain.estimate.dto.response.EstimatedBreakdownResponse.MonthlyItem;
import com.jeongbiseo.domain.estimate.dto.response.EstimatedBreakdownResponse.SeparateBenefit;
import com.jeongbiseo.domain.estimate.dto.response.EstimatedTotalResponse;
import com.jeongbiseo.domain.estimate.service.EstimatedAmountService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 예상 총액 카드와 내역 조회를 다룸(API명세서 19·20번, operationId
 * getEstimatedTotal·getEstimatedBreakdown). 파라미터 없이 회원의 추천 inScope 상위 노출분을 서버에서 계산함.
 * 컨트롤러는 회원 식별과 응답 변환만 맡고, 조립은 EstimatedAmountService에 위임함(추천 컨트롤러 관용). operationId는 어노테이션
 * 없이 메서드명으로 노출됨(다른 컨트롤러와 동일 관용).
 */
@Tag(name = "EstimatedAmount", description = "예상 지원금 총액 카드와 내역 조회")
@RestController
@RequestMapping("/api/v1/estimated-total")
public class EstimatedAmountController {

	private static final String CURRENCY_KRW = "KRW";

	private static final String NOTICE_EMPTY = "추천 상위 20건 중 금액이 확정된 지원금이 아직 없어요";

	private static final String NOTICE_CASH = "추천 상위 20건 중 현금으로 확정된 지원금만 더한 예상 금액이에요";

	private final EstimatedAmountService estimatedAmountService;

	private final FixedMemberResolver memberResolver;

	public EstimatedAmountController(EstimatedAmountService estimatedAmountService,
			FixedMemberResolver memberResolver) {
		this.estimatedAmountService = estimatedAmountService;
		this.memberResolver = memberResolver;
	}

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서 실제 발생함(OnboardingController와 동일 관용).
	@Operation(summary = "예상 총액 카드 조회",
			description = "회원의 추천 상위 노출분 중 금액이 확정된 지원금만 합산한 예상 총액 카드를 조회함. 온보딩 미완료면 404로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "예상 총액 카드 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER400_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1) 또는 온보딩 정보 없음(ONB404_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"),
							@ExampleObject(name = "ONB404_1",
									value = "{\"isSuccess\":false,\"code\":\"ONB404_1\",\"message\":\"온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요\",\"result\":null}") })),
			@ApiResponse(responseCode = "500", description = "예상 금액 계산 서버 오류(AMT500_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AMT500_1",
							value = "{\"isSuccess\":false,\"code\":\"AMT500_1\",\"message\":\"예상 금액을 계산하지 못했어요\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<EstimatedTotalResponse> getEstimatedTotal() {
		Long memberId = memberResolver.resolveMemberId();
		EstimatedTotalResult result = estimatedAmountService.getEstimatedTotal(memberId);
		return CustomResponse.ok(toCard(result));
	}

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서 실제 발생함(OnboardingController와 동일 관용).
	@Operation(summary = "예상 총액 내역 조회", description = "회원의 추천 상위 노출분을 일시금·월 지급·별도 혜택으로 분류한 내역을 조회함. 온보딩 미완료면 404로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "예상 총액 내역 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER400_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1) 또는 온보딩 정보 없음(ONB404_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"),
							@ExampleObject(name = "ONB404_1",
									value = "{\"isSuccess\":false,\"code\":\"ONB404_1\",\"message\":\"온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요\",\"result\":null}") })),
			@ApiResponse(responseCode = "500", description = "예상 금액 계산 서버 오류(AMT500_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "AMT500_1",
							value = "{\"isSuccess\":false,\"code\":\"AMT500_1\",\"message\":\"예상 금액을 계산하지 못했어요\",\"result\":null}"))) })
	@GetMapping("/breakdown")
	public CustomResponse<EstimatedBreakdownResponse> getEstimatedBreakdown() {
		Long memberId = memberResolver.resolveMemberId();
		EstimatedTotalResult result = estimatedAmountService.getEstimatedTotal(memberId);
		return CustomResponse.ok(toBreakdown(result));
	}

	private static EstimatedTotalResponse toCard(EstimatedTotalResult result) {
		int itemCount = result.oneTimeItems().size();
		int monthlyItemCount = result.monthlyItems().size();
		Long cashTotalMin = itemCount == 0 ? null : result.cashTotalMin();
		Long cashTotalMax = itemCount == 0 ? null : result.cashTotalMax();
		Long monthlyTotalMin = monthlyItemCount == 0 ? null : result.monthlyTotalMin();
		Long monthlyTotalMax = monthlyItemCount == 0 ? null : result.monthlyTotalMax();
		boolean hasConfirmedAmount = itemCount > 0 || monthlyItemCount > 0;
		String notice = hasConfirmedAmount ? NOTICE_CASH : NOTICE_EMPTY;
		return new EstimatedTotalResponse(result.totalCount(), itemCount, cashTotalMin, cashTotalMax, monthlyItemCount,
				monthlyTotalMin, monthlyTotalMax, result.separateItems().size(), CURRENCY_KRW, true, notice);
	}

	private static EstimatedBreakdownResponse toBreakdown(EstimatedTotalResult result) {
		List<CashItem> items = result.oneTimeItems().stream().map(EstimatedAmountController::toCashItem).toList();
		List<MonthlyItem> monthlyItems = result.monthlyItems()
			.stream()
			.map(EstimatedAmountController::toMonthlyItem)
			.toList();
		List<SeparateBenefit> separateBenefits = result.separateItems()
			.stream()
			.map(EstimatedAmountController::toSeparateBenefit)
			.toList();
		return new EstimatedBreakdownResponse(result.cashTotalMin(), result.cashTotalMax(), result.monthlyTotalMin(),
				result.monthlyTotalMax(), CURRENCY_KRW, true, items, monthlyItems, separateBenefits);
	}

	private static CashItem toCashItem(IncludedItem item) {
		return new CashItem(item.subsidyId(), item.name(), item.amountMin(), item.amountMax(), PaymentType.CASH.name(),
				true);
	}

	private static MonthlyItem toMonthlyItem(IncludedItem item) {
		return new MonthlyItem(item.subsidyId(), item.name(), item.amountMin(), item.amountMax(),
				PaymentType.MONTHLY.name());
	}

	private static SeparateBenefit toSeparateBenefit(SeparateItem item) {
		String paymentType = item.paymentType() == null ? PaymentType.UNKNOWN.name() : item.paymentType().name();
		return new SeparateBenefit(item.subsidyId(), item.name(), paymentType, item.note());
	}

}
