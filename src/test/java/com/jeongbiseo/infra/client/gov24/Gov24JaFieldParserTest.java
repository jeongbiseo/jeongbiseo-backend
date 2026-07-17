package com.jeongbiseo.infra.client.gov24;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.gov24.dto.DeadlineFailureReason;
import com.jeongbiseo.infra.client.gov24.dto.DeadlineParseResult;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;

/**
 * gov24 파서의 JA 필드(소득 5개·가구 9개) 판독과 신청기한 파싱을 실호출 스냅샷(demo/fixtures/regression/)으로 검증하는 회귀
 * 테스트임(임무 지시 2장·3장). 스냅샷은 2026-07-12 보조금24 supportConditions·serviceDetail을 실호출로
 * 전수(10,968건) 수신한 뒤 체계적 표집(전체 순서에서 10건마다 1건, 1,097건)한 것이라 네트워크 호출 없이 매 실행마다 같은 결과를 냄. 두
 * 스냅샷은 같은 표집 규칙을 써서 서비스ID 집합이 정확히 일치함(플래그-원문 대조 테스트에 필요). 수집 방법·건수는
 * fixtures/regression/SNAPSHOT_META.md 참조.
 *
 * <p>
 * 허용 오차 ±5퍼센트포인트의 근거: 전수(10,968건) 대비 체계적 표집(step=10, n=1,097)의 실측 편차가 소득 1.55%p, 가구
 * 1.59%p였고, step=20(n=549)도 1.1%p 안팎이었음(수집 스크립트 산출 로그, SNAPSHOT_META.md 기록). ±5%p면 이 표집
 * 변동을 충분히 흡수하면서도 로직이 실제로 깨졌을 때(예: 판정 분기가 반대로 뒤집히는 등 10%p 이상 벌어지는 사고)는 잡아낼 만큼 좁음.
 */
class Gov24JaFieldParserTest {

	private static final Path SNAPSHOT_DIR = Path.of("fixtures", "regression");

	private static final double TOLERANCE_PP = 5.0;

	// 외부API-부족분-조사-2026-07-12.md 1장·3장 확정본 — 전수 10,968건 실측치(퍼센트포인트)
	private static final double REPORT_INCOME_VALID_PCT = 90.61;

	private static final double REPORT_INCOME_RESTRICTED_PCT = 19.98;

	private static final double REPORT_HOUSEHOLD_VALID_PCT = 90.75;

	private static final double REPORT_HOUSEHOLD_RESTRICTED_PCT = 11.75;

	private static final Pattern MEDIAN_INCOME_PERCENT = Pattern.compile("중위소득\\s*(\\d+)\\s*%");

	private final Gov24Parser parser = new Gov24Parser();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void supportConditionsSnapshot_incomeSignalDistribution_withinToleranceOfReport() throws IOException {
		List<Gov24SupportConditionDto> conditions = loadSupportConditionsSnapshot();
		Map<EligibilitySignal, Long> counts = countBySignal(conditions, Gov24SupportConditionDto::incomeSignal);

		int total = conditions.size();
		double unrestrictedPct = percentOf(counts.get(EligibilitySignal.UNRESTRICTED), total);
		double restrictedPct = percentOf(counts.get(EligibilitySignal.RESTRICTED), total);
		double unknownPct = percentOf(counts.get(EligibilitySignal.UNKNOWN), total);
		double validPct = unrestrictedPct + restrictedPct;

		System.out.println("=== 소득 3분류 분포 (스냅샷 n=" + total + ") ===");
		System.out.printf("  제한없음(UNRESTRICTED): %.2f%%%n", unrestrictedPct);
		System.out.printf("  제한형(RESTRICTED): %.2f%%%n", restrictedPct);
		System.out.printf("  데이터없음(UNKNOWN): %.2f%%%n", unknownPct);
		System.out.printf("  유효신호(제한없음+제한형): %.2f%% (리포트 전수치 %.2f%%)%n", validPct, REPORT_INCOME_VALID_PCT);

		assertThat(validPct).isCloseTo(REPORT_INCOME_VALID_PCT, within(TOLERANCE_PP));
		assertThat(restrictedPct).isCloseTo(REPORT_INCOME_RESTRICTED_PCT, within(TOLERANCE_PP));
	}

