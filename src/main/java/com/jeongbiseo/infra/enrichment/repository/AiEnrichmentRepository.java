package com.jeongbiseo.infra.enrichment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.infra.enrichment.entity.AiEnrichment;

/**
 * 보강 결과 저장소임. 조회는 반드시 현재 원문 해시를 함께 넘겨야 하며(해시 없는 조회 메서드를 두지 않음), 이는 본문이 바뀐 뒤에도 옛 보강이 화면에
 * 남는 사고를 타입 수준에서 막기 위함임(배치 설계 6장).
 */
public interface AiEnrichmentRepository extends JpaRepository<AiEnrichment, Long> {

	/**
	 * 이미 같은 조합으로 보강했는지 봄. 배치가 호출 전에 확인해 같은 원문을 같은 모델·프롬프트로 다시 부르지 않게 함(배치 설계 7장 중복 억제).
	 */
	boolean existsBySubsidyIdAndContentHashAndModelIdAndPromptVersion(Long subsidyId, String contentHash,
			String modelId, String promptVersion);

	/**
	 * 화면 노출용 조회임. 현재 원문 해시와 일치하는 것 중 가장 최근 행을 봄 — 프롬프트를 올려 재보강하면 새 행이 쌓이므로 최신이 유효분임.
	 */
	Optional<AiEnrichment> findTopBySubsidyIdAndContentHashOrderByIdDesc(Long subsidyId, String contentHash);

}
