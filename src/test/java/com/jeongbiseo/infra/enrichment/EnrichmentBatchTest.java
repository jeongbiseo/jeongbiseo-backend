package com.jeongbiseo.infra.enrichment;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import tools.jackson.databind.ObjectMapper;

import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.infra.client.nim.NimClient;
import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;
import com.jeongbiseo.infra.enrichment.repository.AiEnrichmentRepository;
import com.jeongbiseo.infra.enrichment.repository.EnrichmentTargetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EnrichmentBatch 단위 테스트임(NIM 호출은 목이라 외부 통신 0건). 배치가 지켜야 할 계약 넷을 고정함 — 금액 표현 없는 건은 부르지
 * 않고, 이미 보강한 건은 다시 부르지 않으며, 호출·저장이 실패해도 배치가 서지 않고, 호출 상한을 넘지 않음.
 */
class EnrichmentBatchTest {

	private static final String BODY = "지원내용: 월 20만원을 최대 12개월간 지원합니다.";

	private static final String OK_JSON = """
			{"amountKind":"SINGLE","paymentPeriod":"MONTHLY","amountValue":null,"monthlyAmount":200000,\
			"durationMonths":12,"conditionExpression":null,\
			"evidence":"월 20만원을 최대 12개월간 지원합니다.","abstained":false,"abstainReason":null}""";

	private final EnrichmentTargetRepository targetRepository = Mockito.mock(EnrichmentTargetRepository.class);

	private final AiEnrichmentRepository enrichmentRepository = Mockito.mock(AiEnrichmentRepository.class);

	private final NimClient nimClient = Mockito.mock(NimClient.class);

	private EnrichmentBatch batchWithMaxCalls(int maxCalls) {
		return new EnrichmentBatch(this.targetRepository, this.enrichmentRepository, this.nimClient,
				new EnrichmentValidator(new ObjectMapper()), "test-model", maxCalls, 0L);
	}

	private static SubsidyEntity subsidy(long id, String description) {
		SubsidyEntity entity = SubsidyEntity.builder()
			.id(id)
			.sourceId("batch-test")
			.externalId("EXT-" + id)
			.name("배치 대상 " + id)
			.description(description)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.paymentType(PaymentType.CASH)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.build();
		return entity;
	}

	private void givenCandidates(SubsidyEntity... entities) {
		Page<SubsidyEntity> page = new PageImpl<>(List.of(entities), Pageable.ofSize(100), entities.length);
		when(this.targetRepository.findEnrichmentCandidates(any(), any())).thenReturn(page);
	}

	@Test
	void 금액_표현이_없는_건은_호출하지_않는다() {
		givenCandidates(subsidy(1L, "고효율 LED 등과 노후 기관 교체를 지원합니다."));

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		verify(this.nimClient, never()).completeAsJson(anyString(), anyString(), anyString(), any());
		assertThat(result.calls()).isZero();
		assertThat(result.skipped()).isEqualTo(1);
	}

	@Test
	void 이미_보강한_조합은_다시_호출하지_않는다() {
		givenCandidates(subsidy(1L, BODY));
		when(this.enrichmentRepository.existsBySubsidyIdAndContentHashAndModelIdAndPromptVersion(anyLong(), anyString(),
				anyString(), anyString()))
			.thenReturn(true);

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		verify(this.nimClient, never()).completeAsJson(anyString(), anyString(), anyString(), any());
		assertThat(result.skipped()).isEqualTo(1);
	}

