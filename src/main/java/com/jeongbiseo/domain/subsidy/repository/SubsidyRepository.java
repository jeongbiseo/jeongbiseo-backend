package com.jeongbiseo.domain.subsidy.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.subsidy.SubsidyReader;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;

/**
 * SubsidyEntity Spring Data JPA 저장소임. SubsidyReader(domain.subsidy)를 직접 구현해 DIP 방향을 지킴.
 * findCandidates는 JPQL DTO 프로젝션으로 판정 필드만 조회하고(2026-07-22 힙 OOM 대응), findSummaries는 엔티티 조회
 * 후 정적 매퍼(toSummary)로 매핑함. storage 타입은 domain 밖으로 새지 않음. search와 countByIdIn은 subsidy 자기
 * 도메인(검색·상세, setReceivedSubsidies 존재 검증)이 직접 쓰는 메서드라 SubsidyReader 포트에는 넣지 않음(추천 경계와 분리,
 * PLAN 08-subsidy-search-detail 1.1).
 */
public interface SubsidyRepository extends JpaRepository<SubsidyEntity, Long>, SubsidyReader {

	// 지원금 검색(API명세서 13번 searchSubsidies). 융자 상품은 항상 제외하고, keyword·category는 nullable로
	// 처리함. keyword 매칭은 공백 무시임 — 컬럼 쪽 replace(col, ' ', '')는 일반 띄어쓰기(U+0020)만 지우고
	// (탭·전각 공백 U+3000은 안 지움), 키워드 쪽 공백 제거는 SubsidyService.search 진입부에서 1회 전처리함
	// ("청년 월세"로 "청년월세"를 잡음).
	// WHERE(loanProduct·keyword·category 3절)는
	// search·searchOrderByDeadline·searchOrderByName 3벌이 동일하게 유지돼야 함 — 한 곳을 고치면 3곳(각
	// main·count) 함께 고칠 것.
	@Query(value = """
			select new com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult(s.id, s.name, s.agency, s.category,
				s.deadline, s.estimatedAmountMin, s.estimatedAmountMax)
			from SubsidyEntity s
			where s.loanProduct = false
			and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
			and (:category is null or s.category = :category)
				and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
			""", countQuery = """
					select count(s) from SubsidyEntity s
					where s.loanProduct = false
					and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
					and (:category is null or s.category = :category)
			and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
					""")
	Page<SubsidySearchResult> search(@Param("keyword") String keyword, @Param("category") SubsidyCategory category,
			@Param("includeClosed") boolean includeClosed, @Param("asOf") LocalDate asOf, Pageable pageable);

	// sort=DEADLINE 정렬 검색. 마감일 미상(null)은 case 표현식으로 항상 뒤로 보냄(Pageable Sort로는 nulls last를
	// 못 그려 order by를 본문에 명시함). WHERE·countQuery는 search와 동일하고 order by만 다름. Pageable은
	// 페이지·크기만
	// 실어 넘김(정렬 미포함).
	@Query(value = """
			select new com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult(s.id, s.name, s.agency, s.category,
				s.deadline, s.estimatedAmountMin, s.estimatedAmountMax)
			from SubsidyEntity s
			where s.loanProduct = false
			and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
			and (:category is null or s.category = :category)
				and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
			order by case when s.deadline is null then 1 else 0 end, s.deadline asc, s.id asc
			""", countQuery = """
					select count(s) from SubsidyEntity s
					where s.loanProduct = false
					and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
					and (:category is null or s.category = :category)
			and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
					""")
	Page<SubsidySearchResult> searchOrderByDeadline(@Param("keyword") String keyword,
			@Param("category") SubsidyCategory category, @Param("includeClosed") boolean includeClosed,
			@Param("asOf") LocalDate asOf, Pageable pageable);

	// sort=NAME 정렬 검색. 가나다순은 DB 컬럼 collation에 의존함(utf8mb4 기본 collation은 현대 한글을 가나다순으로
	// 정렬함). tie-breaker로 s.id asc를 붙여 동명 지원금의 페이지 경계 중복·누락을 막음.
	@Query(value = """
			select new com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult(s.id, s.name, s.agency, s.category,
				s.deadline, s.estimatedAmountMin, s.estimatedAmountMax)
			from SubsidyEntity s
			where s.loanProduct = false
			and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
			and (:category is null or s.category = :category)
				and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
			order by s.name asc, s.id asc
			""", countQuery = """
					select count(s) from SubsidyEntity s
					where s.loanProduct = false
					and (:keyword is null
				or replace(s.name, ' ', '') like concat('%', :keyword, '%')
				or replace(s.agency, ' ', '') like concat('%', :keyword, '%'))
					and (:category is null or s.category = :category)
			and (:includeClosed = true or s.deadline is null or s.deadline >= :asOf)
					""")
	Page<SubsidySearchResult> searchOrderByName(@Param("keyword") String keyword,
			@Param("category") SubsidyCategory category, @Param("includeClosed") boolean includeClosed,
			@Param("asOf") LocalDate asOf, Pageable pageable);

	// setReceivedSubsidies 존재 검증용. 요청 id 목록 중 실제 존재하는 개수를 세어 전부 존재하는지 판정함.
	long countByIdIn(List<Long> ids);

