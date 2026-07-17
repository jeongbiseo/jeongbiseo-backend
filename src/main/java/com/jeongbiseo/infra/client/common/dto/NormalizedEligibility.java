package com.jeongbiseo.infra.client.common.dto;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;

/**
 * 4종 소스 공통 자격조건임. 매칭에 쓰는 4개 축(연령·소득·가구·고용)을 한 곳에 모음.
 *
 * <p>
 * <b>축마다 {@link EligibilitySignal} 3분류를 따로 두는 것이 이 레코드의 핵심임.</b> 값(예 ageMin)만 저장하면 "제한
 * 없음"과 "데이터 없음"이 똑같이 null로 뭉개지는데, 이 둘은 매칭에서 정반대로 다뤄야 함 — 제한 없음은 <b>통과</b>, 데이터 없음은 <b>판단
 * 보류</b>임. gov24 실측만 봐도 연령 null이 9.3%(10,968건 중 1,016건), 소득 데이터 없음이 9.39%, 가구가 9.25%라 무시할
 * 수 없는 규모임(조사 리포트 3장 G1).
 *
 * <p>
 * <b>4종 소스는 축을 서로 보완함</b>(어느 소스도 4축을 다 주지 않음). gov24가 소득·가구를 주고(JA 플래그), 온통청년이 연령·고용을 줌.
 * 기업마당·K-Startup은 기업·창업 대상이라 개인 자격조건 축이 사실상 없음. 따라서 <b>UNKNOWN이 많은 것은 파서 버그가 아니라 소스의
 * 성질임</b> — UNKNOWN을 억지로 UNRESTRICTED로 바꿔 통과시키지 말 것.
 *
 * @param ageSignal 연령 조건 신호. UNRESTRICTED는 소스가 "연령 무관"을 <b>선언</b>했을 때만 씀(온통청년
 * {@code sprtTrgtAgeLmtYn='N'}). gov24는 이 선언 필드가 없어 UNRESTRICTED를 만들 수 없고 RESTRICTED 또는
 * UNKNOWN만 나옴
 * @param ageMin 대상 연령 하한(만 나이). RESTRICTED가 아니면 null
 * @param ageMax 대상 연령 상한(만 나이). RESTRICTED가 아니면 null
 * @param incomeSignal 소득 조건 신호. <b>gov24가 유일하게 쓸 만한 소스임</b>(JA0201에서 JA0205 중위소득 5구간, 전수
 * 유효 90.61%). 온통청년 earnCndSeCd는 83%가 "무관"이라 변별력이 없음. <b>하드 배제 필터로 쓰지 말 것</b> — 적대 검증에서 JA
 * 플래그가 선정기준 원문보다 좁게 잡힌 사례가 확인됨(중위소득 110% 한부모가 잘못 탈락). 소프트 랭킹 신호로 쓸지는 회의 안건 22번
 * @param householdSignal 가구 조건 신호. <b>gov24 전용임</b>(JA0401에서 JA0414 9필드, 전수 유효 90.75%).
 * 온통청년 sBizCd는 스키마에 있으나 500건 전부 공백이라 못 씀(함정 1의 실제 사례)
 * @param employmentSignal 고용 조건 신호. <b>온통청년이 낫음</b>({@code jobCd} 10종, 채움 100%). gov24
 * JA0326·JA0327은 신호 있는 레코드가 31.52%뿐이고 그중 다수가 둘 다 Y라 배타적 상태값이 아님 — 현재 gov24 파서는 이 두 필드를 아예
 * 읽지 않고 UNKNOWN으로 둠
 * @param employmentRawCode 고용 조건 원문 코드(온통청년 {@code jobCd}, 예 "0013010"). <b>이 코드를
 * {@code EmploymentStatus} enum으로 매핑하지 말 것</b> — 코드 의미를 정의한 온통청년 코드정의서 xlsx가 2026-07-12
 * 기준 <b>미확인 상태</b>임(외부API-통합가이드.md). 의미를 모르는 코드를 추측으로 매핑하면 고용 매칭이 조용히 틀림. 코드정의서 확보 전까지는
 * 원문 코드만 보존하고 매칭에 쓰지 않음
 */
public record NormalizedEligibility(EligibilitySignal ageSignal, Integer ageMin, Integer ageMax,
		EligibilitySignal incomeSignal, EligibilitySignal householdSignal, EligibilitySignal employmentSignal,
		String employmentRawCode) {

	/**
	 * 4축 모두 근거가 없는 상태임(기업 대상 소스의 기본값).
	 * @return 전 축 UNKNOWN인 자격조건
	 */
	public static NormalizedEligibility unknown() {
		return new NormalizedEligibility(EligibilitySignal.UNKNOWN, null, null, EligibilitySignal.UNKNOWN,
				EligibilitySignal.UNKNOWN, EligibilitySignal.UNKNOWN, null);
	}

}
