package com.jeongbiseo.infra.enrichment;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
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
 * 저장한다.
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

	// 연속 공백을 하나로 접기 위한 패턴임. 원문은 공공 API에서 와 줄바꿈·탭·전각 공백이 뒤섞여 있고, 모델은 그것을 정리해 인용하는
	// 경향이 있어 이 압축 없이 대조하면 정상 근거가 대량 폐기됨(기권율이 허위로 치솟는 원인).
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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

		String violatedPhrase = findForbiddenPhrase(value.evidence());
		if (violatedPhrase != null) {
			return ValidationResult.reject(RejectionReason.POLICY_VIOLATION,
					"지급액이 아닌 금액을 지급액으로 판정함: " + violatedPhrase);
		}

		return ValidationResult.accept(value);
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

	private static String normalize(String text) {
		return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").trim();
	}

	/**
	 * 근거에 지급액이 아닌 금액을 가리키는 표현이 있는지 봄. 근거 문장만 보는 것은 의도임 — 본문 전체를 보면 "대출한도" 문장이 어딘가 있다는 이유로
	 * 무관한 지급액 판정까지 거부되어 누락이 생김.
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
