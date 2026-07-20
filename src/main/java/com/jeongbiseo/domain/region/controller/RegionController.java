package com.jeongbiseo.domain.region.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Region", description = "거주지 시/도·시군구 목록 조회")
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

	// GET /api/v1/regions (operationId: getRegions). 인증 불필요이며 sido는 자유 문자열이라 검증 제약이
	// 없고(RegionCatalog가 미등록 조합은 빈 목록으로 처리), 코드가 실제로 던지는 예외가 없어 에러 응답이 없음.
	@Operation(summary = "거주지 목록 조회", description = "sido 미지정 시 시 또는 도 목록, 지정 시 해당 시군구 목록을 반환함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "거주지 목록 조회 성공", useReturnTypeSchema = true) })
	// 온보딩 전 비로그인 화면이 지역 목록을 먼저 부르므로 글로벌 Bearer 요구를 해제함.
	@SecurityRequirements
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
