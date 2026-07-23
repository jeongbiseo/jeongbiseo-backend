package com.jeongbiseo.infra.enrichment;

import com.jeongbiseo.support.MySqlContainerSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.enrichment.dto.AmountEnrichment;
import com.jeongbiseo.infra.enrichment.dto.PaymentPeriod;
import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;
import com.jeongbiseo.infra.enrichment.repository.AiEnrichmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ai_enrichment 영속성 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL, Docker 필요). 중복
 * 방지 UNIQUE 제약과 "현재 원문 해시와 일치하는 것만 조회된다"는 노출 규칙을 실제 DB에서 고정함 — 이 둘이 깨지면 본문이 바뀐 뒤에도 옛 보강이
 * 화면에 남거나 같은 원문을 반복 호출하게 됨.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AiEnrichmentPersistenceIntegrationTest extends MySqlContainerSupport {

	private static final String MODEL = "meta/llama-3.1-70b-instruct";

	private static final String EVIDENCE = "월 20만원을 최대 12개월간 지원합니다.";

	@Autowired
	private AiEnrichmentRepository enrichmentRepository;

	@Autowired
	private SubsidyRepository subsidyRepository;

	private SubsidyEntity givenSubsidy(String externalId) {
		return subsidyRepository.save(SubsidyEntity.builder()
			.sourceId("enrichment-test")
			.externalId(externalId)
			.name("보강 대상 " + externalId)
			.category(SubsidyCategory.YOUTH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.paymentType(PaymentType.CASH)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.build());
	}

	private static AmountEnrichment givenValue() {
		return new AmountEnrichment(AmountKind.SINGLE, PaymentPeriod.MONTHLY, null, 200000L, 12, null, EVIDENCE, false,
				null);
	}

	@Test
	void 검증_통과분을_저장하고_현재_해시로_조회한다() {
		SubsidyEntity subsidy = givenSubsidy("SAVE-1");
		String hash = ContentHasher.hash(EVIDENCE);

		enrichmentRepository.save(AiEnrichment.of(subsidy, hash, MODEL, "amount-v2", givenValue()));

		AiEnrichment found = enrichmentRepository.findTopBySubsidyIdAndContentHashOrderByIdDesc(subsidy.getId(), hash)
			.orElseThrow();
		assertThat(found.getAmountKind()).isEqualTo(AmountKind.SINGLE);
		assertThat(found.getPaymentPeriod()).isEqualTo(PaymentPeriod.MONTHLY);
		assertThat(found.getMonthlyAmount()).isEqualTo(200000L);
		assertThat(found.getEvidence()).isEqualTo(EVIDENCE);
		assertThat(found.getModelId()).isEqualTo(MODEL);
	}

	/**
	 * 본문이 바뀌면 옛 보강이 화면에서 빠져야 함. 조회에 현재 해시를 넘기므로 옛 행은 애초에 잡히지 않음 — 이것이 낡은 AI 해석 노출을 막는 유일한
	 * 장치라 실제 DB에서 고정함.
	 */
	@Test
	void 원문이_바뀌면_옛_보강은_조회되지_않는다() {
		SubsidyEntity subsidy = givenSubsidy("STALE-1");
		String oldHash = ContentHasher.hash("옛 본문: 월 20만원 지원");
		enrichmentRepository.save(AiEnrichment.of(subsidy, oldHash, MODEL, "amount-v2", givenValue()));

		String newHash = ContentHasher.hash("새 본문: 월 30만원으로 인상됨");

		assertThat(enrichmentRepository.findTopBySubsidyIdAndContentHashOrderByIdDesc(subsidy.getId(), newHash))
			.isEmpty();
		// 옛 해시로는 여전히 남아 있음(레코드를 지우지 않고 노출에서만 빠지는 것이 판정원칙 4번 정합)
		assertThat(enrichmentRepository.findTopBySubsidyIdAndContentHashOrderByIdDesc(subsidy.getId(), oldHash))
			.isPresent();
	}

	@Test
	void 같은_원문과_모델과_프롬프트_조합은_중복_저장되지_않는다() {
		SubsidyEntity subsidy = givenSubsidy("DUP-1");
		String hash = ContentHasher.hash(EVIDENCE);
		enrichmentRepository.save(AiEnrichment.of(subsidy, hash, MODEL, "amount-v2", givenValue()));

		assertThatThrownBy(() -> enrichmentRepository
			.saveAndFlush(AiEnrichment.of(subsidy, hash, MODEL, "amount-v2", givenValue())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * 프롬프트를 올리면 같은 원문이라도 새로 보강할 수 있어야 함. 버전이 UNIQUE 키에 들어 있어 재보강이 막히지 않는 것을 고정함.
	 */
	@Test
	void 프롬프트_버전이_다르면_같은_원문도_다시_저장된다() {
		SubsidyEntity subsidy = givenSubsidy("VER-1");
		String hash = ContentHasher.hash(EVIDENCE);
		enrichmentRepository.save(AiEnrichment.of(subsidy, hash, MODEL, "amount-v1", givenValue()));
		enrichmentRepository.saveAndFlush(AiEnrichment.of(subsidy, hash, MODEL, "amount-v2", givenValue()));

		// 조회는 최신 행을 보므로 v2가 나옴
		AiEnrichment found = enrichmentRepository.findTopBySubsidyIdAndContentHashOrderByIdDesc(subsidy.getId(), hash)
			.orElseThrow();
		assertThat(found.getPromptVersion()).isEqualTo("amount-v2");
	}

	@Test
	void 이미_보강한_조합인지_확인할_수_있다() {
		SubsidyEntity subsidy = givenSubsidy("EXISTS-1");
		String hash = ContentHasher.hash(EVIDENCE);
		enrichmentRepository.save(AiEnrichment.of(subsidy, hash, MODEL, "amount-v2", givenValue()));

		assertThat(enrichmentRepository.existsBySubsidyIdAndContentHashAndModelIdAndPromptVersion(subsidy.getId(), hash,
				MODEL, "amount-v2"))
			.isTrue();
		assertThat(enrichmentRepository.existsBySubsidyIdAndContentHashAndModelIdAndPromptVersion(subsidy.getId(), hash,
				MODEL, "amount-v3"))
			.isFalse();
	}

}
