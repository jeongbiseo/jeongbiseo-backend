package com.jeongbiseo.infra.enrichment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;

/**
 * 보강 대상 지원금을 읽는 저장소임. {@code SubsidyRepository}에 메서드를 더하지 않고 별도로 둔 이유는 두 가지임 — 그 파일은 다른
 * 레인이 동시에 건드리고 있어 충돌 면적을 만들지 않으려는 것이고, 보강은 읽기만 하므로 {@link Repository}로 좁혀 쓰기 메서드를 아예 노출하지
 * 않으려는 것임.
 */
public interface EnrichmentTargetRepository extends Repository<SubsidyEntity, Long> {

	/**
	 * 파서가 금액을 산출하지 못한 활성 개인 대상 지원금을 봄.
	 *
	 * <p>
	 * <b>여기서 걸러지지 않는 것</b>: 본문에 금액 표현이 실제로 있는지는 SQL로 판정하지 않음. 한국어 금액 표기가 "만원"·"천원"·"억
	 * 원"·"1,000,000원"처럼 갈래가 많아 LIKE를 여러 개 이어 붙이면 조건이 길고 빠뜨리기 쉬움. 자바 정규식으로 거르는 쪽이 정확하고
	 * 테스트로 고정할 수 있어 그렇게 함(호출측 책임).
	 * </p>
	 *
	 * <p>
	 * <b>CONDITIONAL 건은 이 조회에 잡히지 않음.</b> 파서가 조건부로 분류해도 금액 후보는 채우기 때문에
	 * {@code estimatedAmountMin}이 null이 아님. 그리고 {@code AmountKind}는 DB에 저장되지 않아 SQL로 구분할
	 * 방법이 없음(2026-07-20 실측). 등급 1의 1차 대상을 "금액 서술이 있는데 파서가 못 뽑은 건"으로 좁힌 것은 의도된 선택임 —
	 * CONDITIONAL은 파서가 이미 값을 내 화면에 무언가 나오는 상태이고, 여기 잡히는 건은 아예 비어 있어 보강 이득이 가장 큼.
	 * </p>
	 * @param excluded 제외할 대상 구분(기업·사업자)
	 * @param pageable 페이지 요청
	 * @return 보강 후보 페이지
	 */
	@Query("""
			select s from SubsidyEntity s
			where s.active = true
			  and s.loanProduct = false
			  and s.targetAudience <> :excluded
			  and s.estimatedAmountMin is null
			  and s.description is not null
			order by s.id asc
			""")
	Page<SubsidyEntity> findEnrichmentCandidates(TargetAudience excluded, Pageable pageable);

}
