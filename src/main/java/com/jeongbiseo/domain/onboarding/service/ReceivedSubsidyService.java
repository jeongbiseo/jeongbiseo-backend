package com.jeongbiseo.domain.onboarding.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 기수령 지원금 목록을 조회·교체하는 도메인 서비스임(DISCUSS.md 3.2 소형 애그리게이트, ONB-230 추천 1차 제외 필터로 씀).
 * replaceAll은 setReceivedSubsidies 엔드포인트(우선순위 4)가 호출함.
 */
@Service
public class ReceivedSubsidyService {

	private final ReceivedSubsidyRepository repository;

	private final SubsidyRepository subsidyRepository;

	public ReceivedSubsidyService(ReceivedSubsidyRepository repository, SubsidyRepository subsidyRepository) {
		this.repository = repository;
		this.subsidyRepository = subsidyRepository;
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

	/**
	 * 회원의 기수령 지원금 목록을 요청 전체로 교체함(누적 아님). 존재하지 않는 subsidyId가 하나라도 있으면 SUBSIDY404_1을 던짐. 빈
	 * 배열은 전체 해제로 처리함(TC-DEMO-021).
	 * @param memberId 대상 회원
	 * @param subsidyIds 교체할 지원금 id 전체 목록
	 * @return 교체 완료된 기수령 지원금 id 목록
	 * @throws CustomException 존재하지 않는 subsidyId가 포함되면 SUBSIDY404_1
	 */
	@Transactional
	public List<Long> replaceAll(Long memberId, List<Long> subsidyIds) {
		List<Long> distinct = subsidyIds.stream().distinct().toList();
		long found = subsidyRepository.countByIdIn(distinct);
		if (found < distinct.size()) { // 존재하지 않는 id 포함
			throw new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND);
		}
		repository.deleteByMemberId(memberId); // 벌크 삭제(즉시 실행)
		List<ReceivedSubsidy> entities = distinct.stream() // distinct로 저장(H3) — 중복 id
															// UNIQUE 위반 방지
			.map(id -> ReceivedSubsidy.builder().memberId(memberId).subsidyId(id).build())
			.toList();
		repository.saveAll(entities);
		return repository.findSubsidyIdsByMemberId(memberId);
	}

}
