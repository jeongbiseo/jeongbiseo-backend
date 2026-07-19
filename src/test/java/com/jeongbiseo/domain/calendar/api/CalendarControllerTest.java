package com.jeongbiseo.domain.calendar.api;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.calendar.application.CalendarService;
import com.jeongbiseo.domain.calendar.dto.CalendarDay;
import com.jeongbiseo.domain.calendar.dto.CalendarDayElement;
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
			.willReturn(new CalendarResponse(2026, 7, List.of()));

		mockMvc.perform(get("/api/v1/calendar").param("year", "2026").param("month", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.result.year").value(2026))
			.andExpect(jsonPath("$.result.month").value(7));
	}

	@Test
	void getDeadlineCalendar_연월_미지정_시_기본값으로_200을_반환한다() throws Exception {
		given(calendarService.getDeadlineCalendar(anyInt(), anyInt(), any()))
			.willReturn(new CalendarResponse(2026, 7, List.of()));

		mockMvc.perform(get("/api/v1/calendar"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true));
	}

	/**
	 * days를 날짜 키 객체(Map)로 되돌리는 회귀를 막음. 명세서 18번은 days를 배열로, 원소를 date·items로, 항목을
	 * subsidyId·name·deadline·dDay로 계약함. 이 단언이 없던 탓에 Map 직렬화(빈 값이 [] 아닌 {})가 오래 방치됐음.
	 */
	@Test
	void getDeadlineCalendar_days는_배열이고_항목은_명세서_필드명을_쓴다() throws Exception {
		CalendarDayElement item = new CalendarDayElement(101L, "청년월세 특별지원", LocalDate.of(2026, 8, 31), 53L);
		given(calendarService.getDeadlineCalendar(anyInt(), anyInt(), any())).willReturn(
				new CalendarResponse(2026, 8, List.of(new CalendarDay(LocalDate.of(2026, 8, 31), List.of(item)))));

		mockMvc.perform(get("/api/v1/calendar").param("year", "2026").param("month", "8"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.days").isArray())
			.andExpect(jsonPath("$.result.days[0].date").value("2026-08-31"))
			.andExpect(jsonPath("$.result.days[0].items").isArray())
			.andExpect(jsonPath("$.result.days[0].items[0].subsidyId").value(101))
			.andExpect(jsonPath("$.result.days[0].items[0].name").value("청년월세 특별지원"))
			.andExpect(jsonPath("$.result.days[0].items[0].deadline").value("2026-08-31"))
			.andExpect(jsonPath("$.result.days[0].items[0].dDay").value(53));
	}

	@Test
	void getDeadlineCalendar_관심등록이_없으면_days가_빈_배열이다() throws Exception {
		given(calendarService.getDeadlineCalendar(anyInt(), anyInt(), any()))
			.willReturn(new CalendarResponse(2026, 8, List.of()));

		mockMvc.perform(get("/api/v1/calendar").param("year", "2026").param("month", "8"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.days").isArray())
			.andExpect(jsonPath("$.result.days").isEmpty());
	}

}
