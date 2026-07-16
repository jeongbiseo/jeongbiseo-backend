package com.jeongbiseo.infra.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.infra.client.gov24.Gov24Parser;

/**
 * <b>gov24 대상 판정(개인 대 사업자)을 고정함.</b>
 *
 * <p>
 * 이 테스트의 절반은 <b>반대 방향 회귀</b>임. 기업 지원금을 걸러내는 규칙은 너무 세게 잡으면 <b>진짜 개인 지원금을 함께 죽임</b> — 금액이
 * 크다는 이유로 배제하면 미혼모 자립 1,500만원과 보호종료아동 1,000만원이 사라지고, 어휘로 배제하면 빈집재생 3,000만원이 사라짐. 그래서 배제
 * 케이스와 <b>유지 케이스를 같은 무게로</b> 박아 둠.
 */
class Gov24TargetAudienceTest {

	// 사용자구분 원문 -> 판정. 전수 10,974건에서 관측된 조합을 대표로 담음.
	@ParameterizedTest(name = "[{index}] 사용자구분 \"{0}\" -> {1}")
	@CsvSource({
			// 개인 축 단독
			"개인, PERSONAL", "가구, PERSONAL", "개인||가구, PERSONAL",
			// 사업자 축 단독
			"소상공인, BUSINESS", "법인/시설/단체, BUSINESS", "소상공인||법인/시설/단체, BUSINESS",
			// 두 축이 함께 켜짐 — BUSINESS로 뭉개면 개인이 실제로 받는 지원금이 죽으므로 MIXED로 남김
			"개인||소상공인, MIXED", "개인||법인/시설/단체, MIXED", "개인||가구||소상공인||법인/시설/단체, MIXED",
			// 알려진 어휘 밖 — BUSINESS로 추측하지 않고 사람이 보게 함
			"미래에 생길 새 어휘, UNKNOWN" })
	void classifyTargetAudience_mapsEveryObservedVocabulary(String userTypeText, TargetAudience expected) {
		assertThat(Gov24Parser.classifyTargetAudience(userTypeText)).isEqualTo(expected);
	}

	@Test
	void classifyTargetAudience_isUnknownWhenTheFieldIsAbsent() {
		// serviceDetail만 읽으면 이 필드가 없음. 없는 것을 개인으로 가정하면 기업 지원금이 합산에 섞임
		assertThat(Gov24Parser.classifyTargetAudience(null)).isEqualTo(TargetAudience.UNKNOWN);
		assertThat(Gov24Parser.classifyTargetAudience("  ")).isEqualTo(TargetAudience.UNKNOWN);
	}

	@Test
	void everyRealRecordIsClassified_becauseServiceListJoinFillsTheField() throws IOException {
		List<NormalizedSubsidy> gov24 = AllSourcesSnapshotFixture.loadGov24();

		long unknown = gov24.stream().filter(s -> s.targetAudience() == TargetAudience.UNKNOWN).count();

		assertThat(gov24).hasSize(1097);
		assertThat(unknown).as("join이 온전하면 UNKNOWN은 0건이어야 함. 0이 아니면 판정이 조용히 무너진 것임").isZero();
	}

	// 배제되어야 할 기업 대상. 금액과 지급유형 필드는 개인 지원금과 똑같이 정상이라 원문 대상을 봐야만 갈림.
	// 뒤의 3건은 1천만원 컷오프 아래라 사람이 하던 수동 검토가 놓쳤던 것이고, 사용자구분이 자동으로 잡아냄.
	@ParameterizedTest(name = "[{index}] {1} -> BUSINESS")
	@CsvSource({ "B55101400003, 스포츠 액셀러레이팅 5500만원", "142000000061, 중소기업 기술유출 방지 4000만원",
			"427000000239, 소상공인 경영환경 개선 800만원", "496000000109, 소상공인 카드수수료 40만원", "642000000735, 노란우산공제 신규가입 장려금 1만원" })
	void businessRecordsAreExcluded(String serviceId, String label) throws IOException {
		assertThat(audienceOf(serviceId)).as(label).isEqualTo(TargetAudience.BUSINESS);
	}

	// **반대 방향 회귀** — 이 4건이 개인으로 남아야 함. 금액이 크다는 이유로 기업 취급하면 전부 죽음.
	@ParameterizedTest(name = "[{index}] {1} -> PERSONAL 유지")
	@CsvSource({ "374000000115, 미혼모가족복지시설 퇴소자 자립 1500만원", "448000000122, 보호종료아동 자립정착금 1000만원",
			"569000000361, 보호종료아동 자립정착금(타 지자체)", "474100000036, 희망하우스 빈집재생 3000만원" })
	void personalRecordsSurvive_soTheFilterDoesNotKillRealSupport(String serviceId, String label) throws IOException {
		assertThat(audienceOf(serviceId)).as(label).isEqualTo(TargetAudience.PERSONAL);
	}

	private TargetAudience audienceOf(String serviceId) throws IOException {
		Map<String, TargetAudience> byId = AllSourcesSnapshotFixture.loadGov24()
			.stream()
			.filter(s -> s.source() == SubsidySource.GOV24)
			.collect(java.util.stream.Collectors.toMap(NormalizedSubsidy::externalId, NormalizedSubsidy::targetAudience,
					(first, second) -> first));
		assertThat(byId).as("스냅샷에 서비스ID가 있어야 함: " + serviceId).containsKey(serviceId);
		return byId.get(serviceId);
	}

}
