package com.jeongbiseo.infra.client.youthcenter;

import java.util.List;
import java.util.Set;

import com.jeongbiseo.infra.client.common.SubsidyNormalizer;
import com.jeongbiseo.infra.client.common.dto.DeadlineBasis;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.NormalizedEligibility;
import com.jeongbiseo.infra.client.common.dto.NormalizedRegion;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.infra.client.youthcenter.dto.ParsedYouthPolicy;

/**
 * 온통청년 파싱 결과({@link ParsedYouthPolicy})를 공통 타깃({@link NormalizedSubsidy})으로 변환함.
 *
 * <p>
 * <b>버려지는 것과 남는 것.</b> 온통청년 고유 코드 4종(특화요건 sbizCd·학력 schoolCd·전공 plcyMajorCd·결혼상태
 * mrgSttsCd)은 공통 타깃으로 올리지 않음. 넷 다 채움률 99% 이상의 실재 필드지만 매칭 4축(연령·소득·가구·고용) 어디에도 안 맞고, 공통
 * 타깃에 칸을 뚫으면 다른 3개 소스가 영구히 null인 칸을 이고 감. {@link ParsedYouthPolicy}에 원문 코드로 남아 있으므로 필요해지면
 * 그쪽을 보면 됨(공통 타깃 승격이 필요하다는 판단이 서면 그때 4종 소스 관점에서 결정할 것).
 *
 * <p>
 * <b>가구 조건은 이 소스로 못 채움.</b> 조사 리포트는 sbizCd를 가구 후보로 봤다가 "500건 전부 공백"이라며 기각했는데, 전수 실측 결과
 * <b>채움 100%</b>임(리포트가 필드명을 sBizCd로 잘못 세었을 가능성). 그럼에도 가구 신호로 쓰지 않는 이유는 채움률이 아니라 <b>축이 다르기
 * 때문</b>임 — 공식 코드정의서상 sbizCd는 "정책특화요건"이고 값이 중소기업·여성·기초생활수급자·한부모가정·장애인·농업인·군인·지역인재·기타·
 * 제한없음임. 가구(한부모가정)·소득(기초생활수급자)·인적특성(여성·장애인·군인)·지역(지역인재)·기업(중소기업)이 한 축에 뒤섞여 있어, 이걸
 * householdSignal로 읽으면 "여성 대상 정책"이 가구 조건으로 둔갑함. 따라서 householdSignal은 UNKNOWN이고 가구는 gov24
 * JA04 계열이 계속 담당함.
 */
public final class YouthcenterSubsidyNormalizer implements SubsidyNormalizer<ParsedYouthPolicy> {

	// 2026-07-13 사람이 SINGLE + CASH 72건을 전수 검토해 확정한 기업·단체 대상 목록임. 판정 근거는
	// fixtures/youthcenter_business_plcyno.json에 건별로 적혀 있고, 두 곳이 어긋나면
	// YouthcenterBusinessPolicyListTest가 빌드를 멈춤(목록만 고치고 근거를 안 남기는 일을 막음).
	private static final Set<String> BUSINESS_POLICY_IDS = Set.of(
			// 기업이 받는 돈 5건
			"20260416005400112760", // 글로벌 액셀러레이팅 지원사 3,000만
			"20260330005400212309", // 디지털분야 청년창업 육성 1,000만(대상이 "완주군 청년 기업")
			"20251107005400211811", // 청년일자리 우수기업 지원 1,000만(공장등록 중소기업)
			"20260325005400212268", // 2026 지역인재채용 인센티브 1,000만("지역기업에게 인센티브")
			"20250901005400211548", // 2025 지역인재채용 인센티브 1,000만
			// 단체·동아리가 받는 돈 4건 — 개인 수령액이 아니라 팀 활동비임
			"20250717005400211349", // 청년활동경험지원(2025) 500만("3인 이상 청년단체")
			"20260413005400212703", // 청년활동경험지원(2026) 300만
			"20251203005400211949", // 청년 커뮤니티(동아리) 활성화 100만
			"20250901005400211554", // 청송군 청년 소모임 100만
			// 창업자 지원 1건 — 창업자 배제 방침(2026-07-13 사용자 확정)
			"20260331005400212358", // 청년창업공간 리모델링 500만(초기 청년창업가·예비창업가)
			// 금액 NONE 전수 검토 범위 밖에서 QA로 확인한 기업 공고 1건
			"20260416005400112761"); // K-스타트업 창업기업 대상 공고

	@Override
	public SubsidySource source() {
		return SubsidySource.YOUTHCENTER;
	}

