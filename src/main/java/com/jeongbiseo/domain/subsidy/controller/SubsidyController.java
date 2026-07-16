package com.jeongbiseo.domain.subsidy.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidyPageResponse;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 지원금 검색·상세 조회를 다룸(API명세서 §13 searchSubsidies, §15 getSubsidyDetail). 두 엔드포인트 모두 인증 여부와
 * 무관하게 permitAll 상태임(소셜 인증 전, 상세는 isFavorite 항상 false로 선택 인증 기본값과 정합).
 */
@RestController
@RequestMapping("/api/v1/subsidies")
public class SubsidyController {

	private final SubsidyService subsidyService;

	public SubsidyController(SubsidyService subsidyService) {
		this.subsidyService = subsidyService;
	}

	// 지원금 검색(GET /api/v1/subsidies, operationId searchSubsidies)
	@GetMapping
	public CustomResponse<SubsidyPageResponse> searchSubsidies(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) SubsidyCategory category, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (page < 0) { // 음수 page는 PageRequest.of가 던져 500이 되므로 먼저 거절(L2, limit 선례 정합)
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		Pageable pageable = PageRequest.of(page, size > 0 ? size : 20);
		return CustomResponse.ok(SubsidyPageResponse.from(subsidyService.search(keyword, category, pageable)));
	}

	// 지원금 상세 조회(GET /api/v1/subsidies/{subsidyId}, operationId getSubsidyDetail)
	@GetMapping("/{subsidyId}")
	public CustomResponse<SubsidyDetailResponse> getSubsidyDetail(@PathVariable Long subsidyId) {
		return CustomResponse.ok(subsidyService.getDetail(subsidyId));
	}

}
