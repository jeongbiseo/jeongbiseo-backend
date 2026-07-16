package com.jeongbiseo.domain.onboarding.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;

/**
 * 기수령 지원금 목록을 조회하는 도메인 서비스임(DISCUSS.md 3.2 소형 애그리게이트, ONB-230 추천 1차 제외 필터로 씀). 전체
 * 교체(replaceAll)는 setReceivedSubsidies 엔드포인트 구현 시점(우선순위 4)으로 미룸 — 이 서비스는 추천이 쓰는 조회만 담음.
 */
@Service
public class ReceivedSubsidyService {

	private final ReceivedSubsidyRepository repository;

	public ReceivedSubsidyService(ReceivedSubsidyRepository repository) {
		this.repository = repository;
	}

	/**
	 * 회원의 현재 기수령 지원금 id 목록을 조회함(추천 후보에서 제외할 대상, TC-DEMO-020).
	 * @param memberId 대상 회원
	 * @return 기수령 지원금 id 목록(없으면 빈 리스트)
	 */
	@Transactional(readOnly = true)
	public List<Long> findReceivedSubsidyIds(Long memberId) {
		return repository.findSubsidyIdsByMemberId(memberId);
	}

}
