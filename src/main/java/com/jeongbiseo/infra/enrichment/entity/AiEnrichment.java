package com.jeongbiseo.infra.enrichment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.global.common.entity.BaseEntity;
import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.enrichment.dto.AmountEnrichment;
import com.jeongbiseo.infra.enrichment.dto.PaymentPeriod;

/**
 * LLM 금액 보강 결과 저장 엔티티임(데이터모델 3.8). <b>검증기를 통과한 결과만</b> 이 테이블에 들어감 — 기권·근거 불일치·정책 위반 건은
 * 저장하지 않고 지표로만 남김(배치 설계 6장).
 *
 * <p>
 * <b>이 엔티티가 domain이 아니라 infra에 있는 것은 의도임.</b> 등급 1~2에서 {@code domain} 패키지에 LLM import가
 * 0건이어야 한다는 불변식(HANDOFF 3장)을 주석이 아니라 패키지 구조로 보장하기 위함임. 원천 {@code subsidy} 레코드는 이 값으로 덮어쓰지
 * 않으며, 여기 담긴 값은 화면 표시용 부가 정보임.
 * </p>
 *
 * <p>
 * <b>버전 저장임.</b> 같은 지원금이라도 원문(content_hash)이나 모델·프롬프트 버전이 바뀌면 기존 행을 고치지 않고 새 행을 쌓음. 조회는
 * 현재 원문 해시와 일치하는 행만 보므로, 본문이 바뀌면 옛 보강은 자동으로 노출에서 빠짐(배치 설계 6장 "현재 본문 해시와 일치하는 검증 통과분만
 * 노출").
 * </p>
 */
@Getter
@Entity
@Table(name = "ai_enrichment",
		uniqueConstraints = @UniqueConstraint(name = "uk_ai_enrichment_dedup",
				columnNames = { "subsidy_id", "content_hash", "model_id", "prompt_version" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEnrichment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subsidy_id", nullable = false)
	private SubsidyEntity subsidy;

	// 보강 시점 원문의 SHA-256 hex임(64자). 조회 시 현재 원문 해시와 대조해 낡은 결과를 걸러내는 열쇠라 인덱스 대상임.
	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	@Column(name = "model_id", nullable = false, length = 100)
	private String modelId;

	@Column(name = "prompt_version", nullable = false, length = 50)
	private String promptVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "amount_kind", nullable = false, length = 20)
	private AmountKind amountKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_period", nullable = false, length = 20)
	private PaymentPeriod paymentPeriod;

	@Column(name = "amount_value")
	private Long amountValue;

	@Column(name = "monthly_amount")
	private Long monthlyAmount;

	@Column(name = "duration_months")
	private Integer durationMonths;

	@Column(name = "condition_expression", length = 500)
	private String conditionExpression;

	// 화면에 근거 문장으로 그대로 노출됨. 공고 본문 문장이라 길 수 있어 TEXT로 둠.
	@Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
	private String evidence;

	@Builder
	public AiEnrichment(SubsidyEntity subsidy, String contentHash, String modelId, String promptVersion,
			AmountKind amountKind, PaymentPeriod paymentPeriod, Long amountValue, Long monthlyAmount,
			Integer durationMonths, String conditionExpression, String evidence) {
		this.subsidy = subsidy;
		this.contentHash = contentHash;
		this.modelId = modelId;
		this.promptVersion = promptVersion;
		this.amountKind = amountKind;
		this.paymentPeriod = paymentPeriod;
		this.amountValue = amountValue;
		this.monthlyAmount = monthlyAmount;
		this.durationMonths = durationMonths;
		this.conditionExpression = conditionExpression;
		this.evidence = evidence;
	}

	/**
	 * 검증을 통과한 보강 값으로 저장 엔티티를 만듦. 기권 건은 애초에 검증기가 통과시키지 않으므로 여기 오지 않음.
	 * @param subsidy 대상 지원금
	 * @param contentHash 보강 시점 원문 해시
	 * @param modelId 호출한 모델 ID(모델 자기보고가 아니라 호출한 코드가 아는 값)
	 * @param promptVersion 프롬프트 버전
	 * @param value 검증 통과한 보강 값
	 * @return 저장 엔티티
	 */
	public static AiEnrichment of(SubsidyEntity subsidy, String contentHash, String modelId, String promptVersion,
			AmountEnrichment value) {
		return AiEnrichment.builder()
			.subsidy(subsidy)
			.contentHash(contentHash)
			.modelId(modelId)
			.promptVersion(promptVersion)
			.amountKind(value.amountKind())
			.paymentPeriod(value.paymentPeriod())
			.amountValue(value.amountValue())
			.monthlyAmount(value.monthlyAmount())
			.durationMonths(value.durationMonths())
			.conditionExpression(value.conditionExpression())
			.evidence(value.evidence())
			.build();
	}

}
