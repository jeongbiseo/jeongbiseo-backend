package com.jeongbiseo.domain.favorite.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;

/**
 * 관심 등록 저장소임. 회원과 지원금 복합 키 조회와 마감 캘린더 대상 조회를 제공함.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	boolean existsByMemberIdAndSubsidyId(Long memberId, Long subsidyId);

	Optional<Favorite> findByMemberIdAndSubsidyId(Long memberId, Long subsidyId);

	// 관심 목록 조회(API명세서 getFavorites). join으로 N+1을 막고, 검색 결과와 동일한 SubsidySearchResult로 직접
	// 투영해 프론트가 같은 카드 스키마를 재사용하게 함. 최근 등록순(f.id desc)임.
	@Query("""
			select new com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult(s.id, s.name, s.agency, s.category, s.deadline)
			from Favorite f
			join f.subsidy s
			where f.member.id = :memberId
			order by f.id desc
			""")
	List<SubsidySearchResult> findFavoriteSubsidies(@Param("memberId") Long memberId);

	// SubsidyEntity에는 DATE_RANGE 같은 마감 유형 필드가 없으므로 deadline 존재 여부로 캘린더 대상을 판정함.
	// 지정 기간의 관심 지원금을 마감 오름차순으로 한 번에 가져옴.
	@Query("""
			select f from Favorite f
			join fetch f.subsidy s
			where f.member.id = :memberId
			  and s.deadline is not null
			  and s.deadline between :from and :to
			order by s.deadline asc
			""")
	List<Favorite> findCalendarTargets(@Param("memberId") Long memberId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

}
