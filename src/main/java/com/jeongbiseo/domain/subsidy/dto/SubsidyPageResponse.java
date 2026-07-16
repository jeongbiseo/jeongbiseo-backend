package com.jeongbiseo.domain.subsidy.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 지원금 검색 페이지 응답임(API명세서 13번 searchSubsidies). Page를 그대로 노출하지 않고 필요한 필드만 평평하게 담아 응답 계약을
 * 명시적으로 고정함.
 *
 * @param content 이 페이지의 검색 결과 목록
 * @param page 현재 페이지 번호(0-base)
 * @param size 페이지 크기
 * @param totalElements 전체 결과 수
 * @param totalPages 전체 페이지 수
 * @param last 마지막 페이지 여부
 */
public record SubsidyPageResponse(List<SubsidySearchResult> content, int page, int size, long totalElements,
		int totalPages, boolean last) {

	public static SubsidyPageResponse from(Page<SubsidySearchResult> page) {
		return new SubsidyPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.isLast());
	}

}
