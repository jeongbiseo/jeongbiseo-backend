package com.jeongbiseo.infra.enrichment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.enrichment.dto.AmountEnrichment;
import com.jeongbiseo.infra.enrichment.dto.PaymentPeriod;
import com.jeongbiseo.infra.enrichment.dto.RejectionReason;
import com.jeongbiseo.infra.enrichment.dto.ValidationResult;

/**
 * LLM 보강 결과 검증기임. <b>모델 출력을 신뢰해 검증을 생략하지 않는다</b>는 책임 경계(배치 설계 2장)의 구현체이며, 여기를 통과한 결과만 활성
 * 저장함.
 *
 * <p>
 * 검사 5종을 배치 설계 6장 순서대로 수행함 — 스키마·enum, 필드 간 규칙, 근거 부분문자열, 원문 해시 일치, 정책 불변식. 어느 하나라도 실패하면
 * 저장하지 않고 사유만 남김.
 * </p>
 *
 * <p>
 * <b>이 클래스를 느슨하게 고치지 말 것.</b> 검증이 헐거워지면 환각이 그대로 사용자 화면에 나가는데, 등급 1은 근거 문장을 함께 보여주는 구조라 틀린
 * 근거가 붙으면 오히려 신뢰를 깎음.
 * </p>
 */
@Component
public class EnrichmentValidator {

	// 문장 속 금액 표기를 뽑는 패턴임. 한글 수사(5백만원)를 받는 것이 핵심 — 공고 원문이 실제로 그렇게 적혀 있고(2026-07-20
	// 실측: "대출한도: 최대 5백만원") 이를 못 읽으면 정상 결과가 금액 불일치로 대량 거부됨. 순서가 중요해 긴 단위를 앞에 둠
	// ("천만"이 "천"보다 먼저 와야 1천만원을 1천원으로 읽지 않음).
	private static final Pattern AMOUNT_LITERAL = Pattern.compile("([0-9][0-9,]*)\\s*(억|천만|백만|만|천)?\\s*원");

	// 정책 어휘를 볼 때 근거 문장 앞으로 함께 살필 글자 수임. 좁게 잡은 것은 의도이며 근거는
	// evidenceWithLeadingContext Javadoc 참조.
	private static final int POLICY_CONTEXT_CHARS = 40;

	// 지급액이 아닌 금액을 지급액으로 판정했는지 보는 어휘임. 좁게 잡은 것은 의도임 -- 넓히면 "대출이자 지원 최대 100만원"처럼
	// 진짜 지급액인 건까지 거부해 누락(거짓 음성)이 생기고, 누락은 이 프로젝트에서 최대 위험임(판정원칙 1번). 한도·총액을
	// 직접 가리키는 표현만 넣고, "대출"·"융자" 같은 낱말 단독은 넣지 않음.
	private static final List<String> POLICY_FORBIDDEN_PHRASES = List.of("대출한도", "대출 한도", "대출최대한도", "대출 최대한도", "융자한도",
			"융자 한도", "대출잔액", "보증한도", "보증 한도", "총사업비", "사업예산", "사업비 예산", "자부담금", "자부담 금액");

	private final ObjectMapper objectMapper;

	public EnrichmentValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * 보강 결과를 검증함.
	 * @param rawJson 모델이 낸 JSON 문자열
	 * @param noticeBody 요청에 실었던 공고 본문(근거 대조 원본)
	 * @param contentHashAtRequest 요청 시점의 본문 해시
	 * @param contentHashNow 저장 직전의 본문 해시
	 * @return 검증 판정
	 */
	public ValidationResult validate(String rawJson, String noticeBody, String contentHashAtRequest,
			String contentHashNow) {
		// 원문이 그 사이 바뀌었으면 결과가 무엇이든 낡은 것이라 파싱할 것도 없이 거부함(배치 설계 7장 "처리 중 원문 변경").
		if (!Objects.equals(contentHashAtRequest, contentHashNow)) {
			return ValidationResult.reject(RejectionReason.CONTENT_CHANGED, "요청 시점 해시와 저장 직전 해시가 다름");
		}

		AmountEnrichment value;
		try {
			value = this.objectMapper.readValue(rawJson, AmountEnrichment.class);
		}
		catch (RuntimeException exception) {
			// 모델 원본 메시지는 내부 클래스명을 흘리므로 예외 종류만 남김.
			return ValidationResult.reject(RejectionReason.SCHEMA_INVALID,
					"JSON 파싱 실패: " + exception.getClass().getSimpleName());
		}
		if (value == null || value.amountKind() == null || value.paymentPeriod() == null) {
			return ValidationResult.reject(RejectionReason.SCHEMA_INVALID, "필수 enum 필드가 비어 있음");
		}

		// 기권은 실패가 아니라 정상 동작임. 값 검증을 더 하지 않고 여기서 끝냄 -- 기권한 결과의 값 필드는 애초에 신뢰 대상이 아님.
		if (value.abstained()) {
			String reason = (value.abstainReason() == null || value.abstainReason().isBlank()) ? "사유 미기재"
					: value.abstainReason();
			return ValidationResult.reject(RejectionReason.ABSTAINED, reason);
		}

		ValidationResult fieldRuleFailure = checkFieldRules(value);
		if (fieldRuleFailure != null) {
			return fieldRuleFailure;
		}

		if (!isEvidenceInSource(value.evidence(), noticeBody)) {
			return ValidationResult.reject(RejectionReason.EVIDENCE_NOT_IN_SOURCE, "근거 문장이 원문에 없음");
		}

		String violatedPhrase = findForbiddenPhrase(evidenceWithLeadingContext(value.evidence(), noticeBody));
		if (violatedPhrase != null) {
			return ValidationResult.reject(RejectionReason.POLICY_VIOLATION,
					"지급액이 아닌 금액을 지급액으로 판정함: " + violatedPhrase);
		}

		String amountMismatch = findAmountMismatch(value);
		if (amountMismatch != null) {
			return ValidationResult.reject(RejectionReason.AMOUNT_NOT_IN_EVIDENCE, amountMismatch);
		}

		return ValidationResult.accept(value);
	}

