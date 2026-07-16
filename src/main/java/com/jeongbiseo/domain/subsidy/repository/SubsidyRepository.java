package com.jeongbiseo.domain.subsidy.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.subsidy.SubsidyReader;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;

/**
 * SubsidyEntity Spring Data JPA 저장소임. SubsidyReader(domain.subsidy)를 직접 구현해 DIP 방향을 지킴.
 * findCandidates와 findSummaries는 엔티티 조회 후 정적 매퍼(toCriteria, toSummary)로 매핑함 — 순서 불변식을 매퍼
 * 한 곳에 가둠. storage 타입은 domain 밖으로 새지 않음. 검색(searchSubsidies)과 소스별 조회(적재)는 순위 4 소관이라 이
 * 슬라이스에서 제외함(PLAN 07-subsidy-recommendation 3.C).
 */
public interface SubsidyRepository extends JpaRepository<SubsidyEntity, Long>, SubsidyReader {

	// 추천 후보 조건: 활성·추천 가능·비융자·대표 행이면서 기준일에 신청 가능함(마감일 미상은 누락 방지를 위해 통과).
	// 마감 필터(HANDOFF 4장)와 융자 제외(2.B-13, 2026-07-15)를 레코드 속성 필터로 함께 둠 —
	// deadline·recommendable와 같은 자리.
	@Query("""
			select s
			from SubsidyEntity s
			where s.active = true and s.recommendable = true and s.loanProduct = false and s.duplicateOfId is null
			and (s.deadline is null or s.deadline >= :asOf)
			""")
	List<SubsidyEntity> findCandidateEntities(@Param("asOf") LocalDate asOf);

	@Override
	default List<SubsidyCriteria> findCandidates(LocalDate asOf) {
		return findCandidateEntities(asOf).stream().map(SubsidyRepository::toCriteria).toList();
	}

	// 추천 응답 조립용 표시 정보. RecommendationService가 매칭을 마친 subsidyId만 넘김.
	// id in 조회는 JpaRepository.findAllById를 그대로 씀(별도 JPQL 불필요).
	@Override
	default List<SubsidySummary> findSummaries(List<Long> subsidyIds) {
		return findAllById(subsidyIds).stream().map(SubsidyRepository::toSummary).toList();
	}

	/**
	 * 매칭 조건 스냅샷 매핑임. SubsidyCriteria 22개 컴포넌트의 순서 불변식을 이 메서드 한 곳에 가둠(JPQL 문자열과 record 선언의
	 * 이중관리 제거). DB 없이 단위 테스트 가능함.
	 */
	static SubsidyCriteria toCriteria(SubsidyEntity entity) {
		return new SubsidyCriteria(entity.getId(), entity.getTargetAudience(), entity.getOccupationRestriction(),
				entity.getAgeSignal(), entity.getAgeMin(), entity.getAgeMax(), entity.getRegionScope(),
				entity.getRegionCode(), entity.getEmploymentSignal(), entity.getEmploymentTags(),
				entity.getEmploymentRawCode(), entity.getIncomeSignal(), entity.getIncomeThreshold(),
				entity.getHouseholdSignal(), entity.getHouseholdCondition(), entity.getEstimatedAmountMin(),
				entity.getEstimatedAmountMax(), entity.getMonthlyAmount(), entity.getPaymentType(),
				entity.getDeadline(), entity.getSourceId(), entity.getExternalId());
	}

	/**
	 * 표시 정보 매핑임. eligibilitySummary에는 원문 eligibilityText를 담음(SubsidySummary javadoc 계약).
	 */
	static SubsidySummary toSummary(SubsidyEntity entity) {
		return new SubsidySummary(entity.getId(), entity.getName(), entity.getAgency(), entity.getDeadline(),
				entity.getEligibilityText(), entity.getEstimatedAmountMin(), entity.getEstimatedAmountMax());
	}

	// API명세서의 응답 전체 dataUpdatedAt 대표값임. 소스 갱신 시각만 집계하고 fetchedAt은 섞지 않음
	@Override
	@Query("select max(s.dataUpdatedAt) from SubsidyEntity s")
	LocalDateTime findLatestDataUpdatedAt();

}
