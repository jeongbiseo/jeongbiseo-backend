package com.jeongbiseo.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.member.dto.request.DeleteMemberRequest;
import com.jeongbiseo.domain.member.dto.response.MemberProfileResponse;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.service.MemberService;
import com.jeongbiseo.domain.onboarding.dto.request.OnboardingRequest;
import com.jeongbiseo.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 회원 자원(내 회원 정보 조회, 내 온보딩 조회·수정, 회원 탈퇴)을 다룸(API명세서 21번 getMe·6번 getMyOnboarding·7번
 * updateMyOnboarding·8번 deleteMember). 회원 식별은 FixedMemberResolver 고정 회원임(소셜 인증 전, 결정 7번).
 */
@Tag(name = "Member", description = "회원 자원(내 회원 정보 조회, 내 온보딩 조회·수정, 회원 탈퇴)")
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

	// 내 회원 정보 조회 처리함 (GET /api/v1/members/me, operationId getMe)
	// 앱 시작·새로고침 직후 프론트가 로그인 상태와 표시용 회원 정보를 복구하는 용도임. onboardingCompleted는 Member 플래그를 그대로
	// 실어 온보딩 전 회원도 200으로 반환함 — getMyOnboarding은 온보딩 프로필 레코드가 없으면 ONB404_1을 던져 이 용도로 쓸 수
	// 없음(프론트 요청, 2026-07-19).
	@Operation(summary = "내 회원 정보 조회",
			description = "로그인한 회원의 id·이름·이메일·온보딩 완료 여부를 반환함. 온보딩 전 회원도 200으로 반환함. "
					+ "name은 소셜 첫 로그인 때 프로필에서 받아 저장한 표시용 이름이라 온보딩 전에도 채워져 있으며, 소셜이 동의항목을 주지 않은 경우에만 null임.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "회원 정보 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER400_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"))) })
	@GetMapping("/me")
	public CustomResponse<MemberProfileResponse> getMe() {
		Member member = memberService.getMe(memberResolver.resolveMemberId());
		return CustomResponse.ok(MemberProfileResponse.from(member));
	}

	// 내 온보딩 정보 조회 처리함 (GET /api/v1/members/me/onboarding, operationId getMyOnboarding)
	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서
	// 실제 발생하며, 프론트가 이 계약으로 구현 중이라 명세대로 문서화함(명세서 각주 COMMON401 정합). MEMBER400_1(탈퇴 계정)은
	// memberReader.getActiveMember가 실제로 던지나 명세서 FAIL 목록에는 없어 코드 실측 기준으로 추가 문서화함(불일치
	// 표 참조).
	@Operation(summary = "내 온보딩 조회", description = "회원의 온보딩 프로필을 조회함. 온보딩을 완료하지 않았으면 404로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "온보딩 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER400_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1) 또는 온보딩 미완료(ONB404_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"),
							@ExampleObject(name = "ONB404_1",
									value = "{\"isSuccess\":false,\"code\":\"ONB404_1\",\"message\":\"온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요\",\"result\":null}") })) })
	@GetMapping("/me/onboarding")
	public CustomResponse<OnboardingProfileResponse> getMyOnboarding() {
		OnboardingProfile profile = onboardingService.getMyOnboarding(memberResolver.resolveMemberId());
		return CustomResponse.ok(toResponse(profile));
	}

	// 내 온보딩 정보 수정 처리함 (PUT /api/v1/members/me/onboarding, operationId updateMyOnboarding)
	@Operation(summary = "내 온보딩 수정", description = "온보딩 정보를 전체 교체함(PUT 의미론, 생략 필드는 null로 교체). 교체할 프로필이 없으면 404로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "온보딩 수정 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "요청 검증 실패(VALID400_1) 또는 탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_1",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"birthDate\":\"생년월일은 필수예요\"}}"),
							@ExampleObject(name = "MEMBER400_1",
									value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}") })),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1) 또는 온보딩 미완료(ONB404_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"),
							@ExampleObject(name = "ONB404_1",
									value = "{\"isSuccess\":false,\"code\":\"ONB404_1\",\"message\":\"온보딩 정보가 없어요, 온보딩을 먼저 진행해주세요\",\"result\":null}") })) })
	@PutMapping("/me/onboarding")
	public CustomResponse<OnboardingProfileResponse> updateMyOnboarding(@Valid @RequestBody OnboardingRequest request) {
		Long memberId = memberResolver.resolveMemberId();
		OnboardingProfile updated = onboardingService.update(memberId, request.birthDate(), request.sido(),
				request.sigungu(), request.employmentStatus(), request.incomeBracket(), request.householdSize());
		return CustomResponse.ok(toResponse(updated));
	}

	// 회원 탈퇴 처리함 (DELETE /api/v1/members/me, operationId deleteMember)
	@Operation(summary = "회원 탈퇴", description = "회원을 soft delete로 탈퇴 처리함. 탈퇴 사유는 선택이며 본문 없이 호출해도 됨.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴 사유 200자 초과(VALID400_1) 또는 이미 탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_1",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"reason\":\"탈퇴 사유는 200자 이하여야 해요\"}}"),
							@ExampleObject(name = "MEMBER400_1",
									value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}") })),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"))) })
	@DeleteMapping("/me")
	public CustomResponse<String> deleteMember(@Valid @RequestBody(required = false) DeleteMemberRequest request) {
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
