package com.jeongbiseo.domain.onboarding.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;

/**
 * 기수령 지원금 목록 저장소임(Spring Data JPA 평범한 리포지토리, 팀 레포 관례 — lab의 DIP 포트 분리 대신 team-repo가 다른
 * onboarding 리포지토리와 통일한 스타일 적응). 쓰기 경로(replaceAll/deleteByMemberId)는 setReceivedSubsidies
 * 엔드포인트(AGENTS.md 8장 3번, 우선순위 4)로 미룸 — 이 슬라이스는 추천 후보 제외에 필요한 조회만 담음(ONB-230).
 */
public interface ReceivedSubsidyRepository extends JpaRepository<ReceivedSubsidy, Long> {

	List<ReceivedSubsidy> findByMemberId(Long memberId);

	/** 회원의 현재 기수령 지원금 id 목록을 반환함(추천 후보에서 제외할 대상, TC-DEMO-020). */
	default List<Long> findSubsidyIdsByMemberId(Long memberId) {
		return findByMemberId(memberId).stream().map(ReceivedSubsidy::getSubsidyId).toList();
	}

}
