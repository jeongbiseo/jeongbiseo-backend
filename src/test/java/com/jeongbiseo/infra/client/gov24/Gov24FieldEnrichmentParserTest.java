package com.jeongbiseo.infra.client.gov24;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.gov24.dto.Gov24ApplicationMethodFlags;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.ParsedSubsidyResult;
import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * gov24 serviceDetail 5필드(agency·description·eligibilityText·externalUrl·dataUpdatedAt)
 * 판독, 지원유형(paymentType) 45종 매핑, 신청방법 키워드 플래그, 구비서류 정규화를 실호출
 * 스냅샷(demo/fixtures/regression/)으로 검증하는 회귀 테스트임(임무 지시 1장에서 4장). 스냅샷 출처·수집 방법은
 * Gov24JaFieldParserTest와 동일(체계적 표집 step=10, n=1,097,
 * gov24_supportConditions_snapshot.json과 서비스ID 집합 완전 일치 — 수집 방법은
 * fixtures/regression/SNAPSHOT_META.md 참조). 스냅샷 파일이 고정돼 있어 매 실행마다 같은 레코드를 읽으므로, 이 테스트의 분포
 * 검증은 Gov24JaFieldParserTest의 JA 필드 분포 검증과 달리 허용 오차 없이 정확한 건수로 고정함(모집단 대 표본 비교가 아니라 같은
 * 표본을 두 번 세는 것이므로).
 */
class Gov24FieldEnrichmentParserTest {

	private static final Path SNAPSHOT_DIR = Path.of("fixtures", "regression");

	private final Gov24Parser parser = new Gov24Parser();

	// ---- 5필수필드 채움률 (임무 지시 1장) ----

	@Test
	void serviceDetailSnapshot_agencyAndDescription_areFullyFilled() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		long agencyFilled = items.stream().filter(i -> notBlank(i.agency())).count();
		long descriptionFilled = items.stream().filter(i -> notBlank(i.description())).count();

		System.out.println("=== agency·description 채움률 (스냅샷 n=" + items.size() + ") ===");
		System.out.println("  agency 채움: " + agencyFilled + "건");
		System.out.println("  description 채움: " + descriptionFilled + "건");

