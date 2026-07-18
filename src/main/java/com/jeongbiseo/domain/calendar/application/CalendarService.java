package com.jeongbiseo.domain.calendar.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import java.util.Collections;

@Service
@Transactional(readOnly = true)
public class CalendarService {

	/**
	 * 마감 캘린더를 조회함. month 범위(1~12)를 벗어나면 VALID400_0을 던짐.
	 */
	public CalendarResponse getDeadlineCalendar(int year, int month, Long memberId) {
		if (month < 1 || month > 12) {
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		// ponytail: 즐겨찾기 도메인이 아직 이식되지 않아 항상 빈 캘린더를 반환함(CAL-빈상태).
		// FavoriteSubsidyRepository 이식 후
		// 마감일 필터·d-day 계산·날짜별 그룹핑을 실제 조회로 교체함. 그전까지 더미 엔티티로 흉내 내면 SpotBugs UWF에 걸리고 죽은
		// 코드가 남으므로 두지 않음.
		return new CalendarResponse(year, month, Collections.emptyMap());
	}

}
