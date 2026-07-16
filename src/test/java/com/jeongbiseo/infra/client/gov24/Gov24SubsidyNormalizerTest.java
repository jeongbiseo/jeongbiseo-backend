package com.jeongbiseo.infra.client.gov24;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.infra.client.common.dto.DeadlineBasis;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.NormalizedEligibility;
import com.jeongbiseo.infra.client.common.dto.NormalizedRegion;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.gov24.dto.ParsedSubsidyResult;

/**
 * gov24 파싱 결과를 4종 소스 공통 타깃({@link NormalizedSubsidy})으로 변환하는 어댑터의 회귀 테스트임. 스팟 검증 대신 실호출
 * 스냅샷 n=1097 전량을 변환해 <b>불변식</b>을 고정함 — 어댑터의 값은 "값을 지어내지 않는다"에 있으므로, 지어내지 않았는지를 전수로 확인하는
 * 편이 맞음(수집 방법은 fixtures/regression/SNAPSHOT_META.md 참조).
 */
class Gov24SubsidyNormalizerTest {

	private static final Path SNAPSHOT_DIR = Path.of("fixtures", "regression");

	private final Gov24Parser parser = new Gov24Parser();

	private final Gov24SubsidyNormalizer normalizer = new Gov24SubsidyNormalizer();

	@Test
	void source_isGov24() {
		assertThat(this.normalizer.source()).isEqualTo(SubsidySource.GOV24);
		assertThat(SubsidySource.GOV24.sourceId()).isEqualTo("gov24");
	}

	// 원문 5필드(서비스ID·서비스명·소관기관명·지원내용·자격조건)가 손실 없이 옮겨지는지 스팟 확인함.
	@Test
	void normalize_carriesRawFieldsWithoutLoss() throws IOException {
		ParsedSubsidyResult raw = findParsedById("000000465790");

		NormalizedSubsidy normalized = this.normalizer.normalize(raw);

		assertThat(normalized.source()).isEqualTo(SubsidySource.GOV24);
		assertThat(normalized.externalId()).isEqualTo(raw.serviceId());
		assertThat(normalized.name()).isEqualTo(raw.serviceName());
		assertThat(normalized.agency()).isEqualTo(raw.agency());
		assertThat(normalized.description()).isEqualTo(raw.description());
		assertThat(normalized.eligibilityText()).isEqualTo(raw.eligibilityText());
		assertThat(normalized.categoryRawText()).isEqualTo(raw.categoryRawText());
		assertThat(normalized.paymentType()).isEqualTo(raw.paymentType());
		assertThat(normalized.paymentTypeRawText()).isEqualTo(raw.paymentTypeRawText());
		assertThat(normalized.amount()).isEqualTo(raw.amount());
		assertThat(normalized.deadline()).isEqualTo(raw.parsedDeadline());
		assertThat(normalized.applicationUrl()).isEqualTo(raw.externalUrl());
		assertThat(normalized.requiredDocumentsText()).isEqualTo(raw.requiredDocumentsText());
		assertThat(normalized.dataUpdatedAt()).isEqualTo(raw.dataUpdatedAt());
	}

