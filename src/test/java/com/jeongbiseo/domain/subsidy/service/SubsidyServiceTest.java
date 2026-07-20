package com.jeongbiseo.domain.subsidy.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.favorite.service.FavoriteService;
import com.jeongbiseo.domain.subsidy.AiExplanationReader;
import com.jeongbiseo.domain.subsidy.dto.AiExplanation;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * SubsidyService 단위 테스트임(Mockito). getDetail의 존재/부재 분기, eligibilityText null 그대로 반환(H1),
 * dDay 계산·null-safe를 고정함. Clock은 고정 시각으로 목킹함.
 */
@ExtendWith(MockitoExtension.class)
class SubsidyServiceTest {

	private static final LocalDate AS_OF = LocalDate.of(2026, 7, 16);

	@Mock
	private SubsidyRepository subsidyRepository;

	@Mock
	private FavoriteService favoriteService;

	// 포트 목임. 구현(infra.enrichment)을 끌어오지 않아도 되므로 이 테스트는 LLM 쪽을 전혀 모름 -- 포트를 둔 이유가
	// 그대로 드러나는 지점임.
	@Mock
	private AiExplanationReader aiExplanationReader;

	private final Clock clock = Clock.fixed(AS_OF.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			ZoneId.of("Asia/Seoul"));

	private SubsidyService subsidyService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		subsidyService = new SubsidyService(subsidyRepository, favoriteService, aiExplanationReader, clock);
	}

	@Test
	void getDetail_존재하면_상세를_반환한다() {
		SubsidyEntity entity = base().deadline(AS_OF.plusDays(10)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.name()).isEqualTo("청년월세지원");
		assertThat(response.dDay()).isEqualTo(10);
		assertThat(response.isFavorite()).isFalse();
		assertThat(response.paymentType()).isEqualTo(PaymentType.CASH);
		assertThat(response.category()).isEqualTo(SubsidyCategory.YOUTH);
	}

	@Test
	void getDetail_존재하지않으면_SUBSIDY404_1을_던진다() {
		given(subsidyRepository.findById(anyLong())).willReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> subsidyService.getDetail(999L, null)).isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode())
				.isEqualTo(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
	}

	@Test
	void getDetail_eligibilityText가_null이면_null_그대로_반환한다() {
		SubsidyEntity entity = base().deadline(AS_OF.plusDays(1)).build(); // eligibilityText
																			// 미지정 = null
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.eligibilityText()).isNull();
	}

	@Test
	void getDetail_deadline이_null이면_dDay도_null이다() {
		SubsidyEntity entity = base().deadline(null).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.dDay()).isNull();
	}

	@Test
	void getDetail_paymentType와_category가_null이면_null로_매핑한다() {
		SubsidyEntity entity = base().paymentType(null).category(null).deadline(AS_OF.plusDays(1)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.paymentType()).isNull();
		assertThat(response.category()).isNull();
	}

	@Test
	void getDetail_로그인회원이_관심등록했으면_isFavorite가_true다() {
		SubsidyEntity entity = base().deadline(AS_OF.plusDays(1)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));
		given(favoriteService.isFavorite(7L, 1L)).willReturn(true);

		SubsidyDetailResponse response = subsidyService.getDetail(1L, 7L);

		assertThat(response.isFavorite()).isTrue();
	}

	/**
	 * 검증을 통과해 저장된 해석이 있으면 상세에 실림(등급 1). 값과 함께 근거 문장이 오는 것이 핵심임 — 조건 오분류 같은 의미 오류는 검증기가 못
	 * 잡으므로 사용자가 원문과 대조할 수 있어야 함.
	 */
	@Test
	void getDetail_저장된_AI해석이_있으면_상세에_싣는다() {
		SubsidyEntity entity = base().description("월 20만원을 최대 12개월간 지원합니다.").deadline(AS_OF.plusDays(1)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));
		given(aiExplanationReader.findFor(1L, "월 20만원을 최대 12개월간 지원합니다."))
			.willReturn(java.util.Optional.of(new AiExplanation(null, 200000L, 12, null, "월 20만원을 최대 12개월간 지원합니다.")));

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.aiExplanation()).isNotNull();
		assertThat(response.aiExplanation().monthlyAmount()).isEqualTo(200000L);
		assertThat(response.aiExplanation().durationMonths()).isEqualTo(12);
		assertThat(response.aiExplanation().evidence()).isEqualTo("월 20만원을 최대 12개월간 지원합니다.");
	}

	/**
	 * 보강 결과가 없는 것이 정상 상태임(검증 통과분에만 실림). 이때 나머지 응답이 멀쩡해야 함 — 보강이 없다고 원래 보이던 정보까지 감추면 정보
	 * 격차가 오히려 커짐(판정원칙 5번).
	 */
	@Test
	void getDetail_AI해석이_없으면_null이고_나머지는_그대로다() {
		SubsidyEntity entity = base().description("설명").deadline(AS_OF.plusDays(10)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));
		given(aiExplanationReader.findFor(1L, "설명")).willReturn(java.util.Optional.empty());

		SubsidyDetailResponse response = subsidyService.getDetail(1L, null);

		assertThat(response.aiExplanation()).isNull();
		assertThat(response.name()).isEqualTo("청년월세지원");
		assertThat(response.dDay()).isEqualTo(10);
	}

	private static SubsidyEntity.SubsidyEntityBuilder base() {
		return SubsidyEntity.builder()
			.id(1L)
			.sourceId("gov24")
			.externalId("ext-1")
			.name("청년월세지원")
			.category(SubsidyCategory.YOUTH)
			.paymentType(PaymentType.CASH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.loanProduct(false);
	}

}
