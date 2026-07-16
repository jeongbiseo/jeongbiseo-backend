package com.jeongbiseo.domain.subsidy.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 지원금 마스터 엔티티임(ERD SUBSIDY). 비교값(ageMin, ageMax, regionScope, regionCode, employmentTags,
 * incomeThreshold, householdCondition), 자격조건 4축 신호, 고용 원문 코드와 금액·지급 필드를 담음.
 * SubsidyRepository가 SubsidyCriteria(domain.subsidy.dto)로 변환해 도메인에 넘김(storage 타입이 domain
 * 밖으로 새지 않음).
 *
 * <p>
 * duplicationPolicy와 amountSource는 ERD 주석이 "enum"으로 명시하지 않은 varchar 필드라 자바 enum을 새로 만들지
 * 않고 문자열 그대로 둠(ponytail).
 * </p>
 */
@Getter
@Entity
@Table(name = "subsidy",
		uniqueConstraints = @UniqueConstraint(name = "uk_subsidy_source_external",
				columnNames = { "source_id", "external_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubsidyEntity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_id", nullable = false)
	private String sourceId;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(name = "duplicate_of_id")
	private Long duplicateOfId;

	@Column(name = "name", nullable = false)
	private String name;

	// 2026-07-14 실측: 외부 소스에 소관기관이 없는 레코드가 실재해 NOT NULL을 유지할 수 없음(제약을 살리면 적재가
	// NULL not allowed for column "AGENCY"로 실패함). 기관을 모른다고 레코드를 버리면 누락이 생기므로 null을 허용하고
	// 프론트에서 미표기로 노출함. name은 null인 레코드가 없어 NOT NULL을 유지함.
	@Column(name = "agency")
	private String agency;

	@Enumerated(EnumType.STRING)
	@Column(name = "category")
	private SubsidyCategory category;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "eligibility_text", columnDefinition = "TEXT")
	private String eligibilityText;

	@Column(name = "external_url", columnDefinition = "TEXT")
	private String externalUrl;

	@Column(name = "deadline")
	private LocalDate deadline;

	@Column(name = "estimated_amount_min")
	private Long estimatedAmountMin;

	@Column(name = "estimated_amount_max")
	private Long estimatedAmountMax;

	@Column(name = "amount_source")
	private String amountSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_type", nullable = false)
	private PaymentType paymentType;

	@Column(name = "monthly_months")
	private Integer monthlyMonths;

	@Column(name = "monthly_amount")
	private Long monthlyAmount;

	@Column(name = "duplication_policy", nullable = false)
	private String duplicationPolicy;

	@Column(name = "exclusivity_group")
	private String exclusivityGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_audience", nullable = false)
	private TargetAudience targetAudience;

	@Enumerated(EnumType.STRING)
	@Column(name = "occupation_restriction", nullable = false)
	private OccupationRestriction occupationRestriction;

	@Column(name = "age_min")
	private Integer ageMin;

	@Column(name = "age_max")
	private Integer ageMax;

	@Enumerated(EnumType.STRING)
	@Column(name = "age_signal")
	private EligibilitySignal ageSignal;

	@Enumerated(EnumType.STRING)
	@Column(name = "region_scope", nullable = false)
	private RegionScope regionScope;

	@Column(name = "region_code")
	private String regionCode;

	// 다중 지역 전체를 보존하는 CSV임(regionScope·regionCode는 대표 단일 코드만 담음). 강등 랭킹은 후속 이슈.
	// 온통청년 다중 지역코드 CSV를 원문 그대로 담음. 실측상 zipCd 나열이 255자를 넘는 공고가 16.7%(최대 1,535자)라
	// VARCHAR(255)면 온통청년 배치가 통째로 롤백되므로 TEXT로 둠(강등 랭킹은 후속 이슈).
	@Column(name = "region_codes", columnDefinition = "TEXT")
	private String regionCodes;

	@Column(name = "employment_tags")
	private String employmentTags;

	@Enumerated(EnumType.STRING)
	@Column(name = "employment_signal")
	private EligibilitySignal employmentSignal;

	@Column(name = "employment_raw_code")
	private String employmentRawCode;

	@Column(name = "income_threshold")
	private Long incomeThreshold;

	@Enumerated(EnumType.STRING)
	@Column(name = "income_signal")
	private EligibilitySignal incomeSignal;

	@Column(name = "household_condition")
	private String householdCondition;

	@Enumerated(EnumType.STRING)
	@Column(name = "household_signal")
	private EligibilitySignal householdSignal;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "is_recommendable", nullable = false)
	private boolean recommendable;

	// 지원유형이 융자인 상품임(gov24 "현금(융자)" 등). 팀 판정으로 융자 계열은 이자·보증 지원까지 서비스에서 제외함. 대출 성격
	// 지원금이 오히려 신뢰도를 깎고 해커톤 범위 밖이라, 순수 대출과 이자 지원을 구분하지 않고 융자 유형 전체를 뺌(2026-07-15 확정).
	// 레코드는 원천 보존을 위해 지우지 않고 플래그로만 남김(사유를 recommendable에 겹치지 않음).
	@Column(name = "is_loan_product", nullable = false)
	private boolean loanProduct;

	@Column(name = "data_updated_at")
	private LocalDateTime dataUpdatedAt;

	@Column(name = "fetched_at")
	private LocalDateTime fetchedAt;

	@Builder
	public SubsidyEntity(Long id, String sourceId, String externalId, Long duplicateOfId, String name, String agency,
			SubsidyCategory category, String description, String eligibilityText, String externalUrl,
			LocalDate deadline, Long estimatedAmountMin, Long estimatedAmountMax, String amountSource,
			PaymentType paymentType, Integer monthlyMonths, Long monthlyAmount, String duplicationPolicy,
			String exclusivityGroup, TargetAudience targetAudience, OccupationRestriction occupationRestriction,
			Integer ageMin, Integer ageMax, EligibilitySignal ageSignal, RegionScope regionScope, String regionCode,
			String regionCodes, String employmentTags, EligibilitySignal employmentSignal, String employmentRawCode,
			Long incomeThreshold, EligibilitySignal incomeSignal, String householdCondition,
			EligibilitySignal householdSignal, boolean active, boolean recommendable, boolean loanProduct,
			LocalDateTime dataUpdatedAt, LocalDateTime fetchedAt) {
		this.id = id;
		this.sourceId = sourceId;
		this.externalId = externalId;
		this.duplicateOfId = duplicateOfId;
		this.name = name;
		this.agency = agency;
		this.category = category;
		this.description = description;
		this.eligibilityText = eligibilityText;
		this.externalUrl = externalUrl;
		this.deadline = deadline;
		this.estimatedAmountMin = estimatedAmountMin;
		this.estimatedAmountMax = estimatedAmountMax;
		this.amountSource = amountSource;
		this.paymentType = paymentType;
		this.monthlyMonths = monthlyMonths;
		this.monthlyAmount = monthlyAmount;
		this.duplicationPolicy = duplicationPolicy;
		this.exclusivityGroup = exclusivityGroup;
		this.targetAudience = targetAudience;
		this.occupationRestriction = occupationRestriction;
		this.ageMin = ageMin;
		this.ageMax = ageMax;
		this.ageSignal = ageSignal;
		this.regionScope = regionScope;
		this.regionCode = regionCode;
		this.regionCodes = regionCodes;
		this.employmentTags = employmentTags;
		this.employmentSignal = employmentSignal;
		this.employmentRawCode = employmentRawCode;
		this.incomeThreshold = incomeThreshold;
		this.incomeSignal = incomeSignal;
		this.householdCondition = householdCondition;
		this.householdSignal = householdSignal;
		this.active = active;
		this.recommendable = recommendable;
		this.loanProduct = loanProduct;
		this.dataUpdatedAt = dataUpdatedAt;
		this.fetchedAt = fetchedAt;
	}

}
