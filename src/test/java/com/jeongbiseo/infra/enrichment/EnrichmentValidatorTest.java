package com.jeongbiseo.infra.enrichment;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.enrichment.dto.PaymentPeriod;
import com.jeongbiseo.infra.enrichment.dto.RejectionReason;
import com.jeongbiseo.infra.enrichment.dto.ValidationResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EnrichmentValidator 단위 테스트임. 이 검증기가 환각을 거르는 유일한 기계적 방어선이라, 거부 경로뿐 아니라 <b>정상 결과가
 * 통과하는지</b>도 함께 고정함 — 거부만 테스트하면 전부 거부하는 검증기도 통과해 버림.
 */
class EnrichmentValidatorTest {

	private static final String HASH = "sha256-abc";

	private static final String BODY = "안양시 청년 월세 지원 사업\n지원내용: 월 20만원을 최대 12개월간 지원합니다.\n대상: 만 19~34세 무주택 청년";

	private final EnrichmentValidator validator = new EnrichmentValidator(new ObjectMapper());

	private ValidationResult validate(String json) {
		return this.validator.validate(json, BODY, HASH, HASH);
	}

	private static String json(String amountKind, String paymentPeriod, String amountValue, String monthlyAmount,
			String durationMonths, String conditionExpression, String evidence, boolean abstained,
			String abstainReason) {
		return """
				{"amountKind":"%s","paymentPeriod":"%s","amountValue":%s,"monthlyAmount":%s,\
				"durationMonths":%s,"conditionExpression":%s,"evidence":"%s","abstained":%s,"abstainReason":%s}"""
			.formatted(amountKind, paymentPeriod, amountValue, monthlyAmount, durationMonths, conditionExpression,
					evidence, abstained, abstainReason);
	}

	/** 월 지급 정상 건임. 이것이 떨어지면 검증기가 과하게 조인 것이므로 다른 테스트보다 먼저 의심할 것. */
	@Test
	void 정상_월지급_결과는_통과한다() {
		String body = json("SINGLE", "MONTHLY", "null", "200000", "12", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isTrue();
		assertThat(result.value().amountKind()).isEqualTo(AmountKind.SINGLE);
		assertThat(result.value().paymentPeriod()).isEqualTo(PaymentPeriod.MONTHLY);
		assertThat(result.value().monthlyAmount()).isEqualTo(200000L);
		assertThat(result.value().durationMonths()).isEqualTo(12);
	}

	/**
	 * 모델이 원문의 줄바꿈·연속 공백을 정리해 인용하는 것은 흔한 일이라 통과시켜야 함. 이 테스트가 깨지면 정규화(NFC 더하기 공백 압축)가 빠진
	 * 것이고, 그 상태로 배치를 돌리면 정상 결과가 대량 폐기되어 기권율이 허위로 치솟음.
	 */
	@Test
	void 근거의_공백_차이는_정규화해_통과시킨다() {
		String body = json("SINGLE", "MONTHLY", "null", "200000", "12", "null", "지원내용:  월 20만원을   최대 12개월간 지원합니다.",
				false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isTrue();
	}

	@Test
	void 원문에_없는_근거는_폐기한다() {
		String body = json("SINGLE", "LUMP_SUM", "500000", "null", "null", "null", "일시금 50만원을 지급합니다.", false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.EVIDENCE_NOT_IN_SOURCE);
	}

	@Test
	void 원문이_바뀌었으면_결과를_쓰지_않는다() {
		String body = json("SINGLE", "MONTHLY", "null", "200000", "12", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = this.validator.validate(body, BODY, HASH, "sha256-changed");

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.CONTENT_CHANGED);
	}

	@Test
	void 기권은_실패가_아니라_기권_사유로_남는다() {
		String body = json("NONE", "UNKNOWN", "null", "null", "null", "null", "", true, "\"금액 표현이 모호함\"");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.ABSTAINED);
		assertThat(result.detail()).isEqualTo("금액 표현이 모호함");
	}

	@Test
	void 대출_한도를_지급액으로_판정하면_거부한다() {
		String loanBody = "청년 머물자리론\n대출한도 : 최대 1억원";
		String body = json("SINGLE", "LUMP_SUM", "100000000", "null", "null", "null", "대출한도 : 최대 1억원", false, "null");

		ValidationResult result = this.validator.validate(body, loanBody, HASH, HASH);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.POLICY_VIOLATION);
	}

