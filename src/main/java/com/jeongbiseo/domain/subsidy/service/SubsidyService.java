package com.jeongbiseo.domain.subsidy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 지원금 검색·상세 조회 서비스임(API명세서 13번 searchSubsidies, 15번 getSubsidyDetail). SubsidyReader 포트가
 * 아니라 SubsidyRepository를 직접 써서, 추천 도메인의 읽기 경계(SubsidyReader 3메서드)와 subsidy 자기 도메인의 조회 경로를
 * 분리함(PLAN 08-subsidy-search-detail 1.1).
 */
@Service
public class SubsidyService {

	private final SubsidyRepository subsidyRepository;

	private final Clock clock;

	public SubsidyService(SubsidyRepository subsidyRepository, Clock clock) {
		this.subsidyRepository = subsidyRepository;
		this.clock = clock;
	}

	/**
	 * 키워드·분류로 지원금을 검색함(융자 상품은 항상 제외, keyword·category는 nullable).
	 * @param keyword 지원금명·소관기관 부분 일치 키워드(null이면 무시)
	 * @param category 지원금 분류 필터(null이면 무시)
	 * @param pageable 페이지 요청
	 * @return 검색 결과 페이지
	 */
	@Transactional(readOnly = true)
	public Page<SubsidySearchResult> search(String keyword, SubsidyCategory category, Pageable pageable) {
		return subsidyRepository.search(keyword, category, pageable);
	}

	/**
	 * 지원금 상세를 조회함. active=false와 중복(duplicateOfId) 행도 노출함(기수령 선택 유즈케이스라 과거와 중복 공고가 필요함).
	 * @param subsidyId 조회할 지원금 id
	 * @return 지원금 상세 응답
	 * @throws CustomException 지원금이 존재하지 않으면 SUBSIDY404_1
	 */
	@Transactional(readOnly = true)
	public SubsidyDetailResponse getDetail(Long subsidyId) {
		LocalDate asOf = LocalDate.now(clock);
		SubsidyEntity s = subsidyRepository.findById(subsidyId)
			.orElseThrow(() -> new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
		Integer dDay = (s.getDeadline() == null) ? null : (int) ChronoUnit.DAYS.between(asOf, s.getDeadline());
		// paymentType·category는 enum 그대로 실음. Jackson이 상수명으로 직렬화해 JSON은 동일하고, springdoc이
		// 허용값과
		// 라벨 설명을 스키마에 실어 줌(String으로 평탄화하면 그 정보가 문서에서 사라짐).
		return new SubsidyDetailResponse(s.getId(), s.getName(), s.getAgency(), s.getEligibilityText(), s.getDeadline(),
				dDay, s.getEstimatedAmountMin(), s.getEstimatedAmountMax(), s.getPaymentType(), s.getCategory(),
				s.getDescription(), s.getExternalUrl(), false);
	}

}
