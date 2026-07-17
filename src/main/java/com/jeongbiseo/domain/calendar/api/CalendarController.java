package com.jeongbiseo.domain.calendar.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private final Clock clock; // 현재 연월 기본값 산출용 주입

    /**
     * 마감 캘린더 조회 API: GET /api/v1/calendar
     */
    @GetMapping
    public ResponseEntity<CustomResponse<CalendarResponse>> getDeadlineCalendar(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month
    ) {
        // 파라미터가 생략되어 들어오면 현재 날짜 기준 연월을 기본값으로 사용
        LocalDate current = LocalDate.now(clock);
        int targetYear = (year != null) ? year : current.getYear();
        int targetMonth = (month != null) ? month : current.getMonthValue();

        // 아직 JWT 완결 전이므로 개발용 고정 회원 식별자(1L) 주입 처리
        Long activeMemberId = 1L;

        CalendarResponse data = calendarService.getDeadlineCalendar(targetYear, targetMonth, activeMemberId);

        return ResponseEntity.ok(CustomResponse.success(data));
    }
}