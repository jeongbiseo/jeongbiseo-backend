package com.jeongbiseo.domain.onboarding.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * 기수령 지원금 전체 교체 요청임(API명세서 setReceivedSubsidies). 빈 배열은 전체 해제를 의미하므로 `@NotEmpty`는 두지 않고
 * `@NotNull`만 검증함(TC-DEMO-021).
 *
 * @param subsidyIds 기수령으로 설정할 지원금 id 전체 목록(교체, 누적 아님)
 */
public record ReceivedSubsidiesRequest(@NotNull(message = "지원금 ID 목록은 필수예요") List<Long> subsidyIds) {
}
