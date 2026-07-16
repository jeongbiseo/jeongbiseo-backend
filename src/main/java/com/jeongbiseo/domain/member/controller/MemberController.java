package com.jeongbiseo.domain.member.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.onboarding.dto.request.OnboardingRequest;
import com.jeongbiseo.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 회원 자원(내 온보딩 조회·수정)을 다룸(API명세서 6번 getMyOnboarding·7번 updateMyOnboarding). 회원
 * 탈퇴(deleteMember)는 같은 클래스에 DELETE /me로 추가 예정임(W4). 회원 식별은 FixedMemberResolver 고정 회원임(소셜
 * 인증 전, 결정 7번).
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

	private final OnboardingService onboardingService;

	private final FixedMemberResolver memberResolver;

	public MemberController(OnboardingService onboardingService, FixedMemberResolver memberResolver) {
		this.onboardingService = onboardingService;
		this.memberResolver = memberResolver;
	}

	// GET /api/v1/members/me/onboarding (operationId: getMyOnboarding)
	@GetMapping("/me/onboarding")
	public CustomResponse<OnboardingProfileResponse> getMyOnboarding() {
		OnboardingProfile profile = onboardingService.getMyOnboarding(memberResolver.resolveMemberId());
		return CustomResponse.ok(toResponse(profile));
	}

	// PUT /api/v1/members/me/onboarding (operationId: updateMyOnboarding)
	@PutMapping("/me/onboarding")
	public CustomResponse<OnboardingProfileResponse> updateMyOnboarding(@Valid @RequestBody OnboardingRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		OnboardingProfile updated = onboardingService.update(memberId, request.name(), request.birthDate(),
				request.sido(), request.sigungu(), request.employmentStatus(), request.incomeBracket(),
				request.householdSize());
		return CustomResponse.ok(toResponse(updated));
	}

	private static OnboardingProfileResponse toResponse(OnboardingProfile profile) {
		int age = AgeCalculator.calculateAge(profile.getBirthDate());
		return new OnboardingProfileResponse(profile.getMember().getName(), profile.getBirthDate(), age,
				profile.getSido(), profile.getSigungu(), profile.getEmploymentStatus(), profile.getIncomeBracket(),
				profile.getHouseholdSize());
	}

}
