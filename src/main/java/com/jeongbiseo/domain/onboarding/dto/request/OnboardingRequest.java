package com.jeongbiseo.domain.onboarding.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;

/**
 * 온보딩 제출과 수정 요청 본문임(API명세서 9번 submitOnboarding과 7번 updateMyOnboarding, 두 요청 필드가 동일해 하나로
 * 공유함). 이름은 실명 2자에서 12자 필수이며 소셜 프로필명이 아니라 사용자 입력이 정본임(v1.4, D6). 소득구간과 가구원 수는 선택이며 생략 시
 * null로 처리함.
 *
 * @param name 이름(실명, 필수, 2자에서 12자)
 * @param birthDate 생년월일(필수, 과거 날짜)
 * @param sido 거주지 시 또는 도(필수)
 * @param sigungu 거주지 시군구(필수)
 * @param employmentStatus 고용상태(필수)
 * @param incomeBracket 소득구간(선택)
 * @param householdSize 가구원 수(선택, 1에서 10)
 */
// 필수 여부는 Jakarta Validation(@NotBlank·@NotNull)을 정본으로 삼고 @Schema에 requiredMode를 겹쳐 적지 않음.
// swagger-core가 검증 어노테이션을 required 목록에 자동 반영하므로 중복 선언은 둘이 어긋날 위험만 남김.
public record OnboardingRequest(
		@Schema(description = "이름(실명). 소셜 프로필명을 자동 저장하지 않으므로 온보딩 화면에서 직접 입력받음. 동명이인을 허용해 UNIQUE 제약이 없음",
				example = "홍길동") @NotBlank(
						message = "이름은 필수예요") @Size(min = 2, max = 12, message = "이름은 2자에서 12자여야 해요") String name,
		@Schema(description = "생년월일(YYYY-MM-DD). 만 나이는 서버가 계산하므로 나이를 따로 보내지 않음", example = "1999-03-15") @NotNull(
				message = "생년월일은 필수예요") @Past(message = "생년월일은 과거 날짜여야 해요") LocalDate birthDate,
		@Schema(description = "거주지 시 또는 도. 코드값이 아니라 전체 명칭 문자열임. 선택지는 GET /api/v1/regions 응답을 씀",
				example = "서울특별시") @NotBlank(message = "거주지는 필수예요") String sido,
		@Schema(description = "거주지 시군구. 코드값이 아니라 전체 명칭 문자열임(GET /api/v1/regions 응답의 sigunguList[].name)",
				example = "관악구") @NotBlank(message = "시군구는 필수예요") String sigungu,
		@NotNull(message = "고용상태는 필수예요") EmploymentStatus employmentStatus,
		@Schema(description = "소득구간(선택). 건너뛰기는 필드를 생략하거나 null을 보냄. 소득이 0원인 경우는 생략이 아니라 UNDER_200을 보냄"
				+ "(생략하면 소득 조건이 있는 공고에서 산정불가 사유가 붙음)") IncomeBracket incomeBracket,
		@Schema(description = "가구원 수(선택, 1에서 10). 본인 포함", example = "3") @Min(value = 1,
				message = "가구원 수는 1명 이상이어야 해요") @Max(value = 10,
						message = "가구원 수는 10명 이하여야 해요") Integer householdSize) {

}
