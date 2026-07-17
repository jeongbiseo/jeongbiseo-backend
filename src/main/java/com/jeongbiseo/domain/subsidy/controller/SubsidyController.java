package com.jeongbiseo.domain.subsidy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * 지원금 검색·상세 조회를 다룸(API명세서 13번 searchSubsidies, 15번 getSubsidyDetail). 두 엔드포인트 모두 현재
 * permitAll 상태임(소셜 인증 전, 상세는 isFavorite 항상 false로 선택 인증 기본값과 정합). 명세서상 searchSubsidies는
 * 인증 필요라, 인증 Wave에서 SecurityConfig 작성 시 authenticated로 전환할 것.
 */
@Tag(name = "Subsidy", description = "지원금 검색과 상세 조회")
@RestController
@RequestMapping("/api/v1/subsidies")
public class SubsidyController {

	// 검색 노출 개수 기본값·성능 상한임. size 미지정·0 이하는 기본값, 상한 초과는 클램프함(과대 페이지 조회로 인한 무거운 쿼리 방지).
	private static final int DEFAULT_PAGE_SIZE = 20;

	private static final int MAX_PAGE_SIZE = 100;

	private final SubsidyService subsidyService;

	public SubsidyController(SubsidyService subsidyService) {
		this.subsidyService = subsidyService;
	}

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서 실제 발생함(명세서 각주 COMMON401 정합).
	@Operation(summary = "지원금 검색",
			description = "키워드·분류로 지원금을 검색함(융자 상품은 항상 제외). keyword·category는 nullable, "
					+ "page·size는 음수·과대값이면 400 또는 클램프로 처리함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "지원금 검색 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "쿼리 파라미터 검증 실패(VALID400_0, page 음수 또는 page·size 타입 불일치)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<SubsidyPageResponse> searchSubsidies(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) SubsidyCategory category, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (page < 0) { // 음수 page는 PageRequest.of가 던져 500이 되므로 먼저 거절(추천 limit 검증과 같은 선례)
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		int effectiveSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		// id 오름차순 안정 정렬을 고정해 페이지 간 중복·누락을 막음(정렬 없으면 반환 순서 비결정)
		Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.ASC, "id"));
		return CustomResponse.ok(SubsidyPageResponse.from(subsidyService.search(keyword, category, pageable)));
	}

	@Operation(summary = "지원금 상세 조회",
			description = "지원금 상세를 조회함. active=false와 중복(duplicateOfId) 행도 노출함(기수령 선택 유즈케이스). "
					+ "비로그인 요청도 허용하며 선택 인증 기본값으로 isFavorite는 항상 false임.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "지원금 상세 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "경로 변수 타입 불일치(VALID400_0, subsidyId가 정수로 파싱되지 않음)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "지원금 미존재(SUBSIDY404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "SUBSIDY404_1",
							value = "{\"isSuccess\":false,\"code\":\"SUBSIDY404_1\",\"message\":\"해당 지원금 정보를 찾을 수 없어요\",\"result\":null}"))) })
	@GetMapping("/{subsidyId}")
	public CustomResponse<SubsidyDetailResponse> getSubsidyDetail(@PathVariable Long subsidyId) {
		return CustomResponse.ok(subsidyService.getDetail(subsidyId));
	}

}
