package com.jeongbiseo.domain.calendar.application;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CalendarService 계약 테스트임. 즐겨찾기 조회는 아직 더미(빈 결과)라 지금은 빈 상태·검증 경로만 고정함.
 * FavoriteSubsidyRepository 이식 후 실데이터 그룹핑 테스트를 추가하는 것이 팀원 몫(캘린더는 즐겨찾기 런타임 의존, HANDOFF
 * 7-4).
 */
class CalendarServiceTest {

	private final CalendarService calendarService = new CalendarService(Clock.system(ZoneId.of("Asia/Seoul")));

	@Test
	void month가_범위를_벗어나면_VALID400_0을_던진다() {
		assertThatThrownBy(() -> calendarService.getDeadlineCalendar(2026, 0, 1L)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");

		assertThatThrownBy(() -> calendarService.getDeadlineCalendar(2026, 13, 1L)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");
	}

	@Test
	void 관심등록이_없으면_요청한_연월과_빈_days를_반환한다() {
		CalendarResponse response = calendarService.getDeadlineCalendar(2026, 7, 1L);

		assertThat(response.year()).isEqualTo(2026);
		assertThat(response.month()).isEqualTo(7);
		assertThat(response.days()).isEmpty();
	}

}