	/**
	 * 모델이 답한 금액이 근거 문장에 실제로 적힌 금액과 맞는지 봄.
	 *
	 * <p>
	 * <b>이 검사가 필요한 이유</b>: 근거 부분문자열 검사는 "이 문장이 원문에 있는가"만 보므로, 근거는 진짜인데 값만 틀린 경우를 잡지 못함.
	 * 예를 들어 근거가 "월 2만원 지원"인데 monthlyAmount를 200000으로 답하면 자릿수 오독이 그대로 화면에 나감. 검증기가 잡을 수 있는
	 * 의미 오류 중 가장 위험한 축이라 결정론적으로 막음.
	 * </p>
	 *
	 * <p>
	 * <b>근거에서 금액을 하나도 못 찾으면 통과시킴.</b> 표기가 특이해 파싱에 실패했을 뿐 결과는 정상일 수 있는데, 여기서 거부하면 누락이 생김 —
	 * 누락은 사용자가 놓친 사실조차 모르는 최대 위험임(판정원칙 1번). 반대로 금액을 찾았는데 어느 것과도 안 맞으면 오독이 확실하므로 거부함.
	 * </p>
	 * @return 불일치 사유. 문제 없으면 null
	 */
	private static String findAmountMismatch(AmountEnrichment value) {
		Long claimed = (value.monthlyAmount() != null) ? value.monthlyAmount() : value.amountValue();
		if (claimed == null) {
			return null;
		}
		Set<Long> inEvidence = parseAmounts(value.evidence());
		if (inEvidence.isEmpty()) {
			return null;
		}
		if (inEvidence.contains(claimed)) {
			return null;
		}
		return "금액 " + claimed + "이 근거 문장의 금액 " + inEvidence + "와 다름";
	}

	/**
	 * 문장에서 원 단위 금액을 모두 뽑음. "5백만원"처럼 한글 수사가 섞인 표기를 받아야 함 — 실측에서 공고 원문이 그렇게 적혀 있었고, 이를 못
	 * 읽으면 정상 결과가 대량 거부됨.
	 */
	private static Set<Long> parseAmounts(String text) {
		Set<Long> amounts = new LinkedHashSet<>();
		if (text == null) {
			return amounts;
		}
		Matcher matcher = AMOUNT_LITERAL.matcher(normalize(text));
		while (matcher.find()) {
			long base = Long.parseLong(matcher.group(1).replace(",", ""));
			amounts.add(base * unitMultiplier(matcher.group(2)));
		}
		return amounts;
	}

	private static long unitMultiplier(String unit) {
		if (unit == null || unit.isBlank()) {
			return 1L;
		}
		return switch (unit.strip()) {
			case "억" -> 100_000_000L;
			case "천만" -> 10_000_000L;
			case "백만" -> 1_000_000L;
			case "만" -> 10_000L;
			case "천" -> 1_000L;
			default -> 1L;
		};
	}

