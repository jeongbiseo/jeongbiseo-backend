package com.jeongbiseo.domain.recommendation.controller;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.recommendation.EligibilityReason;
import com.jeongbiseo.domain.recommendation.MatchResult;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.recommendation.dto.response.RecommendationItemResponse;
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

	@GetMapping
	public CustomResponse<RecommendationResponse> getRecommendations(@RequestParam(required = false) Integer limit) {
		validateLimit(limit);
		Long memberId = memberResolver.resolveMemberId();
		RecommendationView view = recommendationQueryService.getRecommendations(memberId, limit);

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
		return new RecommendationItemResponse(summary.subsidyId(), summary.name(), summary.agency(), summary.deadline(),
				dDay, summary.eligibilitySummary(), summary.estimatedAmountMin(), summary.estimatedAmountMax(),
				matchResult.matchScore(), matchResult.uncomputable(), reasons);
	}

}
