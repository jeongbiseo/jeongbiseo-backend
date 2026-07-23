package com.jeongbiseo.domain.estimate.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.estimate.EstimateCandidate;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult;
import com.jeongbiseo.domain.recommendation.ApplicantProfile;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService.ApplicantContext;
import com.jeongbiseo.domain.recommendation.service.RecommendationService;
import com.jeongbiseo.global.apiPayload.code.EstimatedAmountErrorCode;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.infra.client.common.dto.AmountKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * EstimatedAmountService 단위 테스트임(순수 JUnit 더하기 Mockito, 스프링 컨텍스트 없음). 컨텍스트 조립·후보 선정을 목으로
 * 대체해 예외 경로(도메인 예외 전파, 예기치 못한 오류의 AMT500_1 래핑)와 정상 위임만 검증함. 실제 분류·배관은 계산기 단위 테스트와 통합 테스트가
 * 담당함.
 */
class EstimatedAmountServiceTest {

	private static final ApplicantContext CONTEXT = new ApplicantContext(
			new ApplicantProfile(27, "11620", EmploymentStatus.JOB_SEEKING, IncomeBracket.UNDER_200, 1), Set.of(),
			LocalDate.of(2026, 7, 17));

	private final RecommendationQueryService recommendationQueryService = mock(RecommendationQueryService.class);

	private final RecommendationService recommendationService = mock(RecommendationService.class);

	private final EstimatedAmountService service = new EstimatedAmountService(recommendationQueryService,
			recommendationService);

	@Test
	void getEstimatedTotal_정상이면_후보를_분류한_결과를_반환한다() {
		given(recommendationQueryService.resolveContext(anyLong())).willReturn(CONTEXT);
		given(recommendationService.estimateCandidates(any(), any(), any(), any()))
			.willReturn(List.of(new EstimateCandidate(1L, "일시금 현금", PaymentType.CASH, TargetAudience.PERSONAL,
					AmountKind.SINGLE, 100L, 300L, null, false)));

		EstimatedTotalResult result = service.getEstimatedTotal(1L);

		assertThat(result.totalCount()).isEqualTo(1);
		assertThat(result.oneTimeItems()).hasSize(1);
		assertThat(result.cashTotalMax()).isEqualTo(300L);
	}

	@Test
	void getEstimatedTotal_도메인예외는_그대로_전파한다() {
		// 온보딩 미완료(CustomException) 등은 AMT500_1로 감싸지 않고 원래 예외를 그대로 올림.
		CustomException domainException = new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		given(recommendationQueryService.resolveContext(anyLong())).willThrow(domainException);

		assertThatThrownBy(() -> service.getEstimatedTotal(1L)).isSameAs(domainException);
	}

	@Test
	void getEstimatedTotal_예기치못한오류는_AMT500_1로_감싼다() {
		given(recommendationQueryService.resolveContext(anyLong())).willReturn(CONTEXT);
		given(recommendationService.estimateCandidates(any(), any(), any(), any()))
			.willThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> service.getEstimatedTotal(1L)).isInstanceOf(CustomException.class)
			.extracting(thrown -> ((CustomException) thrown).getErrorCode())
			.isEqualTo(EstimatedAmountErrorCode.ESTIMATED_AMOUNT_SERVER_ERROR);
	}

}
