package com.jeongbiseo.domain.calendar.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.jeongbiseo.domain.calendar.application.CalendarService;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.apiPayload.CustomResponse;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	private final Clock clock;

	@GetMapping
	public ResponseEntity<CustomResponse<CalendarResponse>> getDeadlineCalendar(
			@RequestParam(name = "year", required = false) Integer year,
			@RequestParam(name = "month", required = false) Integer month) {
		LocalDate now = LocalDate.now(clock);
		int targetYear = (year != null) ? year : now.getYear();
		int targetMonth = (month != null) ? month : now.getMonthValue();

		Long memberId = 1L;

		CalendarResponse data = calendarService.getDeadlineCalendar(targetYear, targetMonth, memberId);

		// [★수정] success 대신 규격에 맞는 ok 메서드 호출
		return ResponseEntity.ok(CustomResponse.ok(data));
	}

}