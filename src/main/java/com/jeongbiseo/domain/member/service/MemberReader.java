package com.jeongbiseo.domain.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.global.apiPayload.code.MemberErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 활성 회원(미탈퇴)을 조회하는 공통 헬퍼임. 온보딩·탈퇴 등 회원 스코프 기능이 요청 회원을 꺼낼 때 이 한 곳을 거쳐, 회원
 * 미존재(MEMBER404_1)와 탈퇴 계정(MEMBER400_1) 판정을 일관되게 함(데이터모델 (다) "조회 경로가 member 비탈퇴 검증을 통과"
 * 전제의 구현 지점). 소셜 인증 도입 전에는 FixedMemberResolver가 넘기는 고정 id로 부름.
 */
@Service
public class MemberReader {

	private final MemberRepository memberRepository;

	public MemberReader(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	/**
	 * 활성 회원을 조회함. 회원이 없으면 MEMBER404_1, 탈퇴한 회원이면 MEMBER400_1을 던짐.
	 * @param memberId 대상 회원 id
	 * @return 활성 회원
	 */
	@Transactional(readOnly = true)
	public Member getActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
		if (member.isDeleted()) {
			throw new CustomException(MemberErrorCode.MEMBER_DELETED);
		}
		return member;
	}

}
