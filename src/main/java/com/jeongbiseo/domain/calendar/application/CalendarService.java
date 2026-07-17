package com.jeongbiseo.domain.calendar.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jeongbiseo.domain.calendar.dto.CalendarDayElement;
import com.jeongbiseo.domain.calendar.dto.CalendarResponse;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final Clock clock; // 글로벌 ClockConfig 주입 구조 적용

    /**
     * CAL-정상 / CAL-빈상태 시나리오 처리 비즈니스 로직
     */
    public CalendarResponse getDeadlineCalendar(int year, int month, Long memberId) {
        // [VALID400_0] month가 1~12 범위를 벗어나면 예외 처리
        if (month < 1 || month > 12) {
            throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
        }

        LocalDate today = LocalDate.now(clock);

        // TODO: 향후 FavoriteSubsidyRepository 구현 완료 시 실제 DB 조회 메서드로 교체 연동
        List<DummySubsidyEntity> userFavorites = new ArrayList<>();

        // 1. 명세서에 명시된 필터링 기준 적용 (마감일 보유 유형, 오늘 이후 마감, 선택 연월 일치)
        List<CalendarDayElement> filteredList = userFavorites.stream()
                .filter(sub -> sub.getDeadlineKind() == DeadlineKind.DATE_RANGE) // 마감일 있는 유형만 대상
                .filter(sub -> !sub.getDeadlineDate().isBefore(today))         // 지난 마감은 쿼리 단계에서 제외
                .filter(sub -> sub.getDeadlineDate().getYear() == year && sub.getDeadlineDate().getMonthValue() == month)
                .map(sub -> {
                    long dDay = ChronoUnit.DAYS.between(today, sub.getDeadlineDate()); // d-day 계산 (0 이상)
                    return new CalendarDayElement(sub.getId(), sub.getTitle(), sub.getDeadlineDate(), dDay);
                })
                .toList();

        // [CAL-빈상태] 관심 등록이 없거나 캘린더 대상이 0건이면 빈 데이터 구조로 정상 반환
        if (filteredList.isEmpty()) {
            return new CalendarResponse(year, month, Collections.emptyMap());
        }

        // 2. 결과를 마감일 오름차순으로 정렬한 후 날짜별 그룹핑 (LinkedHashMap으로 순서 보존)
        Map<LocalDate, List<CalendarDayElement>> groupedDays = filteredList.stream()
                .sorted(Comparator.comparing(CalendarDayElement::deadlineDate))
                .collect(Collectors.groupingBy(
                        CalendarDayElement::deadlineDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return new CalendarResponse(year, month, groupedDays);
    }

    // --- 컴파일 완결성 유지를 위한 가상 엔티티 스펙 ---
    private enum DeadlineKind { DATE_RANGE, ALWAYS }
    @lombok.Getter
    private static class DummySubsidyEntity {
        private Long id;
        private String title;
        private DeadlineKind deadlineKind;
        private LocalDate deadlineDate;
    }
}