package com.jeongbiseo.domain.onboarding.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.onboarding.dto.request.OnboardingRequest;
import com.jeongbiseo.domain.onboarding.dto.request.ReceivedSubsidiesRequest;
import com.jeongbiseo.domain.onboarding.dto.response.OnboardingSubmitResponse;
import com.jeongbiseo.domain.onboarding.dto.response.ReceivedSubsidiesResponse;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.domain.onboarding.service.ReceivedSubsidyService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 온보딩 최초 제출과 기수령 지원금 설정을 다룸(API명세서 9번 submitOnboarding, setReceivedSubsidies). 회원 식별은
 * FixedMemberResolver 고정 회원임(소셜 인증 전, 결정 7번).
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

	private final OnboardingService onboardingService;

	private final ReceivedSubsidyService receivedSubsidyService;

	private final FixedMemberResolver memberResolver;

	public OnboardingController(OnboardingService onboardingService, ReceivedSubsidyService receivedSubsidyService,
			FixedMemberResolver memberResolver) {
		this.onboardingService = onboardingService;
		this.receivedSubsidyService = receivedSubsidyService;
		this.memberResolver = memberResolver;
	}

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

	@PutMapping("/received-subsidies")
	public CustomResponse<ReceivedSubsidiesResponse> setReceivedSubsidies(
			@Valid @RequestBody ReceivedSubsidiesRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		return CustomResponse
			.ok(new ReceivedSubsidiesResponse(receivedSubsidyService.replaceAll(memberId, request.subsidyIds())));
	}

}
