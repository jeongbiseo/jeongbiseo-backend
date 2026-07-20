package com.jeongbiseo.domain.onboarding.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Onboarding", description = "온보딩 최초 제출과 기수령 지원금 설정")
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

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서
	// 실제 발생하며, 프론트가 이 계약으로 구현 중이라 명세대로 문서화함(명세서 각주 COMMON401 정합).
	@Operation(summary = "온보딩 최초 제출",
			description = "이름·생년월일·거주지·고용상태 등을 받아 온보딩을 최초 제출함. 이미 완료한 회원이면 409로 거절함. "
					+ "온보딩 화면의 기수령 지원금 선택은 이 요청에 포함되지 않고 PUT /api/v1/onboarding/received-subsidies로 따로 보냄. "
					+ "온보딩 수정은 PUT /api/v1/members/me/onboarding이며 이 경로는 최초 제출 전용임.")
	@ApiResponses({ @ApiResponse(responseCode = "201", description = "온보딩 제출 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "요청 검증 실패(VALID400_1) 또는 탈퇴 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_1",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"birthDate\":\"생년월일은 필수예요\"}}"),
							@ExampleObject(name = "MEMBER400_1",
									value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}") })),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "409", description = "이미 온보딩 완료(ONB409_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "ONB409_1",
							value = "{\"isSuccess\":false,\"code\":\"ONB409_1\",\"message\":\"이미 온보딩을 완료했어요\",\"result\":null}"))) })
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

	@Operation(summary = "기수령 지원금 설정",
			description = "회원의 기수령 지원금 목록을 요청 전체로 교체함(누적 아님, 빈 배열은 전체 해제). 존재하지 않는 지원금 id가 있으면 404로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "기수령 지원금 교체 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "요청 검증 실패(VALID400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_1",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"subsidyIds\":\"지원금 ID 목록은 필수예요\"}}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "존재하지 않는 지원금 id 포함(SUBSIDY404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "SUBSIDY404_1",
							value = "{\"isSuccess\":false,\"code\":\"SUBSIDY404_1\",\"message\":\"해당 지원금 정보를 찾을 수 없어요\",\"result\":null}"))) })
	@PutMapping("/received-subsidies")
	public CustomResponse<ReceivedSubsidiesResponse> setReceivedSubsidies(
			@Valid @RequestBody ReceivedSubsidiesRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		return CustomResponse
			.ok(new ReceivedSubsidiesResponse(receivedSubsidyService.replaceAll(memberId, request.subsidyIds())));
	}

}