	/**
	 * 과잉 거부 방지임. "대출이자 지원"의 금액은 실제로 받는 돈이라 통과해야 함 — 여기서 거부하면 누락(거짓 음성)이 생기고, 누락은 사용자가
	 * 놓쳤다는 사실조차 모르는 최대 위험임(판정원칙 1번).
	 */
	@Test
	void 대출이자_지원금은_지급액이므로_통과한다() {
		String interestBody = "청년 대출이자 지원\n지원내용: 대출이자 지원 최대 100만원";
		String body = json("SINGLE", "LUMP_SUM", "1000000", "null", "null", "null", "대출이자 지원 최대 100만원", false, "null");

		ValidationResult result = this.validator.validate(body, interestBody, HASH, HASH);

		assertThat(result.accepted()).isTrue();
	}

	@Test
	void 월지급인데_월액이_없으면_거부한다() {
		String body = json("SINGLE", "MONTHLY", "null", "null", "12", "null", "월 20만원을 최대 12개월간 지원합니다.", false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	@Test
	void 월지급이_아닌데_월액이_채워지면_거부한다() {
		String body = json("SINGLE", "LUMP_SUM", "null", "200000", "null", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	@Test
	void 조건부인데_조건_표현이_없으면_거부한다() {
		String body = json("CONDITIONAL", "PER_UNIT", "1000000", "null", "null", "null", "월 20만원을 최대 12개월간 지원합니다.",
				false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	@Test
	void 금액_없음인데_금액이_채워지면_거부한다() {
		String body = json("NONE", "UNKNOWN", "500000", "null", "null", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	@Test
	void 금액이_0_이하면_거부한다() {
		String body = json("SINGLE", "LUMP_SUM", "0", "null", "null", "null", "월 20만원을 최대 12개월간 지원합니다.", false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	@Test
	void 기권하지_않았는데_근거가_비면_거부한다() {
		String body = json("SINGLE", "LUMP_SUM", "500000", "null", "null", "null", "", false, "null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
	}

	/**
	 * 종신·평생 지급은 총액 개념 자체가 없음(판정원칙 7번). 주기를 모르는데 개월 수를 채우면 없는 총액이 만들어지므로 막음. 판정원칙 3번이 "종신
	 * 수당을 임의 지급 기간으로 환산"을 LLM 금지 행위로 명시한 것의 기계적 구현임.
	 *
	 * <p>
	 * 스모크 표본에 종신 사례가 없어 이 경로는 실제 모델 응답으로 검증되지 않았음. 프롬프트가 안 먹어도 여기서 막히는지를 단위로 고정해 둠.
	 * </p>
	 */
	@Test
	void 주기를_모르는데_기간이_채워지면_거부한다() {
		String body = json("SINGLE", "UNKNOWN", "300000", "null", "12", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.FIELD_RULE_VIOLATION);
		assertThat(result.detail()).contains("기간");
	}

	/** 종신 지급을 기간 없이 답하는 것은 정상이므로 통과해야 함(위 규칙이 과잉 거부로 번지지 않는지 확인). */
	@Test
	void 주기를_모르고_기간도_비면_통과한다() {
		String body = json("SINGLE", "UNKNOWN", "300000", "null", "null", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		assertThat(validate(body).accepted()).isTrue();
	}

	@Test
	void 파싱되지_않는_JSON은_스키마_위반으로_거부한다() {
		ValidationResult result = validate("{\"amountKind\": \"SINGLE\", 깨진");

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.SCHEMA_INVALID);
	}

	@Test
	void 계약에_없는_enum_값은_스키마_위반으로_거부한다() {
		// 실측 사례 -- 2026-07-20 curl 검증에서 모델이 계약에 없는 "RANGE"를 냈음. 스키마 강제를 걸어도 서버가
		// 무시하는 경로가 있으므로 검증기가 마지막 방어선임.
		String body = json("RANGE", "MONTHLY", "null", "200000", "12", "null", "월 20만원을 최대 12개월간 지원합니다.", false,
				"null");

		ValidationResult result = validate(body);

		assertThat(result.accepted()).isFalse();
		assertThat(result.reason()).isEqualTo(RejectionReason.SCHEMA_INVALID);
	}

}
