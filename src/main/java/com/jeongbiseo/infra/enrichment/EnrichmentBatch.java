package com.jeongbiseo.infra.enrichment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.infra.client.nim.NimClient;
import com.jeongbiseo.infra.enrichment.dto.ValidationResult;
import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;
import com.jeongbiseo.infra.enrichment.repository.AiEnrichmentRepository;
import com.jeongbiseo.infra.enrichment.repository.EnrichmentTargetRepository;

/**
 * LLM 금액 보강 배치임. 자정 원천 수집이 성공한 뒤 이어지는 후속 작업이며, 원천 스냅샷 게시를 막지 않음(배치 설계 3장).
 *
 * <p>
 * <b>실패가 위로 전파되지 않음.</b> 한 건이 실패해도 다음 건으로 넘어가고, 배치 전체가 실패해도 호출한 쪽은 예외를 받지 않음 — LLM은 사용자
 * 요청의 필수 의존성이 아니며 보강 실패로 원래 보이던 정보까지 못 보게 되면 정보 격차가 오히려 커짐(판정원칙 5번).
 * </p>
 */
@Component
public class EnrichmentBatch {

	private static final Logger log = LoggerFactory.getLogger(EnrichmentBatch.class);

	// 본문에 금액 표현이 있는지 보는 패턴임. 쉼표 낀 숫자와 만·천·억 단위를 함께 받음. SQL이 아니라 자바에서 거르는 이유는
	// EnrichmentTargetRepository Javadoc 참조.
	private static final Pattern AMOUNT_MENTION = Pattern.compile("[0-9][0-9,]*\\s*(만원|천원|억원|억|만\\s*원|원)");

	private static final int PAGE_SIZE = 100;

	private final EnrichmentTargetRepository targetRepository;

	private final AiEnrichmentRepository enrichmentRepository;

	private final NimClient nimClient;

	private final EnrichmentValidator validator;

	private final String modelId;

	// 한 회차 호출 상한임. 계정 한도가 분당 40회라 무제한으로 돌면 몇 시간씩 붙잡히고, 실패가 반복될 때 빠져나오지 못함.
	private final int maxCalls;

	// 호출 간격임. 분당 40회 한도(2026-07-20 계정 화면 실측)에 여유를 둬 분당 30회 이하로 잡음.
	private final long intervalMillis;

	public EnrichmentBatch(EnrichmentTargetRepository targetRepository, AiEnrichmentRepository enrichmentRepository,
			NimClient nimClient, EnrichmentValidator validator,
			@Value("${app.llm.nim.model-id:meta/llama-3.1-70b-instruct}") String modelId,
			@Value("${app.llm.enrichment.max-calls:200}") int maxCalls,
			@Value("${app.llm.enrichment.interval-millis:2100}") long intervalMillis) {
		this.targetRepository = targetRepository;
		this.enrichmentRepository = enrichmentRepository;
		this.nimClient = nimClient;
		this.validator = validator;
		this.modelId = modelId;
		this.maxCalls = maxCalls;
		this.intervalMillis = intervalMillis;
	}