	// SubsidyIngestionAdapter의 (source, externalId) upsert 키 조회용. 소스별 기존 행 전체를 한 번에 읽어
	// 멱등 적재의 존재 여부를 판정함.
	List<SubsidyEntity> findAllBySourceIdIn(Set<String> sourceIds);

	// 추천 후보 조건: 활성·추천 가능·비융자·대표 행이면서 기준일에 신청 가능함(마감일 미상은 누락 방지를 위해 통과).
	// 마감 필터(HANDOFF 4장)와 융자 제외(HANDOFF 2.B-13, 2026-07-15)를 레코드 속성 필터로 함께 둠(deadline,
	// recommendable와 같은 자리).
	//
	// 2026-07-22 힙 OOM 장애 대응 3종이 이 쿼리에 모여 있음(리서치 문서 docs/md/추천-후보조회-힙-OOM-AWS-대응-리서치
	// -2026-07-22.md 3장):
	// 1) DTO 프로젝션 — 엔티티 전체(description·eligibilityText 등 긴 TEXT 포함)를 힙에 올리지 않고 판정 필드만
	// 조회함. 생성자 인자 순서는 SubsidyCriteria 정규 생성자 선언 순서와 일치해야 함.
	// 2) 확실 탈락 프리필터 — 기업 대상·1차산업 전용을 DB에서 제외함. 판정 정본은 RecommendationPolicy.inScope()
	// 이고 이 WHERE 절은 그 정본의 성능용 복제임(UNKNOWN·MIXED는 통과 원칙 그대로, 두 컬럼은 NOT NULL이라 null 분기
	// 불필요). inScope가 바뀌면 여기도 함께 고칠 것.
	// 3) order by — MAX_CANDIDATES 절단을 결정적으로 만듦. 주의: 노출 정렬(DeadlineRanking)의 1차 키는
	// 지역 비강등이라 이 절단 축(마감 임박순)과 같지 않음. 캡에 실제로 닿으면 상시모집(deadline null)이 계통
	// 탈락하므로 캡은 모집단(실측 8,647건) 위 여유값으로만 운용함(SubsidyReader.MAX_CANDIDATES javadoc).
	@Query("""
			select new com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria(s.id, s.targetAudience,
				s.occupationRestriction, s.ageSignal, s.ageMin, s.ageMax, s.regionScope, s.regionCode,
				s.employmentSignal, s.employmentTags, s.employmentRawCode, s.incomeSignal, s.incomeThreshold,
				s.householdSignal, s.householdCondition, s.estimatedAmountMin, s.estimatedAmountMax, s.amountKind,
				s.monthlyAmount, s.paymentType, s.deadline, s.sourceId, s.externalId, s.regionCodes)
			from SubsidyEntity s
			where s.active = true and s.recommendable = true and s.loanProduct = false and s.duplicateOfId is null
			and (s.deadline is null or s.deadline >= :asOf)
			and s.targetAudience <> com.jeongbiseo.domain.common.enums.TargetAudience.BUSINESS
			and s.occupationRestriction <> com.jeongbiseo.domain.common.enums.OccupationRestriction.PRIMARY_INDUSTRY_ONLY
			order by case when s.deadline is null then 1 else 0 end, s.deadline asc, s.id asc
			""")
	List<SubsidyCriteria> findCandidateCriteria(@Param("asOf") LocalDate asOf, Pageable pageable);

	@Override
	default List<SubsidyCriteria> findCandidates(LocalDate asOf) {
		return findCandidateCriteria(asOf, PageRequest.of(0, MAX_CANDIDATES));
	}

	// 추천 응답 조립용 표시 정보. RecommendationService가 매칭·정렬을 마친 subsidyId를 정렬 순서대로 넘김.
	// findAllById는 입력 순서를 보장하지 않으므로 id로 인덱싱한 뒤 입력 순서대로 재구성함(SubsidyReader 계약의 입력 순서 대응
	// 보장).
	@Override
	default List<SubsidySummary> findSummaries(List<Long> subsidyIds) {
		Map<Long, SubsidySummary> summariesById = new HashMap<>();
		for (SubsidyEntity entity : findAllById(subsidyIds)) {
			summariesById.put(entity.getId(), toSummary(entity));
		}
		return subsidyIds.stream().map(summariesById::get).filter(Objects::nonNull).toList();
	}

	/**
	 * 표시 정보 매핑임. eligibilitySummary에는 원문 eligibilityText를 담음(SubsidySummary javadoc 계약).
	 */
	static SubsidySummary toSummary(SubsidyEntity entity) {
		return new SubsidySummary(entity.getId(), entity.getName(), entity.getAgency(), entity.getDeadline(),
				entity.getEligibilityText(), entity.getEstimatedAmountMin(), entity.getEstimatedAmountMax(),
				entity.getPaymentType());
	}

	// API명세서의 응답 전체 dataUpdatedAt 대표값임. 소스 갱신 시각만 집계하고 fetchedAt은 섞지 않음
	@Override
	@Query("select max(s.dataUpdatedAt) from SubsidyEntity s")
	LocalDateTime findLatestDataUpdatedAt();

}
