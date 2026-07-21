package com.jeongbiseo.domain.onboarding.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 기수령 지원금 목록 조회 응답임(API명세서 getReceivedSubsidies). totalCount는 마이페이지 "기존수령 N건" 카운트용임.
 *
 * @param content 기수령 지원금 목록(지원금 id와 이름)
 * @param totalCount 기수령 지원금 총 개수
 */
public record ReceivedSubsidyListResponse(List<ReceivedSubsidyItem> content,
		@Schema(description = "기수령 지원금 총 개수", example = "3") int totalCount) {

	public static ReceivedSubsidyListResponse from(List<ReceivedSubsidyItem> content) {
		return new ReceivedSubsidyListResponse(content, content.size());
	}

}
