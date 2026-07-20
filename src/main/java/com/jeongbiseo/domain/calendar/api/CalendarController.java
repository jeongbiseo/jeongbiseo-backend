package com.jeongbiseo.domain.calendar.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.jeongbiseo.domain.calendar.application.CalendarService;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.security.FixedMemberResolver;

import java.time.Clock;
import java.time.LocalDate;

@Tag(name = "Calendar", description = "관심 지원금 마감 캘린더")
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	private final Clock clock;

	private final FixedMemberResolver memberResolver;

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서 실제 발생함(다른 컨트롤러와 동일 관용).
	@Operation(summary = "마감 캘린더 조회",
			description = "관심 등록한 지원금의 마감일을 월 단위로 모아 D-day와 함께 반환함. year·month를 생략하면 서버 기준 현재 연월을 씀.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "마감 캘린더 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "month가 1에서 12 범위 밖이거나 year·month 타입 불일치(VALID400_0)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<CalendarResponse> getDeadlineCalendar(
			@Parameter(description = "조회할 연도(선택). 생략하면 서버 기준 현재 연도", example = "2026") @RequestParam(name = "year",
					required = false) Integer year,
			@Parameter(description = "조회할 월(선택, 1에서 12). 생략하면 서버 기준 현재 월", example = "7") @RequestParam(name = "month",
					required = false) Integer month) {
		LocalDate now = LocalDate.now(clock);
		int targetYear = (year != null) ? year : now.getYear();
		int targetMonth = (month != null) ? month : now.getMonthValue();

		Long memberId = memberResolver.resolveMemberId();

		CalendarResponse data = calendarService.getDeadlineCalendar(targetYear, targetMonth, memberId);

		return CustomResponse.ok(data);
	}

}