		assertThat(agencyFilled).isEqualTo(items.size());
		assertThat(descriptionFilled).isEqualTo(items.size());
	}

	@Test
	void serviceDetailSnapshot_externalUrlFillRate_matchesReport() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		long filled = items.stream().filter(i -> notBlank(i.externalUrl())).count();
		double fillRate = filled * 100.0 / items.size();

		System.out.println("=== externalUrl 채움률 (스냅샷 n=" + items.size() + ") ===");
		System.out.printf("  채움: %d건 (%.2f%%, 리포트 18.2%%)%n", filled, fillRate);
		System.out.println("  나머지는 상세 화면의 \"신청하러 가기\" 버튼이 뜨지 않는다는 뜻임(임무 지시 1장)");

		// 스냅샷이 고정돼 있어 결정적임(회귀 고정)
		assertThat(filled).isEqualTo(200L);
	}

	@Test
	void serviceDetailSnapshot_dataUpdatedAt_parsesForEveryRecord() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		long parsed = items.stream().filter(i -> parser.parseDataUpdatedAt(i.dataUpdatedAtText()) != null).count();

		System.out.println("=== dataUpdatedAt 파싱 성공률 (스냅샷 n=" + items.size() + ") ===");
		System.out.println("  성공: " + parsed + "건");

		assertThat(parsed).isEqualTo(items.size());
	}

	@Test
	void parseDataUpdatedAt_dateOnlyFormat_parsesAtStartOfDay() {
		LocalDateTime result = parser.parseDataUpdatedAt("2026-01-29");

		assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 29, 0, 0));
	}

	@Test
	void parseDataUpdatedAt_fourteenDigitFallbackFormat_parsesWithTime() {
		// 로컬 픽스처 sample_serviceList.json에서 관찰된 형식임(실호출 재확인은 안 됐음, 방어적으로 지원함)
		LocalDateTime result = parser.parseDataUpdatedAt("20260129201825");

		assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 29, 20, 18, 25));
	}

	@Test
	void parseDataUpdatedAt_unrecognizedFormatOrNull_returnsNull() {
		assertThat(parser.parseDataUpdatedAt("모름")).isNull();
		assertThat(parser.parseDataUpdatedAt(null)).isNull();
	}

	@Test
	void toParsedSubsidy_serviceId000000465790_populatesFiveEssentialFields() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("000000465790");

		ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());

		assertThat(result.agency()).isEqualTo("교육부");
		assertThat(result.description()).contains("3~5세에 대해 교육비를 지급합니다");
		assertThat(result.eligibilityText()).contains("국공립 및 사립유치원에 다니는 3~5세 유아")
			.contains("[선정기준]")
			.contains("2027.2.28");
		assertThat(result.externalUrl()).isEqualTo("https://www.bokjiro.go.kr");
		assertThat(result.dataUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 29, 0, 0));
	}

	@Test
	void toParsedSubsidy_serviceId119200000027_externalUrlIsNullWhenNotProvided() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("119200000027");

		ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());

		assertThat(result.externalUrl()).isNull();
		assertThat(result.agency()).isEqualTo("해양수산부");
	}

	@Test
	void toParsedSubsidy_serviceId300000000154_eligibilityTextOmitsAbsentSelectionCriteria() throws IOException {
		// 선정기준이 없는(null) 대다수 케이스(채움률 9.75%) — eligibilityText는 지원대상만 담아야 함
		Gov24ServiceItemDto item = findBySnapshotId("300000000154");

		ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());

		assertThat(result.eligibilityText()).isEqualTo("복지 사각지대 위기 의심가구");
		assertThat(result.eligibilityText()).doesNotContain("[선정기준]");
	}

	// ---- paymentType 매핑 (임무 지시 2장) ----

	@Test
	void mapPaymentType_cashOnly_isCash() {
		assertThat(parser.mapPaymentType("현금")).isEqualTo(PaymentType.CASH);
	}

	@Test
	void mapPaymentType_reduction_isReduction() {
		assertThat(parser.mapPaymentType("현금(감면)")).isEqualTo(PaymentType.REDUCTION);
	}

	@Test
	void mapPaymentType_voucher_isVoucher() {
		assertThat(parser.mapPaymentType("이용권")).isEqualTo(PaymentType.VOUCHER);
	}

	@Test
	void mapPaymentType_inKind_isInKind() {
		assertThat(parser.mapPaymentType("현물")).isEqualTo(PaymentType.IN_KIND);
	}

	@Test
	void mapPaymentType_loan_isUnknownNotCash() {
		// 융자(대출)는 상환 의무가 있어 무상 지원금과 다름 — "현금"이라는 단어가 있어도 CASH로 분류하면
		// 예상총액(AMT-622)이 부풀려짐(임무 지시 2장)
		assertThat(parser.mapPaymentType("현금(융자)")).isEqualTo(PaymentType.UNKNOWN);
	}

	@Test
	void mapPaymentType_ambiguousServiceOrOtherType_isUnknown() {
		assertThat(parser.mapPaymentType("서비스(의료)")).isEqualTo(PaymentType.UNKNOWN);
		assertThat(parser.mapPaymentType("기타")).isEqualTo(PaymentType.UNKNOWN);
	}

	@Test
	void mapPaymentType_comboValue_isUnknownRegardlessOfCashComponent() {
		// "현금"이 포함돼 있어도 콤보는 유형별 금액을 나눌 수 없어 UNKNOWN임(임무 지시 2장)
		assertThat(parser.mapPaymentType("기타||현금")).isEqualTo(PaymentType.UNKNOWN);
		assertThat(parser.mapPaymentType("기타||현물")).isEqualTo(PaymentType.UNKNOWN);
	}

	@Test
	void mapPaymentType_unseenValue_isUnknown() {
		assertThat(parser.mapPaymentType("본적없는유형")).isEqualTo(PaymentType.UNKNOWN);
	}

	@Test
	void serviceDetailSnapshot_paymentTypeDistribution_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		Map<PaymentType, Long> counts = new EnumMap<>(PaymentType.class);
		for (PaymentType type : PaymentType.values()) {
			counts.put(type, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			counts.merge(parser.mapPaymentType(item.paymentTypeText()), 1L, Long::sum);
		}

		System.out.println("=== 지원유형 대 PaymentType 분포 (스냅샷 n=" + items.size() + ") ===");
		counts.forEach(
				(type, count) -> System.out.printf("  %s: %d건 (%.2f%%)%n", type, count, count * 100.0 / items.size()));

		assertThat(counts.get(PaymentType.CASH)).isEqualTo(433L);
		assertThat(counts.get(PaymentType.IN_KIND)).isEqualTo(134L);
		assertThat(counts.get(PaymentType.VOUCHER)).isEqualTo(60L);
		assertThat(counts.get(PaymentType.REDUCTION)).isEqualTo(53L);
		assertThat(counts.get(PaymentType.UNKNOWN)).isEqualTo(417L);
		assertThat(counts.get(PaymentType.MONTHLY)).isEqualTo(0L);
	}

	// ---- 신청방법 키워드 플래그 (임무 지시 3장) ----

	@Test
	void parseApplicationMethod_visitAndOnlineTogether_bothFlagsTrue() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("000000465790");

		Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(item.applicationMethodText());

		assertThat(flags.online()).isTrue();
		assertThat(flags.visit()).isTrue();
		assertThat(flags.mail()).isFalse();
		assertThat(flags.unclassified()).isFalse();
	}

	@Test
	void parseApplicationMethod_mailAndVisit_bothFlagsTrue() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("119200000027");

		Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(item.applicationMethodText());

		assertThat(flags.mail()).isTrue();
		assertThat(flags.visit()).isTrue();
		assertThat(flags.online()).isFalse();
	}

	@Test
	void parseApplicationMethod_autoProvidedPhrase_setsAutoProvidedFlagOnly() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("300000000154");

		Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(item.applicationMethodText());

		assertThat(flags.autoProvided()).isTrue();
		assertThat(flags.online()).isFalse();
		assertThat(flags.visit()).isFalse();
		assertThat(flags.unclassified()).isFalse();
	}

	@Test
	void parseApplicationMethod_noKnownKeyword_isUnclassified() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("119200000192");

		Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(item.applicationMethodText());

		assertThat(flags.unclassified()).isTrue();
		assertThat(flags.online()).isFalse();
		assertThat(flags.visit()).isFalse();
		assertThat(flags.mail()).isFalse();
		assertThat(flags.fax()).isFalse();
		assertThat(flags.phone()).isFalse();
		assertThat(flags.autoProvided()).isFalse();
	}

	@Test
	void parseApplicationMethod_nullText_isUnclassified() {
		Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(null);

		assertThat(flags.unclassified()).isTrue();
	}

	@Test
	void serviceDetailSnapshot_applicationMethodFlagDistribution_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		long online = 0;
		long visit = 0;
		long mail = 0;
		long fax = 0;
		long phone = 0;
		long autoProvided = 0;
		long unclassified = 0;
		for (Gov24ServiceItemDto item : items) {
			Gov24ApplicationMethodFlags flags = parser.parseApplicationMethod(item.applicationMethodText());
			if (flags.online()) {
				online++;
			}
			if (flags.visit()) {
				visit++;
			}
			if (flags.mail()) {
				mail++;
			}
			if (flags.fax()) {
				fax++;
			}
			if (flags.phone()) {
				phone++;
			}
			if (flags.autoProvided()) {
				autoProvided++;
			}
			if (flags.unclassified()) {
				unclassified++;
			}
		}

		int total = items.size();
		System.out.println("=== 신청방법 키워드 분류 분포 (스냅샷 n=" + total + ") ===");
		System.out.printf("  온라인: %d건 (%.2f%%)%n", online, online * 100.0 / total);
		System.out.printf("  방문: %d건 (%.2f%%)%n", visit, visit * 100.0 / total);
		System.out.printf("  우편: %d건 (%.2f%%)%n", mail, mail * 100.0 / total);
		System.out.printf("  팩스: %d건 (%.2f%%)%n", fax, fax * 100.0 / total);
		System.out.printf("  전화: %d건 (%.2f%%)%n", phone, phone * 100.0 / total);
		System.out.printf("  자동제공: %d건 (%.2f%%)%n", autoProvided, autoProvided * 100.0 / total);
		System.out.printf("  미분류: %d건 (%.2f%%)%n", unclassified, unclassified * 100.0 / total);

		assertThat(online).isEqualTo(251L);
		assertThat(visit).isEqualTo(785L);
		assertThat(mail).isEqualTo(54L);
		assertThat(fax).isEqualTo(31L);
		assertThat(phone).isEqualTo(72L);
		assertThat(autoProvided).isEqualTo(64L);
		assertThat(unclassified).isEqualTo(136L);
	}

	// ---- 구비서류 (임무 지시 4장) ----

	@Test
	void toParsedSubsidy_requiredDocumentsNotApplicable_normalizesToNull() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("300000000154");

		ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());

		assertThat(result.requiredDocumentsText()).isNull();
	}

	@Test
	void toParsedSubsidy_requiredDocumentsPresent_keepsRawText() throws IOException {
		Gov24ServiceItemDto item = findBySnapshotId("000000465790");

		ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());

		assertThat(result.requiredDocumentsText()).contains("사회복지서비스 및 급여제공(변경) 신청서");
	}

	@Test
	void serviceDetailSnapshot_requiredDocumentsNotApplicableRate_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		long normalizedToNull = 0;
		for (Gov24ServiceItemDto item : items) {
			ParsedSubsidyResult result = parser.toParsedSubsidy(item, Map.of());
			if (result.requiredDocumentsText() == null) {
				normalizedToNull++;
			}
		}

		double rate = normalizedToNull * 100.0 / items.size();
		System.out.println("=== 구비서류 \"해당없음\" 정규화 비율 (스냅샷 n=" + items.size() + ") ===");
		System.out.printf("  null 처리: %d건 (%.2f%%, 리포트 41.7%%)%n", normalizedToNull, rate);

		assertThat(normalizedToNull).isEqualTo(457L);
	}

	private Gov24ServiceItemDto findBySnapshotId(String serviceId) throws IOException {
		return loadServiceDetailSnapshot().stream()
			.filter(i -> serviceId.equals(i.serviceId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("스냅샷에서 서비스ID를 찾지 못함: " + serviceId));
	}

	private List<Gov24ServiceItemDto> loadServiceDetailSnapshot() throws IOException {
		String json = readSnapshot("gov24_serviceDetail_snapshot.json");
		return parser.parseServiceItems(json);
	}

	private String readSnapshot(String fileName) throws IOException {
		return Files.readString(SNAPSHOT_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
