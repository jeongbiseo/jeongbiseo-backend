package com.jeongbiseo.infra.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;

/**
 * <b>1차산업 전용 판정의 truth table을 실측 조합으로 고정함.</b>
 *
 * <p>
 * 이 판정은 제품 타깃(20~30대 청년·사회초년생) 밖의 지원금을 추천 스코프에서 자름. 규칙이 한 문장이라 단순해 보이지만, <b>경계에서 틀리면 반대로
 * 개인 지원금을 죽임</b> — 특히 JA0322(해당사항없음)를 "제한"으로 오독하면 유아학비(누리과정)가 사라짐. 그래서 표본 1,097건에서 관측된 조합
 * 6종을 전부 케이스로 박음.
 */
class Gov24OccupationRestrictionTest {

	/**
	 * 실측 truth table (gov24 표본 1,097건).
	 *
	 * <ul>
	 * <li>빈 집합 83건 — 데이터 없음. 통과</li>
	 * <li>17개 전부 켜짐 245건 — 제한 없음. 통과</li>
	 * <li>JA0322 단독 122건 — 해당사항없음. 통과(유아학비가 여기 있음)</li>
	 * <li>JA0322 혼합 67건 — 통과</li>
	 * <li><b>1차산업 전용 177건 — 제외</b></li>
	 * <li>그 외 제한형 403건(장애인·보훈·학생 등) — 통과</li>
	 * </ul>
	 */
	@ParameterizedTest(name = "[{index}] {2}")
	@CsvSource({
			// 1차산업 전용 — 유일한 제외 대상
			"119200000073, PRIMARY_INDUSTRY_ONLY, 어업인 후계자 교육지원(JA0314 단독)",
			"119200000111, PRIMARY_INDUSTRY_ONLY, 수산동물질병 예방백신 공급(JA0314 단독)",
			// JA0322 단독 — 해당사항없음은 제한이 아님. 제한으로 읽으면 이 지원금이 죽음
			"000000465790, NONE, 유아학비(누리과정) — JA0322 단독",
			// 그 외 제한형 — 장애인·보훈·학생 전용은 자르지 않음(장애인 청년도 우리 사용자임)
			"135200000103, NONE, 장애인법률구조지원(JA0328 단독이지만 통과)",
			// 17개 전부 켜짐 = 제한 없음
			"134200000045, NONE, 평생교육바우처(17개 전부 켜짐)",
			// 빈 집합 = 데이터 없음. 없는 제한을 지어내지 않음
			"119200000027, NONE, 산지 유통자금 융자(플래그 없음)" })
	void occupationRestrictionFollowsTheMeasuredTruthTable(String serviceId, OccupationRestriction expected,
			String label) throws IOException {
		Map<String, OccupationRestriction> byId = restrictionById();
		assertThat(byId).as("스냅샷에 서비스ID가 있어야 함: " + serviceId).containsKey(serviceId);
		assertThat(byId.get(serviceId)).as(label).isEqualTo(expected);
	}

	@Test
	void primaryIndustryOnlyMatchesTheMeasuredCount() throws IOException {
		long primaryIndustry = AllSourcesSnapshotFixture.loadGov24()
			.stream()
			.filter(s -> s.occupationRestriction() == OccupationRestriction.PRIMARY_INDUSTRY_ONLY)
			.count();

		// 표본 1,097건 실측치임. 이 수가 흔들리면 판정 규칙이나 스냅샷이 바뀐 것이므로 사람이 확인해야 함
		assertThat(primaryIndustry).as("1차산업 전용 건수(표본 1,097건 기준 실측 177건)").isEqualTo(177);
	}

	@Test
	void otherSourcesHaveNoOccupationEvidence_soTheyAllPass() throws IOException {
		// gov24만 JA 플래그를 줌. 나머지 3종은 판정 근거가 없으므로 전부 NONE이어야 함 —
		// 없는 제한을 지어내면 온통청년의 청년 농창업 지원 같은 것이 근거 없이 사라짐
		long restrictedOutsideGov24 = AllSourcesSnapshotFixture.loadAll()
			.stream()
			.filter(s -> s.source() != com.jeongbiseo.infra.client.common.dto.SubsidySource.GOV24)
			.filter(s -> s.occupationRestriction() != OccupationRestriction.NONE)
			.count();

		assertThat(restrictedOutsideGov24).as("gov24 외 소스는 직업군 판정 근거가 없어 전부 NONE이어야 함").isZero();
	}

	private Map<String, OccupationRestriction> restrictionById() throws IOException {
		return AllSourcesSnapshotFixture.loadGov24()
			.stream()
			.collect(Collectors.toMap(NormalizedSubsidy::externalId, NormalizedSubsidy::occupationRestriction,
					(first, second) -> first));
	}

}
