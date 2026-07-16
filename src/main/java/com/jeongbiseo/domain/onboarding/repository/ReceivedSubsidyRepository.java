package com.jeongbiseo.domain.onboarding.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;

/**
 * 기수령 지원금 목록 저장소임(Spring Data JPA 평범한 리포지토리, 팀 레포 관례). lab의 DIP 포트 분리 대신 team-repo가 다른
 * onboarding 리포지토리와 통일한 스타일로 적응함. setReceivedSubsidies(우선순위 4)가 쓰는 벌크 삭제를 더해 쓰기 경로를
 * 완성함(ONB-230 조회에 이어).
 */
public interface ReceivedSubsidyRepository extends JpaRepository<ReceivedSubsidy, Long> {

	List<ReceivedSubsidy> findByMemberId(Long memberId);

	/** 회원의 현재 기수령 지원금 id 목록을 반환함(추천 후보에서 제외할 대상, TC-DEMO-020). */
	default List<Long> findSubsidyIdsByMemberId(Long memberId) {
		return findByMemberId(memberId).stream().map(ReceivedSubsidy::getSubsidyId).toList();
	}

	// 파생 deleteBy는 flush가 saveAll의 INSERT 뒤로 밀려 같은 목록 재PUT 시 UNIQUE 위반이 나므로
	// @Modifying 쿼리로 즉시 실행함.
	@Modifying
	@Query("delete from ReceivedSubsidy r where r.memberId = :memberId")
	void deleteByMemberId(@Param("memberId") Long memberId);

}
