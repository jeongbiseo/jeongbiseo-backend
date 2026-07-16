package com.jeongbiseo.domain.onboarding.dto.response;

/**
 * 온보딩 최초 제출 응답임(API명세서 9번, operationId submitOnboarding).
 *
 * @param profileId 생성된 온보딩 프로필 ID
 * @param onboardingCompleted 온보딩 완료 여부(항상 true)
 * @param age 만 나이(서버 계산)
 */
public record OnboardingSubmitResponse(Long profileId, boolean onboardingCompleted, int age) {

}
