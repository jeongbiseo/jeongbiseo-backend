package com.jeongbiseo.infra.client.youthcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.client.common.dto.AmountParseStatus;
import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.infra.client.youthcenter.dto.ParsedYouthPolicy;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyDto;
import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 온통청년 파서의 회귀 테스트임. 네트워크 없이 회귀 스냅샷
 * {@code fixtures/regression/youthcenter_snapshot.json}(실호출 전수 2,648건에서 체계적 표집 step=2,
 * n=1,324)만 읽음. 스냅샷이 고정돼 있어 분포 검증은 허용 오차 없이 정확한 건수로 고정함(같은 표본을 두 번 세는 것이므로).
 *
 * <p>
 * 이 테스트가 지키는 것은 세 가지임. (가) 코드 매핑이 바뀌지 않았는지(마감·지역·자격·지급유형 분포), (나) 실측으로 찾아낸 예외 케이스가 계속
 * 처리되는지 (다중 기간, 종료일이 시작일보다 빠른 원문 오류, 시도 전역 코드 부재), (다) <b>없는 정보를 지어내지 않는지</b>(연령
 * UNRESTRICTED 0건, 가구 신호 부재).
 */
class YouthcenterParserTest {

	private static final Path SNAPSHOT = Path.of("fixtures", "regression", "youthcenter_snapshot.json");

	private static final Path SAMPLE = Path.of("fixtures", "sample_youthcenter.json");

	// 실측 예외 케이스 — 한 필드에 두 모집 회차가 역슬래시 N으로 이어 붙은 레코드(전수 2건 중 스냅샷에 든 1건).
	private static final String MULTI_PERIOD_ID = "20250316005400210639";

	// 실측 예외 케이스 — 원문 자체가 종료일(2025-07-10)을 시작일(2026-06-01)보다 앞에 적어 둔 레코드.
	private static final String INVERTED_RANGE_ID = "20260422005400212868";

	private final YouthcenterParser parser = new YouthcenterParser();

	// ---- 스냅샷 적재 ----

	@Test
	void parsePolicies_snapshot_loadsEveryRecord() throws IOException {
		List<YouthcenterPolicyDto> policies = loadSnapshot();

		assertThat(policies).hasSize(1324);
		assertThat(policies).allSatisfy(p -> assertThat(p.policyId()).isNotBlank());
		assertThat(policies).allSatisfy(p -> assertThat(p.policyName()).isNotBlank());
		// plcySprtCn(지원내용)은 전수 채움 100%임 — 금액 파싱의 유일한 입력이라 비면 안 됨
		assertThat(policies).allSatisfy(p -> assertThat(p.policySupportContent()).isNotBlank());
	}

