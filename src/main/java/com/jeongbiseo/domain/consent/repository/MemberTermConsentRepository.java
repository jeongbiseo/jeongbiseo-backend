package com.jeongbiseo.domain.consent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.MemberTermConsent;

/**
 * 회원별 약관 동의 저장소임. (member_id, term_type)이 UNIQUE라 회원의 항목당 최신 동의 1건만 존재함.
 */
public interface MemberTermConsentRepository extends JpaRepository<MemberTermConsent, Long> {

	/**
	 * 회원의 항목 동의를 조회함(재동의·필수 충족 판정용). memberId는 member 연관관계의 id로
	 * traverse됨(MemberTermConsent에 memberId 필드 없음).
	 */
	Optional<MemberTermConsent> findByMemberIdAndTermType(Long memberId, TermType termType);

}