	@Test
	void supportConditionsSnapshot_householdSignalDistribution_withinToleranceOfReport() throws IOException {
		List<Gov24SupportConditionDto> conditions = loadSupportConditionsSnapshot();
		Map<EligibilitySignal, Long> counts = countBySignal(conditions, Gov24SupportConditionDto::householdSignal);

		int total = conditions.size();
		double unrestrictedPct = percentOf(counts.get(EligibilitySignal.UNRESTRICTED), total);
		double restrictedPct = percentOf(counts.get(EligibilitySignal.RESTRICTED), total);
		double unknownPct = percentOf(counts.get(EligibilitySignal.UNKNOWN), total);
		double validPct = unrestrictedPct + restrictedPct;

		System.out.println("=== 가구 3분류 분포 (스냅샷 n=" + total + ") ===");
		System.out.printf("  제한없음(UNRESTRICTED): %.2f%%%n", unrestrictedPct);
		System.out.printf("  제한형(RESTRICTED): %.2f%%%n", restrictedPct);
		System.out.printf("  데이터없음(UNKNOWN): %.2f%%%n", unknownPct);
		System.out.printf("  유효신호(제한없음+제한형): %.2f%% (리포트 전수치 %.2f%%)%n", validPct, REPORT_HOUSEHOLD_VALID_PCT);

		assertThat(validPct).isCloseTo(REPORT_HOUSEHOLD_VALID_PCT, within(TOLERANCE_PP));
		assertThat(restrictedPct).isCloseTo(REPORT_HOUSEHOLD_RESTRICTED_PCT, within(TOLERANCE_PP));
	}

	// 아래 6개는 스냅샷에서 실제로 찾은 고정 서비스ID로 개별 케이스를 고정함(회귀 감지 — 판정 로직이 바뀌면
	// 이 6개 중 하나가 반드시 깨짐). 서비스ID와 기대값은
	// fixtures/regression/gov24_supportConditions_snapshot.json
	// 원문을 직접 대조해 확정함.

	@Test
	void incomeSignal_allFiveIncomeFieldsY_isUnrestricted() throws IOException {
		Gov24SupportConditionDto condition = findBySnapshotId("000000465790");

		assertThat(condition.incomeSignal()).isEqualTo(EligibilitySignal.UNRESTRICTED);
	}

	@Test
	void incomeSignal_onlyLowestTwoBracketsY_isRestricted() throws IOException {
		Gov24SupportConditionDto condition = findBySnapshotId("134200000045");

		assertThat(condition.income0To50()).isEqualTo("Y");
		assertThat(condition.income51To75()).isEqualTo("Y");
		assertThat(condition.income76To100()).isNull();
		assertThat(condition.incomeSignal()).isEqualTo(EligibilitySignal.RESTRICTED);
	}

