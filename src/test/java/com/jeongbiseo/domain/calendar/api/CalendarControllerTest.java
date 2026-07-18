package com.jeongbiseo.domain.calendar.api;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.calendar.application.CalendarService;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.config.ClockConfig;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CalendarController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). year·month 기본값은 Clock에서 채우고 회원은
 * FixedMemberResolver로 받으므로 두 빈을 import함. 서비스는 목이라 응답 봉투·연월 계약만 고정함.
 */
@WebMvcTest(CalendarController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ FixedMemberResolver.class, ClockConfig.class })
class CalendarControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CalendarService calendarService;

	@Test
	void getDeadlineCalendar_연월_지정_시_200과_해당_연월을_반환한다() throws Exception {
		given(calendarService.getDeadlineCalendar(anyInt(), anyInt(), any()))
			.willReturn(new CalendarResponse(2026, 7, Collections.emptyMap()));

		mockMvc.perform(get("/api/v1/calendar").param("year", "2026").param("month", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.result.year").value(2026))
			.andExpect(jsonPath("$.result.month").value(7));
	}

	@Test
	void getDeadlineCalendar_연월_미지정_시_기본값으로_200을_반환한다() throws Exception {
		given(calendarService.getDeadlineCalendar(anyInt(), anyInt(), any()))
			.willReturn(new CalendarResponse(2026, 7, Collections.emptyMap()));

		mockMvc.perform(get("/api/v1/calendar"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true));
	}

}
