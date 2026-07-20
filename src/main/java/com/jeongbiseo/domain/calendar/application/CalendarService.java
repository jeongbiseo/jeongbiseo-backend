package com.jeongbiseo.domain.calendar.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.calendar.dto.CalendarDay;
import com.jeongbiseo.domain.calendar.dto.CalendarDayElement;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.favorite.repository.FavoriteRepository;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

@Service
@Transactional(readOnly = true)
public class CalendarService {

	private final FavoriteRepository favoriteRepository;

	private final Clock clock;

	public CalendarService(FavoriteRepository favoriteRepository, Clock clock) {
		this.favoriteRepository = favoriteRepository;
		this.clock = clock;
	}

	/**
	 * 마감 캘린더를 조회함. month 범위(1~12)를 벗어나면 VALID400_0을 던짐.
	 */
	public CalendarResponse getDeadlineCalendar(int year, int month, Long memberId) {
		if (month < 1 || month > 12) {
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		LocalDate today = LocalDate.now(clock);
		YearMonth target = YearMonth.of(year, month);
		LocalDate firstDay = target.atDay(1);
		// 지난 마감 제외와 월 범위 제한을 조회 한 번으로 끝냄(CAL-510).
		LocalDate from = firstDay.isBefore(today) ? today : firstDay;
		LocalDate to = target.atEndOfMonth();
		if (from.isAfter(to)) {
			return new CalendarResponse(year, month, List.of());
		}

		Map<LocalDate, List<CalendarDayElement>> itemsByDeadline = new LinkedHashMap<>();
		for (Favorite favorite : favoriteRepository.findCalendarTargets(memberId, from, to)) {
			SubsidyEntity subsidy = favorite.getSubsidy();
			LocalDate deadline = subsidy.getDeadline();
			long dDay = ChronoUnit.DAYS.between(today, deadline);
			itemsByDeadline.computeIfAbsent(deadline, ignored -> new ArrayList<>())
				.add(new CalendarDayElement(subsidy.getId(), subsidy.getName(), deadline, dDay));
		}
		List<CalendarDay> days = itemsByDeadline.entrySet()
			.stream()
			.map(entry -> new CalendarDay(entry.getKey(), List.copyOf(entry.getValue())))
			.toList();
		return new CalendarResponse(year, month, days);
	}

}
