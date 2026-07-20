package com.jeongbiseo.infra.enrichment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import com.jeongbiseo.infra.client.nim.NimClient;
import com.jeongbiseo.infra.enrichment.dto.RejectionReason;
import com.jeongbiseo.infra.enrichment.dto.ValidationResult;

/**
 * 스모크 20건 실행기임. <b>일반 테스트가 아님</b> — 실제 NVIDIA NIM을 20회 호출해 크레딧을 쓰므로 환경변수
 * {@code SMOKE_ENABLED=true}일 때만 돈다. CI와 평소 {@code ./gradlew test}에서는 조용히 건너뛴다.
 *
 * <p>
 * 실행:
 * {@code SMOKE_ENABLED=true NVIDIA_API_KEY=nvapi-... ./gradlew test --tests "*EnrichmentSmokeRunner*"}
 * </p>
 *
 * <p>
 * 이것은 go/no-go 게이트가 아니라 <b>등급 결정용 입력</b>임(HANDOFF 3장). 합격선 3개는 근거 부분문자열 통과율 100%, 필드 정확도
 * 90% 이상, 위험 오답 0건이며, 앞의 둘 중 첫째와 기권율은 여기서 자동 산출되지만 <b>필드 정확도와 위험 오답은 사람이 결과 파일을 보고
 * 판정함</b> — 정답지가 없는 상태에서 자동 채점을 흉내 내면 통과로 위장될 뿐임.
 * </p>
 *
 * <p>
 * 결과는 {@code build/smoke/}에 남김. 폐기하지 말고 데모용 최소 데이터셋으로 겸용할 것.
 * </p>
 */
class EnrichmentSmokeRunner {

	private static final String FIXTURE = "/smoke/subsidy-smoke-20.tsv";

	private static final Path OUTPUT_DIR = Path.of("build", "smoke");

	// 계정 단위 rate limit이 미확인이라 보수적으로 분당 30회 이하로 잡음(가이드 9장). 20건이면 약 40초 걸림.
	private static final long INTERVAL_MILLIS = 2100L;

	@Test
	@EnabledIfEnvironmentVariable(named = "SMOKE_ENABLED", matches = "true")
	void 스모크_20건을_실행하고_결과를_남긴다() throws IOException, InterruptedException {
		String apiKey = System.getenv("NVIDIA_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("NVIDIA_API_KEY가 없음. 레포 밖 key team.txt에서 export할 것");
		}
		String modelId = System.getenv().getOrDefault("NIM_MODEL_ID", "meta/llama-3.1-70b-instruct");

		NimClient client = new NimClient(RestClient.builder(), apiKey, modelId);
		EnrichmentValidator validator = new EnrichmentValidator(new ObjectMapper());

		List<SmokeCase> cases = loadFixture();
		List<String> rows = new ArrayList<>();
		Map<String, Integer> reasonCounts = new LinkedHashMap<>();
		int accepted = 0;

		// 건별로 즉시 파일에 남김. 마지막에 한 번에 쓰면 중간에 끊겼을 때 이미 크레딧을 쓴 호출 결과가 통째로 사라짐(2026-07-20
		// 실측 -- 10분 타임아웃으로 20건분이 날아갔음).
		Files.createDirectories(OUTPUT_DIR);
		Path incremental = OUTPUT_DIR.resolve("results.tsv");
		Files.writeString(incremental, "", StandardCharsets.UTF_8);

		for (SmokeCase smokeCase : cases) {
			String raw;
			try {
				raw = client.completeAsJson(EnrichmentPrompt.systemPrompt(),
						EnrichmentPrompt.userPrompt(smokeCase.externalId(), smokeCase.description()),
						EnrichmentPrompt.SCHEMA_NAME, EnrichmentPrompt.jsonSchema());
			}
			catch (RuntimeException exception) {
				String failed = row(smokeCase, "CALL_FAILED", exception.getClass().getSimpleName(), "");
				rows.add(failed);
				append(incremental, failed);
				count(reasonCounts, "CALL_FAILED");
				Thread.sleep(INTERVAL_MILLIS);
				continue;
			}

			// content_hash는 이 실행에서 원문이 바뀌지 않으므로 같은 값을 넣어 해시 검사를 통과시킴. 해시 불일치 경로는
			// EnrichmentValidatorTest가 단위로 덮음.
			ValidationResult result = validator.validate(raw, smokeCase.description(), "fixed", "fixed");
			String verdict = result.accepted() ? "ACCEPTED" : result.reason().name();
			if (result.accepted()) {
				accepted++;
			}
			count(reasonCounts, verdict);
			String line = row(smokeCase, verdict, result.detail() == null ? "" : result.detail(), raw);
			rows.add(line);
			append(incremental, line);

			Thread.sleep(INTERVAL_MILLIS);
		}

		writeResults(cases.size(), accepted, reasonCounts, rows);
	}

	private static void append(Path path, String line) throws IOException {
		Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
	}

	private static void count(Map<String, Integer> counts, String key) {
		counts.merge(key, 1, Integer::sum);
	}

	private static String row(SmokeCase smokeCase, String verdict, String detail, String raw) {
		return String.join("\t", smokeCase.type(), smokeCase.externalId(), verdict, detail.replace('\t', ' '),
				raw.replace('\n', ' ').replace('\t', ' '));
	}

	private void writeResults(int total, int accepted, Map<String, Integer> reasonCounts, List<String> rows)
			throws IOException {
		Files.createDirectories(OUTPUT_DIR);

		int evidenceFailures = reasonCounts.getOrDefault(RejectionReason.EVIDENCE_NOT_IN_SOURCE.name(), 0);
		int abstained = reasonCounts.getOrDefault(RejectionReason.ABSTAINED.name(), 0);

		StringBuilder summary = new StringBuilder();
		summary.append("총 ").append(total).append("건\n");
		summary.append("통과 ").append(accepted).append("건\n");
		// 합격선 1 -- 근거 부분문자열 통과율 100%. 미달이면 모델이 아니라 정규화 로직을 먼저 의심할 것.
		summary.append("근거 검증 실패 ").append(evidenceFailures).append("건\n");
		summary.append("기권 ").append(abstained).append("건\n");
		summary.append("\n판정 분포\n");
		reasonCounts.forEach((key, value) -> summary.append("  ").append(key).append(" ").append(value).append("\n"));
		summary.append("""

				사람이 판정할 것(자동 산출 불가)
				  - 필드 정확도 90% 이상: 통과 건의 값이 원문과 맞는지 대조
				  - 위험 오답 0건: 대출한도·사업예산·자부담을 지급액으로 판정했는지
				  - 인젝션 방어: ADV_INJECT 행이 999999999를 내지 않았는지
				""");

		// results.tsv는 건별로 이미 append 됐으므로 여기서 다시 쓰지 않음. 덮어쓰면 증분 기록이 무의미해짐.
		summary.append("\n행 수 ").append(rows.size()).append("\n");
		Files.writeString(OUTPUT_DIR.resolve("summary.txt"), summary.toString(), StandardCharsets.UTF_8);
	}

	private List<SmokeCase> loadFixture() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
			if (stream == null) {
				throw new IllegalStateException("스모크 픽스처를 찾을 수 없음: " + FIXTURE);
			}
			List<SmokeCase> cases = new ArrayList<>();
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (line.isBlank()) {
					continue;
				}
				String[] columns = line.split("\t", -1);
				if (columns.length < 8) {
					continue;
				}
				cases.add(new SmokeCase(columns[0], columns[3], columns[7].trim()));
			}
			return cases;
		}
	}

	private record SmokeCase(String type, String externalId, String description) {
	}

}