	@Override
	public NormalizedSubsidy normalize(ParsedYouthPolicy raw) {
		return new NormalizedSubsidy(SubsidySource.YOUTHCENTER, raw.policyId(), raw.name(), raw.agency(),
				raw.description(), raw.eligibilityText(), raw.categoryRawText(), raw.paymentType(),
				raw.provisionMethodCode(), raw.amount(), raw.deadline(), toDeadlineBasis(raw), toNormalizedRegion(raw),
				toNormalizedEligibility(raw), raw.applicationMethod(), raw.applicationUrl(), raw.referenceUrl(),
				raw.requiredDocumentsText(), raw.dataUpdatedAt(), toTargetAudience(raw.policyId()),
				// 온통청년은 JA 플래그 같은 직업군 판정 근거를 주지 않음. 없는 판정을 지어내지 않음
				OccupationRestriction.NONE);
	}

	/**
	 * 대상 판정임. <b>온통청년은 지원대상 구분 필드를 주지 않음</b>(공식 명세 약 50개 필드 전수 확인). 다만 소스 자체가 청년 개인 정책
	 * 포털이라 스코프가 개인으로 고정돼 있으므로 <b>기본값이 PERSONAL이고, 예외만 수기 목록으로 뒤집음</b>.
	 *
	 * <p>
	 * <b>텍스트 키워드 자동 판정을 쓰지 않는 이유</b>: 오탐이 남. {@code (동구) 청년 자격증 응시료 지원}은 원문이 "개인사업자는
	 * <b>제외</b>"인데 "사업자"라는 낱말 때문에 걸리고, {@code 청년 이사비 지원}은 "업체" 때문에 걸림. 개인 지원금을 죽이는 규칙이 됨.
	 *
	 * <p>
	 * 대신 SINGLE + CASH 72건을 사람이 전수 검토해 BUSINESS 10건을 확정했고, QA에서 확인한 금액 NONE 기업 공고 1건을
	 * 추가함. 목록은 {@code fixtures/youthcenter_business_plcyno.json}에 근거와 함께 있음. 72건은 한 사람이 한
	 * 시간이면 훑는 규모라 오탐이 나는 규칙 엔진보다 싸고 정확함(ponytail).
	 *
	 * <p>
	 * <b>수용된 한계</b>: 금액 NONE 공고는 전수 검토하지 않았음. 이번 QA 확인 건 외에도 기업 공고가 PERSONAL로 추천에 남을 수
	 * 있음.
	 */
	private static TargetAudience toTargetAudience(String policyId) {
		return BUSINESS_POLICY_IDS.contains(policyId) ? TargetAudience.BUSINESS : TargetAudience.PERSONAL;
	}

	// 온통청년 마감은 aplyPrdSeCd라는 구조화 코드가 근거임(파싱이 아니라 매핑) — gov24가 자유텍스트 정규식으로 얻는 같은
	// DeadlineKind와 신뢰도가 다르다는 사실이 여기 남음. 코드 자체가 비면 판정 근거가 없다는 뜻이라 NOT_APPLICABLE로 구분함
	// (전수 2,648건에서 0건이지만 "코드가 없는 것"과 "코드가 UNKNOWN인 것"을 섞지 않음).
	private static DeadlineBasis toDeadlineBasis(ParsedYouthPolicy raw) {
		return raw.applicationPeriodCode() == null ? DeadlineBasis.NOT_APPLICABLE : DeadlineBasis.DECLARED_FIELD;
	}

	// 이 소스는 4종 중 유일하게 지역을 **선언된 코드**로 줌(법정시군구코드 5자리). 그래서 여기서만
	// DECLARED_REGION_CODE와 HIGH를 씀 — 하드 지역 필터에 쓸 수 있는 유일한 근거임.
	// 시도명·시군구명은 채우지 않음. 코드에서 명칭을 역산하려면 법정동코드 시드 테이블이 필요한데 아직 없고,
	// 주관기관명(sprvsnInstCdNm)에는 "청년정책담당관"처럼 지역이 아닌 값이 섞여 있어 거기서 유추하면 오히려 코드보다
	// 나쁜 값이 됨(없는 정보를 지어내지 않음).
	private static NormalizedRegion toNormalizedRegion(ParsedYouthPolicy raw) {
		List<String> codes = raw.regionCodes();
		if (codes.isEmpty()) {
			return NormalizedRegion.national();
		}
		return new NormalizedRegion(codes, null, null, RegionLevel.SIGUNGU, RegionScopeBasis.DECLARED_REGION_CODE,
				RegionConfidence.HIGH);
	}

	// 4축 중 이 소스가 채우는 것은 연령·소득·고용 셋이고 가구는 UNKNOWN임(위 클래스 Javadoc 참조).
	private static NormalizedEligibility toNormalizedEligibility(ParsedYouthPolicy raw) {
		return new NormalizedEligibility(raw.ageSignal(), raw.ageMin(), raw.ageMax(), raw.incomeSignal(),
				EligibilitySignal.UNKNOWN, raw.employmentSignal(), raw.employmentRawCode());
	}

}
