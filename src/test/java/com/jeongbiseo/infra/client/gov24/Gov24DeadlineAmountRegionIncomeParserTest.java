package com.jeongbiseo.infra.client.gov24;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.client.common.dto.AmountParseStatus;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.gov24.dto.IncomeConsistencyStatus;
import com.jeongbiseo.infra.client.gov24.dto.IncomeSignalSource;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.infra.client.gov24.dto.ParsedRegion;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;

/**
 * gov24 파서의 신청기한 7분류(DeadlineKind)·금액 4분류(AmountKind)·지역 유추(ParsedRegion)·소득 신호 일치성(후속 임무
 * 1장에서 4장)을 검증하는 테스트임. 스팟 테스트는 실호출 스냅샷(demo/fixtures/regression/)에서 찾은 고정 서비스ID로, 분포 테스트는
 * 스냅샷 n=1097 전량으로 회귀 고정함(수집 방법은 fixtures/regression/SNAPSHOT_META.md 참조).
 */
class Gov24DeadlineAmountRegionIncomeParserTest {

	private static final Path SNAPSHOT_DIR = Path.of("fixtures", "regression");

	private final Gov24Parser parser = new Gov24Parser();

	// ---- DeadlineKind 7분류 (임무 지시 1장) ----

	@Test
	void classifyDeadlineKind_alwaysOpenKeyword_isAlwaysOpen() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("000000465790");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.ALWAYS_OPEN);
		assertThat(result.startDate()).isNull();
		assertThat(result.endDate()).isNull();
	}

	@Test
	void classifyDeadlineKind_dateRangeWithFullYear_extractsRealDates() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("135200005013");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.DATE_RANGE);
		assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 5, 4));
		assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 5, 20));
	}

	@Test
	void classifyDeadlineKind_dateRangeSecondDateOmitsYear_inheritsFirstYear() {
		// "2025.1.21. ~ 12.20." — 둘째 날짜가 연도를 생략하면 첫째 날짜 연도(2025)를 물려받아야 함(구 파서의
		// Year.now() 버그를 반복하지 않음)
		ParsedDeadline result = parser.classifyDeadlineKind("2025.1.21. ~ 12.20.");

		assertThat(result.kind()).isEqualTo(DeadlineKind.DATE_RANGE);
		assertThat(result.startDate()).isEqualTo(LocalDate.of(2025, 1, 21));
		assertThat(result.endDate()).isEqualTo(LocalDate.of(2025, 12, 20));
	}

	@Test
	void classifyDeadlineKind_absoluteDateWithKkaji_isFixedDate() {
		ParsedDeadline result = parser.classifyDeadlineKind("2025년 8월 30일까지");

		assertThat(result.kind()).isEqualTo(DeadlineKind.FIXED_DATE);
		assertThat(result.startDate()).isNull();
		assertThat(result.endDate()).isEqualTo(LocalDate.of(2025, 8, 30));
	}

	@Test
	void classifyDeadlineKind_budgetExhaustionKeyword_isUntilBudgetExhausted() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("360000000109");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.UNTIL_BUDGET_EXHAUSTED);
	}

	@Test
	void classifyDeadlineKind_budgetKeywordBeatsAlwaysOpenWhenBothPresent() {
		// "상시신청(예산 소진시 당해 사업종료)" — 상시와 소진이 함께 있으면 실제 마감 조건인 예산 소진 쪽을 우선함
		ParsedDeadline result = parser.classifyDeadlineKind("상시신청(예산 소진시 당해 사업종료)");

		assertThat(result.kind()).isEqualTo(DeadlineKind.UNTIL_BUDGET_EXHAUSTED);
	}

	@Test
	void classifyDeadlineKind_periodicKeyword_isPeriodic() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("119200000073");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.PERIODIC);
	}

	@Test
	void classifyDeadlineKind_announcementKeyword_isAnnouncementBased() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("119200000027");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.ANNOUNCEMENT_BASED);
	}

	@Test
	void classifyDeadlineKind_noKnownPattern_isUnknown() throws IOException {
		// "접수기관 별 상이" — 근거가 있는 표현이지만 7분류 중 정직하게 표현할 수 있는 게 없어 UNKNOWN(임무 지시 1장
		// "상상 금지")
		Gov24ServiceItemDto item = findServiceDetailById("129000000009");

		ParsedDeadline result = parser.classifyDeadlineKind(item.applicationDeadlineText());

		assertThat(result.kind()).isEqualTo(DeadlineKind.UNKNOWN);
	}

	@Test
	void classifyDeadlineKind_blankOrNull_isUnknown() {
		assertThat(parser.classifyDeadlineKind(null).kind()).isEqualTo(DeadlineKind.UNKNOWN);
		assertThat(parser.classifyDeadlineKind("").kind()).isEqualTo(DeadlineKind.UNKNOWN);
	}

	@Test
	void serviceDetailSnapshot_deadlineKindDistribution_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		Map<DeadlineKind, Long> counts = new EnumMap<>(DeadlineKind.class);
		for (DeadlineKind kind : DeadlineKind.values()) {
			counts.put(kind, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			counts.merge(parser.classifyDeadlineKind(item.applicationDeadlineText()).kind(), 1L, Long::sum);
		}

		System.out.println("=== 신청기한 7분류(DeadlineKind) 분포 (스냅샷 n=" + items.size() + ") ===");
		counts.forEach(
				(kind, count) -> System.out.printf("  %s: %d건 (%.2f%%)%n", kind, count, count * 100.0 / items.size()));

		assertThat(counts.get(DeadlineKind.FIXED_DATE)).isEqualTo(0L);
		assertThat(counts.get(DeadlineKind.DATE_RANGE)).isEqualTo(51L);
		assertThat(counts.get(DeadlineKind.ALWAYS_OPEN)).isEqualTo(673L);
		assertThat(counts.get(DeadlineKind.PERIODIC)).isEqualTo(79L);
		assertThat(counts.get(DeadlineKind.UNTIL_BUDGET_EXHAUSTED)).isEqualTo(11L);
		assertThat(counts.get(DeadlineKind.ANNOUNCEMENT_BASED)).isEqualTo(37L);
		assertThat(counts.get(DeadlineKind.UNKNOWN)).isEqualTo(246L);
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		assertThat(total).isEqualTo(items.size());
	}

	// ---- AmountKind 4분류 (임무 지시 2장) ----

	@Test
	void parseAmount_blankOrNull_isNone() {
		assertThat(parser.parseAmount(null).amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(parser.parseAmount(null).parseStatus()).isEqualTo(AmountParseStatus.NOT_FOUND);
		assertThat(parser.parseAmount(null).amountCandidates()).isEmpty();
		assertThat(parser.parseAmount("").amountKind()).isEqualTo(AmountKind.NONE);
	}

	@Test
	void parseAmount_noAmountExpression_isNone() throws IOException {
		Gov24ServiceItemDto item = findServiceDetailById("119200000130");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(result.parseStatus()).isEqualTo(AmountParseStatus.NOT_FOUND);
	}

	@Test
	void parseAmount_singleFlatAmountWithManwonUnit_convertsToWon() throws IOException {
		// "최대 4천만원" — 단위 환산이 정확한지(4천만원 -> 40,000,000원)
		Gov24ServiceItemDto item = findServiceDetailById("142000000061");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.SINGLE);
		assertThat(result.amountCandidates()).containsExactly(40_000_000L);
		assertThat(result.minAmount()).isEqualTo(40_000_000L);
		assertThat(result.maxAmount()).isEqualTo(40_000_000L);
		assertThat(result.parseStatus()).isEqualTo(AmountParseStatus.PARSED);
	}

	@Test
	void parseAmount_multipleAmountsWithoutConditionMarker_isMultiple() throws IOException {
		// 국공립/사립처럼 대상별 금액이 나열되지만 "당"·"별" 같은 조건 표현이 없어 MULTIPLE임(CONDITIONAL 아님)
		Gov24ServiceItemDto item = findServiceDetailById("000000465790");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.MULTIPLE);
		assertThat(result.amountCandidates()).containsExactly(100_000L, 280_000L, 50_000L, 70_000L, 200_000L);
		assertThat(result.minAmount()).isEqualTo(50_000L);
		assertThat(result.maxAmount()).isEqualTo(280_000L);
		assertThat(result.conditionSummary()).isNull();
	}

	@Test
	void parseAmount_householdPerUnitMarker_isConditionalWithHouseholdUnit() throws IOException {
		// "가구당 3만원" — 단일 금액이라도 "당" 조건 표현이 붙으면 CONDITIONAL, amountUnit은 "가구"
		Gov24ServiceItemDto item = findServiceDetailById("300000000123");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.amountCandidates()).containsExactly(30_000L);
		assertThat(result.amountUnit()).isEqualTo("가구");
		assertThat(result.conditionSummary()).isNotBlank().contains("가구당");
		assertThat(result.conditionSummary().length()).isLessThanOrEqualTo(200);
	}

	@Test
	void parseAmount_personPerUnitMarker_isConditionalWithPersonUnit() throws IOException {
		// "신생아 1명당 100만원" — amountUnit은 "인"
		Gov24ServiceItemDto item = findServiceDetailById("311000000106");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.amountUnit()).isEqualTo("인");
	}

	@Test
	void parseAmount_haPerUnitMultipleTiers_isConditional() throws IOException {
		// "ha 당 50 ~ 600만 원", 작물별로 여러 금액이 "당" 조건과 함께 나열됨 — 조건부 차등의 전형
		Gov24ServiceItemDto item = findServiceDetailById("154300005041");

		ParsedAmount result = parser.parseAmount(item.description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.amountCandidates()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void parseAmount_siMarkerAsPartOfUnrelatedWord_isNotTreatedAsConditional() {
		// "시비 3만원" — "시"가 조건 어미가 아니라 "시비"(시 예산)라는 단어의 일부라 조건으로 잡히면 안 됨
		ParsedAmount result = parser.parseAmount("구비 2만원, 시비 3만원 지원");

		assertThat(result.amountKind()).isNotEqualTo(AmountKind.CONDITIONAL);
	}

	@Test
	void parseAmount_siMarkerAsGenuineConditionSuffix_isConditional() {
		// "전입신고 시 20만원" — "시"가 독립된 조건 어미(뒤가 한글이 아님)라 조건으로 잡혀야 함
		ParsedAmount result = parser.parseAmount("전입신고 시 20만원 지급");

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
	}

	// ---- 금액 오분류 고정 테스트 (2026-07-12 오분류 수정 임무, 유형A~E 재발 방지) ----
	//
	// 아래 테스트들은 "직전 검증이 실측으로 잡은 오분류"와 이번 전수 재검사에서 새로 찾은 오분류를 서비스ID로
	// 못 박음. 규칙 창(window) 하나만 건드려도 이 중 하나는 반드시 깨지도록 양방향으로 배치함 — 배제 규칙을
	// 넓히면 유형F(과잉 배제 방지) 테스트가 깨지고, 좁히면 유형A 테스트가 깨짐.

	@Test
	void parseAmount_typeA_projectBudgetOnly_isExcludedAndNone() throws IOException {
		// 519000000102 "ㅇ 사업예산 : 97,730천원" — 사업 전체 예산이라 개인 지급액이 아님. 수정 전 SINGLE
		// 97,730,000원이었음
		ParsedAmount budget = parser.parseAmount(findServiceDetailById("519000000102").description());
		assertThat(budget.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(budget.amountCandidates()).isEmpty();
		assertThat(budget.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);

		// 559000000440 "상금 총 19,000천원" — 대회 총상금이라 개인 수상액이 아님. 수정 전 SINGLE
		// 19,000,000원이었음
		ParsedAmount prize = parser.parseAmount(findServiceDetailById("559000000440").description());
		assertThat(prize.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(prize.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);
	}

	@Test
	void parseAmount_typeA_spacedOutBudgetKeyword_isExcluded() throws IOException {
		// 650000000330 "❍ 사 업 비 : 250백만원" — 자간을 띄운 예산 어휘. 공백을 지우고 대조하지 않으면
		// 못 잡음. 수정 전 SINGLE 250,000,000원이었음
		ParsedAmount spacedBusinessCost = parser.parseAmount(findServiceDetailById("650000000330").description());
		assertThat(spacedBusinessCost.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(spacedBusinessCost.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);

		// 402000000148 "❍ 예 산 액 : 50,000천원(80,000원 × 625명)" — 예산액만 배제하고 실제 1회요금
		// 80,000원은 남겨야 함. 수정 전 maxAmount가 50,000,000원이었음
		ParsedAmount spacedBudget = parser.parseAmount(findServiceDetailById("402000000148").description());
		assertThat(spacedBudget.amountCandidates()).containsExactly(80_000L, 80_000L);
		assertThat(spacedBudget.maxAmount()).isEqualTo(80_000L);
		assertThat(spacedBudget.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);
	}

	@Test
	void parseAmount_typeA_totalScaleWithoutBudgetKeyword_isExcluded() throws IOException {
		// 430000000135 "보조금 지원(799,920천원/3,333ha)" — 예산 어휘 없이 총액을 총규모로 나눠 적은 표기.
		// 수정 전 SINGLE 799,920,000원으로 스냅샷 최대 오염원이었음
		ParsedAmount bulkDivisor = parser.parseAmount(findServiceDetailById("430000000135").description());
		assertThat(bulkDivisor.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(bulkDivisor.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);

		// O00081200001 "선발규모 : 총94명, 86,224천원 ... 지급금액: 중학생50만원, 고등학생100만원,
		// 대학생200만원" — 총 장학예산만 배제하고 학교급별 실제 지급액 3건은 남겨야 함. 수정 전 maxAmount가
		// 86,224,000원이었음
		ParsedAmount totalHeadcount = parser.parseAmount(findServiceDetailById("O00081200001").description());
		assertThat(totalHeadcount.amountCandidates()).containsExactly(500_000L, 1_000_000L, 2_000_000L);
		assertThat(totalHeadcount.maxAmount()).isEqualTo(2_000_000L);
		assertThat(totalHeadcount.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);
	}

	@Test
	void parseAmount_typeA_sameBudgetValueRestated_isAlsoExcluded() throws IOException {
		// 154300000061 "(사업비지원) 1년차 48백만원(농식품부 24, 지자체 24), 2년차 48백만원" — 둘째
		// "48백만원"은 앞에 마커가 없어 살아남던 문제. 같은 값의 재언급은 같은 예산으로 봄. 수정 전 SINGLE
		// 48,000,000원이었음
		ParsedAmount result = parser.parseAmount(findServiceDetailById("154300000061").description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(result.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);
	}

	@Test
	void parseAmount_typeA_perUnitAllocation_isNotExcluded_butDemotedToConditional() {
		// **과잉 배제 차단(2026-07-12 적대 검증 최우선 지시).** 단위 수식어가 예산 어휘에 바로 붙으면 그 금액은 사업
		// 총예산이 아니라 수혜자 1단위 배분액이라 살려야 함. 온통청년 20250901005400211556 원문 형태
		ParsedAmount perPerson = parser.parseAmount("○ 지원내용 : 1인당 사업비 12백만원 및 창업 교육 멘토링 서비스 제공");
		assertThat(perPerson.amountCandidates()).containsExactly(12_000_000L);
		assertThat(perPerson.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(perPerson.parseStatus()).isEqualTo(AmountParseStatus.PARSED);

		// 온통청년 20250109005400210105 원문 형태 — 같은 문장의 사업 총예산은 그대로 배제하고 개소당 배분액만 살림
		ParsedAmount perSite = parser.parseAmount("○ 사 업 비: 300백만원(군비70% 270, 자담10% 30) / 개소당 사업비 30백만원");
		assertThat(perSite.amountCandidates()).containsExactly(30_000_000L);
		assertThat(perSite.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(perSite.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);
	}

	@Test
	void parseAmount_typeA_budgetKeywordNotFusedWithUnitMarker_isStillExcluded() {
		// 반대 방향 고정 — 단위 수식어가 예산 어휘와 **떨어져** 있으면 융합이 아니므로 예산 배제를 유지함.
		// 155000000018 형태: "건당"은 뒤따르는 8천만원에 붙은 마커라 앞의 158억원은 사업예산 그대로임
		ParsedAmount separated = parser.parseAmount("소규모 발굴조사 연평균 200여건 지원, 예산 158억 원 내외, 건당 평균 8천만 원 내외");
		assertThat(separated.amountCandidates()).containsExactly(80_000_000L);
		assertThat(separated.amountCandidates()).doesNotContain(15_800_000_000L);
		assertThat(separated.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);

		// 20260527005400113224 형태: 수식어와 예산 어휘 사이에 다른 문구가 끼면 융합이 아님 — 242억원은 사업예산
		ParsedAmount interrupted = parser.parseAmount("응시료의 50%를 선 지원 (1인당 年 3회 지원, 단 예산 242억 원 소진시 마감)");
		assertThat(interrupted.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(interrupted.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT);
	}

	@Test
	void parseAmount_typeB_perUnitMarkerAfterAmount_isConditional() throws IOException {
		// 금액 **뒤**에 오는 "N당" 조건 마커. 수정 전 전부 SINGLE이라 예상총액 합산 대상이었음
		// 422000000133 "지원금액: 200천원(1인당)"
		assertConditionalWithSingleCandidate("422000000133", 200_000L);
		// 405000000628 "입학준비금 지원(연 1회/10만원 이내/ 아동당)"
		assertConditionalWithSingleCandidate("405000000628", 100_000L);
	}

	@Test
	void parseAmount_typeB_perUnitMarkerFarBeforeAmount_isConditional() throws IOException {
		// 금액 **앞** 10자 밖에 있어 기존 좁은 창으로는 못 잡던 "N당" 조건 마커. 전부 수정 전 SINGLE이었음
		// 422000000119 "1인당 1회 태백사랑상품권 10만원을 지급"
		assertConditionalWithSingleCandidate("422000000119", 100_000L);
		// 511000000113 "자녀 1인당 교복(하복) 지원금 실비 20만원 지급"
		assertConditionalWithSingleCandidate("511000000113", 200_000L);
		// 650000001140 "1인당 안경(렌즈 등) 구입비 최대 10만원 범위내(1년)"
		assertConditionalWithSingleCandidate("650000001140", 100_000L);
		// 328000000120 "업소당 조리환경 개선 비용 최대 30만원 지원"
		assertConditionalWithSingleCandidate("328000000120", 300_000L);
		// 349000000109 "지원한도 : 농가당 1대 지원(최대 240만원 보조)"
		assertConditionalWithSingleCandidate("349000000109", 2_400_000L);
	}

	@Test
	void parseAmount_typeC_tieredEnumWithOmittedUnit_isConditional() throws IOException {
		// 439000000873 "입학지원금 지급(초 30, 중 40, 고 50만원)" — 단위가 마지막 항목에만 붙은 다단 차등.
		// 정규식은 "50만원"만 금액으로 인식하므로 SINGLE 50만원이 되어 초·중 금액이 통째로 사라졌음. 항목별
		// 역산은 하지 않고(스냅샷 전수 1건뿐이라 로직을 만들지 않음) CONDITIONAL로 내려 원문을
		// conditionSummary에 남김
		ParsedAmount result = parser.parseAmount(findServiceDetailById("439000000873").description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.conditionSummary()).isNotBlank().contains("초 30, 중 40, 고 50만원");
	}

	@Test
	void parseAmount_typeD_perPersonUnitFromRawText_staysPerson() throws IOException {
		// 134200000045 "1인당 연간 35만원" — amountUnit이 "인"으로 나오는 것은 원문 "1인당"을 그대로 관찰한
		// 값이라 오추출이 아니라고 판단함(임무 지시의 유형D 지목을 self-reject). 근거 세 가지:
		// (1) 같은 형태("신생아 1명당 100만원")를 "인"으로 고정한 기존 테스트
		// parseAmount_personPerUnitMarker_isConditionalWithPersonUnit이 이 의미를 정본으로 못 박고
		// 있고,
		// 그 테스트를 약화시키지 말라는 제약이 있음
		// (2) "1인당(신청자 본인)"과 "신생아 1명당(수혜 객체)"은 텍스트가 같은 모양이라 기계적으로 구분 불가 —
		// 구분 규칙을 지어내면 근거 없는 추측이 됨
		// (3) 1인당이라는 사실은 CONDITIONAL 판정과 conditionSummary가 이미 보존하고, CONDITIONAL은 예상총액
		// 자동 채움 대상이 아니라 이 단위값이 총액을 오염시키지 않음
		ParsedAmount result = parser.parseAmount(findServiceDetailById("134200000045").description());

		assertThat(result.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.amountCandidates()).containsExactly(350_000L);
		assertThat(result.amountUnit()).isEqualTo("인");
		assertThat(result.conditionSummary()).contains("1인당 연간 35만원");
	}

	@Test
	void parseAmount_typeE_eligibilityThresholdAfterAmount_isConditionalNotSingle() throws IOException {
		// 전수 재검사에서 새로 찾은 유형 — 자격 기준선을 개인 지급액으로 오인하던 건들. 전부 수정 전 SINGLE이라
		// 예상총액 자동 채움 대상이었음
		// B55307700005 "소송비용 및 변호사 비용 지원(단, 승소가액 3억원 이상 ... 제외)" — 실제 지급액은 아예
		// 없고 3억원은 지원 배제 기준선임. 수정 전 SINGLE 300,000,000원(스냅샷 최대 오염원 중 하나)
		assertConditionalWithSingleCandidate("B55307700005", 300_000_000L);
		// 308000000120 "건강보험 지역가입자의 월 보험료가 22,340원 이하인 가구" — 자격 기준선
		assertConditionalWithSingleCandidate("308000000120", 22_340L);
		// 630000000154 "농산물의 연간 판매액이 120만원 이상인 자" — 자격 기준선
		assertConditionalWithSingleCandidate("630000000154", 1_200_000L);
	}

	@Test
	void parseAmount_typeF_realCapAfterBudgetKeyword_isNotOverExcluded() throws IOException {
		// 과잉 배제 방지 — 배제 규칙이 진짜 개인(기업) 지급액까지 먹지 않는지 반대 방향으로 고정함.
		// 142000000061 "총사업비의 50% 이내 / 최대 4천만원"에서 4천만원은 실제 상한액임. 예산 문맥 창을 17자
		// 이상으로 넓히면 앞의 "사업비"가 창에 들어와 이 금액이 배제됨 — 창 15자의 상한 근거
		ParsedAmount cap = parser.parseAmount(findServiceDetailById("142000000061").description());
		assertThat(cap.amountKind()).isEqualTo(AmountKind.SINGLE);
		assertThat(cap.amountCandidates()).containsExactly(40_000_000L);
		assertThat(cap.parseStatus()).isEqualTo(AmountParseStatus.PARSED);

		// 571000000107 "일반농가 ha당 35만원, 친환경농가 ha당 50만원 보상금 지급" — "보상금"에 "상금"이
		// 들어 있음. 총상금 마커를 "상금"으로 좁히지 않고 "상금총"으로 둔 근거
		ParsedAmount compensation = parser.parseAmount(findServiceDetailById("571000000107").description());
		assertThat(compensation.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(compensation.amountCandidates()).containsExactly(350_000L, 500_000L);
		assertThat(compensation.parseStatus()).isEqualTo(AmountParseStatus.PARSED);

		// 626000000112 "1인당 수술 5백만원 이내, 재활치료 3백만원 이내(예산범위 내 지원)" — "예산"이 금액
		// **뒤**에 옴. 예산 어휘를 뒤쪽까지 보면 이 두 지급액이 통째로 날아감
		ParsedAmount budgetAfter = parser.parseAmount(findServiceDetailById("626000000112").description());
		assertThat(budgetAfter.amountCandidates()).containsExactly(5_000_000L, 3_000_000L);
		assertThat(budgetAfter.parseStatus()).isEqualTo(AmountParseStatus.PARSED);
	}

	@Test
	void parseAmount_typeG_selfPayAmountOnly_isExcludedAndNone() throws IOException {
		// 383000000172 "ㆍ보조금 : 설치금액의 80% / ㆍ자부담금 : 19만원 내외(445W 판넬 1개 기준)" — 19만원은
		// 이용자가 **내는** 돈이고 실제 보조금은 비율로만 적혀 금액이 없음. 수정 전 SINGLE 190,000원·CASH라
		// 예상총액 자동 채움 경로에 있었음(2026-07-12 적대 검증 High)
		ParsedAmount solar = parser.parseAmount(findServiceDetailById("383000000172").description());
		assertThat(solar.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(solar.amountCandidates()).isEmpty();
		assertThat(solar.maxAmount()).isNull();
		assertThat(solar.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_SELF_PAY_CONTEXT);

		// 304000000150 "연 가입비 중 일부 지원 ※ 학생 자부담금 : 10,000원" — 같은 구조(지원액은 "일부"로만
		// 적히고 금액은 자부담금뿐). 수정 전 SINGLE 10,000원
		ParsedAmount lecture = parser.parseAmount(findServiceDetailById("304000000150").description());
		assertThat(lecture.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(lecture.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_SELF_PAY_CONTEXT);
	}

	@Test
	void parseAmount_typeG_selfPayNextToRealSubsidy_keepsRealAmountsAndDemotesToConditional() throws IOException {
		// 392000000164 "(자기부담금 5천원) ... (4만원 지원, 자부담 5천원) ... 자기부담금 5천원 송금" —
		// 자부담 3건만 걷어내고 **진짜 지원액** 4만원은 남겨야 함. "자기부담"은 "자부담"의 부분문자열이 아니라
		// 별도 마커가 필요함. 수정 전 MULTIPLE [5000, 40000, 5000, 5000]
		ParsedAmount voucher = parser.parseAmount(findServiceDetailById("392000000164").description());
		assertThat(voucher.amountCandidates()).containsExactly(40_000L);
		assertThat(voucher.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(voucher.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);
		assertThat(voucher.conditionSummary()).isNotBlank();

		// 483000000113 "지원단가 : 20,000천원 /개소 (보조 10,000천원, 자부담 10,000천원)" 더하기 "지원단가 :
		// 10,000천원 / 개소(보조 7,000천원, 자부담 3,000천원)" — **값 중복 배제 금지의 근거**. 자부담
		// 10,000천원을 값으로 배제하면 같은 값인 진짜 보조 10,000천원과 주택수리비 지원단가 10,000천원까지
		// 죽음(과잉 배제). 자부담 2건만 빠지고 나머지 4건은 그대로 남아야 함
		ParsedAmount returnFarmer = parser.parseAmount(findServiceDetailById("483000000113").description());
		assertThat(returnFarmer.amountCandidates()).containsExactly(20_000_000L, 10_000_000L, 10_000_000L, 7_000_000L);
		assertThat(returnFarmer.minAmount()).isEqualTo(7_000_000L);
		assertThat(returnFarmer.maxAmount()).isEqualTo(20_000_000L);
		assertThat(returnFarmer.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);

		// 571000000120 "최대 5천만원 한도 ... 보조 3,500만원(70%), 자담 1,500만원(30%)" — "자담" 축약형.
		// 자담 1,500만원만 빠지고 한도 5천만원과 보조 3,500만원은 남아야 함
		ParsedAmount youngFarmer = parser.parseAmount(findServiceDetailById("571000000120").description());
		assertThat(youngFarmer.amountCandidates()).containsExactly(50_000_000L, 35_000_000L);
		assertThat(youngFarmer.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);

		// 650000001099 "검진비용 지원: 22만원 기준 자부담 2만원 내외" — 자부담 2만원을 빼면 조건 마커("기준")도
		// 함께 사라져 22만원이 SINGLE로 **승격**될 뻔했음. 자부담 구조는 남은 금액이 순 지급액이 아니라 총
		// 기준단가라는 뜻이므로 CONDITIONAL로 강등해 예상총액 자동 채움에서 뺌
		ParsedAmount checkup = parser.parseAmount(findServiceDetailById("650000001099").description());
		assertThat(checkup.amountCandidates()).containsExactly(220_000L);
		assertThat(checkup.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(checkup.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);
	}

	@Test
	void parseAmount_typeG_selfPayWindowUpperBound_doesNotEatRealUnitPrice() throws IOException {
		// 516000000123 "과수전용소형농기계(군비 40%, 자부담 60%, 복숭아적화기 50만원/대, 꽃가루살포기 90만원/대)
		// ... 과실장기저장제(군비 50%, 자부담 50%, 33,000원/㎡)" — 자부담 창(15자)의 **상한 근거**. 16자로
		// 넓히면 앞의 "자부담 60%"·"자부담 50%"에 걸려 진짜 기준단가 50만원과 700만원까지 배제됨(과잉 배제).
		// 자부담 바로 뒤에 붙은 33,000원만 빠지고 자재 기준단가 4건은 살아 있어야 함
		ParsedAmount result = parser.parseAmount(findServiceDetailById("516000000123").description());

		assertThat(result.amountCandidates()).containsExactly(500_000L, 900_000L, 7_000_000L, 4_000_000L);
		assertThat(result.minAmount()).isEqualTo(500_000L);
		assertThat(result.maxAmount()).isEqualTo(7_000_000L);
		assertThat(result.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);
	}

	@Test
	void parseAmount_typeG_copayPaidByGovernment_isNotExcluded() throws IOException {
		// **과잉 배제 방지 — 반대 방향 고정**. "본인부담금"은 지급 방향이 양쪽이라(이용자가 내는 돈일 수도,
		// 정부가 대신 내주는 혜택일 수도) 마커로 쓰면 안 됨. 아래 6건은 전부 "본인부담금을 **지원**한다"는
		// 진짜 지급액이라 SELF_PAY 배제에 걸리면 안 됨 — 마커를 "부담금"·"본인부담"으로 넓히는 순간 이 테스트가
		// 깨짐. 원문에 "자부담"이 없다는 것이 분리 가능의 근거임
		assertNotSelfPayExcluded("458000000115", 1_000L); // "약제비 본인부담금 1천원 지원"
		assertNotSelfPayExcluded("454000000102", 400_000L); // "본인부담금 90%, 최대 40만원 지원"
		assertNotSelfPayExcluded("641000000147", 1_500_000L); // "본인부담금 지원(연 150만원)"
		assertNotSelfPayExcluded("319000000191", 30_000L); // "비급여항목의 본인부담금 3만원"

		// 648000001029 "발생하는 본인부담금 지원(24만원 ... 65천원)" — 후보 2건 모두 살아 있어야 함
		ParsedAmount screening = parser.parseAmount(findServiceDetailById("648000001029").description());
		assertThat(screening.amountCandidates()).contains(240_000L, 65_000L);
		assertThat(screening.parseStatus()).isEqualTo(AmountParseStatus.PARSED);

		// B37003100009 "금액 중 본인부담금 지원(3,000만원 ...)" — 체육인 복지 지원
		ParsedAmount athlete = parser.parseAmount(findServiceDetailById("B37003100009").description());
		assertThat(athlete.amountCandidates()).contains(30_000_000L);
		assertThat(athlete.parseStatus()).isEqualTo(AmountParseStatus.PARSED);
	}

	@Test
	void parseAmount_typeH_loanLimitIsExcluded_becauseItIsDebtCeilingNotPayout() throws IOException {
		// **대출·보증 한도는 아무도 주지 않는 돈임**(빌린 뒤 갚아야 하는 채무 상한). 예산(정부가 쓰는 돈)·자부담
		// (이용자가 내는 돈)과 방향이 또 다른 제3의 오염원이라 상태를 따로 남김

		// 142100000071 "보증한도 : 같은기업당 재단보증금액 2억원 이내" — 금액이 보증 한도뿐이라 산정불가
		ParsedAmount guarantee = parser.parseAmount(findServiceDetailById("142100000071").description());
		assertThat(guarantee.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(guarantee.amountCandidates()).isEmpty();
		assertThat(guarantee.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// B55370100029 경남동행론 "ㅇ대출한도 : 최대 300만원" — 300만원은 빌릴 수 있는 돈이지 받는 돈이 아님.
		// 자릿수가 작아 유형J(1억원 안전망)로는 절대 못 잡는 자리라, 의미 판정 마커가 반드시 필요한 사례임
		ParsedAmount cityLoan = parser.parseAmount(findServiceDetailById("B55370100029").description());
		assertThat(cityLoan.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(cityLoan.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// 300000000165 "융자한도 3천만원 이하"
		ParsedAmount rentLoan = parser.parseAmount(findServiceDetailById("300000000165").description());
		assertThat(rentLoan.amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(rentLoan.parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// 451000000242 "공공임대주택 임대보증금 대출이자 지원(대출한도 5천만 원) ... 연 최대 1.5백만 원 범위 내" —
		// 대출한도 5천만원만 빠지고 지원 상한은 남음. **남은 금액을 CONDITIONAL로 강등하는 것이 핵심임** — 배제가
		// 없었으면 MULTIPLE이던 것이 SINGLE로 승격돼 없던 예상총액 항목이 새로 생김(자부담 규칙과 같은 함정)
		ParsedAmount interestSupport = parser.parseAmount(findServiceDetailById("451000000242").description());
		assertThat(interestSupport.amountCandidates()).doesNotContain(50_000_000L);
		assertThat(interestSupport.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(interestSupport.parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION);
	}

	@Test
	void parseAmount_typeH_interestSubsidyIsNotExcluded_becauseTheWordLoanIsNotTheTest() throws IOException {
		// **과잉 배제 방지 — 이 임무의 반대 방향 고정임.** "대출"이라는 낱말이 있다고 배제하면 진짜 현금 지원이
		// 통째로 날아감. 아래 금액은 전부 **실제로 계좌에 들어오는 돈**(대출이자·보증료를 지자체가 대신 내주는 것)이라
		// 살아 있어야 함. 마커를 낱말 단위로 넓히거나 gap 차단 어휘를 지우는 순간 이 테스트가 깨짐

		// 486000000133 "주택구입 대출이자 납부액(월 최대 25만 원), 36개월간 지원" — 금액 앞 15자에 "대출"이 있지만
		// 한도 어휘와 붙어 있지 않음(대출한도가 아니라 대출이자임)
		assertNotLoanExcluded("486000000133", 250_000L);

		// 541000000154 "전세자금 대출잔액의 2% 지원(연 최대 100만원)" — **융합 마커 "대출잔액"이 창 안에 있는데도**
		// 살아남아야 함. 마커와 금액 사이에 "%"와 "지원"이 있어 이 금액이 대출 잔액이 아니라 거기서 파생된 지원금임이
		// 드러나기 때문임(gap 차단 규칙의 존재 이유)
		assertNotLoanExcluded("541000000154", 1_000_000L);

		// 537000000110 "주택전세자금 대출 잔액의 1.5% 지원 (최대 100만원 까지)"
		assertNotLoanExcluded("537000000110", 1_000_000L);

		// 338000000419 "가구 당 최대 100만원 한도의 대출 이자 지원" — 금액 **뒤**에 "한도의 대출"이 오지만
		// 순방향 마커의 부정 선읽기가 "대출이자"를 걸러 냄
		assertNotLoanExcluded("338000000419", 1_000_000L);

		// 382000000174 "기 납부한 보증료의 전부 또는 일부(최대 40만원)을 지원" — 보증료 지원금은 진짜 지급액임
		assertNotLoanExcluded("382000000174", 400_000L);
	}

	@Test
	void parseAmount_typeI_businessCommercializationFund_isDemotedNotDropped() throws IOException {
		// 창업 사업화 자금은 **실제로 지급되는 돈**이라 배제하면 거짓말이 됨(기업이 실제로 받음). 다만 개인 생활
		// 지원금이 아니라 기업 단위 사업비이고 자부담 매칭·사업비 정산이 붙어 있어 개인 예상총액에 합산하면 안 됨.
		// 그래서 금액은 보존하고 CONDITIONAL로만 내림 — 배제와 강등을 가르는 기준이 여기 있음

		// B55273500013 초기창업패키지 "사업화 자금(최대 1억원)" — 오케스트레이터가 지목한 CASH 오염 6건 중 하나
		ParsedAmount earlyStartup = parser.parseAmount(findServiceDetailById("B55273500013").description());
		assertThat(earlyStartup.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(earlyStartup.amountCandidates()).containsExactly(100_000_000L);
		assertThat(earlyStartup.conditionSummary()).isNotBlank();

		// B55307700024 강한 소상공인 성장지원 "사업화 자금 지원 (최대 1억원 이내)"
		ParsedAmount smallBusiness = parser.parseAmount(findServiceDetailById("B55307700024").description());
		assertThat(smallBusiness.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(smallBusiness.amountCandidates()).containsExactly(100_000_000L);

		// B55400900023 청년창업사관학교 "사업화 지원금 - 최대 1억원 이내(총 사업비의 70%)" — 괄호가 자부담 30%
		// 매칭 구조를 그대로 드러냄. 단순 합산 금지 신호의 교과서적 사례임
		ParsedAmount academy = parser.parseAmount(findServiceDetailById("B55400900023").description());
		assertThat(academy.amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(academy.amountCandidates()).containsExactly(100_000_000L);
	}

	@Test
	void parseAmount_typeJ_noSingleReachesHundredMillion_becauseSingleFeedsTheEstimatedTotal() throws IOException {
		// 최후 그물임. SINGLE은 공통 타깃 정의상 "예상총액 자동 채움에 가장 안전한 경우"인데, 전수 실측 결과 1억원
		// 이상 SINGLE 중 개인 지급액인 것이 0건이었음(전부 대출 한도·사업 총예산·기업 사업비). 마커 규칙이 놓친
		// 새 표현이 들어와도 여기서 걸러지게 함. 강등이라 금액은 남으므로 과잉 배제가 아님
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		List<ParsedAmount> singles = items.stream()
			.map(item -> parser.parseAmount(item.description()))
			.filter(amount -> amount.amountKind() == AmountKind.SINGLE)
			.toList();

		assertThat(singles).isNotEmpty();
		assertThat(singles).allSatisfy(amount -> assertThat(amount.maxAmount()).isLessThan(100_000_000L));
	}

	// 대출 배제에 걸리지 않았는지(금액이 살아 있고 배제 상태가 아닌지) 확인하는 헬퍼임(과잉 배제 방지 전용).
	private void assertNotLoanExcluded(String serviceId, long expectedAmount) throws IOException {
		ParsedAmount result = parser.parseAmount(findServiceDetailById(serviceId).description());

		assertThat(result.amountCandidates()).as("서비스ID %s 금액이 대출 한도로 오배제됨", serviceId).contains(expectedAmount);
		assertThat(result.parseStatus()).as("서비스ID %s는 대출 배제 대상이 아님", serviceId)
			.isNotIn(AmountParseStatus.EXCLUDED_LOAN_CONTEXT, AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION);
	}

	// 자부담 배제에 걸리지 않았는지(금액이 살아 있고 배제 상태가 아닌지) 확인하는 헬퍼임(과잉 배제 방지 전용).
	private void assertNotSelfPayExcluded(String serviceId, long expectedAmount) throws IOException {
		ParsedAmount result = parser.parseAmount(findServiceDetailById(serviceId).description());

		assertThat(result.amountCandidates()).as("서비스ID %s 금액이 자부담으로 오배제됨", serviceId).contains(expectedAmount);
		assertThat(result.parseStatus()).as("서비스ID %s는 자부담 배제 대상이 아님", serviceId)
			.isNotIn(AmountParseStatus.EXCLUDED_SELF_PAY_CONTEXT, AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION);
	}

	// 조건부 판정 + 금액 후보 1건을 함께 확인하는 헬퍼임(오분류 고정 테스트 전용).
	private void assertConditionalWithSingleCandidate(String serviceId, long expectedAmount) throws IOException {
		ParsedAmount result = parser.parseAmount(findServiceDetailById(serviceId).description());

		assertThat(result.amountKind()).as("서비스ID %s는 CONDITIONAL이어야 함", serviceId).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(result.amountCandidates()).as("서비스ID %s 금액 후보", serviceId).containsExactly(expectedAmount);
		assertThat(result.conditionSummary()).as("서비스ID %s conditionSummary", serviceId).isNotBlank();
	}

	@Test
	void serviceDetailSnapshot_amountKindDistribution_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		Map<AmountKind, Long> counts = new EnumMap<>(AmountKind.class);
		for (AmountKind kind : AmountKind.values()) {
			counts.put(kind, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			counts.merge(parser.parseAmount(item.description()).amountKind(), 1L, Long::sum);
		}

		System.out.println("=== 금액 4분류(AmountKind) 분포 - 전체 (스냅샷 n=" + items.size() + ") ===");
		counts.forEach(
				(kind, count) -> System.out.printf("  %s: %d건 (%.2f%%)%n", kind, count, count * 100.0 / items.size()));

		// 2026-07-12 금액 오분류 수정 임무로 갱신한 실측치임(수정 전 645/146/103/203, 유형A~E 반영 후
		// 651/126/92/228, 유형G(자부담) 반영 후 653/124/89/231). 이번 회차(유형H 대출 한도 배제, 유형I
		// 사업화 자금 강등, 유형J 1억원 안전망)로 NONE 5건 증가(142100000071·300000000165·B55369400017·
		// B55370100029·B55370100042 — 금액이 대출·보증 한도뿐이라 산정불가), SINGLE 7건 감소,
		// CONDITIONAL 10건 증가임. SINGLE이 줄어든 것이 이번 수정의 핵심 실익임 — SINGLE만 예상총액 자동 채움
		// 대상이라, 여기서 걷어낸 건이 곧 막아낸 오염임.
		assertThat(counts.get(AmountKind.NONE)).isEqualTo(658L);
		assertThat(counts.get(AmountKind.SINGLE)).isEqualTo(117L);
		assertThat(counts.get(AmountKind.MULTIPLE)).isEqualTo(81L);
		assertThat(counts.get(AmountKind.CONDITIONAL)).isEqualTo(241L);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(items.size());
	}

	@Test
	void serviceDetailSnapshot_amountParseStatusDistribution_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		Map<AmountParseStatus, Long> counts = new EnumMap<>(AmountParseStatus.class);
		for (AmountParseStatus status : AmountParseStatus.values()) {
			counts.put(status, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			counts.merge(parser.parseAmount(item.description()).parseStatus(), 1L, Long::sum);
		}

		System.out.println("=== 금액 파싱 상태(AmountParseStatus) 분포 (스냅샷 n=" + items.size() + ") ===");
		counts.forEach((status, count) -> System.out.printf("  %s: %d건%n", status, count));

		// 배제가 일어난 레코드는 31건임 — 사유를 **돈의 방향**으로 셋으로 나눠 남김. 예산 12건(정부가 쓰는 돈),
		// 자부담 7건(이용자가 내는 돈), 대출·보증 한도 12건(아무도 주지 않는 돈 — 빌린 뒤 갚아야 하는 채무 상한).
		// 셋을 한 상태로 뭉치지 않는 이유는 팀이 엑셀에서 "왜 산정불가인가"를 바로 읽어야 하기 때문임.
		assertThat(counts.get(AmountParseStatus.NOT_FOUND)).isEqualTo(645L);
		assertThat(counts.get(AmountParseStatus.EXCLUDED_BUDGET_CONTEXT)).isEqualTo(6L);
		assertThat(counts.get(AmountParseStatus.PARSED)).isEqualTo(421L);
		assertThat(counts.get(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION)).isEqualTo(6L);
		assertThat(counts.get(AmountParseStatus.EXCLUDED_SELF_PAY_CONTEXT)).isEqualTo(2L);
		assertThat(counts.get(AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION)).isEqualTo(5L);
		assertThat(counts.get(AmountParseStatus.EXCLUDED_LOAN_CONTEXT)).isEqualTo(5L);
		assertThat(counts.get(AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION)).isEqualTo(7L);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(items.size());
	}

	@Test
	void serviceDetailSnapshot_amountKindDistribution_cashOnlySubset_isFixed() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();
		List<Gov24ServiceItemDto> cashItems = items.stream().filter(i -> "현금".equals(i.paymentTypeText())).toList();

		Map<AmountKind, Long> counts = new EnumMap<>(AmountKind.class);
		for (AmountKind kind : AmountKind.values()) {
			counts.put(kind, 0L);
		}
		for (Gov24ServiceItemDto item : cashItems) {
			counts.merge(parser.parseAmount(item.description()).amountKind(), 1L, Long::sum);
		}

		System.out.println("=== 금액 4분류(AmountKind) 분포 - 현금성(지원유형 원문 \"현금\") (n=" + cashItems.size() + ") ===");
		counts.forEach((kind, count) -> System.out.printf("  %s: %d건 (%.2f%%)%n", kind, count,
				count * 100.0 / cashItems.size()));

		// 2026-07-12 금액 오분류 수정 임무로 갱신한 실측치임(수정 전 154/95/55/129, 유형A~E 반영 후
		// 160/83/47/143, 유형G(자부담) 반영 후 161/82/45/145). 이번 회차에서 현금성 SINGLE 2건이 빠짐 —
		// B55273500013(초기창업패키지 "사업화 자금(최대 1억원)")과 B55307700024(강한 소상공인 "사업화 자금 지원
		// (최대 1억원 이내)")로, 둘 다 개인 생활 지원금이 아니라 창업기업 사업비라 강등된 것임(오케스트레이터가 지목한
		// CASH 오염 6건 중 gov24 몫 2건). 예상총액 자동 채움에 안전하게 쓸 수 있는 구간은 현금성 안에서도
		// SINGLE 80건(18.48%)뿐임.
		assertThat(cashItems).hasSize(433);
		assertThat(counts.get(AmountKind.NONE)).isEqualTo(161L);
		assertThat(counts.get(AmountKind.SINGLE)).isEqualTo(80L);
		assertThat(counts.get(AmountKind.MULTIPLE)).isEqualTo(44L);
		assertThat(counts.get(AmountKind.CONDITIONAL)).isEqualTo(148L);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(cashItems.size());
	}

	// ---- 지역 유추 (임무 지시 3장) ----

	@Test
	void parseRegion_sidoAndSigunguPattern_isSigunguLevel() {
		ParsedRegion result = parser.parseRegion("서울특별시 종로구");

		assertThat(result.sidoName()).isEqualTo("서울특별시");
		assertThat(result.sigunguName()).isEqualTo("종로구");
		assertThat(result.regionLevel()).isEqualTo(RegionLevel.SIGUNGU);
		assertThat(result.scopeBasis()).isEqualTo(RegionScopeBasis.INFERRED_FROM_AGENCY_NAME);
		assertThat(result.confidence()).isEqualTo(RegionConfidence.MEDIUM);
	}

	@Test
	void parseRegion_sidoOnlyPattern_isSidoLevel() {
		ParsedRegion result = parser.parseRegion("경기도");

		assertThat(result.sidoName()).isEqualTo("경기도");
		assertThat(result.sigunguName()).isNull();
		assertThat(result.regionLevel()).isEqualTo(RegionLevel.SIDO);
		assertThat(result.confidence()).isEqualTo(RegionConfidence.LOW);
	}

	@Test
	void parseRegion_centralAgency_isNationalLevel() {
		ParsedRegion result = parser.parseRegion("보건복지부");

		assertThat(result.sidoName()).isNull();
		assertThat(result.sigunguName()).isNull();
		assertThat(result.regionLevel()).isEqualTo(RegionLevel.NATIONAL);
		assertThat(result.scopeBasis()).isEqualTo(RegionScopeBasis.NOT_APPLICABLE);
		assertThat(result.confidence()).isEqualTo(RegionConfidence.LOW);
	}

	@Test
	void parseRegion_agencyNameWithoutSpaceEvenIfRegional_fallsBackToNational() {
		// "광진구시설관리공단"처럼 공백 없이 붙은 기초자치단체 산하기관은 유추 불가로 NATIONAL에 떨어짐 —
		// 이것이 confidence를 LOW로 낮게 잡는 근거임(임무 지시 3장)
		ParsedRegion result = parser.parseRegion("광진구시설관리공단");

		assertThat(result.regionLevel()).isEqualTo(RegionLevel.NATIONAL);
	}

	@Test
	void parseRegion_blankOrNull_isNationalLevel() {
		assertThat(parser.parseRegion(null).regionLevel()).isEqualTo(RegionLevel.NATIONAL);
		assertThat(parser.parseRegion("").regionLevel()).isEqualTo(RegionLevel.NATIONAL);
	}

	@Test
	void serviceDetailSnapshot_regionLevelDistribution_matchesOrchestratorFigures() throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();

		Map<RegionLevel, Long> counts = new EnumMap<>(RegionLevel.class);
		for (RegionLevel level : RegionLevel.values()) {
			counts.put(level, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			counts.merge(parser.parseRegion(item.agency()).regionLevel(), 1L, Long::sum);
		}

		System.out.println("=== 지역 유추 단계(RegionLevel) 분포 (스냅샷 n=" + items.size() + ") ===");
		counts.forEach((level, count) -> System.out.printf("  %s: %d건 (%.2f%%)%n", level, count,
				count * 100.0 / items.size()));

		// 외부API-부족분-조사-2026-07-12.md 임무 지시 3장 오케스트레이터 실측(58.8%/645건, 12.8%/140건,
		// 28.4%/312건)과 정확히 일치함
		assertThat(counts.get(RegionLevel.SIGUNGU)).isEqualTo(645L);
		assertThat(counts.get(RegionLevel.SIDO)).isEqualTo(140L);
		assertThat(counts.get(RegionLevel.NATIONAL)).isEqualTo(312L);
	}

	@Test
	void serviceDetailSnapshot_regionCrossCheckWithAutonomousRegulation_reportsMatchRate() throws IOException {
		List<ExportRawRecord> details = loadExportRawSnapshot();

		int audited = 0;
		int matched = 0;
		for (ExportRawRecord detail : details) {
			ParsedRegion region = parser.parseRegion(detail.agency());
			if (region.regionLevel() == RegionLevel.NATIONAL) {
				continue;
			}
			String autonomousRegulation = detail.autonomousRegulation();
			if (autonomousRegulation == null || autonomousRegulation.isBlank()) {
				continue;
			}
			audited++;
			boolean sidoMatches = region.sidoName() != null && autonomousRegulation.contains(region.sidoName());
			boolean sigunguMatches = region.sigunguName() != null
					&& autonomousRegulation.contains(region.sigunguName());
			if (sidoMatches || sigunguMatches) {
				matched++;
			}
		}

		double matchRate = audited == 0 ? 0.0 : matched * 100.0 / audited;
		System.out.println("=== 소관기관명 유추 대 자치법규 교차검증 (스냅샷 n=" + details.size() + ") ===");
		System.out.println("  대조 대상(자치법규 채움 더하기 지역 유추 성공): " + audited + "건");
		System.out.printf("  일치: %d건 (%.2f%%, 시도 또는 시군구명이 자치법규 원문에 포함되는지 기준)%n", matched, matchRate);

		// 스냅샷이 고정돼 있어 결정적임(회귀 고정) — 이 일치율(94.32%)이 confidence를 MEDIUM으로 잡은 근거임
		assertThat(audited).isEqualTo(493);
		assertThat(matched).isEqualTo(465);
	}

	// ---- 소득 신호 일치성 (임무 지시 4장) ----

	@Test
	void computeIncomeSignalSource_onlyJaFlags_isJaFlags() {
		Gov24SupportConditionDto condition = incomeCondition("Y", "Y", null, null, null);

		IncomeSignalSource result = parser.computeIncomeSignalSource("자유 텍스트, 소득 언급 없음", condition);

		assertThat(result).isEqualTo(IncomeSignalSource.JA_FLAGS);
	}

	@Test
	void computeIncomeSignalSource_onlyTextEvidence_isText() {
		Gov24SupportConditionDto condition = incomeCondition(null, null, null, null, null);

		IncomeSignalSource result = parser.computeIncomeSignalSource("기준중위소득 125% 이하 한부모가족", condition);

		assertThat(result).isEqualTo(IncomeSignalSource.TEXT);
	}

	@Test
	void computeIncomeSignalSource_bothPresent_isBoth() {
		Gov24SupportConditionDto condition = incomeCondition("Y", null, null, null, null);

		IncomeSignalSource result = parser.computeIncomeSignalSource("중위소득 50% 이하 가구 대상", condition);

		assertThat(result).isEqualTo(IncomeSignalSource.BOTH);
	}

	@Test
	void computeIncomeSignalSource_neitherPresent_defaultsToJaFlags() {
		IncomeSignalSource result = parser.computeIncomeSignalSource("소득 언급 없는 텍스트", null);

		assertThat(result).isEqualTo(IncomeSignalSource.JA_FLAGS);
	}

	@Test
	void computeIncomeConsistencyStatus_noTextMention_isNoTextEvidence() {
		Gov24SupportConditionDto condition = incomeCondition("Y", "Y", "Y", "Y", "Y");

		IncomeConsistencyStatus result = parser.computeIncomeConsistencyStatus("중위소득 언급 없는 자유 텍스트", condition);

		assertThat(result).isEqualTo(IncomeConsistencyStatus.NO_TEXT_EVIDENCE);
	}

	@Test
	void computeIncomeConsistencyStatus_textMatchesFlags_isConsistent() {
		// 원문 "중위소득 50% 이하"는 JA0201만 커버하면 됨
		Gov24SupportConditionDto condition = incomeCondition("Y", null, null, null, null);

		IncomeConsistencyStatus result = parser.computeIncomeConsistencyStatus("중위소득 50% 이하 가구 대상", condition);

		assertThat(result).isEqualTo(IncomeConsistencyStatus.CONSISTENT);
	}

	@Test
	void computeIncomeConsistencyStatus_textExceedsFlags_isConflict() {
		// 원문 "중위소득 125% 이하"는 JA0201~JA0204까지 필요한데 JA0201~JA0202만 Y — 과소
		// 커버(SNAPSHOT_META.md
		// 실측 사례와 같은 패턴)
		Gov24SupportConditionDto condition = incomeCondition("Y", "Y", null, null, null);

		IncomeConsistencyStatus result = parser.computeIncomeConsistencyStatus("기준중위소득 125% 이하 한부모가족", condition);

		assertThat(result).isEqualTo(IncomeConsistencyStatus.CONFLICT);
	}

	@Test
	void extractIncomeTextEvidence_mentionPresent_returnsExcerpt() {
		String evidence = parser.extractIncomeTextEvidence("○ 기준중위소득 125% 이하 한부모가족 대상");

		assertThat(evidence).isNotNull().contains("중위소득").contains("125");
	}

	@Test
	void extractIncomeTextEvidence_noMentionOrNull_returnsNull() {
		assertThat(parser.extractIncomeTextEvidence("소득 언급 없는 텍스트")).isNull();
		assertThat(parser.extractIncomeTextEvidence(null)).isNull();
	}

	@Test
	void serviceDetailSnapshot_incomeSignalSourceDistribution_isFixed() throws IOException {
		distributionOverJoinedSnapshot(
				(eligibilityText, condition, counts) -> counts
					.merge(parser.computeIncomeSignalSource(eligibilityText, condition), 1L, Long::sum),
				this::assertIncomeSignalSourceDistribution, IncomeSignalSource.class);
	}

	@Test
	void serviceDetailSnapshot_incomeConsistencyStatusDistribution_isFixed() throws IOException {
		distributionOverJoinedSnapshot(
				(eligibilityText, condition, counts) -> counts
					.merge(parser.computeIncomeConsistencyStatus(eligibilityText, condition), 1L, Long::sum),
				this::assertIncomeConsistencyDistribution, IncomeConsistencyStatus.class);
	}

	private void assertIncomeSignalSourceDistribution(Map<IncomeSignalSource, Long> counts) {
		System.out.println("=== 소득 신호 출처(IncomeSignalSource) 분포 ===");
		counts.forEach((source, count) -> System.out.printf("  %s: %d건%n", source, count));
		assertThat(counts.get(IncomeSignalSource.JA_FLAGS)).isEqualTo(1_033L);
		assertThat(counts.get(IncomeSignalSource.BOTH)).isEqualTo(63L);
		assertThat(counts.get(IncomeSignalSource.TEXT)).isEqualTo(1L);
	}

	private void assertIncomeConsistencyDistribution(Map<IncomeConsistencyStatus, Long> counts) {
		System.out.println("=== 소득 신호 일치성(IncomeConsistencyStatus) 분포 ===");
		counts.forEach((status, count) -> System.out.printf("  %s: %d건%n", status, count));
		// NO_TEXT_EVIDENCE를 뺀 나머지(대조 가능 건수)는 기존 Gov24JaFieldParserTest 감사 결과(64건 대조,
		// 14건 불일치)와 정합함
		assertThat(counts.get(IncomeConsistencyStatus.NO_TEXT_EVIDENCE)).isEqualTo(1_033L);
		assertThat(counts.get(IncomeConsistencyStatus.CONSISTENT)).isEqualTo(50L);
		assertThat(counts.get(IncomeConsistencyStatus.CONFLICT)).isEqualTo(14L);
	}

	@FunctionalInterface
	private interface IncomeDistributionAccumulator<T extends Enum<T>> {

		void accumulate(String eligibilityText, Gov24SupportConditionDto condition, Map<T, Long> counts);

	}

	private <T extends Enum<T>> void distributionOverJoinedSnapshot(IncomeDistributionAccumulator<T> accumulator,
			Consumer<Map<T, Long>> assertion, Class<T> enumType) throws IOException {
		List<Gov24ServiceItemDto> items = loadServiceDetailSnapshot();
		Map<String, Gov24SupportConditionDto> conditionsById = new LinkedHashMap<>();
		for (Gov24SupportConditionDto condition : loadSupportConditionsSnapshot()) {
			conditionsById.put(condition.serviceId(), condition);
		}

		Map<T, Long> counts = new EnumMap<>(enumType);
		for (T value : enumType.getEnumConstants()) {
			counts.put(value, 0L);
		}
		for (Gov24ServiceItemDto item : items) {
			Gov24SupportConditionDto condition = conditionsById.get(item.serviceId());
			String eligibilityText = buildEligibilityTextForTest(item);
			accumulator.accumulate(eligibilityText, condition, counts);
		}
		assertion.accept(counts);
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		assertThat(total).isEqualTo(items.size());
	}

	// Gov24Parser.buildEligibilityText는 private이라 테스트에서 toParsedSubsidy를 거쳐 간접 검증하는 대신,
	// 같은 결합 규칙(지원대상 더하기 "[선정기준]" 마커 더하기 선정기준)을 테스트에서 재현함 — 이 값이 정확히
	// toParsedSubsidy와 같다는 것은 Gov24FieldEnrichmentParserTest의 eligibilityText 관련 테스트로 이미
	// 보장됨
	private static String buildEligibilityTextForTest(Gov24ServiceItemDto item) {
		boolean hasSummary = item.eligibilitySummaryText() != null && !item.eligibilitySummaryText().isBlank();
		boolean hasCriteria = item.selectionCriteriaText() != null && !item.selectionCriteriaText().isBlank();
		if (hasSummary && hasCriteria) {
			return item.eligibilitySummaryText() + "\n\n[선정기준] " + item.selectionCriteriaText();
		}
		if (hasSummary) {
			return item.eligibilitySummaryText();
		}
		return hasCriteria ? item.selectionCriteriaText() : null;
	}

	private static Gov24SupportConditionDto incomeCondition(String income0To50, String income51To75,
			String income76To100, String income101To200, String incomeOver200) {
		// 앞 17개는 기존 인자(서비스ID, 연령 2, 소득 5, 가구 9)이고 뒤 17개는 JA03 계열임. JA03은 이 테스트의
		// 관심사(소득)와 무관하므로 전부 null임 — 직업군 판정은 근거가 없으면 NONE으로 통과함
		return new Gov24SupportConditionDto("TEST", null, null, income0To50, income51To75, income76To100,
				income101To200, incomeOver200, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	private Gov24ServiceItemDto findServiceDetailById(String serviceId) throws IOException {
		return loadServiceDetailSnapshot().stream()
			.filter(i -> serviceId.equals(i.serviceId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("스냅샷에서 서비스ID를 찾지 못함: " + serviceId));
	}

	private List<Gov24ServiceItemDto> loadServiceDetailSnapshot() throws IOException {
		String json = readSnapshot("gov24_serviceDetail_snapshot.json");
		return parser.parseServiceItems(json);
	}

	private List<Gov24SupportConditionDto> loadSupportConditionsSnapshot() throws IOException {
		String json = readSnapshot("gov24_supportConditions_snapshot.json");
		return parser.parseSupportConditions(json);
	}

	private List<ExportRawRecord> loadExportRawSnapshot() throws IOException {
		String json = readSnapshot("gov24_serviceDetail_snapshot.json");
		ExportRawSnapshotWrapper wrapper = new ObjectMapper().readValue(json, ExportRawSnapshotWrapper.class);
		return wrapper.data() == null ? List.of() : wrapper.data();
	}

	private String readSnapshot(String fileName) throws IOException {
		return Files.readString(SNAPSHOT_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

	// 자치법규 필드는 운영 DTO(Gov24ServiceItemDto)의 매칭 범위 밖이라(교차검증 감사 전용) 여기서만 최소
	// 필드로 읽음(Gov24JaFieldParserTest의 ServiceDetailRecord와 같은 패턴 — ponytail: 운영 코드 비대화
	// 방지)
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ExportRawRecord(@JsonProperty("서비스ID") String serviceId, @JsonProperty("소관기관명") String agency,
			@JsonProperty("자치법규") String autonomousRegulation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ExportRawSnapshotWrapper(@JsonProperty("data") List<ExportRawRecord> data) {
	}

}
