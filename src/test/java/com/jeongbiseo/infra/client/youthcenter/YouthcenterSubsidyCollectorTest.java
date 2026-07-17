package com.jeongbiseo.infra.client.youthcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/** 온통청년 운영 수집 경로를 얼린 실호출 픽스처와 가짜 HTTP 서버로 검증함. */
class YouthcenterSubsidyCollectorTest {

	private static final String BASE_URL = "https://www.youthcenter.go.kr";

	private static final String DUMMY_KEY = "test-key";

	@Test
	void collect_fetchesAndNormalizesEveryPolicy() throws IOException {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(pageUrl()))
			.andExpect(method(GET))
			.andRespond(withSuccess(withTotalCount(readFixture(), 2), MediaType.APPLICATION_JSON));
		YouthcenterSubsidyCollector collector = new YouthcenterSubsidyCollector(builder.build(), DUMMY_KEY, 500, 2);

		List<NormalizedSubsidy> result = collector.collect();

		assertThat(collector.source()).isEqualTo(SubsidySource.YOUTHCENTER);
		assertThat(result).hasSize(2)
			.allSatisfy(item -> assertThat(item.source()).isEqualTo(SubsidySource.YOUTHCENTER));
		assertThat(result).extracting(NormalizedSubsidy::externalId)
			.containsExactly("20260708005400213252", "20260708005400213251");
		server.verify();
	}

	@Test
	void collect_rejectsIncompletePopulation() throws IOException {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(pageUrl()))
			.andRespond(withSuccess(withTotalCount(readFixture(), 3), MediaType.APPLICATION_JSON));
		YouthcenterSubsidyCollector collector = new YouthcenterSubsidyCollector(builder.build(), DUMMY_KEY, 500, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class).hasMessage("온통청년 전량 수집 실패");
		server.verify();
	}

	@Test
	void collect_rejectsMissingPagingMetadata() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(pageUrl()))
			.andRespond(withSuccess("{\"result\":{\"youthPolicyList\":[]}}", MediaType.APPLICATION_JSON));
		YouthcenterSubsidyCollector collector = new YouthcenterSubsidyCollector(builder.build(), DUMMY_KEY, 500, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class).hasMessage("온통청년 전체 건수 누락");
		server.verify();
	}

	@Test
	void collect_rejectsMissingKeyBeforeRequest() {
		YouthcenterSubsidyCollector collector = new YouthcenterSubsidyCollector(RestClient.create(BASE_URL), null, 500,
				1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class)
			.hasMessage("온통청년 API 키가 설정되지 않음");
	}

	@Test
	void collect_sanitizesHttpFailureWithoutRequestUri() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(pageUrl())).andRespond(withServerError());
		YouthcenterSubsidyCollector collector = new YouthcenterSubsidyCollector(builder.build(), DUMMY_KEY, 500, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("온통청년 요청 실패:")
			.hasMessageNotContaining(DUMMY_KEY);
		server.verify();
	}

	private static String pageUrl() {
		return BASE_URL + "/go/ythip/getPlcy?apiKeyNm=" + DUMMY_KEY + "&pageNum=1&pageSize=500&rtnType=json";
	}

	private static String withTotalCount(String json, int totalCount) {
		return json.replaceFirst("\\\"totCount\\\":\\d+", "\\\"totCount\\\":" + totalCount);
	}

	private static String readFixture() throws IOException {
		return Files.readString(Path.of("fixtures", "sample_youthcenter.json"), StandardCharsets.UTF_8);
	}

}
