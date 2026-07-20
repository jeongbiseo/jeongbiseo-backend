package com.jeongbiseo.infra.client.nim;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * NimClient 단위 테스트임(MockRestServiceServer, 실제 NVIDIA 호출 없음). 공개 생성자는 전용 requestFactory를
 * 덮어써서 MockRestServiceServer 바인딩을 지우므로, 테스트는 RestClient를 직접 받는 패키지 전용 생성자를 씀(gov24 수집기
 * 테스트와 같은 관례).
 */
class NimClientTest {

	private static final String ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions";

	private static final String BASE_URL = "https://integrate.api.nvidia.com/v1";

	private static final String OK_BODY = """
			{"choices":[{"index":0,"message":{"role":"assistant","content":"{\\"amountKind\\":\\"FIXED\\"}"},
			"finish_reason":"stop"}]}
			""";

	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("amountKind", Map.of("type", "string"));
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		return schema;
	}

	private static NimClient clientOf(RestClient.Builder builder, String apiKey) {
		return new NimClient(builder.baseUrl(BASE_URL).build(), apiKey, "meta/llama-3.1-70b-instruct");
	}

	@Test
	void 정상_응답이면_모델이_낸_JSON_문자열을_그대로_반환한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT))
			.andExpect(method(POST))
			.andExpect(header("Authorization", "Bearer test-key"))
			.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		String result = clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema());

		// 파싱하지 않고 문자열 그대로 넘김 -- 스키마·근거·정책 검증은 검증기 책임이라 클라이언트가 해석하지 않음
		assertThat(result).isEqualTo("{\"amountKind\":\"FIXED\"}");
		server.verify();
	}

	/**
	 * 스키마 강제 방식이 response_format.json_schema임을 요청 바디로 고정함. nvext.guided_json으로 되돌리면 호스티드
	 * 엔드포인트가 에러 없이 무시해(2026-07-20 실측) 모델이 제멋대로 필드를 내는데 HTTP 200이라 드러나지 않음. 이 테스트가 그 회귀를
	 * 잡는 유일한 장치임.
	 */
	@Test
	void 요청은_response_format_json_schema로_스키마를_강제한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT))
			.andExpect(jsonPath("$.response_format.type").value("json_schema"))
			.andExpect(jsonPath("$.response_format.json_schema.name").value("amount_extraction"))
			.andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
			.andExpect(jsonPath("$.response_format.json_schema.schema.type").value("object"))
			// 무시되는 nvext로 되돌아가지 않았는지 확인함
			.andExpect(jsonPath("$.nvext").doesNotExist())
			// 같은 공고를 두 번 돌렸을 때 결과가 흔들리면 검수와 재현이 불가능하므로 0으로 고정함
			.andExpect(jsonPath("$.temperature").value(0.0))
			.andExpect(jsonPath("$.messages[0].role").value("system"))
			.andExpect(jsonPath("$.messages[1].role").value("user"))
			.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema());

		server.verify();
	}

	@Test
	void 키가_비어_있으면_호출하지_않고_실패한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		assertThatThrownBy(
				() -> clientOf(builder, "  ").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("키가 설정되지 않음");

		// 요청 자체가 나가지 않아야 함(크레딧·시간 낭비 방지)
		server.verify();
	}

	@Test
	void 인증_실패는_재시도하지_않고_즉시_중단한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		// expect를 한 번만 등록함 -- 재시도하면 두 번째 요청에서 검증이 깨져 테스트가 실패함
		server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(
				() -> clientOf(builder, "bad-key").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("인증 실패");

		server.verify();
	}

	@Test
	void 키가_null이면_호출하지_않고_실패한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		assertThatThrownBy(
				() -> clientOf(builder, null).completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("키가 설정되지 않음");

		server.verify();
	}

	@Test
	void 응답에_choices_필드가_아예_없으면_실패로_처리한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(
				() -> clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("본문이 없음");
	}

	@Test
	void 본문이_공백뿐이면_실패로_처리한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT))
			.andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(
				() -> clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("본문이 없음");
	}

	/**
	 * 서버 오류가 계속되면 무한히 매달리지 않고 재시도를 소진한 뒤 실패함. 최초 1회 더하기 재시도 3회로 총 4번 호출하는지도 함께 고정함 -- 재시도
	 * 횟수가 늘면 배치가 한 건에 붙잡혀 전체가 밀림. 백오프 2·4·8초를 실제로 기다리므로 이 테스트는 느림.
	 */
	@Test
	void 서버_오류가_계속되면_재시도를_소진하고_실패한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (int i = 0; i < 4; i++) {
			server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		}

		assertThatThrownBy(
				() -> clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("재시도 소진");

		server.verify();
	}

	@Test
	void 응답에_선택지가_없으면_실패로_처리한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(
				() -> clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("본문이 없음");
	}

	/**
	 * 429는 재시도로 회복함. 백오프가 실제로 2초 재우므로 이 테스트만 느림 -- 재시도가 도는지 확인하는 값이 그 비용보다 큼.
	 */
	@Test
	void 요청_한도를_넘기면_물러섰다가_재시도한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		String result = clientOf(builder, "test-key").completeAsJson("system", "user", "amount_extraction", schema());

		assertThat(result).isEqualTo("{\"amountKind\":\"FIXED\"}");
		server.verify();
	}

}
