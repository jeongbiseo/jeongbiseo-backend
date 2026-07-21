package com.jeongbiseo.domain.consent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.consent.dto.request.MarketingConsentRequest;
import com.jeongbiseo.domain.consent.dto.response.MarketingConsentResponse;
import com.jeongbiseo.domain.consent.dto.response.TermConsentsResponse;
import com.jeongbiseo.domain.consent.service.TermConsentService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 마이페이지 약관 자원(약관 동의 조회, 마케팅 수신 동의 변경)을 다룸(API명세서 24번 getMyTermConsents·25번
 * updateMarketingConsent). 경로는 members 도메인 공간(/api/v1/members/me/terms)에 두어 "내 자원"의 /me
 * 규칙을 따르고, 컨트롤러는 consent 도메인에 둠. 회원 식별은 FixedMemberResolver 고정 회원임(소셜 인증 전, 결정 7번).
 */
@Tag(name = "Consent", description = "마이페이지 약관 자원(약관 동의 조회, 마케팅 수신 동의 변경)")
@RestController
@RequestMapping("/api/v1/members/me/terms")
public class ConsentController {

	private final TermConsentService termConsentService;

	private final FixedMemberResolver memberResolver;

	public ConsentController(TermConsentService termConsentService, FixedMemberResolver memberResolver) {
		this.termConsentService = termConsentService;
		this.memberResolver = memberResolver;
	}

	// 마이페이지 약관 조회 처리함 (GET /api/v1/members/me/terms, operationId getMyTermConsents)
	// 표시 약관 2종(서비스 이용약관·개인정보 처리방침)의 동의 상태·시각과 마케팅 수신 동의 상태를 반환함. 동의 이력이 없는 회원은 표시 약관이
	// agreed=false, agreedAt=null로 나옴 — 소셜 가입 흐름의 동의 기록 연결 전까지의 계약임.
	@Operation(summary = "마이페이지 약관 조회",
			description = "표시 약관 2종의 동의 여부·동의 시각과 마케팅 수신 동의 상태를 반환함. 동의 이력이 없는 회원은 표시 약관이 미동의(agreed=false)로 나옴.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "약관 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER400_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<TermConsentsResponse> getMyTermConsents() {
		return CustomResponse.ok(termConsentService.getMyTermConsents(memberResolver.resolveMemberId()));
	}

	// 마케팅 수신 동의 변경 처리함 (POST /api/v1/members/me/terms/marketing, operationId
	// updateMarketingConsent)
	// 토글이지만 서버는 현재 값을 뒤집지 않고 요청 바디의 목표 상태(agreed)로 멱등하게 설정함. 상태가 실제로 바뀔 때만 변경 시각을 갱신하고 같은
	// 값
	// 재전송은 시각을 보존함.
	@Operation(summary = "마케팅 수신 동의 변경",
			description = "마케팅 정보 수신 동의를 요청 바디의 목표 상태로 설정함(멱등 set, 서버 플립 아님). 변경 후 상태와 변경 시각을 반환함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "마케팅 동의 변경 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "요청 검증 실패(VALID400_1) 또는 탈퇴된 계정(MEMBER400_1)",
					content = @Content(mediaType = "application/json", examples = { @ExampleObject(name = "VALID400_1",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_1\",\"message\":\"잘못된 DTO 필드입니다.\",\"result\":{\"agreed\":\"동의 여부는 필수예요\"}}"),
							@ExampleObject(name = "MEMBER400_1",
									value = "{\"isSuccess\":false,\"code\":\"MEMBER400_1\",\"message\":\"탈퇴된 계정이에요\",\"result\":null}") })),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "회원 미존재(MEMBER404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "MEMBER404_1",
							value = "{\"isSuccess\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원이 존재하지 않습니다\",\"result\":null}"))) })
	@PostMapping("/marketing")
	public CustomResponse<MarketingConsentResponse> updateMarketingConsent(
			@Valid @RequestBody MarketingConsentRequest request) {
		return CustomResponse
			.ok(termConsentService.updateMarketingConsent(memberResolver.resolveMemberId(), request.agreed()));
	}

}
