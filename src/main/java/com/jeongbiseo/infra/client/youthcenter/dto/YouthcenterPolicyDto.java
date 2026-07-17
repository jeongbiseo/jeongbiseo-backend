package com.jeongbiseo.infra.client.youthcenter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 온통청년 청년정책 API(getPlcy) 응답 항목 1건임. 전수 2,648건(2026-07-12 실호출)의 키 합집합은 <b>60개</b>이고, 그중 이
 * DTO가 읽는 것은 아래 28개임. 읽지 않는 32개는 매칭·화면에 쓸 데가 없거나(조회수 {@code inqCnt}, 등록기관 계열
 * {@code rgtr*}, 기본계획 계열 {@code bscPlan*}, 담당자명 {@code *PicNm}) 의미가 위험해서 일부러 뺀 것임 — 특히
 * <b>사업기간 계열({@code bizPrdSeCd}·{@code bizPrdBgngYmd}·{@code bizPrdEndYmd})은 신청 마감일이 아니라
 * 사업 운영 기간이라 읽지 않음</b>(전수 크로스탭에서 {@code bizPrdSeCd=0056001}이 {@code bizPrdBgngYmd} 채움을
 * 1,597/1,599로 예측해 구조는 멀쩡하나, 이걸 마감일로 쓰면 복지로 시행종료일자를 접수 마감일로 오인했던 것과 같은 사고임).
 *
 * <p>
 * <b>코드 필드의 의미는 공식 코드정의서로 확정함</b> — 온통청년 오픈API 안내 페이지가 배포하는
 * {@code /downloadform/API코드정보.xlsx}(2026-07-12 내려받아 대조). 조사 리포트가 "코드정의서 미확인"이라 남겨 둔 항목이
 * 이 파일로 전부 해소됨. 각 코드의 값 정의는 {@code YouthcenterParser}의 매핑 상수 주석에 그대로 옮겨 적었음.
 *
 * @param policyId 정책번호({@code plcyNo}) — 채움 100%, {@code external_id}로 씀
 * @param policyName 정책명({@code plcyNm}) — 채움 100%
 * @param policyExplanation 정책설명({@code plcyExplnCn}) — 채움 100%. "무엇을 하는 정책인가"
 * @param policySupportContent 정책지원내용({@code plcySprtCn}) — 채움 100%. "무엇을 주는가"이고 <b>금액 표현이
 * 섞여 있는 유일한 필드</b>임(전수 39.43%에 "원" 금액 표현 있음). 금액 파싱은 이 필드에만 걸어야 함 — 정책설명까지 함께 넣으면 사업
 * 규모·예산 문장이 금액 후보로 새어 들어옴
 * @param largeCategoryName 정책대분류명({@code lclsfNm}) — 채움 99.96%
 * @param mediumCategoryName 정책중분류명({@code mclsfNm}) — 채움 99.96%
 * @param supervisingAgencyName 주관기관명({@code sprvsnInstCdNm}) — 채움 100%. <b>지역 추론에 쓰지
 * 않음</b> — "청년정책담당관"·"고용노동부"처럼 지역이 아닌 값이 섞여 있고, 이 소스에는 zipCd라는 선언된 지역 코드가 따로 있음
 * @param provisionMethodCode 정책제공방법코드({@code plcyPvsnMthdCd}) — 채움 100%, 단일값, distinct
 * 13. gov24의 지원유형에 대응하는 유일한 필드임
 * @param applicationPeriodCode 신청기간구분코드({@code aplyPrdSeCd}) — 채움 100%, distinct
 * 3(특정기간·상시·마감)
 * @param applicationPeriodText 신청기간({@code aplyYmd}) — 채움 49.89%. 특정기간(0057001)일 때만
 * 채워짐(전수 크로스탭 위반 0건)
 * @param regionCodesText 정책신청지역코드({@code zipCd}) — 채움 99.32%. 법정시군구코드 5자리의 콤마 나열(전수 코드
 * 인스턴스 122,789개 전부 5자리, xx000 형태 0건)
 * @param supportTargetMinAge 지원대상최소연령({@code sprtTrgtMinAge}) — "0"은 값이 아니라 미입력 센티널임
 * @param supportTargetMaxAge 지원대상최대연령({@code sprtTrgtMaxAge}) — 위와 같음
 * @param ageLimitYn 지원대상연령제한여부({@code sprtTrgtAgeLmtYn}) — <b>이 필드는 판정에 쓰지 않음</b>. 이유는
 * {@code YouthcenterParser.parseAgeSignal} Javadoc에 실측 크로스탭과 함께 적었음
 * @param incomeConditionCode 소득조건구분코드({@code earnCndSeCd}) — 채움 99.92%, distinct
 * 3(무관·연소득·기타)
 * @param incomeEtcContent 소득기타내용({@code earnEtcCn}) — 채움 12.31%. 코드가 "기타"(0043003)인 326건과
 * 정확히 일치함
 * @param jobCode 정책취업요건코드({@code jobCd}) — 채움 100%. 10종 코드의 콤마 나열
 * @param specializedRequirementCode 정책특화요건코드({@code sbizCd}) — 채움 100%(조사 리포트의 "500건 전부
 * 공백"은 반증됨. 7장 참조)
 * @param schoolCode 정책학력요건코드({@code schoolCd}) — 채움 99.92%. 매칭 4축 밖이라 원문만 보존함
 * @param majorCode 정책전공요건코드({@code plcyMajorCd}) — 채움 100%. 매칭 4축 밖이라 원문만 보존함
 * @param maritalStatusCode 결혼상태코드({@code mrgSttsCd}) — 채움 99.92%. 매칭 4축 밖이라 원문만 보존함
 * @param additionalQualificationContent 추가신청자격조건내용({@code addAplyQlfcCndCn}) — 채움 33.91%
 * @param participationRestrictionContent 참여제한대상내용({@code ptcpPrpTrgtCn}) — 채움 24.58%
 * @param applicationMethodContent 정책신청방법내용({@code plcyAplyMthdCn}) — 채움
 * <b>53.59%뿐</b>임(gov24 신청방법 100%와 대비됨). 자유텍스트라 키워드 분류만 가능
 * @param submissionDocumentContent 제출서류내용({@code sbmsnDcmntCn}) — 채움 33.61%. "해당없음"·"-"
 * 센티널이 섞여 있음
 * @param applicationUrl 신청URL({@code aplyUrlAddr}) — 채움 32.85%
 * @param referenceUrl 참고URL1({@code refUrlAddr1}) — 채움 63.78%. 신청 URL과 구분함
 * @param lastModifiedAt 최종수정일시({@code lastMdfcnDt}) — 채움 100%, "yyyy-MM-dd HH:mm:ss" 고정
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouthcenterPolicyDto(@JsonProperty("plcyNo") String policyId, @JsonProperty("plcyNm") String policyName,
		@JsonProperty("plcyExplnCn") String policyExplanation, @JsonProperty("plcySprtCn") String policySupportContent,
		@JsonProperty("lclsfNm") String largeCategoryName, @JsonProperty("mclsfNm") String mediumCategoryName,
		@JsonProperty("sprvsnInstCdNm") String supervisingAgencyName,
		@JsonProperty("plcyPvsnMthdCd") String provisionMethodCode,
		@JsonProperty("aplyPrdSeCd") String applicationPeriodCode,
		@JsonProperty("aplyYmd") String applicationPeriodText, @JsonProperty("zipCd") String regionCodesText,
		@JsonProperty("sprtTrgtMinAge") String supportTargetMinAge,
		@JsonProperty("sprtTrgtMaxAge") String supportTargetMaxAge, @JsonProperty("sprtTrgtAgeLmtYn") String ageLimitYn,
		@JsonProperty("earnCndSeCd") String incomeConditionCode, @JsonProperty("earnEtcCn") String incomeEtcContent,
		@JsonProperty("jobCd") String jobCode, @JsonProperty("sbizCd") String specializedRequirementCode,
		@JsonProperty("schoolCd") String schoolCode, @JsonProperty("plcyMajorCd") String majorCode,
		@JsonProperty("mrgSttsCd") String maritalStatusCode,
		@JsonProperty("addAplyQlfcCndCn") String additionalQualificationContent,
		@JsonProperty("ptcpPrpTrgtCn") String participationRestrictionContent,
		@JsonProperty("plcyAplyMthdCn") String applicationMethodContent,
		@JsonProperty("sbmsnDcmntCn") String submissionDocumentContent,
		@JsonProperty("aplyUrlAddr") String applicationUrl, @JsonProperty("refUrlAddr1") String referenceUrl,
		@JsonProperty("lastMdfcnDt") String lastModifiedAt) {

}
