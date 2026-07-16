package com.jeongbiseo.domain.estimate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.estimate.EstimateCandidate;
import com.jeongbiseo.domain.estimate.EstimatedTotalCalculator;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService.ApplicantContext;
import com.jeongbiseo.domain.recommendation.service.RecommendationService;
import com.jeongbiseo.global.apiPayload.code.EstimatedAmountErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 예상 총액 조회 유즈케이스를 조립하는 애플리케이션 서비스임. 회원 프로필·기수령·기준일 산정은 RecommendationQueryService의 컨텍스트를
 * 재사용하고("이 변환 한 곳으로 좁힘" 유지), 후보 선정은 RecommendationService에 위임하며, 분류·합산만
 * EstimatedTotalCalculator에 맡김. 카드와 내역 두 엔드포인트가 이 하나의 결과를 서로 다른 DTO로 매핑함.
 */
@Service
public class EstimatedAmountService {

	// 총액 모집단은 추천 노출 상한과 의도적으로 동일함. MAX_LIMIT 변경 시 API명세서 19번의 "상위 20건 기준" 문구를
	// 동기화할 것(PLAN M5).
	private static final int POPULATION_LIMIT = RecommendationService.MAX_LIMIT;

	private final RecommendationQueryService recommendationQueryService;

	private final RecommendationService recommendationService;

	// EstimatedTotalCalculator는 상태 없는 순수 도메인 계산기라 스프링 빈으로 등록하지 않고 여기서 직접 만듦
	// (RecommendationPolicy와 같은 관용).
	private final EstimatedTotalCalculator calculator = new EstimatedTotalCalculator();

	public EstimatedAmountService(RecommendationQueryService recommendationQueryService,
			RecommendationService recommendationService) {
		this.recommendationQueryService = recommendationQueryService;
		this.recommendationService = recommendationService;
	}

	/**
	 * 회원의 예상 총액 계산 결과를 반환함. 온보딩 미완료면 RecommendationQueryService가 던지는 ONB404_1을 그대로 전파함.
	 * 계산 도중 예기치 못한 오류가 나면 AMT500_1로 감싸 던짐(추천 0건 아닌 서버 오류를 구분).
	 * @param memberId 조회 대상 회원
	 * @return 일시금·월 지급·별도 혜택 분류와 두 총액
	 */
	@Transactional(readOnly = true)
	public EstimatedTotalResult getEstimatedTotal(Long memberId) {
		try {
			ApplicantContext ctx = recommendationQueryService.resolveContext(memberId);
			List<EstimateCandidate> candidates = recommendationService.estimateCandidates(ctx.applicant(),
					ctx.receivedIds(), ctx.asOf(), POPULATION_LIMIT);
			return calculator.calculate(candidates);
		}
		catch (CustomException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new CustomException(EstimatedAmountErrorCode.ESTIMATED_AMOUNT_SERVER_ERROR, e);
		}
	}

}
