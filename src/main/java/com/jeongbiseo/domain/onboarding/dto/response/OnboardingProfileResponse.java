package com.jeongbiseo.domain.onboarding.dto.response;

import java.time.LocalDate;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;

/**
 * 내 온보딩 정보 조회와 수정 응답임(API명세서 6번 getMyOnboarding과 7번 updateMyOnboarding, 응답 형태가 동일해 하나로
 * 공유함). name은 Member에서, 나머지는 OnboardingProfile에서 옴. age는 저장하지 않고 birthDate로 매 요청 계산함(D6은
 * 응답에 name 포함을 요구함).
 *
 * @param name 이름(실명)
 * @param birthDate 생년월일
 * @param age 만 나이(서버 계산)
 * @param sido 거주지 시 또는 도
 * @param sigungu 거주지 시군구
 * @param employmentStatus 고용상태
 * @param incomeBracket 소득구간(null 허용)
 * @param householdSize 가구원 수(null 허용)
 */
public record OnboardingProfileResponse(String name, LocalDate birthDate, int age, String sido, String sigungu,
		EmploymentStatus employmentStatus, IncomeBracket incomeBracket, Integer householdSize) {

}
