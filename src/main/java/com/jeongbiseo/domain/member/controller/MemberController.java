package com.jeongbiseo.domain.member.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.member.dto.request.DeleteMemberRequest;
import com.jeongbiseo.domain.member.service.MemberService;
import com.jeongbiseo.domain.onboarding.dto.request.OnboardingRequest;
import com.jeongbiseo.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 회원 자원(내 온보딩 조회·수정, 회원 탈퇴)을 다룸(API명세서 6번 getMyOnboarding·7번
 * updateMyOnboarding·deleteMember). 회원 식별은 FixedMemberResolver 고정 회원임(소셜 인증 전, 결정 7번).
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

	private final OnboardingService onboardingService;

	private final MemberService memberService;

	private final FixedMemberResolver memberResolver;

	public MemberController(OnboardingService onboardingService, MemberService memberService,
			FixedMemberResolver memberResolver) {
		this.onboardingService = onboardingService;
		this.memberService = memberService;
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

	// DELETE /api/v1/members/me (operationId: deleteMember)
	@DeleteMapping("/me")
	public CustomResponse<String> deleteMember(@RequestBody(required = false) DeleteMemberRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		String reason = (request == null) ? null : request.reason();
		memberService.delete(memberId, reason);
		return CustomResponse.ok("회원 탈퇴 성공");
	}

	private static OnboardingProfileResponse toResponse(OnboardingProfile profile) {
		int age = AgeCalculator.calculateAge(profile.getBirthDate());
		return new OnboardingProfileResponse(profile.getMember().getName(), profile.getBirthDate(), age,
				profile.getSido(), profile.getSigungu(), profile.getEmploymentStatus(), profile.getIncomeBracket(),
				profile.getHouseholdSize());
	}

}
