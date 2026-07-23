package com.jeongbiseo.domain.subsidy.ingestion;

import com.jeongbiseo.support.MySqlContainerSupport;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.client.common.dto.AmountParseStatus;
import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.infra.client.common.dto.DeadlineBasis;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.infra.client.common.dto.NormalizedEligibility;
import com.jeongbiseo.infra.client.common.dto.NormalizedRegion;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubsidyIngestionAdapter의 실제 MySQL 멱등 적재 통합 테스트임(SpringBootTest 더하기 Testcontainers,
 * Docker 필요). lab {@code SubsidyIngestionEndToEndTest}(6,840건 스냅샷 종단 재현)는 lab 쪽 증명으로 남기고,
 * 여기서는 팀 스키마(MySQL, 인증 포함 애플리케이션 컨텍스트) 위에서 업서트 키·유니크 제약·region_codes·한글 왕복만 소형으로 재증명함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SubsidyIngestionAdapterIntegrationTest extends MySqlContainerSupport {

	private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 7, 16, 12, 0);

	@Autowired
	private SubsidyIngestionAdapter adapter;

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Test
	void ingest_동일키_재적재는_행수를_유지하고_id를_보존하며_값을_갱신한다() {
		NormalizedSubsidy first = fixture("IDEMPOTENT-1", "청년월세지원", 100_000L);
		adapter.ingest(List.of(first), FETCHED_AT);
		Long id = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("IDEMPOTENT-1"))
			.findFirst()
			.orElseThrow()
			.getId();

		NormalizedSubsidy updated = fixture("IDEMPOTENT-1", "청년월세지원(개편)", 150_000L);
		adapter.ingest(List.of(updated), FETCHED_AT.plusDays(1));

		List<SubsidyEntity> rows = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("IDEMPOTENT-1"))
			.toList();
		assertThat(rows).as("(sourceId, externalId) 키가 같으면 행이 늘지 않아야 함(uk_subsidy_source_external 실동작)").hasSize(1);
		assertThat(rows.get(0).getId()).as("id는 보존돼야 함(update, insert 아님)").isEqualTo(id);
		assertThat(rows.get(0).getName()).isEqualTo("청년월세지원(개편)");
		assertThat(rows.get(0).getEstimatedAmountMin()).isEqualTo(150_000L);
	}

	@Test
	void ingest_다중_지역코드는_regionCodes_CSV로_왕복한다() {
		NormalizedRegion multiRegion = new NormalizedRegion(List.of("11620", "11650", "26110"), "서울특별시", null,
				RegionLevel.SIGUNGU, RegionScopeBasis.DECLARED_REGION_CODE, RegionConfidence.HIGH);
		NormalizedSubsidy subsidy = withRegion(fixture("REGION-MULTI", "다지역 지원금", 50_000L), multiRegion);
		adapter.ingest(List.of(subsidy), FETCHED_AT);

		SubsidyEntity saved = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("REGION-MULTI"))
			.findFirst()
			.orElseThrow();

		assertThat(saved.getRegionCodes()).isEqualTo("11620,11650,26110");
		// 단일 대표 코드 컬럼(regionScope·regionCode)은 다중 지역이면 null로 통과시키는 기존 로직 그대로임.
		assertThat(saved.getRegionScope()).isEqualTo(RegionScope.NATIONWIDE);
		assertThat(saved.getRegionCode()).isNull();
	}

	@Test
	void ingest_255자를_넘는_지역코드_CSV도_잘리지_않고_왕복한다() {
		// zipCd 나열이 255자를 넘는 온통청년 공고(실측 16.7%, 최대 1,535자)가 VARCHAR(255)에서 배치째 롤백되지 않음을
		// 고정함.
		List<String> manyCodes = java.util.stream.IntStream.range(0, 60)
			.mapToObj(i -> String.format("%05d", 11000 + i))
			.toList();
		String expectedCsv = String.join(",", manyCodes);
		assertThat(expectedCsv.length()).as("테스트 전제: CSV가 255자를 넘어야 함").isGreaterThan(255);
		NormalizedRegion wideRegion = new NormalizedRegion(manyCodes, "다지역", null, RegionLevel.SIGUNGU,
				RegionScopeBasis.DECLARED_REGION_CODE, RegionConfidence.HIGH);
		NormalizedSubsidy subsidy = withRegion(fixture("REGION-WIDE", "광역 다지역 지원금", 50_000L), wideRegion);
		adapter.ingest(List.of(subsidy), FETCHED_AT);

		SubsidyEntity saved = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("REGION-WIDE"))
			.findFirst()
			.orElseThrow();
		assertThat(saved.getRegionCodes()).isEqualTo(expectedCsv);
	}

	@Test
	void ingest_한글과_utf8mb4_문자를_원문_그대로_왕복한다() {
		String name = "청년월세지원💙(한글+이모지)";
		NormalizedSubsidy subsidy = fixture("UTF8MB4-1", name, 100_000L);
		adapter.ingest(List.of(subsidy), FETCHED_AT);

		SubsidyEntity saved = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("UTF8MB4-1"))
			.findFirst()
			.orElseThrow();

		assertThat(saved.getName()).isEqualTo(name);
	}

	@Test
	void ingest_CONDITIONAL_금액성격과_금액범위를_함께_왕복한다() {
		NormalizedSubsidy subsidy = fixture("AMOUNT-KIND-1", "조건부 지원금", 500_000L, AmountKind.CONDITIONAL);
		adapter.ingest(List.of(subsidy), FETCHED_AT);

		SubsidyEntity saved = subsidyRepository.findAllBySourceIdIn(java.util.Set.of("gov24"))
			.stream()
			.filter(e -> e.getExternalId().equals("AMOUNT-KIND-1"))
			.findFirst()
			.orElseThrow();

		assertThat(saved.getAmountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(saved.getEstimatedAmountMin()).isEqualTo(500_000L);
		assertThat(saved.getEstimatedAmountMax()).isEqualTo(500_000L);
	}

	// 필수 축만 채운 최소 NormalizedSubsidy 픽스처임. 소스는 gov24 고정, 지역은 전국(빈 목록)으로 둠.
	private static NormalizedSubsidy fixture(String externalId, String name, long amount) {
		return fixture(externalId, name, amount, AmountKind.SINGLE);
	}

	private static NormalizedSubsidy fixture(String externalId, String name, long amount, AmountKind amountKind) {
		ParsedAmount parsedAmount = new ParsedAmount(amountKind, List.of(amount), amount, amount, "원",
				amountKind == AmountKind.CONDITIONAL ? "조건에 따라 지급" : null, AmountParseStatus.PARSED);
		ParsedDeadline deadline = new ParsedDeadline(DeadlineKind.ALWAYS_OPEN, null, null, "상시신청");
		return new NormalizedSubsidy(SubsidySource.GOV24, externalId, name, "테스트기관", "지원 내용 원문", "자격조건 원문", "생활안정",
				PaymentType.CASH, "현금", parsedAmount, deadline, DeadlineBasis.PARSED_FROM_TEXT,
				NormalizedRegion.national(), NormalizedEligibility.unknown(), ApplicationMethodFlags.noEvidence(), null,
				null, null, LocalDateTime.of(2026, 7, 1, 0, 0), TargetAudience.PERSONAL,
				com.jeongbiseo.domain.common.enums.OccupationRestriction.NONE);
	}

	// record는 불변이라 region만 바꾼 사본을 만드는 소형 헬퍼임(빌더를 새로 만들 만큼 축이 많지 않음).
	private static NormalizedSubsidy withRegion(NormalizedSubsidy base, NormalizedRegion region) {
		return new NormalizedSubsidy(base.source(), base.externalId(), base.name(), base.agency(), base.description(),
				base.eligibilityText(), base.categoryRawText(), base.paymentType(), base.paymentTypeRawText(),
				base.amount(), base.deadline(), base.deadlineBasis(), region, base.eligibility(),
				base.applicationMethod(), base.applicationUrl(), base.referenceUrl(), base.requiredDocumentsText(),
				base.dataUpdatedAt(), base.targetAudience(), base.occupationRestriction());
	}

}
