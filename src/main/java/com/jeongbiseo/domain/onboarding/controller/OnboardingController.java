package com.jeongbiseo.domain.onboarding.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.onboarding.dto.request.OnboardingRequest;
import com.jeongbiseo.domain.onboarding.dto.response.OnboardingSubmitResponse;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 온보딩 최초 제출을 다룸(API명세서 9번, operationId submitOnboarding). 기수령 지원금
 * 설정(setReceivedSubsidies)은 Subsidy 이식(순위 4) 뒤로 이연됨(결정 D7). 회원 식별은 FixedMemberResolver 고정
 * 회원임(소셜 인증 전, 결정 7번).
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

	private final OnboardingService onboardingService;

	private final FixedMemberResolver memberResolver;

	public OnboardingController(OnboardingService onboardingService, FixedMemberResolver memberResolver) {
		this.onboardingService = onboardingService;
		this.memberResolver = memberResolver;
	}

	// POST /api/v1/onboarding (operationId: submitOnboarding)
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	public CustomResponse<OnboardingSubmitResponse> submitOnboarding(@Valid @RequestBody OnboardingRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		OnboardingProfile saved = onboardingService.submit(memberId, request.name(), request.birthDate(),
				request.sido(), request.sigungu(), request.employmentStatus(), request.incomeBracket(),
				request.householdSize());
		int age = AgeCalculator.calculateAge(saved.getBirthDate());
		return CustomResponse
			.created(new OnboardingSubmitResponse(saved.getId(), saved.getMember().isOnboardingCompleted(), age));
	}

}
