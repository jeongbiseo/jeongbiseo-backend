package com.jeongbiseo.domain.region.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.region.RegionCatalog;
import com.jeongbiseo.domain.region.dto.response.RegionListResponse;
import com.jeongbiseo.domain.region.dto.response.SigunguResponse;
import com.jeongbiseo.global.apiPayload.CustomResponse;

/**
 * 거주지 목록 조회를 다룸(API명세서 11번, operationId getRegions, 인증 불필요). sido 미지정 시 시/도 목록, 지정 시 해당
 * 시군구 목록을 반환함(2단계 동적 필터링, ONB-212). RegionCatalog가 고정 참조 데이터 정본임.
 */
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

	// GET /api/v1/regions (operationId: getRegions)
	@GetMapping
	public CustomResponse<RegionListResponse> getRegions(@RequestParam(required = false) String sido) {
		RegionListResponse result = (sido == null) ? sidoListResponse() : sigunguListResponse(sido);
		return CustomResponse.ok(result);
	}

	private static RegionListResponse sidoListResponse() {
		return new RegionListResponse(RegionCatalog.sidoList(), null, null);
	}

	private static RegionListResponse sigunguListResponse(String sido) {
		List<SigunguResponse> sigunguList = RegionCatalog.sigunguListOf(sido)
			.stream()
			.map(item -> new SigunguResponse(item.code(), item.name()))
			.toList();
		return new RegionListResponse(null, sido, sigunguList);
	}

}
