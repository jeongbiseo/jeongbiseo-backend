package com.jeongbiseo.infra.client.youthcenter.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 온통청년 원문 파싱 결과임(2층 구조의 <b>1층</b> — 공통 타깃
 * {@link com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy}는 2층이고 변환은
 * {@code YouthcenterSubsidyNormalizer}가 함). gov24의 {@code ParsedSubsidyResult}와 같은 자리에
 * 있음.
 *
 * <p>
 * <b>이 층이 따로 있어야 하는 이유는 공통 타깃에 자리가 없는 온통청년 고유 코드 4종 때문임</b> — 특화요건(sbizCd)·학력(schoolCd)·
 * 전공(plcyMajorCd)·결혼상태(mrgSttsCd). 넷 다 채움률이 99% 이상인 <b>실재하는 선언 필드</b>인데 매칭
 * 4축(연령·소득·가구·고용) 어디에도 안 들어맞음. 공통 타깃에 칸을 뚫으면 다른 3개 소스가 영구히 null인 칸을 이고 가야 하므로(함정 1), 여기서
 * 원문 코드로 보존하고 공통 타깃에는 올리지 않음. 필요해지면 이 레코드를 직접 보면 됨.
 *
 * @param policyId 정책번호(plcyNo)
 * @param name 정책명
 * @param agency 주관기관명
 * @param description 정책설명과 지원내용을 합친 원문
 * @param eligibilityText 자격조건 원문(추가자격·참여제한·소득기타를 라벨 붙여 합침). 셋 다 비면 null
 * @param categoryRawText 대분류와 중분류를 합친 원문(예 "일자리 &gt; 취업"). <b>SubsidyCategory로 매핑하지
 * 않음</b>
 * @param paymentType 정책제공방법코드에서 매핑한 지급유형
 * @param provisionMethodCode 정책제공방법코드 원문(예 "0042006"). 매핑 근거 추적용
 * @param amount 지원내용(plcySprtCn)에서 뽑은 금액 — <b>gov24의 검증된 금액 파서를 그대로 재사용함</b>
 * @param deadline 신청기간 판정 결과(구조화 코드 매핑이지 텍스트 파싱이 아님)
 * @param applicationPeriodCode 신청기간구분코드 원문(0057001·0057002·0057003). 마감 판정의 근거 필드
 * @param regionCodes 법정시군구코드 5자리 목록(zipCd 콤마 분해). 선언된 코드라 그대로 신뢰함
 * @param ageMin 지원대상 최소연령("0"은 미입력이라 null로 정규화함)
 * @param ageMax 지원대상 최대연령(위와 같음)
 * @param ageSignal 연령 신호. <b>UNRESTRICTED를 만들지 않음</b>(사유는 {@code YouthcenterParser} 참조)
 * @param ageLimitYnRawText 지원대상연령제한여부 원문(Y·N). 판정에 쓰지 않고 원문만 남김 — 실측에서 값이 연령 채움 여부와 어긋나기
 * 때문임
 * @param incomeSignal 소득 신호(무관 0043001은 UNRESTRICTED, 연소득·기타는 RESTRICTED)
 * @param employmentSignal 고용 신호(제한없음 0013010 단독은 UNRESTRICTED, 그 외 코드가 있으면 RESTRICTED)
 * @param employmentRawCode 정책취업요건코드 원문(콤마 나열 그대로). {@code EmploymentStatus}로 매핑하지 않음
 * @param specializedRawCode 정책특화요건코드 원문(sbizCd). 가구 조건이 <b>아님</b> — 축이 섞여 있어 매핑하지 않음
 * @param schoolRawCode 정책학력요건코드 원문(schoolCd)
 * @param majorRawCode 정책전공요건코드 원문(plcyMajorCd)
 * @param maritalStatusRawCode 결혼상태코드 원문(mrgSttsCd)
 * @param applicationMethod 신청 채널 플래그(자유텍스트 키워드 분류). 원문 채움이 53.59%뿐이라 미분류가 많음
 * @param applicationUrl 신청 URL(없으면 null)
 * @param referenceUrl 참고·상세 페이지 URL(없으면 null)
 * @param requiredDocumentsText 제출서류 원문("해당없음" 계열 센티널은 null로 정규화함)
 * @param dataUpdatedAt 최종수정일시
 */
public record ParsedYouthPolicy(String policyId, String name, String agency, String description, String eligibilityText,
		String categoryRawText, PaymentType paymentType, String provisionMethodCode, ParsedAmount amount,
		ParsedDeadline deadline, String applicationPeriodCode, List<String> regionCodes, Integer ageMin, Integer ageMax,
		EligibilitySignal ageSignal, String ageLimitYnRawText, EligibilitySignal incomeSignal,
		EligibilitySignal employmentSignal, String employmentRawCode, String specializedRawCode, String schoolRawCode,
		String majorRawCode, String maritalStatusRawCode, ApplicationMethodFlags applicationMethod,
		String applicationUrl, String referenceUrl, String requiredDocumentsText, LocalDateTime dataUpdatedAt) {

}