	/**
	 * 보강 대상을 훑어 검증 통과분을 저장함. 예외를 던지지 않고 결과 요약만 반환함.
	 * @return 회차 요약(호출·저장·건너뜀·거부 사유별 건수)
	 */
	public EnrichmentBatchResult run() {
		Map<String, Integer> outcomes = new LinkedHashMap<>();
		int calls = 0;
		int saved = 0;
		int skipped = 0;

		Pageable pageable = PageRequest.of(0, PAGE_SIZE);
		boolean hasNext = true;
		while (hasNext && calls < this.maxCalls) {
			Page<SubsidyEntity> page = this.targetRepository.findEnrichmentCandidates(TargetAudience.BUSINESS,
					pageable);
			for (SubsidyEntity subsidy : page.getContent()) {
				if (calls >= this.maxCalls) {
					break;
				}
				if (!hasAmountMention(subsidy.getDescription())) {
					skipped++;
					continue;
				}
				String contentHash = ContentHasher.hash(subsidy.getDescription());
				if (this.enrichmentRepository.existsBySubsidyIdAndContentHashAndModelIdAndPromptVersion(subsidy.getId(),
						contentHash, this.modelId, EnrichmentPrompt.VERSION)) {
					// 같은 원문을 같은 모델·프롬프트로 이미 보강했으면 다시 부르지 않음(배치 설계 7장).
					skipped++;
					continue;
				}

				calls++;
				String outcome = enrichOne(subsidy, contentHash);
				outcomes.merge(outcome, 1, Integer::sum);
				if ("ACCEPTED".equals(outcome)) {
					saved++;
				}
				if (!sleepBetweenCalls()) {
					// 인터럽트되면 지금까지 결과를 들고 접음. 예외를 던지면 호출한 수집 배치까지 죽어 원천 스냅샷 게시를 막게 됨.
					log.warn("보강 배치가 중단됨: 지금까지 호출={}, 저장={}", calls, saved);
					return new EnrichmentBatchResult(calls, saved, skipped, Map.copyOf(outcomes));
				}
			}
			hasNext = page.hasNext();
			pageable = pageable.next();
		}

		EnrichmentBatchResult result = new EnrichmentBatchResult(calls, saved, skipped, Map.copyOf(outcomes));
		log.info("LLM 보강 배치 종료: 호출={}, 저장={}, 건너뜀={}, 판정={}", calls, saved, skipped, outcomes);
		return result;
	}

	/**
	 * 한 건을 보강함. 어떤 실패든 위로 던지지 않고 사유 문자열로 돌려 배치가 계속 돌게 함.
	 */
	private String enrichOne(SubsidyEntity subsidy, String contentHash) {
		String raw;
		try {
			raw = this.nimClient.completeAsJson(EnrichmentPrompt.systemPrompt(),
					EnrichmentPrompt.userPrompt(subsidy.getName(), subsidy.getDescription()),
					EnrichmentPrompt.SCHEMA_NAME, EnrichmentPrompt.jsonSchema());
		}
		catch (RuntimeException exception) {
			log.warn("보강 호출 실패: subsidyId={}, cause={}", subsidy.getId(), exception.getClass().getSimpleName());
			return "CALL_FAILED";
		}

		// 저장 직전에 원문을 다시 해싱해 그 사이 본문이 바뀌었는지 봄. 같은 트랜잭션 안이라 실제로는 같지만, 검증기가 해시 비교를
		// 책임지는 구조를 배치에서도 그대로 지킴(배치 설계 6장 3번).
		String hashNow = ContentHasher.hash(subsidy.getDescription());
		ValidationResult result = this.validator.validate(raw, subsidy.getDescription(), contentHash, hashNow);
		if (!result.accepted()) {
			return result.reason().name();
		}

		try {
			// 별도 @Transactional을 두지 않음. 같은 클래스 안에서 부르면 프록시를 타지 않아 트랜잭션이 걸리지 않는데,
			// JpaRepository.save는 SimpleJpaRepository가 이미 자체 트랜잭션에서 실행하므로 한 건 저장에는 그것으로
			// 충분함.
			this.enrichmentRepository
				.save(AiEnrichment.of(subsidy, contentHash, this.modelId, EnrichmentPrompt.VERSION, result.value()));
		}
		catch (RuntimeException exception) {
			// 경합으로 유니크 제약이 터질 수 있음(같은 건을 두 회차가 동시에 보강). 이미 있다는 뜻이므로 배치를 세우지 않음.
			log.warn("보강 저장 실패: subsidyId={}, cause={}", subsidy.getId(), exception.getClass().getSimpleName());
			return "SAVE_FAILED";
		}
		return "ACCEPTED";
	}

	static boolean hasAmountMention(String description) {
		return description != null && AMOUNT_MENTION.matcher(description).find();
	}

	/**
	 * 호출 간격만큼 쉼. 인터럽트되면 false를 돌려 호출측이 배치를 접게 함(예외를 던지지 않는 이유는 run 계약이 실패를 위로 전파하지 않는 것이기
	 * 때문임).
	 */
	// ponytail: 순차 호출이라 스레드를 재우는 것으로 충분함. 스케줄러를 두지 않음.
	private boolean sleepBetweenCalls() {
		try {
			Thread.sleep(this.intervalMillis);
			return true;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