	@Test
	void incomeSignal_allFiveIncomeFieldsNull_isUnknown() throws IOException {
		Gov24SupportConditionDto condition = findBySnapshotId("119200000027");

		assertThat(condition.incomeSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
	}

	@Test
	void householdSignal_onlyNotApplicableFlagY_isUnrestricted() throws IOException {
		Gov24SupportConditionDto condition = findBySnapshotId("119200000130");

		assertThat(condition.householdNotApplicable()).isEqualTo("Y");
		assertThat(condition.multiculturalFamily()).isNull();
		assertThat(condition.householdSignal()).isEqualTo(EligibilitySignal.UNRESTRICTED);
	}

	@Test
	void householdSignal_onlyOneSubstantiveFieldY_isRestrictedEvenWithNotApplicableY() throws IOException {
		// JA0410(해당사항없음)이 Y여도 실질 조건(JA0403 한부모/조손)이 부분적으로 Y면 제한형으로 판정해야 함
		// — JA0410 하나만으로 무조건 무관 처리하면 이 케이스가 잘못 UNRESTRICTED로 뒤집힘
		Gov24SupportConditionDto condition = findBySnapshotId("134200005044");

		assertThat(condition.singleParentOrGrandparentFamily()).isEqualTo("Y");
		assertThat(condition.householdNotApplicable()).isEqualTo("Y");
		assertThat(condition.householdSignal()).isEqualTo(EligibilitySignal.RESTRICTED);
	}

	@Test
	void householdSignal_allNineFieldsNull_isUnknown() throws IOException {
		Gov24SupportConditionDto condition = findBySnapshotId("119200000027");

		assertThat(condition.householdSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
	}

	@Test
	void deadlineParsing_serviceDetailSnapshot_reportsSuccessRateAtScale() throws IOException {
		List<ServiceDetailRecord> details = loadServiceDetailSnapshot();

		Map<DeadlineFailureReason, Long> failureCounts = new EnumMap<>(DeadlineFailureReason.class);
		long parsedCount = 0;
		for (ServiceDetailRecord detail : details) {
			DeadlineParseResult result = parser.parseDeadline(detail.applicationDeadlineText());
			if (result.parsed()) {
				parsedCount++;
			}
			else {
				failureCounts.merge(result.failureReason(), 1L, Long::sum);
			}
		}

		int total = details.size();
		double successRate = parsedCount * 100.0 / total;

		System.out.println("=== 신청기한 파싱 성공률 재측정 (serviceDetail 스냅샷 n=" + total + ") ===");
		System.out.println("  파싱 성공: " + parsedCount + "건 (" + String.format("%.2f", successRate) + "%)");
		System.out.println("  실패 사유별 분류: " + failureCounts);
		System.out.println(
				"  참고: 기존 3건 표본의 33.3% 성공률은 표본이 너무 작아 신뢰할 수 없었음" + "(외부API-부족분-조사-2026-07-12.md 부록 '여전히 확인하지 못한 것').");

		assertThat(total).isGreaterThanOrEqualTo(300);
		assertThat(parsedCount + failureCounts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
		// 스냅샷이 고정돼 있어 결정적임 — 판정 로직이 바뀌면 이 값도 바뀌어야 정상(회귀 고정)
		assertThat(parsedCount).isEqualTo(2);
		assertThat(failureCounts.get(DeadlineFailureReason.ALWAYS_OPEN)).isEqualTo(668);
		assertThat(failureCounts.get(DeadlineFailureReason.BUDGET_EXHAUSTION)).isEqualTo(6);
		assertThat(failureCounts.get(DeadlineFailureReason.UNRECOGNIZED_FORMAT)).isEqualTo(421);
	}

	@Test
	void flagVsOriginalTextAudit_medianIncomePercentMentions_reportsMismatchRateWithoutFailing() throws IOException {
		List<Gov24SupportConditionDto> conditions = loadSupportConditionsSnapshot();
		Map<String, Gov24SupportConditionDto> conditionsById = new LinkedHashMap<>();
		for (Gov24SupportConditionDto condition : conditions) {
			conditionsById.put(condition.serviceId(), condition);
		}
		List<ServiceDetailRecord> details = loadServiceDetailSnapshot();

		int audited = 0;
		int mismatched = 0;
		List<String> mismatchReport = new ArrayList<>();
		for (ServiceDetailRecord detail : details) {
			Gov24SupportConditionDto condition = conditionsById.get(detail.serviceId());
			if (condition == null) {
				continue;
			}
			String criteriaText = joinNullable(detail.selectionCriteria(), detail.supportTarget());
			Integer maxMentionedPercent = maxMedianIncomePercent(criteriaText);
			if (maxMentionedPercent == null) {
				continue;
			}
			audited++;
			List<String> expectedYFields = expectedIncomeFieldsFor(maxMentionedPercent);
			List<String> actualYFields = actualIncomeYFields(condition);
			boolean matches = actualYFields.containsAll(expectedYFields);
			if (!matches) {
				mismatched++;
				mismatchReport.add("  불일치: " + detail.serviceId() + " " + detail.serviceName() + " | 원문 중위소득 "
						+ maxMentionedPercent + "% | 기대 Y=" + expectedYFields + " | 실제 Y=" + actualYFields);
			}
		}

		double mismatchRate = audited == 0 ? 0.0 : mismatched * 100.0 / audited;

		System.out.println("=== 플래그 대 원문 대조 감사 (중위소득 N% 언급 건, n=" + audited + ") ===");
		System.out.println("  정합: " + (audited - mismatched) + "건, 불일치: " + mismatched + "건");
		System.out.printf("  불일치율: %.2f%%%n", mismatchRate);
		mismatchReport.forEach(System.out::println);
		System.out.println("  참고: 이 불일치율은 우리 코드의 버그가 아니라 gov24 원문 데이터의 성질임"
				+ "(외부API-부족분-조사-2026-07-12.md 7장 함정4 — 담당자가 손으로 채운 구조화 필드는 원문과의"
				+ " 정합이 보장되지 않음). 하드 배제 필터 채택 여부는 회의 안건 22번 결정 사항.");

		// 테스트를 실패시키지 않음(임무 지시 3장) — 다만 감사 표본이 실제로 확보됐는지와, 스냅샷이 고정돼
		// 있어 불일치 건수가 결정적으로 재현되는지는 확인함(회귀 고정)
		assertThat(audited).isGreaterThan(0);
		assertThat(audited).isEqualTo(64);
		assertThat(mismatched).isEqualTo(14);
	}

	private Gov24SupportConditionDto findBySnapshotId(String serviceId) throws IOException {
		return loadSupportConditionsSnapshot().stream()
			.filter(c -> serviceId.equals(c.serviceId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("스냅샷에서 서비스ID를 찾지 못함: " + serviceId));
	}

	private List<Gov24SupportConditionDto> loadSupportConditionsSnapshot() throws IOException {
		String json = readSnapshot("gov24_supportConditions_snapshot.json");
		return parser.parseSupportConditions(json);
	}

	private List<ServiceDetailRecord> loadServiceDetailSnapshot() throws IOException {
		String json = readSnapshot("gov24_serviceDetail_snapshot.json");
		ServiceDetailSnapshotWrapper wrapper = objectMapper.readValue(json, ServiceDetailSnapshotWrapper.class);
		return wrapper.data() == null ? List.of() : wrapper.data();
	}

	private String readSnapshot(String fileName) throws IOException {
		return Files.readString(SNAPSHOT_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

	private static Map<EligibilitySignal, Long> countBySignal(List<Gov24SupportConditionDto> conditions,
			Function<Gov24SupportConditionDto, EligibilitySignal> classifier) {
		Map<EligibilitySignal, Long> counts = new EnumMap<>(EligibilitySignal.class);
		for (EligibilitySignal signal : EligibilitySignal.values()) {
			counts.put(signal, 0L);
		}
		for (Gov24SupportConditionDto condition : conditions) {
			counts.merge(classifier.apply(condition), 1L, Long::sum);
		}
		return counts;
	}

	private static double percentOf(Long count, int total) {
		return count == null || total == 0 ? 0.0 : count * 100.0 / total;
	}

	private static String joinNullable(String first, String second) {
		String a = first == null ? "" : first;
		String b = second == null ? "" : second;
		return a + " " + b;
	}

	// 선정기준·지원대상 원문에서 "중위소득 N%" 언급 중 가장 큰 N을 찾음(구간이 여러 번 언급되면 상한 기준으로 봄).
	// 언급이 없으면 null(감사 대상이 아님).
	private static Integer maxMedianIncomePercent(String text) {
		Matcher matcher = MEDIAN_INCOME_PERCENT.matcher(text);
		Integer max = null;
		while (matcher.find()) {
			int value = Integer.parseInt(matcher.group(1));
			if (max == null || value > max) {
				max = value;
			}
		}
		return max;
	}

	// 원문의 중위소득 N% 상한을 swagger 구간 정의(JA0201 0~50 / JA0202 51~75 / JA0203 76~100 / JA0204
	// 101~200 / JA0205 200 초과)에 매핑해, 그 상한을 커버하려면 최소한 Y여야 하는 구간 목록을 반환함.
	private static List<String> expectedIncomeFieldsFor(int maxPercent) {
		if (maxPercent <= 50) {
			return List.of("JA0201");
		}
		if (maxPercent <= 75) {
			return List.of("JA0201", "JA0202");
		}
		if (maxPercent <= 100) {
			return List.of("JA0201", "JA0202", "JA0203");
		}
		if (maxPercent <= 200) {
			return List.of("JA0201", "JA0202", "JA0203", "JA0204");
		}
		return List.of("JA0201", "JA0202", "JA0203", "JA0204", "JA0205");
	}

	private static List<String> actualIncomeYFields(Gov24SupportConditionDto condition) {
		List<String> result = new ArrayList<>();
		if ("Y".equals(condition.income0To50())) {
			result.add("JA0201");
		}
		if ("Y".equals(condition.income51To75())) {
			result.add("JA0202");
		}
		if ("Y".equals(condition.income76To100())) {
			result.add("JA0203");
		}
		if ("Y".equals(condition.income101To200())) {
			result.add("JA0204");
		}
		if ("Y".equals(condition.incomeOver200())) {
			result.add("JA0205");
		}
		return result;
	}

	// serviceDetail 스냅샷 전용 최소 DTO임. Gov24ServiceItemDto(운영 파서용)는 매칭에 쓰는 4필드만 담고
	// 있어 선정기준·지원대상 원문이 없음 — 이 테스트의 플래그-원문 대조 감사에만 필요한 필드라 운영 DTO를
	// 넓히지 않고 테스트 전용으로 별도 정의함(ponytail: 운영 코드 비대화 방지).
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ServiceDetailRecord(@JsonProperty("서비스ID") String serviceId,
			@JsonProperty("서비스명") String serviceName, @JsonProperty("신청기한") String applicationDeadlineText,
			@JsonProperty("선정기준") String selectionCriteria, @JsonProperty("지원대상") String supportTarget) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ServiceDetailSnapshotWrapper(@JsonProperty("data") List<ServiceDetailRecord> data) {
	}

}