	// 실호출 응답 봉투(result.youthPolicyList)를 스냅샷과 같은 경로로 읽는지 확인함 — 스냅샷 전용 파싱 경로를 따로 두면
	// 회귀 테스트가 프로덕션 경로를 검증하지 못함.
	@Test
	void parsePolicies_realApiEnvelope_isReadByTheSameMethod() throws IOException {
		List<YouthcenterPolicyDto> policies = this.parser
			.parsePolicies(Files.readString(SAMPLE, StandardCharsets.UTF_8));

		assertThat(policies).hasSize(2);
		assertThat(policies.get(0).policyId()).isEqualTo("20260708005400213252");
		assertThat(this.parser.toParsedPolicy(policies.get(0)).deadline().kind()).isEqualTo(DeadlineKind.CLOSED);
		assertThat(this.parser.toParsedPolicy(policies.get(1)).deadline()).isEqualTo(new ParsedDeadline(
				DeadlineKind.DATE_RANGE, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 29), "20260713 ~ 20260729"));
	}

	@Test
	void parsePolicies_emptyEnvelope_returnsEmptyList() throws IOException {
		assertThat(this.parser.parsePolicies("{\"resultCode\":200}")).isEmpty();
		assertThat(this.parser.parsePolicies("{\"result\":{}}")).isEmpty();
	}

	// ---- 마감(신청기간구분코드 매핑) ----

	@Test
	void snapshot_deadlineKind_distributionIsFixed() throws IOException {
		Map<DeadlineKind, Long> counts = parsedSnapshot().stream()
			.map(p -> p.deadline().kind())
			.collect(Collectors.groupingBy(k -> k, Collectors.counting()));

		System.out.println("=== 온통청년 DeadlineKind 분포 (스냅샷 n=1324) ===");
		counts.forEach((k, v) -> System.out.printf("  %-22s %4d건 (%.2f%%)%n", k, v, v * 100.0 / 1324));

		// 전수 2,648건 비율(특정기간 49.89%, 마감 33.57%, 상시 16.54%)과 정합함
		assertThat(counts.get(DeadlineKind.DATE_RANGE)).isEqualTo(656L);
		assertThat(counts.get(DeadlineKind.CLOSED)).isEqualTo(442L);
		assertThat(counts.get(DeadlineKind.ALWAYS_OPEN)).isEqualTo(226L);
		// 코드가 3종뿐이라 UNKNOWN으로 떨어지는 레코드가 없음 — gov24(자유텍스트, UNKNOWN 22.42%)와 대비되는 지점임
		assertThat(counts.getOrDefault(DeadlineKind.UNKNOWN, 0L)).isZero();
		// 날짜는 DATE_RANGE에서만 나옴. 상시·마감은 "특정 날짜가 없다"는 뜻이므로 날짜를 지어내지 않음
		assertThat(parsedSnapshot()).allSatisfy(p -> {
			boolean hasDates = p.deadline().startDate() != null && p.deadline().endDate() != null;
			assertThat(hasDates).isEqualTo(p.deadline().kind() == DeadlineKind.DATE_RANGE);
		});
	}

	// 실측 예외 1 — 다중 기간. " ~ " 단순 분리면 토큰이 3개가 나와 종료일이 20271231(둘째 구간 끝)로 오염됨.
	// 첫 구간만 취하고 원문 전체는 rawText에 남김.
	@Test
	void classifyDeadline_multiPeriodRecord_takesFirstPeriodAndKeepsRawText() throws IOException {
		ParsedYouthPolicy policy = findById(MULTI_PERIOD_ID);

		assertThat(policy.deadline().kind()).isEqualTo(DeadlineKind.DATE_RANGE);
		assertThat(policy.deadline().startDate()).isEqualTo(LocalDate.of(2026, 9, 7));
		assertThat(policy.deadline().endDate()).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(policy.deadline().rawText()).isEqualTo("20260907 ~ 20260928\\N20261026 ~ 20261116");
	}

	// 전수 2건 중 스냅샷에 안 든 나머지 1건(20251121005400211919)의 원문을 직접 넣어 같은 규칙을 고정함.
	@Test
	void classifyDeadline_multiPeriodLiteral_isNotContaminatedByTheSecondPeriod() {
		ParsedDeadline deadline = this.parser.classifyDeadline("0057001", "20260101 ~ 20261231\\N20270101 ~ 20271231");

		assertThat(deadline.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(deadline.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	// 실측 예외 2 — 원문이 종료일을 시작일보다 앞에 적어 둠(소스 데이터 오류). 날짜를 뒤집거나 버리지 않고 원문 그대로 파싱함
	// (파서가 소스의 오류를 숨기면 팀이 원문을 확인할 기회가 사라짐). 캘린더가 이 레코드를 어떻게 다룰지는 하류 결정 사항임.
	@Test
	void classifyDeadline_invertedRange_isParsedAsIsNotSilentlyFixed() throws IOException {
		ParsedYouthPolicy policy = findById(INVERTED_RANGE_ID);

		assertThat(policy.deadline().kind()).isEqualTo(DeadlineKind.DATE_RANGE);
		assertThat(policy.deadline().startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
		assertThat(policy.deadline().endDate()).isEqualTo(LocalDate.of(2025, 7, 10));
	}

	@Test
	void classifyDeadline_unknownOrBlankCode_isUnknownWithoutInventedDates() {
		assertThat(this.parser.classifyDeadline("0057009", "20260101 ~ 20261231").kind())
			.isEqualTo(DeadlineKind.UNKNOWN);
		assertThat(this.parser.classifyDeadline(null, null).kind()).isEqualTo(DeadlineKind.UNKNOWN);
		assertThat(this.parser.classifyDeadline("0057001", "상시모집").kind()).isEqualTo(DeadlineKind.UNKNOWN);
		assertThat(this.parser.classifyDeadline("0057001", null).kind()).isEqualTo(DeadlineKind.UNKNOWN);
		// 날짜 값 자체가 유효하지 않으면(2월 30일) 지어내지 않고 UNKNOWN으로 떨어뜨림
		assertThat(this.parser.classifyDeadline("0057001", "20260230 ~ 20260301").kind())
			.isEqualTo(DeadlineKind.UNKNOWN);
		// 상시·마감은 코드 자체가 원문 근거라 rawText에 코드를 남김
		assertThat(this.parser.classifyDeadline("0057002", "").rawText()).isEqualTo("0057002");
	}

	// ---- 지역(zipCd) ----

	@Test
	void snapshot_regionCodes_areDeclaredFiveDigitSigunguCodes() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		long withCodes = policies.stream().filter(p -> !p.regionCodes().isEmpty()).count();
		long instances = policies.stream().mapToLong(p -> p.regionCodes().size()).sum();
		long distinct = policies.stream().flatMap(p -> p.regionCodes().stream()).distinct().count();
		long tenOrMore = policies.stream().filter(p -> p.regionCodes().size() >= 10).count();
		long sidoWide = policies.stream()
			.flatMap(p -> p.regionCodes().stream())
			.filter(code -> code.endsWith("000"))
			.count();

		System.out.println("=== 온통청년 지역 코드 (스냅샷 n=1324) ===");
		System.out.printf("  코드 있는 레코드 %d건, 코드 인스턴스 %d개, distinct %d개, 10개 이상 나열 %d건%n", withCodes, instances, distinct,
				tenOrMore);

		assertThat(withCodes).isEqualTo(1316L);
		assertThat(policies.stream().filter(p -> p.regionCodes().isEmpty()).count()).isEqualTo(8L);
		assertThat(instances).isEqualTo(59_163L);
		assertThat(distinct).isEqualTo(258L);
		// 10개 이상 나열이 최대 버킷이라(전수 41.58%) 목록을 자르면 안 됨 — 자르지 않았음을 고정함
		assertThat(tenOrMore).isEqualTo(549L);
		// 시도 전역(xx000) 코드는 응답에 없음 — 프리픽스 매칭 로직을 만들지 않은 근거를 회귀로 고정함
		assertThat(sidoWide).isZero();
		assertThat(policies).allSatisfy(p -> assertThat(p.regionCodes()).allMatch(code -> code.matches("\\d{5}")));
	}

	@Test
	void parseRegionCodes_dropsMalformedCodesInsteadOfPassingThemThrough() {
		assertThat(this.parser.parseRegionCodes("11110, 11140 ,,11170")).containsExactly("11110", "11140", "11170");
		assertThat(this.parser.parseRegionCodes("1111,ABCDE,11110")).containsExactly("11110");
		assertThat(this.parser.parseRegionCodes(null)).isEmpty();
		assertThat(this.parser.parseRegionCodes("  ")).isEmpty();
	}

	// ---- 자격조건 4축 ----

	// 연령 — 임무 지시가 상정한 "sprtTrgtAgeLmtYn='N'이면 무관"이 실데이터에서 깨진다는 것을 회귀로 박제함. 이 테스트가
	// 실패하면 소스 데이터가 정리됐다는 뜻이므로 그때 플래그 사용을 재검토할 것.
	@Test
	void snapshot_ageLimitFlag_contradictsAgeValues_soItIsNotUsed() throws IOException {
		List<YouthcenterPolicyDto> raw = loadSnapshot();

		long noLimitFlagButAged = raw.stream()
			.filter(p -> "N".equals(p.ageLimitYn()) && this.parser.parseAgeBound(p.supportTargetMinAge()) != null)
			.count();
		long limitFlagButNoAge = raw.stream()
			.filter(p -> "Y".equals(p.ageLimitYn()) && this.parser.parseAgeBound(p.supportTargetMinAge()) == null
					&& this.parser.parseAgeBound(p.supportTargetMaxAge()) == null)
			.count();

		System.out.println("=== sprtTrgtAgeLmtYn 모순 (스냅샷 n=1324) ===");
		System.out.printf("  플래그 N인데 연령 값 있음: %d건 / 플래그 Y인데 연령 값 없음: %d건%n", noLimitFlagButAged, limitFlagButNoAge);

		// 두 방향 모순이 동시에 대량 존재함 — 어느 쪽으로 읽어도 이 플래그는 근거가 못 됨
		assertThat(noLimitFlagButAged).isGreaterThan(500L);
		assertThat(limitFlagButNoAge).isGreaterThan(200L);
	}

	@Test
	void snapshot_ageSignal_isRestrictedOrUnknownButNeverUnrestricted() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		Map<EligibilitySignal, Long> counts = policies.stream()
			.map(ParsedYouthPolicy::ageSignal)
			.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		System.out.println("=== 연령 신호 (스냅샷 n=1324) ===" + counts);

		assertThat(counts.get(EligibilitySignal.RESTRICTED)).isEqualTo(984L);
		assertThat(counts.get(EligibilitySignal.UNKNOWN)).isEqualTo(340L);
		// 연령 무관을 선언하는 믿을 만한 필드가 없으므로 UNRESTRICTED를 만들지 않음(UNKNOWN을 무관으로 승격하면
		// 매칭이 조용히 틀림)
		assertThat(counts.getOrDefault(EligibilitySignal.UNRESTRICTED, 0L)).isZero();
		assertThat(policies).allSatisfy(p -> {
			boolean hasBound = p.ageMin() != null || p.ageMax() != null;
			assertThat(p.ageSignal()).isEqualTo(hasBound ? EligibilitySignal.RESTRICTED : EligibilitySignal.UNKNOWN);
		});
	}

	// "0"은 만 0세가 아니라 미입력 센티널임(전수 min "0" 730건, max "0" 687건).
	@Test
	void parseAgeBound_zeroIsNotEnteredNotAgeZero() {
		assertThat(this.parser.parseAgeBound("0")).isNull();
		assertThat(this.parser.parseAgeBound("")).isNull();
		assertThat(this.parser.parseAgeBound(null)).isNull();
		assertThat(this.parser.parseAgeBound("모름")).isNull();
		assertThat(this.parser.parseAgeBound("19")).isEqualTo(19);
	}

	@Test
	void snapshot_incomeSignal_declaresUnrestrictedFromCode() throws IOException {
		Map<EligibilitySignal, Long> counts = parsedSnapshot().stream()
			.map(ParsedYouthPolicy::incomeSignal)
			.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		System.out.println("=== 소득 신호 (스냅샷 n=1324) ===" + counts);

		// 0043001(무관) — 소스가 명시적으로 선언한 무관이라 UNKNOWN이 아니라 UNRESTRICTED임
		assertThat(counts.get(EligibilitySignal.UNRESTRICTED)).isEqualTo(1158L);
		// 0043002(연소득) + 0043003(기타)
		assertThat(counts.get(EligibilitySignal.RESTRICTED)).isEqualTo(165L);
		// 코드 자체가 빈 레코드(전수 2건 중 1건이 표본에 듦)
		assertThat(counts.get(EligibilitySignal.UNKNOWN)).isEqualTo(1L);
	}

	@Test
	void snapshot_employmentSignal_unrestrictedSentinelIsAlwaysAlone() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		Map<EligibilitySignal, Long> counts = policies.stream()
			.map(ParsedYouthPolicy::employmentSignal)
			.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		System.out.println("=== 고용 신호 (스냅샷 n=1324) ===" + counts);

		assertThat(counts.get(EligibilitySignal.UNRESTRICTED)).isEqualTo(999L);
		assertThat(counts.get(EligibilitySignal.RESTRICTED)).isEqualTo(325L);
		// 원문 코드는 매핑하지 않고 그대로 보존함(EmploymentStatus 매핑 금지)
		assertThat(policies).allSatisfy(p -> assertThat(p.employmentRawCode()).isNotBlank());
		// 0013010(제한없음)은 다른 코드와 절대 함께 오지 않음 — 이 구조가 UNRESTRICTED 판정의 근거임
		assertThat(policies)
			.noneMatch(p -> p.employmentRawCode().contains("0013010") && p.employmentRawCode().contains(","));
	}

	@Test
	void parseEmploymentSignal_blankOrSeparatorOnly_isUnknown() {
		assertThat(this.parser.parseEmploymentSignal(null)).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(this.parser.parseEmploymentSignal(" , ")).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(this.parser.parseEmploymentSignal("0013010")).isEqualTo(EligibilitySignal.UNRESTRICTED);
		assertThat(this.parser.parseEmploymentSignal("0013003,0013006")).isEqualTo(EligibilitySignal.RESTRICTED);
		assertThat(this.parser.parseIncomeSignal(null)).isEqualTo(EligibilitySignal.UNKNOWN);
	}

	// ---- 지급유형(정책제공방법코드) ----

	@Test
	void snapshot_paymentType_onlySubsidyCodeBecomesCash() throws IOException {
		Map<PaymentType, Long> counts = parsedSnapshot().stream()
			.map(ParsedYouthPolicy::paymentType)
			.collect(Collectors.groupingBy(t -> t, Collectors.counting()));

		System.out.println("=== PaymentType 분포 (스냅샷 n=1324) ===");
		counts.forEach((k, v) -> System.out.printf("  %-10s %4d건 (%.2f%%)%n", k, v, v * 100.0 / 1324));

		// 0042006 보조금만 CASH임(예상총액 합산 대상). 나머지는 현금이 아니거나 상환·대납 구조라 보수적으로 UNKNOWN
		assertThat(counts.get(PaymentType.CASH)).isEqualTo(330L);
		assertThat(counts.get(PaymentType.VOUCHER)).isEqualTo(35L);
		assertThat(counts.get(PaymentType.REDUCTION)).isEqualTo(1L);
		assertThat(counts.get(PaymentType.UNKNOWN)).isEqualTo(958L);
		assertThat(counts.getOrDefault(PaymentType.IN_KIND, 0L)).isZero();
	}

	@Test
	void mapPaymentType_unknownCode_fallsBackToUnknown() {
		assertThat(this.parser.mapPaymentType("0042099")).isEqualTo(PaymentType.UNKNOWN);
		assertThat(this.parser.mapPaymentType(null)).isEqualTo(PaymentType.UNKNOWN);
		assertThat(this.parser.mapPaymentType("0042006")).isEqualTo(PaymentType.CASH);
	}

	// ---- 금액(gov24 파서 재사용) ----

	@Test
	void snapshot_amount_reusesGov24ParserAndKeepsBudgetOutOfCashSingles() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		Map<AmountKind, Long> counts = policies.stream()
			.map(p -> p.amount().amountKind())
			.collect(Collectors.groupingBy(k -> k, Collectors.counting()));
		List<ParsedYouthPolicy> cashSingles = policies.stream()
			.filter(p -> p.paymentType() == PaymentType.CASH && p.amount().amountKind() == AmountKind.SINGLE)
			.toList();

		System.out.println("=== AmountKind 분포 (스냅샷 n=1324) ===");
		counts.forEach((k, v) -> System.out.printf("  %-12s %4d건 (%.2f%%)%n", k, v, v * 100.0 / 1324));
		System.out.println("=== 예상총액 자동 채움 후보(CASH + SINGLE) 상위 금액 ===");
		cashSingles.stream()
			.sorted((a, b) -> Long.compare(b.amount().maxAmount(), a.amount().maxAmount()))
			.limit(10)
			.forEach(p -> System.out.printf("  %,15d원  %s%n", p.amount().maxAmount(), p.name()));

		assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(1324L);
		assertThat(cashSingles).isNotEmpty();

		long loanContext = cashSingles.stream().filter(p -> p.description().contains("대출")).count();
		long corporateFunding = cashSingles.stream().filter(p -> p.description().contains("사업화자금")).count();
		long overHundredMillion = cashSingles.stream().filter(p -> p.amount().maxAmount() >= 100_000_000L).count();
		System.out.printf("  CASH+SINGLE %d건 중 대출 문맥 %d건, 사업화자금 문맥 %d건, 1억원 이상 %d건%n", cashSingles.size(), loanContext,
				corporateFunding, overHundredMillion);

		// 2026-07-12 금액 오분류 수정 임무(유형H 대출 한도, 유형I 사업화 자금, 유형J 고액 안전망)로 갱신함.
		// **직전 회차가 "고쳐야 할 결함"으로 박아 둔 tripwire를 이번에 해소한 자리임** — 그때 기대값은 CASH+SINGLE
		// 79건 / 대출 문맥 8건 / 사업화자금 3건 / 1억원 이상 4건이었고, 주석에 "배제 마커가 추가되면 이 수치가 줄면서
		// 이 테스트가 실패하고, 그때 기대값을 내려 잡으면 됨"이라고 적혀 있었음. 실제로 줄었으므로 내려 잡음
		assertThat(cashSingles).hasSize(72);

		// **1억원 이상이 0건이 된 것이 이번 수정의 핵심 실익임.** 수정 전 4건은 전부 대출 한도이거나 기업 사업비였음 —
		// 부산청년 머물자리론(대출최대한도 1억원), 신혼부부 주거자금 대출이자 지원(대출잔액 1억원 한도),
		// 청년 주택 임차보증금 대출이자 지원사업(대출한도 최대 1억원), 창업도약패키지(사업화자금 최대 2억원).
		// SINGLE + CASH가 곧 예상총액 자동 채움 대상이라, 여기서 걷어낸 4건이 곧 막아 낸 오염임
		assertThat(overHundredMillion).isZero();

		// **남은 5건은 과잉 배제를 막은 증거임 — 지우면 안 되는 값임.** description에 "대출"이 있지만 금액은 전부
		// 진짜 지급액(지자체가 대신 내주는 대출이자·임차비)임: 20260430005400212998(월세·전세자금 대출이자 지원
		// 월 최대 50만원), 20260406005400212518(임차비 최대 5백만원), 20251219005400212034(대출이자 납부액
		// 월 최대 25만원), 20251117005400211898(월세·대출이자의 50%, 월 최대 20만원),
		// 20250716005400211316(대출이율 1.5%, 최대 140만원). 대출 배제를 낱말 단위로 넓히면 이 5건이 죽으면서
		// 이 수치가 0이 됨 — 그러면 규칙이 틀린 것임
		assertThat(loanContext).isEqualTo(5L);

		// 사업화자금은 0건이 됨. 마커를 금액 앞뒤 양방향으로 보게 하면서 "연간 20백만원 창업활동비(사업화자금)"
		// (20250829005400211538)·"1천만원 사업화 지원금 지원"(20250114005400210260)처럼 마커가 금액 **뒤**에
		// 오는 표기까지 잡혔기 때문임
		assertThat(corporateFunding).isZero();
	}

	@Test
	void snapshot_amount_typeH_loanLimitIsExcluded_becauseItIsDebtCeilingNotPayout() throws IOException {
		// 오케스트레이터가 지목한 CASH 오염 6건 중 이 소스에 있는 4건을 ID로 고정함. 셋은 대출 한도(유형H), 하나는
		// 기업 사업화자금(유형I)이고, 처방이 서로 다름 — **대출 한도는 아무도 주지 않는 돈이라 배제**하고,
		// **사업화 자금은 기업이 실제로 받는 돈이라 금액을 남기고 강등**함

		// 20250502005400210779 부산청년 머물자리론 "◦대출최대한도 : 1억 원 이내" — 이름부터 "론"이고 전체가 대출
		// 상품임. 실제 혜택은 이자 차등 지원분이고 원문에 그 금액이 없으므로 산정불가가 정답임
		ParsedYouthPolicy busanLoan = findById("20250502005400210779");
		assertThat(busanLoan.paymentType()).isEqualTo(PaymentType.CASH);
		assertThat(busanLoan.amount().amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(busanLoan.amount().amountCandidates()).isEmpty();
		assertThat(busanLoan.amount().parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// 20250502005400210775 신혼부부 주거자금 대출이자 지원 "이자 상환액 지원(대출잔액 1억원 한도 내)" —
		// 실제 혜택은 이자 보전분이지 1억원이 아님
		ParsedYouthPolicy newlywed = findById("20250502005400210775");
		assertThat(newlywed.amount().amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(newlywed.amount().parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// 20250502005400210772 청년 주택 임차보증금 대출이자 지원사업 "대출한도 : 최대 1억원" — 지원내용은
		// "대출금리의 최대 연 3.5% 이자지원"이라 원문에 지급액 금액 표현 자체가 없음
		ParsedYouthPolicy deposit = findById("20250502005400210772");
		assertThat(deposit.amount().amountKind()).isEqualTo(AmountKind.NONE);
		assertThat(deposit.amount().parseStatus()).isEqualTo(AmountParseStatus.EXCLUDED_LOAN_CONTEXT);

		// 20250221005400110545 창업도약패키지 "사업화자금(최대 2억원)" — 창업기업이 실제로 받는 돈이라 배제하지
		// 않고 금액을 남긴 채 강등만 함(개인 예상총액에는 안 들어감)
		ParsedYouthPolicy scaleup = findById("20250221005400110545");
		assertThat(scaleup.amount().amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(scaleup.amount().amountCandidates()).containsExactly(200_000_000L);
	}

	@Test
	void snapshot_amount_typeH_realCashSupportSurvives_becauseTheWordLoanIsNotTheTest() throws IOException {
		// **과잉 배제 방지 — 반대 방향 고정.** "대출"이 들어갔다고 배제하면 지자체 이자 지원금이 통째로 날아감

		// 20251218005400212025 "대출잔액(5천만원 한도)의 이자지원(3%) ... 이자 지원(최대 150만 원) ※ 대출잔액
		// 5,000만원 한도 × 이자율 3% = 150만 원" — 한 원문에 대출 한도와 진짜 지급액이 **나란히** 있음. 5천만원만
		// 죽고 150만원은 살아야 함. 융합 마커("대출잔액")가 창 안에 있어도 gap의 "이자·지원"과 "%"가 살려 냄
		ParsedYouthPolicy gyeongnam = findById("20251218005400212025");
		assertThat(gyeongnam.amount().amountCandidates()).containsExactly(1_500_000L, 1_500_000L);
		assertThat(gyeongnam.amount().parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION);

		// 20260318005400212197 군포시 "대출잔액의 2%, 가구당 최대 300만원 ... 예) 대출잔액이 2억원인 경우" —
		// 예시로 든 대출 잔액 2억원만 죽고, 실제 지급액 300만원·100만원은 전부 살아야 함
		ParsedYouthPolicy gunpo = findById("20260318005400212197");
		assertThat(gunpo.amount().amountCandidates()).doesNotContain(200_000_000L);
		assertThat(gunpo.amount().amountCandidates()).contains(3_000_000L, 1_000_000L);

		// 20260504005400213064 태안 "가구당 연간 100만원 이내 대출이자 지원" — 금액 뒤가 "이내 대출"이라 순방향
		// 마커에 걸릴 뻔했음. 부정 선읽기(대출 뒤 "이자")가 이 진짜 지급액을 지켜 냄
		ParsedYouthPolicy taean = findById("20260504005400213064");
		assertThat(taean.amount().amountCandidates()).contains(1_000_000L);
		assertThat(taean.amount().parseStatus()).isNotIn(AmountParseStatus.EXCLUDED_LOAN_CONTEXT,
				AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION);

		// 20260422005400212868 서귀포 "영농정착지원금 월별 지급 - (1년차) 110만원 ... 5억원 융자" — 융자 원금
		// 5억원만 죽고 월 지급액 3건은 살아야 함
		ParsedYouthPolicy seogwipo = findById("20260422005400212868");
		assertThat(seogwipo.amount().amountCandidates()).containsExactly(1_100_000L, 1_000_000L, 900_000L);
		assertThat(seogwipo.amount().parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION);
	}

	@Test
	void snapshot_amount_typeA_perUnitAllocation_survivesBudgetExclusion() throws IOException {
		// **과잉 배제 방지 — 유형A 반대 방향 고정(2026-07-12 적대 검증 최우선 지시).** "사업비"가 붙었다는 이유로
		// 단위 배분액까지 배제하면 수혜자가 실제로 받는 금액이 화면에서 사라짐. 두 건 다 수정 전에는
		// EXCLUDED_BUDGET_CONTEXT(금액 0건, 산정불가)였음

		// 20250901005400211556 경북청년 예비창업가 육성 "지원내용 : 1인당 사업비 12백만원" — 1인이 받는 돈이라
		// 되살리고, 같은 원문의 사업 총예산 "사 업 비 : 30백만원(도비 9, 군비 21)"은 그대로 배제함
		ParsedYouthPolicy preFounder = findById("20250901005400211556");
		assertThat(preFounder.amount().amountCandidates()).containsExactly(12_000_000L);
		assertThat(preFounder.amount().parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);
		// 단위 배분액이라 SINGLE로 두지 않고 강등함 — 금액은 살리되 예상총액 자동 채움에는 넣지 않음
		assertThat(preFounder.amount().amountKind()).isEqualTo(AmountKind.CONDITIONAL);

		// 20250109005400210105 영세창업농 초기영농비 "사 업 비: 300백만원 ... / 개소당 사업비 30백만원" — 한
		// 문장에 사업 총예산과 단위 배분액이 나란히 있음. 300백만원만 죽고 30백만원은 살아야 함
		ParsedYouthPolicy youngFarmer = findById("20250109005400210105");
		assertThat(youngFarmer.amount().amountCandidates()).containsExactly(30_000_000L);
		assertThat(youngFarmer.amount().amountCandidates()).doesNotContain(300_000_000L);
		assertThat(youngFarmer.amount().amountKind()).isEqualTo(AmountKind.CONDITIONAL);
		assertThat(youngFarmer.amount().parseStatus()).isEqualTo(AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION);
	}

	// ---- 신청방법·서류·URL·수정일시 ----

	@Test
	void snapshot_applicationMethod_hasEmailChannelUnlikeGov24() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		long blankRawText = loadSnapshot().stream()
			.filter(p -> p.applicationMethodContent() == null || p.applicationMethodContent().isBlank())
			.count();
		long unclassified = policies.stream().filter(p -> p.applicationMethod().unclassified()).count();
		long online = policies.stream().filter(p -> p.applicationMethod().online()).count();
		long visit = policies.stream().filter(p -> p.applicationMethod().visit()).count();
		long email = policies.stream().filter(p -> p.applicationMethod().email()).count();

		System.out.println("=== 신청 채널 (스냅샷 n=1324, 중복 허용) ===");
		System.out.printf("  원문 없음 %d건 / 미분류(원문 없음 포함) %d건 / 온라인 %d / 방문 %d / 이메일 %d%n", blankRawText, unclassified,
				online, visit, email);

		// 원문 채움이 53.59%뿐이라 절반 가까이는 원문 자체가 없음(606건). 여기에 원문은 있으나 어느 키워드에도 안 걸린
		// 334건을 더한 940건이 unclassified임 — 플래그만 보면 두 경우를 구분할 수 없으므로(둘 다 전 채널 false),
		// "근거 없음"의 내역이 궁금하면 원문을 봐야 함. 어느 쪽이든 false를 "신청 불가"로 읽으면 안 됨
		assertThat(blankRawText).isEqualTo(606L);
		assertThat(unclassified).isEqualTo(940L);
		assertThat(policies.stream()
			.filter(p -> p.applicationMethod().equals(ApplicationMethodFlags.noEvidence()))
			.count()).isEqualTo(940L);
		assertThat(online).isEqualTo(210L);
		assertThat(visit).isEqualTo(174L);
		// gov24가 만들 수 없는 채널임(키워드 어휘에 이메일이 없음). 이 소스에는 실재함
		assertThat(email).isEqualTo(102L);
		assertThat(policies.stream().filter(p -> p.applicationMethod().mail()).count()).isEqualTo(46L);
		assertThat(policies.stream().filter(p -> p.applicationMethod().phone()).count()).isEqualTo(24L);
		assertThat(policies.stream().filter(p -> p.applicationMethod().fax()).count()).isEqualTo(5L);
	}

	@Test
	void snapshot_requiredDocuments_sentinelsAreNormalizedToNull() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		long filled = policies.stream().filter(p -> p.requiredDocumentsText() != null).count();

		// 원문 채움 447건에서 "해당없음" 계열 센티널 22건을 뺀 425건만 화면에 낼 내용이 있음
		assertThat(filled).isEqualTo(425L);
		assertThat(this.parser.normalizeRequiredDocuments("해당없음")).isNull();
		assertThat(this.parser.normalizeRequiredDocuments("해당 없음")).isNull();
		assertThat(this.parser.normalizeRequiredDocuments("-")).isNull();
		assertThat(this.parser.normalizeRequiredDocuments(null)).isNull();
		// 빈약해도 실제 안내인 값은 남김
		assertThat(this.parser.normalizeRequiredDocuments("별도 문의")).isEqualTo("별도 문의");
	}

	@Test
	void snapshot_urlsAndUpdatedAt_areFilledAsMeasured() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		assertThat(policies.stream().filter(p -> p.applicationUrl() != null).count()).isEqualTo(444L);
		assertThat(policies.stream().filter(p -> p.referenceUrl() != null).count()).isEqualTo(845L);
		// 최종수정일시는 전수 100%가 "yyyy-MM-dd HH:mm:ss"라 전량 파싱돼야 함
		assertThat(policies.stream().filter(p -> p.dataUpdatedAt() != null).count()).isEqualTo(1324L);
		assertThat(this.parser.parseLastModifiedAt("2026-05-06 16:11:43"))
			.isEqualTo(LocalDateTime.of(2026, 5, 6, 16, 11, 43));
		assertThat(this.parser.parseLastModifiedAt("2026-05-06")).isNull();
		assertThat(this.parser.parseLastModifiedAt(null)).isNull();
	}

	// ---- 원문 결합 필드 ----

	@Test
	void snapshot_categoryAndDescription_areAlwaysPresent() throws IOException {
		List<ParsedYouthPolicy> policies = parsedSnapshot();

		// 대분류·중분류는 채움 99.96%이고 표본에서는 결측 0건임
		assertThat(policies).allSatisfy(p -> assertThat(p.categoryRawText()).isNotBlank());
		assertThat(policies).allSatisfy(p -> assertThat(p.description()).isNotBlank());
		// 자격조건 원문은 3개 필드 중 하나라도 있을 때만 생김(표본 1324건 중 743건은 셋 다 비어 null)
		assertThat(policies.stream().filter(p -> p.eligibilityText() == null).count()).isEqualTo(743L);
	}

	// 결합 필드의 부분 결측 처리를 고정함(빈 문자열을 만들지 않고 null을 냄).
	@Test
	void toParsedPolicy_partialRawFields_produceNullsNotEmptyStrings() {
		ParsedYouthPolicy onlySupport = this.parser.toParsedPolicy(dtoWith("지원내용만 있음", null, null, null));
		assertThat(onlySupport.description()).isEqualTo("지원내용만 있음");
		assertThat(onlySupport.eligibilityText()).isNull();
		assertThat(onlySupport.categoryRawText()).isNull();
		assertThat(onlySupport.agency()).isNull();
		assertThat(onlySupport.applicationMethod()).isEqualTo(ApplicationMethodFlags.noEvidence());

		ParsedYouthPolicy onlyLargeCategory = this.parser.toParsedPolicy(dtoWith(null, "정책설명만 있음", "일자리", null));
		assertThat(onlyLargeCategory.description()).isEqualTo("정책설명만 있음");
		assertThat(onlyLargeCategory.categoryRawText()).isEqualTo("일자리");

		ParsedYouthPolicy onlyMediumCategory = this.parser.toParsedPolicy(dtoWith(null, null, null, "취업"));
		assertThat(onlyMediumCategory.categoryRawText()).isEqualTo("취업");
		assertThat(onlyMediumCategory.description()).isNull();
	}

	// ---- 로딩 헬퍼 ----

	private static YouthcenterPolicyDto dtoWith(String supportContent, String explanation, String largeCategory,
			String mediumCategory) {
		return new YouthcenterPolicyDto("PLCY-TEST", "테스트 정책", explanation, supportContent, largeCategory,
				mediumCategory, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null);
	}

	private ParsedYouthPolicy findById(String policyId) throws IOException {
		return parsedSnapshot().stream()
			.filter(p -> policyId.equals(p.policyId()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("스냅샷에서 정책번호를 찾지 못함: " + policyId));
	}

	private List<ParsedYouthPolicy> parsedSnapshot() throws IOException {
		return loadSnapshot().stream().map(this.parser::toParsedPolicy).toList();
	}

	private List<YouthcenterPolicyDto> loadSnapshot() throws IOException {
		return this.parser.parsePolicies(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
	}

}
