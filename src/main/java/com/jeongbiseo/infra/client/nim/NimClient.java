package com.jeongbiseo.infra.client.nim;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jeongbiseo.infra.client.nim.dto.NimChatResponse;

/**
 * NVIDIA NIM chat completions 클라이언트임(OpenAI 호환 엔드포인트). LLM 보강 배치가 공고 한 건씩 호출해 금액 정보를
 * 구조화하는 데만 씀. 사용자 요청 경로에서는 호출하지 않음(판정원칙 5번 — LLM 장애가 사용자 API로 전파되면 안 됨).
 *
 * 이 클래스는 문자열 응답까지만 책임짐. JSON 파싱과 스키마·근거·정책 검증은 검증기가 맡음 — 모델 출력을 신뢰해 검증을 생략하지 않는다는 책임
 * 경계(배치 설계 2장)를 클래스 경계로도 지킴.
 */
@Component
public class NimClient {

	private static final Logger log = LoggerFactory.getLogger(NimClient.class);

	private static final String BASE_URL = "https://integrate.api.nvidia.com/v1";

	// 공용 ingestionRestClientBuilder의 읽기 상한 30초를 그대로 쓰지 않고 전용 상한을 둠. 70B 모델이 수백 토큰을 생성하면
	// 30초를 넘길 수 있어 공용 값으로는 정상 응답도 타임아웃으로 버려짐. 반대로 이 값을 공용 빌더에 올리면 소셜 IdP 토큰 교환까지
	// 느슨해지므로 이쪽만 늘림.
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	// 계정 단위 rate limit이 분당 40회로 알려져 있으나 공식 확인이 안 된 값이라(가이드 3장) 보수적으로 잡음. 429를 만나면
	// 2초·4초·8초로 물러섬. 병렬 호출은 두지 않음 — 계정 단위 한도라 스레드를 늘려도 총량은 같고 429만 늘어남.
	private static final int MAX_RETRIES = 3;

	private static final long BASE_BACKOFF_MILLIS = 2000L;

	// 금액 구조화 출력은 필드 10개 미만이라 1024면 충분함. 부족하면 finishReason이 "length"로 와서 로그에 드러남.
	private static final int MAX_TOKENS = 1024;

	// 같은 공고를 두 번 돌렸을 때 결과가 달라지면 검수와 재현이 불가능하므로 0으로 고정함.
	private static final double TEMPERATURE = 0.0;

	private final RestClient restClient;

	private final String apiKey;

	private final String modelId;

	@Autowired
	public NimClient(RestClient.Builder builder, @Value("${app.llm.nim.api-key:}") String apiKey,
			@Value("${app.llm.nim.model-id:meta/llama-3.1-70b-instruct}") String modelId) {
		this(builder.clone().requestFactory(longReadRequestFactory()).baseUrl(BASE_URL).build(), apiKey, modelId);
	}

	NimClient(RestClient restClient, String apiKey, String modelId) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.modelId = modelId;
	}

	private static JdkClientHttpRequestFactory longReadRequestFactory() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return requestFactory;
	}

	/**
	 * 주어진 JSON 스키마를 강제해 한 건을 구조화함. 반환값은 모델이 낸 JSON 문자열 그대로이며 파싱·검증은 호출측 책임임.
	 *
	 * 스키마 강제는 {@code response_format: json_schema}로 함. <b>{@code nvext.guided_json}으로 바꾸지
	 * 말 것</b> — 호스티드 엔드포인트에서 에러 없이 무시되어(2026-07-20 실측) 모델이 스키마와 무관한 필드명을 지어내는데 HTTP 200이라
	 * 호출측이 성공으로 착각함.
	 * @param systemPrompt 역할·금지사항을 담은 시스템 지시
	 * @param userPrompt 공고 본문을 포함한 사용자 지시
	 * @param schemaName 스키마 이름(모델에 전달되는 식별자)
	 * @param jsonSchema 강제할 JSON 스키마
	 * @return 모델이 낸 JSON 문자열
	 * @throws IllegalStateException 키 미설정, 응답 없음, 재시도 소진 시
	 */
	public String completeAsJson(String systemPrompt, String userPrompt, String schemaName,
			Map<String, Object> jsonSchema) {
		requireApiKey();
		Map<String, Object> body = buildRequestBody(systemPrompt, userPrompt, schemaName, jsonSchema);

		RestClientException lastFailure = null;
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			if (attempt > 0) {
				backOff(attempt);
			}
			try {
				return extractContent(post(body));
			}
			catch (HttpClientErrorException.TooManyRequests exception) {
				lastFailure = exception;
				log.warn("NIM 429 수신, 재시도함: attempt={}/{}", attempt + 1, MAX_RETRIES + 1);
			}
			catch (HttpClientErrorException.Unauthorized exception) {
				// 401은 키·권한 문제라 재시도해도 같은 결과임. 즉시 중단해 크레딧과 시간을 낭비하지 않음(배치 설계 7장).
				throw new IllegalStateException("NIM 인증 실패(키 또는 권한 확인 필요)");
			}
			catch (RestClientException exception) {
				// 5xx·타임아웃은 일시적일 수 있어 재시도함. 예외 메시지에 요청 바디나 키가 섞일 수 있어 클래스명만 남김.
				lastFailure = exception;
				log.warn("NIM 호출 실패, 재시도함: attempt={}/{}, cause={}", attempt + 1, MAX_RETRIES + 1,
						exception.getClass().getSimpleName());
			}
		}
		throw new IllegalStateException(
				"NIM 호출 재시도 소진: " + (lastFailure == null ? "원인 미상" : lastFailure.getClass().getSimpleName()));
	}

	private NimChatResponse post(Map<String, Object> body) {
		return this.restClient.post()
			.uri(uriBuilder -> uriBuilder.pathSegment("chat", "completions").build())
			.header("Authorization", "Bearer " + this.apiKey)
			.body(body)
			.retrieve()
			.body(NimChatResponse.class);
	}

	private static String extractContent(NimChatResponse response) {
		if (response == null) {
			throw new IllegalStateException("NIM 빈 응답");
		}
		String content = response.firstContent();
		if (content == null || content.isBlank()) {
			throw new IllegalStateException("NIM 응답에 본문이 없음");
		}
		return content;
	}

	private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, String schemaName,
			Map<String, Object> jsonSchema) {
		Map<String, Object> schemaSpec = new LinkedHashMap<>();
		schemaSpec.put("name", schemaName);
		schemaSpec.put("strict", true);
		schemaSpec.put("schema", jsonSchema);

		Map<String, Object> responseFormat = new LinkedHashMap<>();
		responseFormat.put("type", "json_schema");
		responseFormat.put("json_schema", schemaSpec);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", this.modelId);
		body.put("max_tokens", MAX_TOKENS);
		body.put("temperature", TEMPERATURE);
		body.put("messages", List.of(message("system", systemPrompt), message("user", userPrompt)));
		body.put("response_format", responseFormat);
		return body;
	}

	private static Map<String, Object> message(String role, String content) {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", role);
		message.put("content", content);
		return message;
	}

	// ponytail: 배치 전용 순차 호출이라 스레드를 재우는 것으로 충분함. 스케줄러·비동기 재시도 프레임워크를 두지 않음.
	private static void backOff(int attempt) {
		long millis = BASE_BACKOFF_MILLIS * (1L << (attempt - 1));
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("NIM 재시도 대기 중 인터럽트됨");
		}
	}

	private void requireApiKey() {
		if (this.apiKey == null || this.apiKey.isBlank()) {
			throw new IllegalStateException("NIM API 키가 설정되지 않음");
		}
	}

}
