package com.jeongbiseo.infra.enrichment;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.enrichment.dto.AmountEnrichment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EnrichmentPrompt 단위 테스트임. 프롬프트 문구 전체를 고정하지는 않음 — 문구는 스모크 결과에 따라 계속 손보는 대상이라 통째로 박으면 개선할
 * 때마다 테스트가 깨져 무의미해짐. 대신 <b>깨지면 조용히 망가지는 두 가지</b>만 고정함: 스키마와 record의 필드 일치, 그리고 인젝션 방어 구조.
 */
class EnrichmentPromptTest {

	/**
	 * 스키마 필드와 record 필드가 어긋나면 역직렬화가 조용히 null을 채우고 검증기가 엉뚱한 사유로 거부함. 한쪽만 고치는 실수가 실제로 일어나는
	 * 지점이라 리플렉션으로 묶어 둠.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void 스키마_필드는_출력_record_필드와_정확히_일치한다() {
		Map<String, Object> schema = EnrichmentPrompt.jsonSchema();
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

		List<String> recordFields = Arrays.stream(AmountEnrichment.class.getRecordComponents())
			.map(RecordComponent::getName)
			.toList();

		assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(recordFields);
		// 모든 필드가 required여야 모델이 일부를 누락하지 못함. 누락된 필드는 null로 와서 검증기가 "필수 enum 필드가 비어 있음"
		// 같은 엉뚱한 사유를 내보냄.
		assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrderElementsOf(recordFields);
	}

	/**
	 * 스키마에 없는 필드를 모델이 덧붙이면 역직렬화에서 조용히 버려져 눈치채기 어려우므로 막아 둠.
	 */
	@Test
	void 스키마는_추가_필드를_허용하지_않는다() {
		assertThat(EnrichmentPrompt.jsonSchema()).containsEntry("additionalProperties", false);
	}

	/**
	 * enum 허용값이 계약과 어긋나면 모델이 계약 밖 값을 낼 수 있음. 2026-07-20 실측에서 모델이 계약에 없는 "RANGE"를 낸 전례가 있어
	 * 허용값을 명시적으로 고정함.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void amountKind_허용값은_파서_계약과_같다() {
		Map<String, Object> properties = (Map<String, Object>) EnrichmentPrompt.jsonSchema().get("properties");
		Map<String, Object> amountKind = (Map<String, Object>) properties.get("amountKind");

		assertThat((List<String>) amountKind.get("enum")).containsExactlyInAnyOrder("NONE", "SINGLE", "MULTIPLE",
				"CONDITIONAL");
	}

	/**
	 * 공고 본문이 데이터 구역으로 감싸이지 않으면 본문 안의 지시문이 프롬프트로 읽힘. 스모크 20건의 적대 사례에 인젝션 가짜 공고가 들어 있어 이
	 * 구조가 실제 방어선임.
	 */
	@Test
	void 사용자_프롬프트는_공고_본문을_데이터_구역으로_감싼다() {
		String prompt = EnrichmentPrompt.userPrompt("청년 월세 지원", "지원내용: 월 20만원");

		assertThat(prompt).contains("<notice>").contains("</notice>");
		// 본문이 태그 안에 들어가야 의미가 있음. 태그만 있고 본문이 밖에 있으면 방어가 성립하지 않음.
		int open = prompt.indexOf("<notice>");
		int close = prompt.indexOf("</notice>");
		assertThat(prompt.substring(open, close)).contains("지원내용: 월 20만원").contains("청년 월세 지원");
	}

	@Test
	void 시스템_프롬프트는_본문_지시를_따르지_말라고_명시한다() {
		String prompt = EnrichmentPrompt.systemPrompt();

		assertThat(prompt).contains("신뢰할 수 없는 데이터").contains("따르지 않는다");
		// 근거를 원문에서 복사하라는 지시가 빠지면 모델이 재서술하고, 재서술은 검증기가 전부 폐기해 기권율이 치솟음.
		assertThat(prompt).contains("그대로 있는 문장을 복사");
	}

	@Test
	void 버전과_스키마_이름이_비어_있지_않다() {
		assertThat(EnrichmentPrompt.VERSION).isNotBlank();
		assertThat(EnrichmentPrompt.SCHEMA_NAME).isNotBlank();
	}

}
