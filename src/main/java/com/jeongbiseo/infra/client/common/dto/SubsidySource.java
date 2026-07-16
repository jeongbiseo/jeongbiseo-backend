package com.jeongbiseo.infra.client.common.dto;

/**
 * 지원금 1건이 어느 수집 출처에서 왔는지임. 데이터모델 4.1의 {@code subsidy.source_id VARCHAR(30)} 컬럼과 1대1로
 * 대응하며, {@code (source_id, external_id)} 복합 유니크가 upsert 키임(데이터소스연동.md 2.3).
 *
 * <p>
 * <b>레코드 단위 출처가 provenance의 1차 축임.</b> 필드마다 출처 래퍼를 씌우지 않고 이 값 하나로 "누가 준 데이터인가"를 답하는 이유는,
 * 4종 소스가 필드를 <b>겹쳐서</b> 주는 경우가 거의 없기 때문임 — 한 레코드는 한 소스에서만 오고(교차 소스 중복은 별도 행으로 들어온 뒤
 * {@code duplicate_of_id}로 대표 행을 지정함, 데이터모델 4.4), 소스가 다르면 강점도 통째로 다름. 필드 단위 출처가 실제로 필요한
 * 곳은 <b>같은 의미를 소스마다 다른 신뢰도로 주는 3개 축(지역·마감·금액)</b>뿐이고, 그 셋만 각각
 * {@link RegionScopeBasis}·{@link DeadlineBasis}· {@link AmountParseStatus}로 근거를 남김.
 */
public enum SubsidySource {

	// 보조금24(중앙·지자체 종합). 강점은 소득·가구 구조화(JA 플래그)와 신청방법·구비서류 채움 100%.
	// 약점은 지역(코드 없음, 소관기관명 유추뿐)과 마감일(자유텍스트, 날짜 파싱 성공 0.18%)
	GOV24("gov24"),

	// 온통청년(청년 정책 한정). 강점은 지역(zipCd 법정시군구코드 5자리, 채움 99.3%)·연령(숫자형 100%)·
	// 고용(jobCd 100%)·마감 상태(aplyPrdSeCd 3종 코드). 약점은 소득(83%가 "무관")과 가구(sBizCd 전부 공백)
	YOUTHCENTER("youthcenter"),

	// 기업마당(중소기업·소상공인). 강점은 신청기간 날짜쌍(87.2%). 약점은 개인 자격조건 전무(기업 대상)
	BIZINFO("bizinfo"),

	// K-Startup(창업). 강점은 마감일·시작일(YYYYMMDD 구조화, 800건 표본 전부 정상)과 신청방법 6필드 분리.
	// 약점은 개인 자격조건 전무(기업·창업 대상)이고 연령도 텍스트 구간
	KSTARTUP("kstartup"),

	// 수기 시드(외부 API가 못 주는 금액 등을 사람이 채운 행). external_id는 "SEED-0001" 형식
	SEED("seed");

	private final String sourceId;

	SubsidySource(String sourceId) {
		this.sourceId = sourceId;
	}

	/**
	 * 데이터모델 {@code subsidy.source_id} 컬럼에 저장하는 문자열임.
	 * @return 출처 식별자(예 "gov24")
	 */
	public String sourceId() {
		return this.sourceId;
	}

}
