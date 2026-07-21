package com.jeongbiseo.domain.subsidy.ingestion;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.infra.client.common.dto.NormalizedRegion;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;

/** 4종 외부 소스의 정규화 결과를 지원금 마스터에 멱등 적재하는 어댑터임. */
@Component
public class SubsidyIngestionAdapter {

	private static final String EXTERNAL_AMOUNT_SOURCE = "EXTERNAL";

	private static final String UNKNOWN_DUPLICATION_POLICY = "UNKNOWN";

	private final SubsidyRepository repository;

	public SubsidyIngestionAdapter(SubsidyRepository repository) {
		this.repository = repository;
	}

	/**
	 * 정규화 결과를 {@code (source, externalId)} 기준으로 upsert함. 같은 키가 입력 안에 반복되면 마지막 값을 사용함.
	 * @param subsidies 정규화 지원금 목록
	 * @param fetchedAt 수집 시각
	 */
	@Transactional
	public void ingest(List<NormalizedSubsidy> subsidies, LocalDateTime fetchedAt) {
		Map<SourceExternalKey, NormalizedSubsidy> uniqueInputs = new LinkedHashMap<>();
		for (NormalizedSubsidy subsidy : subsidies) {
			uniqueInputs.put(SourceExternalKey.from(subsidy), subsidy);
		}

		Set<String> sourceIds = uniqueInputs.values()
			.stream()
			.map(subsidy -> subsidy.source().sourceId())
			.collect(Collectors.toSet());
		Map<SourceExternalKey, SubsidyEntity> existingByKey = this.repository.findAllBySourceIdIn(sourceIds)
			.stream()
			.collect(Collectors.toMap(SourceExternalKey::from, Function.identity()));

		List<SubsidyEntity> entities = uniqueInputs.entrySet()
			.stream()
			.map(entry -> toEntity(entry.getValue(), existingId(existingByKey, entry.getKey()), fetchedAt))
			.toList();
		this.repository.saveAll(entities);
	}

	private static Long existingId(Map<SourceExternalKey, SubsidyEntity> existingByKey, SourceExternalKey key) {
		SubsidyEntity existing = existingByKey.get(key);
		return existing == null ? null : existing.getId();
	}

	private static SubsidyEntity toEntity(NormalizedSubsidy subsidy, Long id, LocalDateTime fetchedAt) {
		ParsedAmount amount = subsidy.amount();
		ParsedDeadline deadline = subsidy.deadline();

		return SubsidyEntity.builder()
			.id(id)
			.sourceId(subsidy.source().sourceId())
			.externalId(subsidy.externalId())
			.name(subsidy.name())
			.agency(subsidy.agency())
			// 소스별 카테고리 매핑(CategoryMapper): 온통청년은 전건 YOUTH, gov24는 서비스분야 10종을 도메인 칩으로,
			// gov24 미매핑·null은 ETC.
			.category(CategoryMapper.map(subsidy.source(), subsidy.categoryRawText()))
			.description(subsidy.description())
			.eligibilityText(subsidy.eligibilityText())
			.externalUrl(firstNonNull(subsidy.applicationUrl(), subsidy.referenceUrl()))
			.deadline(deadline.endDate())
			.estimatedAmountMin(amount.minAmount())
			.estimatedAmountMax(amount.maxAmount())
			.amountSource(EXTERNAL_AMOUNT_SOURCE)
			.paymentType(subsidy.paymentType())
			.duplicationPolicy(UNKNOWN_DUPLICATION_POLICY)
			.targetAudience(subsidy.targetAudience())
			.occupationRestriction(subsidy.occupationRestriction())
			.ageSignal(subsidy.eligibility().ageSignal())
			.ageMin(subsidy.eligibility().ageMin())
			.ageMax(subsidy.eligibility().ageMax())
			.incomeSignal(subsidy.eligibility().incomeSignal())
			.householdSignal(subsidy.eligibility().householdSignal())
			.employmentSignal(subsidy.eligibility().employmentSignal())
			.employmentRawCode(subsidy.eligibility().employmentRawCode())
			.regionScope(regionScope(subsidy.region()))
			.regionCode(singleRegionCode(subsidy.region()))
			.regionCodes(regionCodesCsv(subsidy.region()))
			// ponytail: 소스에 존재하는 행은 활성화하되, 소스가 마감을 선언한 CLOSED만 추천에서 제외함.
			.active(true)
			.recommendable(deadline.kind() != DeadlineKind.CLOSED)
			.loanProduct(isLoanProduct(subsidy.paymentTypeRawText()))
			.dataUpdatedAt(subsidy.dataUpdatedAt())
			.fetchedAt(fetchedAt)
			.build();
	}

	private static String firstNonNull(String first, String second) {
		return first == null ? second : first;
	}

	// ponytail: gov24 지원유형에 "융자"가 든 상품을 모두 대출로 표시함(현금(융자)·기타(융자) 등, 이자·보증 지원 포함). 팀 판정으로
	// 융자 계열은 순수 대출과 이자 지원을 구분하지 않고 서비스에서 전부 제외함(해커톤 범위 밖). 온통청년 제공방법코드는 숫자라 "융자"를
	// 포함하지 않아 이번 회차는 gov24 전용임(온통청년 융자는 신호 미확정으로 보류, 2026-07-15 Wave 0 실측).
	static boolean isLoanProduct(String paymentTypeRawText) {
		return paymentTypeRawText != null && paymentTypeRawText.contains("융자");
	}

	private static RegionScope regionScope(NormalizedRegion region) {
		return singleRegionCode(region) == null ? RegionScope.NATIONWIDE : RegionScope.REGIONAL;
	}

	private static String singleRegionCode(NormalizedRegion region) {
		if (region.regionCodes().size() != 1) {
			// ponytail: 다중 지역을 단일 컬럼의 첫 코드로 자르면 나머지 지역 사용자가 누락되므로 전국으로 통과시킴.
			return null;
		}
		return region.regionCodes().get(0);
	}

	// 다중 지역 전체를 보존하는 CSV임(강등 랭킹은 후속 이슈). 빈 목록이면 null.
	private static String regionCodesCsv(NormalizedRegion region) {
		if (region.regionCodes().isEmpty()) {
			return null;
		}
		return String.join(",", region.regionCodes());
	}

	private record SourceExternalKey(String sourceId, String externalId) {

		private static SourceExternalKey from(NormalizedSubsidy subsidy) {
			return new SourceExternalKey(subsidy.source().sourceId(), subsidy.externalId());
		}

		private static SourceExternalKey from(SubsidyEntity subsidy) {
			return new SourceExternalKey(subsidy.getSourceId(), subsidy.getExternalId());
		}

	}

}
