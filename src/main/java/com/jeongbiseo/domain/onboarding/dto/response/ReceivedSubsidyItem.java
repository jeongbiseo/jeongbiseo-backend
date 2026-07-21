package com.jeongbiseo.domain.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 기수령 지원금 목록의 한 건임(API명세서 getReceivedSubsidies). 마이페이지 "기존 수령중인 지원금" 관리 화면이 이름으로 목록을
 * 렌더하므로 id와 이름만 담음(마감·금액은 관리 화면 목적 밖이라 제외).
 *
 * @param subsidyId 지원금 id
 * @param name 지원금명
 */
public record ReceivedSubsidyItem(@Schema(description = "지원금 id", example = "101") Long subsidyId,
		@Schema(description = "지원금명", example = "청년 월세 특별지원") String name) {
}
