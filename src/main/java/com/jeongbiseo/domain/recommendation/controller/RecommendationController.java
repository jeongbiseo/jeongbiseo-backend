package com.jeongbiseo.domain.recommendation.controller;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.recommendation.EligibilityReason;
import com.jeongbiseo.domain.recommendation.MatchResult;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.recommendation.dto.response.RecommendationItemResponse;
import com.jeongbiseo.domain.recommendation.dto.response.RecommendationItemResponse.ConfirmedAgeRange;
import com.jeongbiseo.domain.recommendation.dto.response.RecommendationResponse;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService.RecommendationView;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 추천 리스트 조회를 다룸(API명세서 14번, operationId getRecommendations). 컨트롤러는 limit HTTP 검증과 응답 변환만
 * 맡고, 프로필 조회·추천 계산 등 오케스트레이션은 RecommendationQueryService에 위임함(HANDOFF 2.B-14). 온보딩 미완료면
 * ONB404_1을 던짐(getMyOnboarding과 동일 예외 재사용, PLAN.md 3장 W3 절).
 */
@Tag(name = "Recommendation", description = "개인 맞춤 추천 리스트 조회")
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

	private final RecommendationQueryService recommendationQueryService;

	private final FixedMemberResolver memberResolver;

	public RecommendationController(RecommendationQueryService recommendationQueryService,
			FixedMemberResolver memberResolver) {
		this.recommendationQueryService = recommendationQueryService;
		this.memberResolver = memberResolver;
	}

	// 401(COMMON401)은 미인증 시 SecurityErrorResponder가 반환함(AUTH-W001 인증 강제화, 명세서 각주
	// COMMON401 정합).
	@Operation(summary = "추천 리스트 조회",
			description = "회원의 온보딩 프로필을 기준으로 개인 맞춤 추천 리스트를 조회함. 온보딩 미완료면 404, 탈퇴 계정이면 400으로 거절함. "
					+ "대출·융자 상품과 마감된 공고는 추천 모집단에서 제외함. 거주 지역이 다른 공고는 탈락시키지 않고 정렬 후순위로 내림. "
					+ "각 항목의 matchScore는 0에서 5 사이 정수이며 통과한 매칭 축의 개수임(백분율이 아님). "
					+ "includeReceived는 생략하면 true이고, false면 이미 받은 지원금을 추천에서 제외함. 예상 총액은 이 값과 무관하게 "
					+ "항상 기수령을 제외함(받을 수 있는 금액 기준).")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "추천 리스트 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400",
					description = "잘못된 limit·includeReceived 파라미터(VALID400_0: limit이 0 이하이거나 타입·형식 불일치) "
							+ "또는 탈퇴 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"),
							@ExampleObject(name = "MEMBER400_1",
									value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}") })),
			@ApiResponse(responseCode = "401", description = "인증 필요(미인증 시 COMMON401)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1) 또는 온보딩 정보 없음(ONB404_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"),
							@ExampleObject(name = "ONB404_1",
									value = "{\"isSuccess\":false,\"code\":\"ONB404_1\",\"message\":\"온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요\",\"result\":null}") })),
			@ApiResponse(responseCode = "500", description = "추천 계산 서버 오류(REC500_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "REC500_1",
							value = "{\"isSuccess\":false,\"code\":\"REC500_1\",\"message\":\"추천을 불러오지 못했어요, 잠시 후 다시 시도해주세요\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<RecommendationResponse> getRecommendations(
			@Parameter(description = "노출 개수(선택). 생략하면 3이고, 20을 넘기면 20으로 줄임. 0 이하는 400으로 거절함",
					example = "3") @RequestParam(required = false) Integer limit,
			@Parameter(description = "기수령 지원금 포함 여부(선택). 생략하면 true(포함), false면 이미 받은 지원금을 추천에서 제외함",
					example = "true") @RequestParam(defaultValue = "true") boolean includeReceived) {
		validateLimit(limit);
		Long memberId = memberResolver.resolveMemberId();
		RecommendationView view = recommendationQueryService.getRecommendations(memberId, limit, includeReceived);

		List<RecommendationItemResponse> responseItems = view.items()
			.stream()
			.map(item -> toItemResponse(item, view.asOf()))
			.toList();
		RecommendationResponse result = new RecommendationResponse(responseItems, view.dataUpdatedAt());
		return CustomResponse.ok(result);
	}

	// limit는 프론트가 개수를 정하는 값이라 상한 초과는 서비스가 클램프하되(정상 200), 0 이하는 의미가 없어 여기서 VALID400_0으로
	// 거절함.
	// 정수로 파싱되지 않는 값(?limit=abc)은 GlobalExceptionHandler가 같은 코드로 변환함.
	private static void validateLimit(Integer limit) {
		if (limit != null && limit <= 0) {
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
	}

	private static RecommendationItemResponse toItemResponse(RecommendationItem item, LocalDate today) {
		SubsidySummary summary = item.summary();
		MatchResult matchResult = item.matchResult();
		Integer dDay = (summary.deadline() == null) ? null : (int) ChronoUnit.DAYS.between(today, summary.deadline());
		List<String> reasons = matchResult.uncomputableReasons().stream().map(EligibilityReason::getMessage).toList();
		int unverifiedConditionCount = (int) matchResult.uncomputableReasons()
			.stream()
			.filter(EligibilityReason::qualificationUncertainty)
			.distinct()
			.count();
		// 연령이 확정됐을 때만(범위 한쪽이라도 존재) 공고 대상 연령 범위를 실음. 아니면 null.
		ConfirmedAgeRange ageRange = (matchResult.confirmedAgeMin() != null || matchResult.confirmedAgeMax() != null)
				? new ConfirmedAgeRange(matchResult.confirmedAgeMin(), matchResult.confirmedAgeMax()) : null;
		return new RecommendationItemResponse(summary.subsidyId(), summary.name(), summary.agency(), summary.deadline(),
				dDay, summary.eligibilitySummary(), summary.estimatedAmountMin(), summary.estimatedAmountMax(),
				summary.paymentType(), matchResult.matchScore(), matchResult.uncomputable(), reasons,
				matchResult.confirmedMatchCount(), unverifiedConditionCount, ageRange);
	}

}
