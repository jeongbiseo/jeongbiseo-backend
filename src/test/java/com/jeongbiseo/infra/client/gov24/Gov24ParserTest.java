package com.jeongbiseo.infra.client.gov24;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.gov24.dto.DeadlineFailureReason;
import com.jeongbiseo.infra.client.gov24.dto.DeadlineParseResult;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.gov24.dto.ParsedSubsidyResult;

/**
 * 보조금24 파서 PoC 테스트임(PLAN.md 3장 W4 절). 픽스처(demo/fixtures/sample_serviceList.json,
 * sample_serviceDetail.json, sample_supportConditions.json)를 읽어 신청기한 자유텍스트 파싱 성공률을 리포트로
 * 출력함. 파싱 실패가 있어도 이 테스트를 실패시키지 않음(PoC 리포트가 목적) — 단 파서가 예외를 던지면 테스트가 실패함(정상 동작).
 */
class Gov24ParserTest {

	private static final Path FIXTURES_DIR = Path.of("fixtures");

	private final Gov24Parser parser = new Gov24Parser();

	@Test
	void parseFixtures_reportsDeadlineParsingSuccessRate() throws IOException {
		String serviceListJson = readFixture("sample_serviceList.json");
		String serviceDetailJson = readFixture("sample_serviceDetail.json");
		String supportConditionsJson = readFixture("sample_supportConditions.json");

		// serviceList와 serviceDetail이 같은 서비스ID를 다시 응답하므로(실제 gov24 호출 순서 재현)
		// 서비스ID로 합쳐 중복 없이 셈함
		Map<String, Gov24ServiceItemDto> itemsById = new LinkedHashMap<>();
		for (Gov24ServiceItemDto item : parser.parseServiceItems(serviceListJson)) {
			itemsById.put(item.serviceId(), item);
		}
		for (Gov24ServiceItemDto item : parser.parseServiceItems(serviceDetailJson)) {
			itemsById.put(item.serviceId(), item);
		}

		Map<String, Gov24SupportConditionDto> conditionsById = parser.parseSupportConditions(supportConditionsJson)
			.stream()
			.collect(Collectors.toMap(Gov24SupportConditionDto::serviceId, c -> c, (a, b) -> a, LinkedHashMap::new));

		List<ParsedSubsidyResult> results = itemsById.values()
			.stream()
			.map(item -> parser.toParsedSubsidy(item, conditionsById))
			.toList();

		long total = results.size();
		long parsedCount = results.stream().filter(r -> r.deadline().parsed()).count();
		long failedCount = total - parsedCount;
		Map<DeadlineFailureReason, Long> failureReasonCounts = results.stream()
			.filter(r -> !r.deadline().parsed())
			.collect(Collectors.groupingBy(r -> r.deadline().failureReason(), Collectors.counting()));
		double successRate = (total == 0) ? 0.0 : (parsedCount * 100.0 / total);

		System.out.println("=== Gov24ParserTest PoC 요약 (PLAN.md 3장 W4 절, 미결-05 근거) ===");
		System.out.println("전체 지원금 수(서비스ID 기준 중복 제거): " + total);
		System.out.println("신청기한 파싱 성공: " + parsedCount + "건");
		System.out.println("신청기한 파싱 실패: " + failedCount + "건");
		System.out.printf("자동 파싱 성공률: %.1f%%%n", successRate);
		System.out.println("실패 사유별 분류: " + failureReasonCounts);
		for (ParsedSubsidyResult result : results) {
			DeadlineParseResult deadline = result.deadline();
			String outcome = deadline.parsed() ? ("성공 -> " + deadline.deadline())
					: ("실패 -> " + deadline.failureReason());
			System.out.println("  [" + result.serviceId() + "] " + result.serviceName() + " | 원문: \""
					+ deadline.rawText() + "\" | " + outcome);
		}

		// 픽스처 구조 자체가 깨지지 않았는지에 대한 최소 불변식만 확인함 — 개별 신청기한 파싱 성공 여부는
		// PoC 리포트 대상이지 테스트 실패 조건이 아님(요청 지시사항 그대로)
		assertThat(total).isEqualTo(3);
		assertThat(parsedCount + failedCount).isEqualTo(total);
	}

	@Test
	void parseDeadline_dateRangeFormat_parsesEndDateAsDeadline() {
		DeadlineParseResult result = parser.parseDeadline("5.1.~5.31.");

		assertThat(result.parsed()).isTrue();
		assertThat(result.deadline().getMonthValue()).isEqualTo(5);
		assertThat(result.deadline().getDayOfMonth()).isEqualTo(31);
	}

	@Test
	void parseDeadline_absoluteDateFormat_parsesExactDate() {
		DeadlineParseResult result = parser.parseDeadline("2025년 8월 30일까지");

		assertThat(result.parsed()).isTrue();
		assertThat(result.deadline()).isEqualTo(LocalDate.of(2025, 8, 30));
	}

	@Test
	void parseDeadline_budgetExhaustion_recordsFailureReason() {
		DeadlineParseResult result = parser.parseDeadline("예산 소진 시까지");

		assertThat(result.parsed()).isFalse();
		assertThat(result.failureReason()).isEqualTo(DeadlineFailureReason.BUDGET_EXHAUSTION);
	}

	@Test
	void parseDeadline_alwaysOpen_recordsFailureReason() {
		DeadlineParseResult result = parser.parseDeadline("상시신청");

		assertThat(result.parsed()).isFalse();
		assertThat(result.failureReason()).isEqualTo(DeadlineFailureReason.ALWAYS_OPEN);
	}

	@Test
	void parseDeadline_externalRegulationReference_recordsFailureReason() {
		DeadlineParseResult result = parser.parseDeadline("주택도시기금 주거안정 월세대출 규정에 따름");

		assertThat(result.parsed()).isFalse();
		assertThat(result.failureReason()).isEqualTo(DeadlineFailureReason.EXTERNAL_REGULATION_REFERENCE);
	}

	private String readFixture(String fileName) throws IOException {
		return Files.readString(FIXTURES_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

}