	/**
	 * 필드 간 규칙을 검사함. 스키마만으로는 막을 수 없는 조합(예 주기가 월이 아닌데 월액이 채워짐)을 여기서 거름.
	 */
	private static ValidationResult checkFieldRules(AmountEnrichment value) {
		if (value.evidence() == null || value.evidence().isBlank()) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "기권하지 않았는데 근거가 비어 있음");
		}
		if (value.paymentPeriod() == PaymentPeriod.MONTHLY && value.monthlyAmount() == null) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "월 지급인데 월액이 없음");
		}
		if (value.paymentPeriod() != PaymentPeriod.MONTHLY && value.monthlyAmount() != null) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "월 지급이 아닌데 월액이 채워짐");
		}
		if (value.amountKind() == AmountKind.CONDITIONAL
				&& (value.conditionExpression() == null || value.conditionExpression().isBlank())) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "조건부인데 조건 표현이 없음");
		}
		if (value.amountKind() != AmountKind.CONDITIONAL && value.conditionExpression() != null) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "조건부가 아닌데 조건 표현이 채워짐");
		}
		if (value.amountKind() == AmountKind.NONE && (value.amountValue() != null || value.monthlyAmount() != null)) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "금액 없음인데 금액이 채워짐");
		}
		// 음수·0은 지급액일 수 없음. 모델이 부호를 잃거나 자릿수를 놓쳤다는 신호라 통과시키지 않음.
		if (isNonPositive(value.amountValue()) || isNonPositive(value.monthlyAmount())) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "금액이 0 이하임");
		}
		if (value.durationMonths() != null && value.durationMonths() <= 0) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "지급 개월 수가 0 이하임");
		}
		// 주기를 모르는데 기간이 채워지면 종신·평생 지급을 임의 개월 수로 환산한 것임. 참전유공자수당 같은 종신 지급은 총액 개념 자체가
		// 없어(판정원칙 7번) 기간을 붙이는 순간 없는 총액이 만들어짐. 판정원칙 3번이 "종신 수당을 임의 지급 기간으로 환산"을
		// LLM 금지 행위로 명시하므로 검증기가 막음.
		if (value.paymentPeriod() == PaymentPeriod.UNKNOWN && value.durationMonths() != null) {
			return ValidationResult.reject(RejectionReason.FIELD_RULE_VIOLATION, "지급 주기를 모르는데 기간이 채워짐");
		}
		return null;
	}

	private static boolean isNonPositive(Long amount) {
		return amount != null && amount <= 0L;
	}

	/**
	 * 근거 문장이 원문의 부분문자열인지 확인함. 양쪽에 같은 정규화(NFC 더하기 공백 압축)를 적용한 뒤 대조함 — 한쪽만 정규화하면 정상 결과가 전부
	 * 떨어짐. 한글은 자모 분리(NFD)와 완성형(NFC) 표현이 공존해 NFC 통일이 필수임.
	 */
	private static boolean isEvidenceInSource(String evidence, String noticeBody) {
		if (evidence == null || noticeBody == null) {
			return false;
		}
		String normalizedEvidence = normalize(evidence);
		if (normalizedEvidence.isEmpty()) {
			return false;
		}
		return normalize(noticeBody).contains(normalizedEvidence);
	}

	// 정규화 규칙은 ContentHasher가 정본임. 해시 계산과 근거 대조가 같은 규칙을 써야 본문이 그대로인데 해시만 달라지거나 정상 근거가
	// 폐기되는 일이 없음.
	private static String normalize(String text) {
		return ContentHasher.normalize(text);
	}

	/**
	 * 근거 문장 앞의 원문 문맥을 조금 붙여 돌려줌.
	 *
	 * <p>
	 * <b>근거 문장만 보면 구멍이 있음</b>: 모델이 "대출한도: 최대 5백만원" 대신 <b>"최대 5백만원"만 인용하면</b> 어휘 검사를 그대로
	 * 빠져나감. 근거를 짧게 자르는 것만으로 정책 가드가 무력화되는 셈이라, 원문에서 근거가 있던 자리 앞쪽을 함께 봄.
	 * </p>
	 *
	 * <p>
	 * <b>앞쪽만, 그것도 좁게 보는 이유</b>: 본문 전체를 검사하면 "대출한도" 문장이 문서 어딘가 있다는 이유로 무관한 지급액 판정까지 거부되어
	 * 누락이 생김(판정원칙 1번). 한국어에서 "대출한도:", "자부담금:"처럼 성격을 규정하는 말이 금액 <b>앞</b>에 오므로 앞쪽 40자면 충분함.
	 * </p>
	 */
	private static String evidenceWithLeadingContext(String evidence, String noticeBody) {
		if (evidence == null || noticeBody == null) {
			return evidence;
		}
		String normalizedEvidence = normalize(evidence);
		String normalizedBody = normalize(noticeBody);
		int at = normalizedBody.indexOf(normalizedEvidence);
		if (at < 0) {
			return normalizedEvidence;
		}
		int from = Math.max(0, at - POLICY_CONTEXT_CHARS);
		return normalizedBody.substring(from, at + normalizedEvidence.length());
	}

	/**
	 * 지급액이 아닌 금액을 가리키는 표현이 있는지 봄.
	 */
	private static String findForbiddenPhrase(String evidence) {
		String normalized = normalize(evidence);
		for (String phrase : POLICY_FORBIDDEN_PHRASES) {
			if (normalized.contains(phrase)) {
				return phrase;
			}
		}
		return null;
	}

}
