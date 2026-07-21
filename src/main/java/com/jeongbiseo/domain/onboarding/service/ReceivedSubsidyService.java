package com.jeongbiseo.domain.onboarding.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.onboarding.dto.response.ReceivedSubsidyItem;
import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
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
	 * 회원이 저장한 기수령 지원금 목록(id와 이름)을 조회함(API명세서 getReceivedSubsidies). 저장된 항목이 없으면 빈 목록임.
	 * 기수령은 온보딩 완료 여부와 독립적으로 저장되는 사용자 입력이라 완료 여부를 조회 조건으로 두지 않음(저장 경로인
	 * setReceivedSubsidies도 완료 여부를 검사하지 않아 저장·조회 계약이 대칭임). ReceivedSubsidy는 연관관계 없이
	 * subsidyId만 갖고 있어 이름은 SubsidyRepository에서 별도로 읽음(2쿼리, N+1 아님). findAllById는 입력 순서를
	 * 보장하지 않으므로 id로 인덱싱한 뒤 원래 순서대로 재구성하고, 참조가 사라진 id는 방어적으로 제외함.
	 * @param memberId 대상 회원
	 * @return 기수령 지원금 목록(없으면 빈 목록)
	 */
	@Transactional(readOnly = true)
	public List<ReceivedSubsidyItem> findReceivedSubsidies(Long memberId) {
		List<Long> ids = repository.findSubsidyIdsByMemberId(memberId);
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, String> nameById = new HashMap<>();
		for (SubsidyEntity entity : subsidyRepository.findAllById(ids)) {
			nameById.put(entity.getId(), entity.getName());
		}
		return ids.stream()
			.filter(nameById::containsKey)
			.map(id -> new ReceivedSubsidyItem(id, nameById.get(id)))
			.toList();
	}

	/**
	 * 회원의 기수령 지원금 목록을 요청 전체로 교체함(누적 아님). 존재하지 않는 subsidyId가 하나라도 있으면 SUBSIDY404_1을 던짐. 빈
	 * 배열은 전체 해제로 처리함(TC-DEMO-021).
	 * @param memberId 대상 회원
	 * @param subsidyIds 교체할 지원금 id 전체 목록
	 * @return 교체 완료된 기수령 지원금 id 목록
	 * @throws CustomException 존재하지 않는 subsidyId가 포함되면 SUBSIDY404_1
	 */
	// ponytail: 회원 단위 잠금 없음. 고정 회원 1명 데모라 동시 PUT이 없고,
	// 실사용 동시성이 생기면 회원 행 잠금 또는 직렬화로 올릴 것(백로그).
	@Transactional
	public List<Long> replaceAll(Long memberId, List<Long> subsidyIds) {
		List<Long> distinct = subsidyIds.stream().distinct().toList();
		long found = subsidyRepository.countByIdIn(distinct);
		if (found < distinct.size()) { // 존재하지 않는 id 포함
			throw new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND);
		}
		repository.deleteByMemberId(memberId); // 벌크 삭제(즉시 실행)
		// 중복 id 요청 시 uk_received_subsidy_member_subsidy 위반을 막기 위해 distinct만 저장함.
		List<ReceivedSubsidy> entities = distinct.stream()
			.map(id -> ReceivedSubsidy.builder().memberId(memberId).subsidyId(id).build())
			.toList();
		repository.saveAll(entities);
		return repository.findSubsidyIdsByMemberId(memberId);
	}

}
