package com.jeongbiseo.domain.onboarding.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;

/**
 * 온보딩 제출과 수정 요청 본문임(API명세서 9번 submitOnboarding과 7번 updateMyOnboarding, 두 요청 필드가 동일해 하나로
 * 공유함). 이름은 실명 2자에서 12자 필수이며 소셜 프로필명이 아니라 사용자 입력이 정본임(v1.4, D6). 소득구간과 가구원 수는 선택이며 생략 시
 * null로 처리함.
 *
 * @param name 이름(실명, 필수, 2자에서 12자)
 * @param birthDate 생년월일(필수, 과거 날짜)
 * @param sido 거주지 시 또는 도(필수)
 * @param sigungu 거주지 시군구(필수)
 * @param employmentStatus 고용상태(필수)
 * @param incomeBracket 소득구간(선택)
 * @param householdSize 가구원 수(선택, 1에서 10)
 */
public record OnboardingRequest(
		@NotBlank(message = "이름은 필수예요") @Size(min = 2, max = 12, message = "이름은 2자에서 12자여야 해요") String name,
		@NotNull(message = "생년월일은 필수예요") @Past(message = "생년월일은 과거 날짜여야 해요") LocalDate birthDate,
		@NotBlank(message = "거주지는 필수예요") String sido, @NotBlank(message = "시군구는 필수예요") String sigungu,
		@NotNull(message = "고용상태는 필수예요") EmploymentStatus employmentStatus, IncomeBracket incomeBracket,
		@Min(value = 1, message = "가구원 수는 1명 이상이어야 해요") @Max(value = 10,
				message = "가구원 수는 10명 이하여야 해요") Integer householdSize) {

}
