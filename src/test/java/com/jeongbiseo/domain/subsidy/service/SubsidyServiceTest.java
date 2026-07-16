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

	private final Clock clock = Clock.fixed(AS_OF.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
			ZoneId.of("Asia/Seoul"));

	private SubsidyService subsidyService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		subsidyService = new SubsidyService(subsidyRepository, clock);
	}

	@Test
	void getDetail_존재하면_상세를_반환한다() {
		SubsidyEntity entity = base().deadline(AS_OF.plusDays(10)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L);

		assertThat(response.name()).isEqualTo("청년월세지원");
		assertThat(response.dDay()).isEqualTo(10);
		assertThat(response.isFavorite()).isFalse();
		assertThat(response.paymentType()).isEqualTo("CASH");
		assertThat(response.category()).isEqualTo("YOUTH");
	}

	@Test
	void getDetail_존재하지않으면_SUBSIDY404_1을_던진다() {
		given(subsidyRepository.findById(anyLong())).willReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> subsidyService.getDetail(999L)).isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode())
				.isEqualTo(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
	}

	@Test
	void getDetail_eligibilityText가_null이면_null_그대로_반환한다() {
		SubsidyEntity entity = base().deadline(AS_OF.plusDays(1)).build(); // eligibilityText
																			// 미지정 = null
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L);

		assertThat(response.eligibilityText()).isNull();
	}

	@Test
	void getDetail_deadline이_null이면_dDay도_null이다() {
		SubsidyEntity entity = base().deadline(null).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L);

		assertThat(response.dDay()).isNull();
	}

	@Test
	void getDetail_paymentType와_category가_null이면_null로_매핑한다() {
		SubsidyEntity entity = base().paymentType(null).category(null).deadline(AS_OF.plusDays(1)).build();
		given(subsidyRepository.findById(1L)).willReturn(java.util.Optional.of(entity));

		SubsidyDetailResponse response = subsidyService.getDetail(1L);

		assertThat(response.paymentType()).isNull();
		assertThat(response.category()).isNull();
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
