package com.jeongbiseo.infra.enrichment;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.subsidy.AiExplanationReader;
import com.jeongbiseo.domain.subsidy.dto.AiExplanation;
import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;
import com.jeongbiseo.infra.enrichment.repository.AiEnrichmentRepository;

/**
 * 저장된 보강 결과를 도메인 값으로 옮기는 어댑터임({@link AiExplanationReader} 구현).
 *
 * <p>
 * <b>외부 호출을 하지 않음.</b> 사용자 요청 경로에서 LLM을 부르면 응답 지연·비용·실패 전파·결과 비결정성이 생기므로, 여기서는 이미 검증을 통과해
 * 저장된 행만 읽음(배치 설계 1장). LLM이 죽어 있어도 이 경로는 멀쩡함.
 * </p>
 *
 * <p>
 * 현재 원문 해시와 일치하는 행만 보므로 <b>본문이 바뀌면 옛 해석이 자동으로 노출에서 빠짐.</b> 행을 지우지는 않음(판정원칙 4번).
 * </p>
 */
@Component
public class StoredAiExplanationReader implements AiExplanationReader {

	private final AiEnrichmentRepository enrichmentRepository;

	public StoredAiExplanationReader(AiEnrichmentRepository enrichmentRepository) {
		this.enrichmentRepository = enrichmentRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AiExplanation> findFor(Long subsidyId, String description) {
		if (subsidyId == null || description == null || description.isBlank()) {
			return Optional.empty();
		}
		return this.enrichmentRepository
			.findTopBySubsidyIdAndContentHashOrderByIdDesc(subsidyId, ContentHasher.hash(description))
			.map(StoredAiExplanationReader::toExplanation);
	}

	private static AiExplanation toExplanation(AiEnrichment entity) {
		return new AiExplanation(entity.getAmountValue(), entity.getMonthlyAmount(), entity.getDurationMonths(),
				entity.getConditionExpression(), entity.getEvidence());
	}

}
