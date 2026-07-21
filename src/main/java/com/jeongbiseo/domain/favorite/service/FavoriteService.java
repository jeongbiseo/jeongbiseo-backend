package com.jeongbiseo.domain.favorite.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.favorite.repository.FavoriteRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.FavoriteErrorCode;
import com.jeongbiseo.global.apiPayload.code.MemberErrorCode;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 관심 등록·해제와 등록 여부 조회를 담당하는 서비스임.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;

	private final SubsidyRepository subsidyRepository;

	private final MemberRepository memberRepository;

	/**
	 * 관심 등록함. 지원금이 없으면 SUBSIDY404_1, 이미 등록돼 있으면 FAVORITE409_1을 던짐.
	 */
	@Transactional
	public void add(Long memberId, Long subsidyId) {
		SubsidyEntity subsidy = subsidyRepository.findById(subsidyId)
			.orElseThrow(() -> new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
		if (favoriteRepository.existsByMemberIdAndSubsidyId(memberId, subsidyId)) {
			throw new CustomException(FavoriteErrorCode.FAVORITE_ALREADY_EXISTS);
		}
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
		try {
			favoriteRepository.saveAndFlush(Favorite.builder().member(member).subsidy(subsidy).build());
		}
		catch (DataIntegrityViolationException e) {
			// 사전 중복 검사를 동시에 통과한 요청의 유니크 제약 충돌도 동일한 409로 변환함.
			throw new CustomException(FavoriteErrorCode.FAVORITE_ALREADY_EXISTS, e);
		}
	}

	/**
	 * 관심 해제함. 등록돼 있지 않으면 FAVORITE404_1을 던짐.
	 */
	@Transactional
	public void remove(Long memberId, Long subsidyId) {
		Favorite favorite = favoriteRepository.findByMemberIdAndSubsidyId(memberId, subsidyId)
			.orElseThrow(() -> new CustomException(FavoriteErrorCode.FAVORITE_NOT_FOUND));
		favoriteRepository.delete(favorite);
	}

	/**
	 * 상세 조회의 isFavorite 반영용임.
	 */
	@Transactional(readOnly = true)
	public boolean isFavorite(Long memberId, Long subsidyId) {
		return favoriteRepository.existsByMemberIdAndSubsidyId(memberId, subsidyId);
	}

	/**
	 * 회원의 관심 목록을 최근 등록순으로 반환함(API명세서 getFavorites). 없으면 빈 목록임. 아이템은 검색 결과와 동일한
	 * SubsidySearchResult임.
	 */
	@Transactional(readOnly = true)
	public List<SubsidySearchResult> getFavorites(Long memberId) {
		return favoriteRepository.findFavoriteSubsidies(memberId);
	}

}
