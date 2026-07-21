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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.favorite.dto.FavoriteListResponse;
import com.jeongbiseo.domain.favorite.dto.FavoriteResponse;
import com.jeongbiseo.domain.favorite.service.FavoriteService;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidyPageResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidySort;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;
import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.ValidationErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.FixedMemberResolver;

/**
 * 지원금 검색·상세 조회와 관심 등록·해제를 다룸(API명세서 13번 searchSubsidies, 15번 getSubsidyDetail, 16번
 * addFavorite, 17번 removeFavorite). 네 엔드포인트 모두 현재 permitAll 상태임(소셜 인증 전). 상세의 isFavorite은
 * 계약상 비로그인이면 false이나, 배포 N에서는 FixedMemberResolver가 무헤더 요청을 고정 회원 1로 해석하므로 그 회원의 등록 여부가
 * 반영됨. 명세서상 searchSubsidies와 관심 등록·해제는 인증 필요라, 인증 Wave에서 SecurityConfig 작성 시
 * authenticated로 전환할 것.
 */
@Tag(name = "Subsidy", description = "지원금 검색·상세 조회와 관심 등록·해제")
@RestController
@RequestMapping("/api/v1/subsidies")
public class SubsidyController {

	// 검색 노출 개수 기본값·성능 상한임. size 미지정·0 이하는 기본값, 상한 초과는 클램프함(과대 페이지 조회로 인한 무거운 쿼리 방지).
	private static final int DEFAULT_PAGE_SIZE = 20;

	private static final int MAX_PAGE_SIZE = 100;

	private final SubsidyService subsidyService;

	private final FavoriteService favoriteService;

	private final FixedMemberResolver memberResolver;

	public SubsidyController(SubsidyService subsidyService, FavoriteService favoriteService,
			FixedMemberResolver memberResolver) {
		this.subsidyService = subsidyService;
		this.favoriteService = favoriteService;
		this.memberResolver = memberResolver;
	}

