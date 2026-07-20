package com.jeongbiseo.infra.enrichment;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.jeongbiseo.domain.subsidy.dto.AiExplanation;
import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.enrichment.dto.PaymentPeriod;
import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;
import com.jeongbiseo.infra.enrichment.repository.AiEnrichmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StoredAiExplanationReader 단위 테스트임. 어댑터가 지켜야 할 것 둘을 고정함 — 현재 본문 해시로 조회한다는 것과, 조회할 수 없는
 * 입력에 대해 저장소를 부르지 않고 빈 값을 돌린다는 것.
 */
class StoredAiExplanationReaderTest {

	private static final String BODY = "월 20만원을 최대 12개월간 지원합니다.";

	private final AiEnrichmentRepository repository = Mockito.mock(AiEnrichmentRepository.class);

	private final StoredAiExplanationReader reader = new StoredAiExplanationReader(this.repository);

	private static AiEnrichment stored() {
		return AiEnrichment.builder()
			.contentHash(ContentHasher.hash(BODY))
			.modelId("test-model")
			.promptVersion("amount-v3")
			.amountKind(AmountKind.SINGLE)
			.paymentPeriod(PaymentPeriod.MONTHLY)
			.monthlyAmount(200000L)
			.durationMonths(12)
			.evidence(BODY)
			.build();
	}

	/**
	 * 조회 키가 현재 본문의 해시여야 함. 이것이 어긋나면 본문이 바뀐 뒤에도 옛 해석이 화면에 남음.
	 */
	@Test
	void 현재_본문_해시로_조회해_도메인_값으로_옮긴다() {
		String expectedHash = ContentHasher.hash(BODY);
		when(this.repository.findTopBySubsidyIdAndContentHashOrderByIdDesc(1L, expectedHash))
			.thenReturn(Optional.of(stored()));

		Optional<AiExplanation> result = this.reader.findFor(1L, BODY);

		assertThat(result).isPresent();
		assertThat(result.get().monthlyAmount()).isEqualTo(200000L);
		assertThat(result.get().durationMonths()).isEqualTo(12);
		assertThat(result.get().evidence()).isEqualTo(BODY);
		verify(this.repository).findTopBySubsidyIdAndContentHashOrderByIdDesc(1L, expectedHash);
	}

	@Test
	void 저장된_해석이_없으면_빈_값이다() {
		when(this.repository.findTopBySubsidyIdAndContentHashOrderByIdDesc(anyLong(), anyString()))
			.thenReturn(Optional.empty());

		assertThat(this.reader.findFor(1L, BODY)).isEmpty();
	}

	/**
	 * 조회 자체가 불가능한 입력에서는 저장소를 부르지 않음. 상세 조회는 본문이 null인 지원금에도 들어오므로(원천에 설명이 없는 레코드가 실재함) 이
	 * 경로가 실제로 탐.
	 */
	@Test
	void 본문이_없으면_저장소를_부르지_않는다() {
		assertThat(this.reader.findFor(1L, null)).isEmpty();
		assertThat(this.reader.findFor(1L, "  ")).isEmpty();
		assertThat(this.reader.findFor(null, BODY)).isEmpty();

		verify(this.repository, never()).findTopBySubsidyIdAndContentHashOrderByIdDesc(any(), any());
	}

}
