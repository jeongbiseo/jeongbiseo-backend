package com.jeongbiseo.infra.client.gov24.dto;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보조금24 supportConditions 응답 항목 DTO임. JA코드 48개(swagger_gov24.json
 * definitions.supportConditions_model) 중 SubsidyCriteria 매칭에 쓰는 연령(JA0110·JA0111), 소득
 * 5구간(JA0201~JA0205), 가구
 * 9필드(JA0401·JA0402·JA0403·JA0404·JA0410·JA0411·JA0412·JA0413·JA0414)만
 * 매핑함(외부API-부족분-조사-2026-07-12.md 3장 G1 확정본 기준). 나머지 JA코드(성별·인적특성·업종 등)는 이 파서의 매칭 범위 밖이라
 * 매핑하지 않음.
 *
 * <p>
 * 소득·가구 플래그는 원문을 그대로 저장만 함 — 하드 배제 필터로 쓸지는 회의 안건 22번 결정 사항이라 이 DTO 단계에서 배제 판단을 내리지 않음.
 * {@link #incomeSignal()}·{@link #householdSignal()}이 계산하는 3분류(제한없음·제한형·데이터없음)는 랭킹·표시용
 * 신호이지 필터 결정이 아니고, 원문 선정기준과 항상 일치하지도 않음(적대 검증에서 대조 표본 불일치 확인 — 자세한 신뢰도 한계는
 * {@link EligibilitySignal} 참조).
 *
 * @param serviceId 서비스ID
 * @param ageMin 대상연령 시작(JA0110, null이면 하한 없음)
 * @param ageMax 대상연령 종료(JA0111, null이면 상한 없음)
 * @param income0To50 중위소득 0~50%(JA0201, "Y" 또는 null)
 * @param income51To75 중위소득 51~75%(JA0202, "Y" 또는 null)
 * @param income76To100 중위소득 76~100%(JA0203, "Y" 또는 null)
 * @param income101To200 중위소득 101~200%(JA0204, "Y" 또는 null)
 * @param incomeOver200 중위소득 200% 초과(JA0205, "Y" 또는 null)
 * @param multiculturalFamily 다문화가족(JA0401, "Y" 또는 null)
 * @param northKoreanDefector 북한이탈주민(JA0402, "Y" 또는 null)
 * @param singleParentOrGrandparentFamily 한부모가정/조손가정(JA0403, "Y" 또는 null)
 * @param singlePersonHousehold 1인가구(JA0404, "Y" 또는 null)
 * @param householdNotApplicable 가구 조건 해당사항없음(JA0410, "Y" 또는 null) — 명시적 무관 표시임(데이터 없음과
 * 다름, 반드시 구분해야 함)
 * @param multiChildHousehold 다자녀가구(JA0411, "Y" 또는 null)
 * @param noHomeownerHousehold 무주택세대(JA0412, "Y" 또는 null)
 * @param newlyRelocatedHousehold 신규전입(JA0413, "Y" 또는 null)
 * @param extendedFamily 확대가족(JA0414, "Y" 또는 null)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Gov24SupportConditionDto(@JsonProperty("서비스ID") String serviceId, @JsonProperty("JA0110") Integer ageMin,
		@JsonProperty("JA0111") Integer ageMax, @JsonProperty("JA0201") String income0To50,
		@JsonProperty("JA0202") String income51To75, @JsonProperty("JA0203") String income76To100,
		@JsonProperty("JA0204") String income101To200, @JsonProperty("JA0205") String incomeOver200,
		@JsonProperty("JA0401") String multiculturalFamily, @JsonProperty("JA0402") String northKoreanDefector,
		@JsonProperty("JA0403") String singleParentOrGrandparentFamily,
		@JsonProperty("JA0404") String singlePersonHousehold, @JsonProperty("JA0410") String householdNotApplicable,
		@JsonProperty("JA0411") String multiChildHousehold, @JsonProperty("JA0412") String noHomeownerHousehold,
		@JsonProperty("JA0413") String newlyRelocatedHousehold, @JsonProperty("JA0414") String extendedFamily,
		// JA03 계열 17개 — 생애주기·직업군·특수계층 특성임. 1차산업 전용 판정에 쓰며, **17개 전부가 필요함**:
		// 켜진 집합이 1차산업 4종의 부분집합인지 보려면 나머지 13개가 꺼졌다는 사실도 알아야 하기 때문임.
		@JsonProperty("JA0301") String expectantOrInfertileParent, @JsonProperty("JA0302") String pregnant,
		@JsonProperty("JA0303") String childbirthOrAdoption, @JsonProperty("JA0313") String farmer,
		@JsonProperty("JA0314") String fisher, @JsonProperty("JA0315") String livestockFarmer,
		@JsonProperty("JA0316") String forester, @JsonProperty("JA0317") String elementaryStudent,
		@JsonProperty("JA0318") String middleSchoolStudent, @JsonProperty("JA0319") String highSchoolStudent,
		@JsonProperty("JA0320") String universityStudent,
		// **"해당사항없음"임. 제한이 아니라 무관 표시.** 이걸 제한으로 읽으면 유아학비(누리과정, 이것만 켜짐)가 죽음
		@JsonProperty("JA0322") String occupationNotApplicable, @JsonProperty("JA0326") String worker,
		@JsonProperty("JA0327") String jobSeeker, @JsonProperty("JA0328") String disabled,
		@JsonProperty("JA0329") String veteran, @JsonProperty("JA0330") String patient) {

	private static final String YES = "Y";

	/**
	 * 소득 조건 3분류를 계산함. 5구간(JA0201~JA0205) 전부 Y면 제한없음, 하나라도 Y가 있으면 제한형, 전부 null이면 데이터없음임.
	 * @return 소득 조건 신호
	 */
	public EligibilitySignal incomeSignal() {
		String[] incomeFields = { income0To50, income51To75, income76To100, income101To200, incomeOver200 };
		long yesCount = countYes(incomeFields);
		if (yesCount == incomeFields.length) {
			return EligibilitySignal.UNRESTRICTED;
		}
		if (yesCount > 0) {
			return EligibilitySignal.RESTRICTED;
		}
		return EligibilitySignal.UNKNOWN;
	}

	/**
	 * 가구 조건 3분류를 계산함. JA0410(해당사항없음)은 "명시적 무관" 표시라 실질 8필드와 분리해서 봄 — 실질 8필드가 전부 Y이거나(전 유형
	 * 해당) 실질 8필드가 전부 null이면서 JA0410만 단독으로 Y면 제한없음, 실질 8필드 중 일부만 Y면(JA0410 값과 무관하게) 제한형,
	 * 실질 8필드와 JA0410이 전부 null이면 데이터없음임. 전수 10,968건 실측 재현으로 검증됨(가구 유효 90.75%/제한형 11.75%,
	 * 외부API-부족분-조사-2026-07-12.md 3장과 일치).
	 * @return 가구 조건 신호
	 */
	public EligibilitySignal householdSignal() {
		String[] substantiveFields = { multiculturalFamily, northKoreanDefector, singleParentOrGrandparentFamily,
				singlePersonHousehold, multiChildHousehold, noHomeownerHousehold, newlyRelocatedHousehold,
				extendedFamily };
		long substantiveYesCount = countYes(substantiveFields);
		if (substantiveYesCount == substantiveFields.length) {
			return EligibilitySignal.UNRESTRICTED;
		}
		if (substantiveYesCount > 0) {
			return EligibilitySignal.RESTRICTED;
		}
		if (YES.equals(householdNotApplicable)) {
			return EligibilitySignal.UNRESTRICTED;
		}
		return EligibilitySignal.UNKNOWN;
	}

	/**
	 * <b>1차산업(농림축수산업) 종사자 전용인지 판정함.</b> 제품 타깃(20~30대 청년·사회초년생) 밖의 지원금을 추천 스코프에서 자르는 근거임.
	 *
	 * <p>
	 * <b>규칙은 한 문장임.</b> 켜진 특성 집합 S가 비어 있지 않고 S가 1차산업 4종(JA0313 농업인, JA0314 어업인, JA0315
	 * 축산업인, JA0316 임업인)의 <b>부분집합</b>일 때만 PRIMARY_INDUSTRY_ONLY임. 그 외는 전부 통과임.
	 *
	 * <p>
	 * <b>JA0322(해당사항없음) 분기를 따로 두지 않은 이유</b>: 잉여임. JA0322가 켜져 있으면 그 순간 S는 1차산업의 부분집합이 아니게
	 * 되어 자동으로 통과함. 분기를 지워도 결과가 같음. 반대로 JA0322를 "제한"으로 오독하면 유아학비(누리과정, JA0322 단독)가 죽음.
	 *
	 * <p>
	 * <b>장애인·보훈·질병 전용은 자르지 않음.</b> 온보딩에 그 축이 없어 판정할 수 없고, 장애인 청년도 우리 사용자임. 함부로 탈락시키면 받을 수
	 * 있는 지원금을 죽임.
	 *
	 * <p>
	 * 표본 1,097건 실측: 전부 켜짐 245건(제한 없음), 빈 집합 83건(데이터 없음), JA0322 단독 122건, JA0322 혼합 67건,
	 * <b>1차산업 전용 177건</b>, 그 외 제한형 403건.
	 * @return 1차산업 전용이면 PRIMARY_INDUSTRY_ONLY, 아니면 NONE
	 */
	public OccupationRestriction occupationRestriction() {
		String[] primaryIndustry = { farmer, fisher, livestockFarmer, forester };
		String[] others = { expectantOrInfertileParent, pregnant, childbirthOrAdoption, elementaryStudent,
				middleSchoolStudent, highSchoolStudent, universityStudent, occupationNotApplicable, worker, jobSeeker,
				disabled, veteran, patient };
		boolean primaryOn = countYes(primaryIndustry) > 0;
		boolean anyOtherOn = countYes(others) > 0;
		return primaryOn && !anyOtherOn ? OccupationRestriction.PRIMARY_INDUSTRY_ONLY : OccupationRestriction.NONE;
	}

	private static long countYes(String[] values) {
		long count = 0;
		for (String value : values) {
			if (YES.equals(value)) {
				count++;
			}
		}
		return count;
	}

}