	@Test
	void 검증을_통과하면_저장한다() {
		givenCandidates(subsidy(1L, BODY));
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any())).thenReturn(OK_JSON);

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		verify(this.enrichmentRepository, times(1)).save(any(AiEnrichment.class));
		assertThat(result.saved()).isEqualTo(1);
		assertThat(result.outcomes()).containsEntry("ACCEPTED", 1);
	}

	/**
	 * LLM 호출이 죽어도 배치가 서면 안 됨. 한 건이 실패해도 다음 건으로 넘어가고 예외가 위로 전파되지 않아야 함(판정원칙 5번).
	 */
	@Test
	void 호출이_실패해도_다음_건으로_넘어간다() {
		givenCandidates(subsidy(1L, BODY), subsidy(2L, BODY));
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any()))
			.thenThrow(new IllegalStateException("NIM 호출 재시도 소진"))
			.thenReturn(OK_JSON);

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		assertThat(result.calls()).isEqualTo(2);
		assertThat(result.saved()).isEqualTo(1);
		assertThat(result.outcomes()).containsEntry("CALL_FAILED", 1).containsEntry("ACCEPTED", 1);
	}

	@Test
	void 저장이_실패해도_배치가_서지_않는다() {
		givenCandidates(subsidy(1L, BODY));
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any())).thenReturn(OK_JSON);
		when(this.enrichmentRepository.save(any(AiEnrichment.class))).thenThrow(new IllegalStateException("제약 위반"));

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		assertThat(result.saved()).isZero();
		assertThat(result.outcomes()).containsEntry("SAVE_FAILED", 1);
	}

	/**
	 * 호출 상한이 없으면 계정 한도(분당 40회) 아래에서 몇 시간씩 붙잡히고 실패가 반복될 때 빠져나오지 못함.
	 */
	@Test
	void 호출_상한을_넘지_않는다() {
		givenCandidates(subsidy(1L, BODY), subsidy(2L, BODY), subsidy(3L, BODY));
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any())).thenReturn(OK_JSON);

		EnrichmentBatchResult result = batchWithMaxCalls(2).run();

		verify(this.nimClient, times(2)).completeAsJson(anyString(), anyString(), anyString(), any());
		assertThat(result.calls()).isEqualTo(2);
	}

	@Test
	void 검증에_걸린_건은_저장하지_않고_사유를_남긴다() {
		givenCandidates(subsidy(1L, BODY));
		// 근거가 원문에 없는 응답임
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any())).thenReturn("""
				{"amountKind":"SINGLE","paymentPeriod":"LUMP_SUM","amountValue":500000,"monthlyAmount":null,\
				"durationMonths":null,"conditionExpression":null,"evidence":"일시금 50만원 지급",\
				"abstained":false,"abstainReason":null}""");

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		verify(this.enrichmentRepository, never()).save(any(AiEnrichment.class));
		assertThat(result.saved()).isZero();
		assertThat(result.outcomes()).containsEntry("EVIDENCE_NOT_IN_SOURCE", 1);
	}

	@Test
	void 금액_표현_판별은_여러_표기를_받는다() {
		assertThat(EnrichmentBatch.hasAmountMention("월 20만원 지원")).isTrue();
		assertThat(EnrichmentBatch.hasAmountMention("1,000,000원 지급")).isTrue();
		assertThat(EnrichmentBatch.hasAmountMention("최대 1억원")).isTrue();
		assertThat(EnrichmentBatch.hasAmountMention("500천원")).isTrue();
		assertThat(EnrichmentBatch.hasAmountMention("고효율 LED 교체 지원")).isFalse();
		assertThat(EnrichmentBatch.hasAmountMention(null)).isFalse();
	}

	/**
	 * 기업·사업자 대상 지원사업은 추천과 총액 양쪽에서 제외되므로 보강할 이유가 없음. 조회 조건이 조용히 바뀌면 기업 공고에 AI 해석이 붙어 화면에
	 * 나가므로 인자를 고정함.
	 */
	@Test
	void 기업_대상은_조회에서_제외한다() {
		givenCandidates();

		batchWithMaxCalls(10).run();

		verify(this.targetRepository).findEnrichmentCandidates(Mockito.eq(TargetAudience.BUSINESS), any());
	}

	/**
	 * 검증에 걸린 건은 저장되지 않으므로 다음 회차에 다시 대상이 됨. 그때 또 부르지 않으려면 건너뛰기 판정이 저장 여부가 아니라 호출 이력에 근거해야
	 * 하는데, 현재 구현은 저장된 행으로만 판단함. <b>즉 거부된 건은 매 회차 재호출됨</b> — 알려진 한계이며 호출 상한이 폭주를 막음.
	 */
	@Test
	void 거부된_건은_저장되지_않아_다음_회차에_다시_대상이_된다() {
		givenCandidates(subsidy(1L, BODY));
		when(this.nimClient.completeAsJson(anyString(), anyString(), anyString(), any())).thenReturn("""
				{"amountKind":"SINGLE","paymentPeriod":"LUMP_SUM","amountValue":500000,"monthlyAmount":null,\
				"durationMonths":null,"conditionExpression":null,"evidence":"원문에 없는 문장",\
				"abstained":false,"abstainReason":null}""");

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		verify(this.enrichmentRepository, never()).save(any(AiEnrichment.class));
		assertThat(result.calls()).isEqualTo(1);
		assertThat(result.saved()).isZero();
	}

	@Test
	void 대상이_없으면_아무것도_하지_않는다() {
		givenCandidates();

		EnrichmentBatchResult result = batchWithMaxCalls(10).run();

		assertThat(result.calls()).isZero();
		assertThat(result.saved()).isZero();
		assertThat(Map.copyOf(result.outcomes())).isEmpty();
	}

}