	// gov24가 줄 수 없는 것을 지어내지 않았는지 전수로 고정함.
	@Test
	void normalize_neverInventsWhatGov24CannotGive() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		assertThat(all).hasSize(1097);
		// (가) 지역 코드 — gov24는 법정동코드를 주지 않음. 시도·시군구 명칭에서 역산하지 않음
		assertThat(all).allSatisfy(n -> assertThat(n.region().regionCodes()).isEmpty());
		// (나) 고용 조건 — JA0326·JA0327이 배타적 상태값이 아니라 파서가 읽지 않음
		assertThat(all)
			.allSatisfy(n -> assertThat(n.eligibility().employmentSignal()).isEqualTo(EligibilitySignal.UNKNOWN));
		assertThat(all).allSatisfy(n -> assertThat(n.eligibility().employmentRawCode()).isNull());
		// (다) 이메일 접수 — gov24 키워드 어휘에 없어 주장 자체가 불가능함("불가"가 아니라 "근거 없음")
		assertThat(all).allSatisfy(n -> assertThat(n.applicationMethod().email()).isFalse());
		// (라) 공고 상세 페이지 URL — gov24는 신청 URL과 별개의 공고 URL을 주지 않음
		assertThat(all).allSatisfy(n -> assertThat(n.referenceUrl()).isNull());
	}

	// serviceList의 서비스분야 원문 분포를 그대로 고정함. 자체 taxonomy라 SubsidyCategory enum으로 매핑하지 않음.
	@Test
	void normalize_categoryRawTextPreservesServiceListDistributionWithoutEnumMapping() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();
		long filled = all.stream().filter(n -> n.categoryRawText() != null).count();
		Map<String, Long> counts = all.stream()
			.filter(n -> n.categoryRawText() != null)
			.collect(Collectors.groupingBy(NormalizedSubsidy::categoryRawText, Collectors.counting()));

		System.out.printf("gov24 categoryRawText 채움: %d/%d (%.2f%%)%n", filled, all.size(),
				filled * 100.0 / all.size());
		System.out.println("gov24 categoryRawText 분포: " + counts);

		assertThat(filled).isEqualTo(1097L);
		assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(Map.entry("생활안정", 227L),
				Map.entry("농림축산어업", 172L), Map.entry("보육·교육", 147L), Map.entry("보건·의료", 126L), Map.entry("임신·출산", 85L),
				Map.entry("고용·창업", 80L), Map.entry("문화·환경", 75L), Map.entry("행정·안전", 66L), Map.entry("주거·자립", 63L),
				Map.entry("보호·돌봄", 56L)));
	}

	@Test
	void normalize_missingServiceCategoryStaysNull() throws IOException {
		Gov24ServiceItemDto detailOnly = this.parser
			.parseServiceItems(readSnapshot("gov24_serviceDetail_snapshot.json"))
			.get(0);

		assertThat(detailOnly.categoryRawText()).isNull();
		ParsedSubsidyResult parsed = this.parser.toParsedSubsidy(detailOnly, Map.of(), Map.of(), Map.of());
		assertThat(this.normalizer.normalize(parsed).categoryRawText()).isNull();
	}

	// gov24 신청기한은 전면 자유텍스트라 근거가 구조화 필드일 수 없음. 스냅샷 전량이 PARSED_FROM_TEXT이고,
	// DECLARED_FIELD는 0건이어야 함(그건 K-Startup·온통청년의 몫임).
	@Test
	void normalize_deadlineBasisIsNeverDeclaredField() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		Map<DeadlineBasis, Long> counts = all.stream()
			.collect(Collectors.groupingBy(NormalizedSubsidy::deadlineBasis, Collectors.counting()));

		assertThat(counts.getOrDefault(DeadlineBasis.DECLARED_FIELD, 0L)).isZero();
		assertThat(counts.getOrDefault(DeadlineBasis.PARSED_FROM_TEXT, 0L)).isEqualTo(1097L);
		assertThat(counts.getOrDefault(DeadlineBasis.NOT_APPLICABLE, 0L)).isZero();
	}

	// 원문이 비면 파싱 근거 자체가 없다는 뜻이라 NOT_APPLICABLE로 떨어져야 함(스냅샷에는 0건이지만, "파싱했는데
	// 모르겠음"과 "볼 원문이 없음"을 섞지 않는다는 규칙을 고정함).
	@Test
	void normalize_blankDeadlineText_isNotApplicable() throws IOException {
		ParsedSubsidyResult raw = findParsedById("000000465790");
		ParsedSubsidyResult blankDeadline = withBlankDeadlineText(raw);

		NormalizedSubsidy normalized = this.normalizer.normalize(blankDeadline);

		assertThat(normalized.deadlineBasis()).isEqualTo(DeadlineBasis.NOT_APPLICABLE);
	}

	// 연령은 JA0110·JA0111에 값이 있을 때만 RESTRICTED임. gov24에는 "연령 무관"을 선언하는 필드가 없으므로
	// UNRESTRICTED가 0건이어야 함 — 데이터 없음(UNKNOWN)을 무관으로 승격해 통과시키면 매칭이 조용히 틀림.
	@Test
	void normalize_ageSignalIsNeverUnrestricted() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		Map<EligibilitySignal, Long> counts = all.stream()
			.map(n -> n.eligibility().ageSignal())
			.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		assertThat(counts.getOrDefault(EligibilitySignal.UNRESTRICTED, 0L)).isZero();
		assertThat(counts.getOrDefault(EligibilitySignal.RESTRICTED, 0L)).isPositive();
		assertThat(counts.getOrDefault(EligibilitySignal.UNKNOWN, 0L)).isPositive();
		assertThat(all).allSatisfy(n -> {
			NormalizedEligibility e = n.eligibility();
			boolean hasAge = e.ageMin() != null || e.ageMax() != null;
			assertThat(e.ageSignal()).isEqualTo(hasAge ? EligibilitySignal.RESTRICTED : EligibilitySignal.UNKNOWN);
		});
	}

	// 지역 근거는 소관기관명 유추뿐이라 DECLARED_REGION_CODE와 HIGH가 0건이어야 함(둘 다 온통청년 전용).
	@Test
	void normalize_regionIsAlwaysInferredNeverDeclared() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		assertThat(all).allSatisfy(n -> assertThat(n.region().scopeBasis())
			.isIn(RegionScopeBasis.INFERRED_FROM_AGENCY_NAME, RegionScopeBasis.NOT_APPLICABLE));
		assertThat(all)
			.allSatisfy(n -> assertThat(n.region().confidence()).isIn(RegionConfidence.MEDIUM, RegionConfidence.LOW));

		Map<RegionLevel, Long> levels = all.stream()
			.map(n -> n.region().regionLevel())
			.collect(Collectors.groupingBy(l -> l, Collectors.counting()));
		// 기존 gov24 파서 회귀치와 동일해야 함(어댑터가 지역 판정을 바꾸지 않았다는 증거)
		assertThat(levels.get(RegionLevel.SIGUNGU)).isEqualTo(645L);
		assertThat(levels.get(RegionLevel.SIDO)).isEqualTo(140L);
		assertThat(levels.get(RegionLevel.NATIONAL)).isEqualTo(312L);
	}

	// 공통 타깃의 "근거 없음" 기본값 3종이 실제로 비어 있는 상태를 뜻하는지 고정함.
	@Test
	void emptyDefaults_meanNoEvidenceNotDenial() {
		NormalizedRegion national = NormalizedRegion.national();
		assertThat(national.regionCodes()).isEmpty();
		assertThat(national.sidoName()).isNull();
		assertThat(national.sigunguName()).isNull();
		assertThat(national.regionLevel()).isEqualTo(RegionLevel.NATIONAL);
		assertThat(national.scopeBasis()).isEqualTo(RegionScopeBasis.NOT_APPLICABLE);
		assertThat(national.confidence()).isEqualTo(RegionConfidence.LOW);

		NormalizedEligibility unknown = NormalizedEligibility.unknown();
		assertThat(unknown.ageSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(unknown.ageMin()).isNull();
		assertThat(unknown.ageMax()).isNull();
		assertThat(unknown.incomeSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(unknown.householdSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(unknown.employmentSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(unknown.employmentRawCode()).isNull();

		ApplicationMethodFlags flags = ApplicationMethodFlags.noEvidence();
		assertThat(flags.unclassified()).isTrue();
		assertThat(flags.online()).isFalse();
		assertThat(flags.visit()).isFalse();
		assertThat(flags.mail()).isFalse();
		assertThat(flags.email()).isFalse();
		assertThat(flags.fax()).isFalse();
		assertThat(flags.phone()).isFalse();
		assertThat(flags.autoProvided()).isFalse();
	}

	// ---- 스냅샷 로딩 ----

	private List<NormalizedSubsidy> normalizeSnapshot() throws IOException {
		return loadParsedSnapshot().stream().map(this.normalizer::normalize).toList();
	}

	private ParsedSubsidyResult findParsedById(String serviceId) throws IOException {
		return loadParsedSnapshot().stream()
			.filter(r -> serviceId.equals(r.serviceId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("스냅샷에서 서비스ID를 찾지 못함: " + serviceId));
	}

	private List<ParsedSubsidyResult> loadParsedSnapshot() throws IOException {
		Map<String, Gov24SupportConditionDto> conditionsById = this.parser
			.parseSupportConditions(readSnapshot("gov24_supportConditions_snapshot.json"))
			.stream()
			.collect(Collectors.toMap(Gov24SupportConditionDto::serviceId, c -> c, (a, b) -> a, LinkedHashMap::new));
		Map<String, String> userTypeById = new LinkedHashMap<>();
		Map<String, String> categoryRawTextById = new LinkedHashMap<>();
		for (Gov24ServiceItemDto item : this.parser
			.parseServiceItems(readSnapshot("gov24_serviceList_snapshot.json"))) {
			userTypeById.put(item.serviceId(), item.userTypeText());
			categoryRawTextById.put(item.serviceId(), item.categoryRawText());
		}
		List<Gov24ServiceItemDto> items = this.parser
			.parseServiceItems(readSnapshot("gov24_serviceDetail_snapshot.json"));
		return items.stream()
			.map(item -> this.parser.toParsedSubsidy(item, conditionsById, userTypeById, categoryRawTextById))
			.toList();
	}

	private String readSnapshot(String fileName) throws IOException {
		return Files.readString(SNAPSHOT_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

	// 신청기한 원문만 비운 사본을 만듦(레코드라 재구성함).
	private static ParsedSubsidyResult withBlankDeadlineText(ParsedSubsidyResult raw) {
		return new ParsedSubsidyResult(raw.serviceId(), raw.serviceName(), raw.agency(), raw.description(),
				raw.eligibilityText(), raw.categoryRawText(), raw.ageMin(), raw.ageMax(), raw.incomeSignal(),
				raw.householdSignal(), raw.paymentType(), raw.paymentTypeRawText(), raw.externalUrl(),
				raw.dataUpdatedAt(), raw.applicationMethod(), raw.requiredDocumentsText(), raw.deadline(),
				new com.jeongbiseo.infra.client.common.dto.ParsedDeadline(raw.parsedDeadline().kind(),
						raw.parsedDeadline().startDate(), raw.parsedDeadline().endDate(), null),
				raw.amount(), raw.region(), raw.incomeSignalSource(), raw.incomeConsistencyStatus(),
				raw.incomeTextEvidence(), raw.targetAudience(), raw.occupationRestriction());
	}

}