	// 401(COMMON401)은 명세서 계약이나 현재 SecurityConfig가 전면 permitAll이라 실제로 던지는 코드는 없음. 소셜 인증
	// Wave에서 실제 발생함(명세서 각주 COMMON401 정합).
	@Operation(summary = "지원금 검색",
			description = "키워드·분류로 지원금을 검색함(융자 상품은 항상 제외). keyword는 지원금명 또는 소관기관 부분 일치이고 "
					+ "keyword·category 모두 생략 가능함. page가 음수면 400으로 거절하고, size는 0 이하면 기본값 20으로 "
					+ "대체하며 100을 넘으면 100으로 줄임(page는 줄이지 않고 거절함). sort는 생략하면 등록순(id 오름차순)이고 "
					+ "DEADLINE(마감 임박순, 마감 미상은 뒤)·NAME(가나다순) 중 하나이며, 허용값 밖이면 400으로 거절함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "지원금 검색 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400",
					description = "쿼리 파라미터 검증 실패(VALID400_0, page 음수 또는 page·size·sort 타입·허용값 불일치)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))) })
	@GetMapping
	public CustomResponse<SubsidyPageResponse> searchSubsidies(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) SubsidyCategory category, @RequestParam(required = false) SubsidySort sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		if (page < 0) { // 음수 page는 PageRequest.of가 던져 500이 되므로 먼저 거절(추천 limit 검증과 같은 선례)
			throw new CustomException(ValidationErrorCode.INVALID_QUERY_PARAMETER);
		}
		int effectiveSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		// sort 미지정은 id 오름차순을 Pageable로 실어 현행 응답을 바이트 동일하게 유지함(하위호환). DEADLINE·NAME은 정렬을
		// order by 본문에 명시한 전용 쿼리라 Pageable엔 페이지·크기만 실어 넘김(정렬 이중 부여 방지).
		Pageable pageable = (sort == null) ? PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.ASC, "id"))
				: PageRequest.of(page, effectiveSize);
		return CustomResponse.ok(SubsidyPageResponse.from(subsidyService.search(keyword, category, sort, pageable)));
	}

	// /favorites는 리터럴 세그먼트라 아래 /{subsidyId} 경로 변수보다 우선 매칭됨(경로 충돌 없음).
	@Operation(summary = "관심 목록 조회",
			description = "현재 회원의 관심 등록 지원금 목록을 최근 등록순으로 반환함. 아이템은 검색 결과와 동일 스키마임. "
					+ "목록의 하트 상태는 이 응답의 subsidyId 집합으로 클라이언트가 대조함(목록 아이템에 isFavorite 필드를 두지 않음).")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "관심 목록 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))) })
	@GetMapping("/favorites")
	public CustomResponse<FavoriteListResponse> getFavorites() {
		return CustomResponse
			.ok(FavoriteListResponse.from(favoriteService.getFavorites(memberResolver.resolveMemberId())));
	}

	@Operation(summary = "지원금 상세 조회",
			description = "지원금 상세를 조회함. active=false와 중복(duplicateOfId) 행도 노출함(기수령 선택 유즈케이스). "
					+ "비로그인 요청도 허용하며 로그인 회원이면 관심 등록 여부를 isFavorite에 반영함.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "지원금 상세 조회 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "경로 변수 타입 불일치(VALID400_0, subsidyId가 정수로 파싱되지 않음)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "지원금 미존재(SUBSIDY404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "SUBSIDY404_1",
							value = "{\"isSuccess\":false,\"code\":\"SUBSIDY404_1\",\"message\":\"해당 지원금 정보를 찾을 수 없어요\",\"result\":null}"))) })
	@GetMapping("/{subsidyId}")
	public CustomResponse<SubsidyDetailResponse> getSubsidyDetail(@PathVariable Long subsidyId) {
		return CustomResponse.ok(subsidyService.getDetail(subsidyId, memberResolver.resolveMemberId()));
	}

	@Operation(summary = "관심 등록", description = "지원금을 현재 회원의 관심 목록에 등록함. 등록 결과는 캘린더에 바로 반영됨.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "관심 등록 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "경로 변수 타입 불일치(VALID400_0)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "지원금 미존재(SUBSIDY404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "SUBSIDY404_1",
							value = "{\"isSuccess\":false,\"code\":\"SUBSIDY404_1\",\"message\":\"해당 지원금 정보를 찾을 수 없어요\",\"result\":null}"))),
			@ApiResponse(responseCode = "409", description = "이미 관심 등록됨(FAVORITE409_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "FAVORITE409_1",
							value = "{\"isSuccess\":false,\"code\":\"FAVORITE409_1\",\"message\":\"이미 관심 등록한 지원금이에요\",\"result\":null}"))) })
	@PostMapping("/{subsidyId}/favorite")
	public CustomResponse<FavoriteResponse> addFavorite(@PathVariable Long subsidyId) {
		favoriteService.add(memberResolver.resolveMemberId(), subsidyId);
		return CustomResponse.ok(new FavoriteResponse(subsidyId, true));
	}

	@Operation(summary = "관심 해제", description = "지원금을 현재 회원의 관심 목록에서 해제함. 해제 결과는 캘린더에 바로 반영됨.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "관심 해제 성공", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", description = "경로 변수 타입 불일치(VALID400_0)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "VALID400_0",
							value = "{\"isSuccess\":false,\"code\":\"VALID400_0\",\"message\":\"잘못된 파라미터 입니다.\",\"result\":null}"))),
			@ApiResponse(responseCode = "401", description = "인증 필요(현재 permitAll, 소셜 인증 Wave에서 실제 발생)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "COMMON401",
							value = "{\"isSuccess\":false,\"code\":\"COMMON401\",\"message\":\"인증이 필요합니다\",\"result\":null}"))),
			@ApiResponse(responseCode = "404", description = "관심 등록되지 않음(FAVORITE404_1)",
					content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "FAVORITE404_1",
							value = "{\"isSuccess\":false,\"code\":\"FAVORITE404_1\",\"message\":\"관심 등록되지 않은 지원금이에요\",\"result\":null}"))) })
	@DeleteMapping("/{subsidyId}/favorite")
	public CustomResponse<FavoriteResponse> removeFavorite(@PathVariable Long subsidyId) {
		favoriteService.remove(memberResolver.resolveMemberId(), subsidyId);
		return CustomResponse.ok(new FavoriteResponse(subsidyId, false));
	}

}
