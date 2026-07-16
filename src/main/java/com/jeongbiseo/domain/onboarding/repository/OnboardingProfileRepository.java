package com.jeongbiseo.domain.onboarding.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;

/**
 * 온보딩 프로필 저장소임. member_id가 UNIQUE라 회원당 최대 1건만 존재함(ONB-200). memberId는 member 연관관계의 id로
 * traverse됨(OnboardingProfile에 memberId 필드 없음).
 */
public interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, Long> {

	/** 회원이 이미 온보딩 프로필을 가지고 있는지 확인함(재제출 거부 ONB409_1 판단용). */
	boolean existsByMemberId(Long memberId);

	/** 회원의 온보딩 프로필을 조회함(없으면 빈 Optional). */
	Optional<OnboardingProfile> findByMemberId(Long memberId);

}
