package com.jeongbiseo.domain.onboarding.dto.response;

import java.util.List;

/**
 * 기수령 지원금 전체 교체 응답임(API명세서 setReceivedSubsidies). 교체 후 회원의 현재 기수령 지원금 id 목록을 그대로 돌려줌.
 *
 * @param receivedSubsidyIds 교체 완료된 기수령 지원금 id 목록
 */
public record ReceivedSubsidiesResponse(List<Long> receivedSubsidyIds) {
}
