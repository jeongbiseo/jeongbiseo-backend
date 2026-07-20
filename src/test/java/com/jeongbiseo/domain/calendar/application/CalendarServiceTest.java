package com.jeongbiseo.domain.calendar.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.favorite.repository.FavoriteRepository;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * CalendarService 단위 테스트임. 조회 기간, 빈 상태, 날짜별 그룹과 D-day 계산을 고정함.
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);

	private static final Long MEMBER_ID = 1L;

	@Mock
	private FavoriteRepository favoriteRepository;

	private CalendarService calendarService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		calendarService = new CalendarService(favoriteRepository, clock);
	}

	@Test
	void month가_범위를_벗어나면_VALID400_0을_던진다() {
		assertThatThrownBy(() -> calendarService.getDeadlineCalendar(2026, 0, MEMBER_ID))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");

		assertThatThrownBy(() -> calendarService.getDeadlineCalendar(2026, 13, MEMBER_ID))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("VALID400_0");
	}

	@Test
	void 관심등록이_없으면_요청한_연월과_빈_days를_반환한다() {
		given(favoriteRepository.findCalendarTargets(MEMBER_ID, TODAY, LocalDate.of(2026, 7, 31)))
			.willReturn(List.of());

		CalendarResponse response = calendarService.getDeadlineCalendar(2026, 7, MEMBER_ID);

		assertThat(response.year()).isEqualTo(2026);
		assertThat(response.month()).isEqualTo(7);
		assertThat(response.days()).isEmpty();
	}

	@Test
	void 현재월은_오늘부터_월말까지만_조회해_지난마감을_제외한다() {
		given(favoriteRepository.findCalendarTargets(MEMBER_ID, TODAY, LocalDate.of(2026, 7, 31)))
			.willReturn(List.of());

		calendarService.getDeadlineCalendar(2026, 7, MEMBER_ID);

		verify(favoriteRepository).findCalendarTargets(MEMBER_ID, TODAY, LocalDate.of(2026, 7, 31));
	}

	@Test
	void 미래월은_월초부터_월말까지_조회한다() {
		given(favoriteRepository.findCalendarTargets(MEMBER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
			.willReturn(List.of());

		calendarService.getDeadlineCalendar(2026, 8, MEMBER_ID);

		verify(favoriteRepository).findCalendarTargets(MEMBER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
	}

	@Test
	void 과거월은_조회하지않고_빈_days를_반환한다() {
		CalendarResponse response = calendarService.getDeadlineCalendar(2026, 6, MEMBER_ID);

		assertThat(response.days()).isEmpty();
		verifyNoInteractions(favoriteRepository);
	}

	@Test
	void 마감일오름차순_조회결과를_날짜별로_그룹핑하고_dDay를_계산한다() {
		LocalDate firstDeadline = LocalDate.of(2026, 8, 10);
		LocalDate secondDeadline = LocalDate.of(2026, 8, 31);
		given(favoriteRepository.findCalendarTargets(MEMBER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
			.willReturn(List.of(favorite(10L, "첫째", firstDeadline), favorite(11L, "둘째", firstDeadline),
					favorite(12L, "셋째", secondDeadline)));

		CalendarResponse response = calendarService.getDeadlineCalendar(2026, 8, MEMBER_ID);

		assertThat(response.days()).extracting(day -> day.date()).containsExactly(firstDeadline, secondDeadline);
		assertThat(response.days().get(0).items()).extracting(item -> item.name()).containsExactly("첫째", "둘째");
		assertThat(response.days().get(0).items()).extracting(item -> item.dDay()).containsOnly(21L);
		assertThat(response.days().get(1).items().get(0).dDay()).isEqualTo(42L);
	}

	private static Favorite favorite(Long subsidyId, String name, LocalDate deadline) {
		SubsidyEntity subsidy = SubsidyEntity.builder().id(subsidyId).name(name).deadline(deadline).build();
		return Favorite.builder().subsidy(subsidy).build();
	}

}
