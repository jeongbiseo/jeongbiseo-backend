package com.jeongbiseo.infra.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.domain.common.enums.TargetAudience;

/**
 * <b>온통청년 수기 판정 목록을 고정함.</b>
 *
 * <p>
 * 온통청년은 지원대상 구분 필드를 주지 않아 사람이 SINGLE + CASH 72건을 전수 검토했음. 그 판정이 코드({@code
 * YouthcenterSubsidyNormalizer}의 상수)와 근거
 * 파일({@code fixtures/youthcenter_business_plcyno.json}) 두 곳에 있으므로, <b>둘이 어긋나면 빌드를 멈춤</b>
 * — 목록만 고치고 근거를 안 남기는 일을 막기 위함임.
 *
 * <p>
 * 나머지 절반은 <b>반대 방향 회귀</b>임. 기업 표지 낱말로 자동 판정하면 개인 지원금이 죽는 것을 실제 데이터로 고정함.
 */
class YouthcenterBusinessPolicyListTest {

	private static final Path EVIDENCE = Path.of("fixtures", "youthcenter_business_plcyno.json");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void codeListAndEvidenceFileAgree() throws IOException {
		List<String> evidenceIds = new ArrayList<>();
		JsonNode root = this.objectMapper.readTree(Files.readString(EVIDENCE, StandardCharsets.UTF_8));
		for (JsonNode policy : root.get("businessPolicies")) {
			evidenceIds.add(policy.get("plcyNo").asText());
			// 근거 없는 판정을 목록에 넣지 못하게 함
			assertThat(policy.get("reason").asText()).as("plcyNo " + policy.get("plcyNo").asText() + "의 판정 근거")
				.isNotBlank();
		}

		List<String> classifiedAsBusiness = audienceById().entrySet()
			.stream()
			.filter(e -> e.getValue() == TargetAudience.BUSINESS)
			.map(Map.Entry::getKey)
			.toList();

		assertThat(classifiedAsBusiness).as("코드 상수와 근거 파일의 목록이 같아야 함").containsExactlyInAnyOrderElementsOf(evidenceIds);
	}

	// 추천·개인 예상 총액 모집단에서 제외해야 하는 기업·단체 대상 정책 11건임(금액 NONE 포함).
	@ParameterizedTest(name = "[{index}] {1} -> BUSINESS")
	@CsvSource({ "20260416005400112760, 글로벌 액셀러레이팅 3000만", "20260330005400212309, 디지털분야 청년창업 육성 1000만",
			"20251107005400211811, 청년일자리 우수기업 1000만", "20260325005400212268, 2026 지역인재채용 인센티브 1000만",
			"20250901005400211548, 2025 지역인재채용 인센티브 1000만", "20250717005400211349, 청년활동경험지원 500만(청년단체)",
			"20260413005400212703, 청년활동경험지원 300만(청년단체)", "20251203005400211949, 청년 커뮤니티 동아리 100만",
			"20250901005400211554, 청송군 청년 소모임 100만", "20260331005400212358, 청년창업공간 리모델링 500만(예비창업가)",
			"20260416005400112761, K-스타트업 창업기업 대상 공고" })
	void businessPoliciesAreExcluded(String policyId, String label) throws IOException {
		assertThat(audienceById().get(policyId)).as(label).isEqualTo(TargetAudience.BUSINESS);
	}

	/**
	 * <b>반대 방향 회귀 — 기업 표지 낱말이 본문에 있어도 개인이 받는 돈임.</b>
	 *
	 * <p>
	 * 이 3건이 텍스트 키워드 자동 판정을 기각한 직접 근거임. {@code (동구) 청년 자격증 응시료 지원}은 원문이 "개인사업자는
	 * <b>제외</b>"인데 "사업자"라는 낱말 때문에 걸리고, {@code 청년 이사비 지원}은 "업체" 때문에 걸림. 규칙을 그렇게 짜면 <b>개인
	 * 지원금이 죽음</b>.
	 */
	@ParameterizedTest(name = "[{index}] {1} -> PERSONAL 유지")
	@CsvSource({ "20250430005400210736, 청년근로자 근속장려금(중소기업 재직은 조건일 뿐 돈은 청년이 받음)", "20260527005400213222, 청년근로자 교통비(2026)",
			"20260409005400212659, 고령군 청년근로자 교통비", "20260406005400212458, (동구) 청년 자격증 응시료(원문은 개인사업자 제외인데 사업자 낱말에 걸림)",
			"20250716005400211308, 청년 이사비 지원(업체 낱말에 걸림)",
			// 온통청년에는 gov24의 JA 플래그가 없어 1차산업 판정 대상이 아님. 농업 계열이라는 이유로 자동으로 자르면
			// 판정 근거 없이 개인 지원금이 사라짐
			"20250715005400211276, 청년 도내 농창업 지원(JA 판정 대상 밖이라 개인 후보로 잔류)" })
	void personalPoliciesSurviveDespiteBusinessKeywords(String policyId, String label) throws IOException {
		assertThat(audienceById().get(policyId)).as(label).isEqualTo(TargetAudience.PERSONAL);
	}

	private Map<String, TargetAudience> audienceById() throws IOException {
		List<NormalizedSubsidy> policies = new ArrayList<>(AllSourcesSnapshotFixture.loadYouthcenter());
		policies.addAll(AllSourcesSnapshotFixture.loadYouthcenterBusinessRegression());
		return policies.stream()
			.collect(Collectors.toMap(NormalizedSubsidy::externalId, NormalizedSubsidy::targetAudience,
					(first, second) -> first));
	}

}
