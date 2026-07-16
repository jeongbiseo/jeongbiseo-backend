package com.jeongbiseo.infra.client.gov24;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.domain.common.enums.TargetAudience;

/** gov24 운영 수집 경로를 얼린 실호출 픽스처와 가짜 HTTP 서버로 검증함. */
@ExtendWith(OutputCaptureExtension.class)
class Gov24SubsidyCollectorTest {

	private static final String BASE_URL = "https://api.odcloud.kr/api/gov24/v3";

	private static final String DUMMY_KEY = "test-key";

	@Test
	void collect_fetchesThreeOperationsAndNormalizesEveryDetail() throws IOException {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		String serviceList = withTotalCount(read("sample_serviceList.json"), 3);
		String serviceDetail = withTotalCount(read("sample_serviceDetail.json"), 3);
		String supportConditions = read("sample_supportConditions.json");
		int conditionCount = new Gov24Parser().parseSupportConditions(supportConditions).size();
		supportConditions = withTotalCount(supportConditions, conditionCount);

		expectPage(server, "serviceList", serviceList);
		expectPage(server, "serviceDetail", serviceDetail);
		expectPage(server, "supportConditions", supportConditions);

		Gov24SubsidyCollector collector = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 2);
		List<NormalizedSubsidy> result = collector.collect();

		assertThat(collector.source()).isEqualTo(SubsidySource.GOV24);
		assertThat(result).hasSize(3).allSatisfy(item -> assertThat(item.source()).isEqualTo(SubsidySource.GOV24));
		assertThat(result).extracting(NormalizedSubsidy::externalId)
			.containsExactly("000000465790", "105100000001", "116010000001");
		server.verify();
	}

	@Test
	void collect_rejectsIncompletePopulation() throws IOException {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectPage(server, "serviceList", withTotalCount(read("sample_serviceList.json"), 4));
		Gov24SubsidyCollector collector = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class).hasMessage("gov24 전량 수집 실패");
		server.verify();
	}

	@Test
	void collect_rejectsMissingKeyBeforeRequest() {
		Gov24SubsidyCollector collector = new Gov24SubsidyCollector(RestClient.create(BASE_URL), " ", 1000, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class)
			.hasMessage("gov24 API 키가 설정되지 않음");
	}

	@Test
	void collect_sanitizesHttpFailureWithoutRequestUri() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(pageUrl("serviceList"))).andExpect(method(GET)).andRespond(withServerError());
		Gov24SubsidyCollector collector = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("gov24 요청 실패:")
			.hasMessageNotContaining(DUMMY_KEY);
		server.verify();
	}

	@Test
	void collect_omitsServiceListRecordMissingFromDetailsAndReportsCount(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectPage(server, "serviceList",
				servicePage(listRow("JOINED", "개인", "생활안정"), listRow("LIST_ONLY", "개인", "보육")));
		expectPage(server, "serviceDetail", servicePage(detailRow("JOINED")));
		expectPage(server, "supportConditions", conditionPage("JOINED", "LIST_ONLY"));

		List<NormalizedSubsidy> result = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1).collect();

		assertThat(result).extracting(NormalizedSubsidy::externalId).containsExactly("JOINED");
		assertThat(output.getAll()).contains("listMinusDetail=1", "detailMinusList=0", "listMinusConditions=0");
		server.verify();
	}

	@Test
	void collect_keepsDetailWithoutSupportConditionsAndReportsCount(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectPage(server, "serviceList", servicePage(listRow("NO_CONDITION", "개인", "생활안정")));
		expectPage(server, "serviceDetail", servicePage(detailRow("NO_CONDITION")));
		expectPage(server, "supportConditions", conditionPage());

		List<NormalizedSubsidy> result = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1).collect();

		assertThat(result).singleElement().satisfies(item -> {
			assertThat(item.externalId()).isEqualTo("NO_CONDITION");
			assertThat(item.eligibility().ageSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
			assertThat(item.eligibility().incomeSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
			assertThat(item.eligibility().householdSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
			assertThat(item.occupationRestriction()).isEqualTo(OccupationRestriction.NONE);
		});
		assertThat(output.getAll()).contains("listMinusDetail=0", "detailMinusList=0", "listMinusConditions=1");
		server.verify();
	}

	@Test
	void collect_acceptsRepeatedPagesWhenRowCountMatchesTotalAndReportsUniqueIdShortfall(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (int page = 1; page <= 2; page++) {
			expectPage(server, "serviceList", page, 1, servicePage(2, listRow("REPEATED", "개인", "생활안정")));
		}
		for (int page = 1; page <= 2; page++) {
			expectPage(server, "serviceDetail", page, 1, servicePage(2, detailRow("REPEATED")));
		}
		for (int page = 1; page <= 2; page++) {
			expectPage(server, "supportConditions", page, 1, conditionPage(2, "REPEATED"));
		}

		List<NormalizedSubsidy> result = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1, 2).collect();

		assertThat(result).extracting(NormalizedSubsidy::externalId).containsExactly("REPEATED", "REPEATED");
		assertThat(output.getAll()).contains("operation=serviceList", "rows=2", "uniqueIds=1", "totalCount=2");
		server.verify();
	}

	@Test
	void collect_overwritesDuplicateServiceListIdWithLastRowAndReportsIt(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectPage(server, "serviceList",
				servicePage(listRow("DUPLICATE", "소상공인", "기업지원"), listRow("DUPLICATE", "개인", "생활안정")));
		expectPage(server, "serviceDetail", servicePage(detailRow("DUPLICATE")));
		expectPage(server, "supportConditions", conditionPage("DUPLICATE"));

		List<NormalizedSubsidy> result = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1).collect();

		assertThat(result).singleElement().satisfies(item -> {
			assertThat(item.targetAudience()).isEqualTo(TargetAudience.PERSONAL);
			assertThat(item.categoryRawText()).isEqualTo("생활안정");
		});
		assertThat(output.getAll()).contains("operation=serviceList", "rows=2", "uniqueIds=1", "totalCount=2");
		server.verify();
	}

	@Test
	void collect_rejectsDetailMissingFromServiceListAfterReportingCount(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectPage(server, "serviceList", servicePage(listRow("JOINED", "개인", "생활안정")));
		expectPage(server, "serviceDetail", servicePage(detailRow("JOINED"), detailRow("DETAIL_ONLY")));
		expectPage(server, "supportConditions", conditionPage("JOINED", "DETAIL_ONLY"));
		Gov24SubsidyCollector collector = new Gov24SubsidyCollector(builder.build(), DUMMY_KEY, 1000, 1);

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class)
			.hasMessage("gov24 serviceList 조인이 불완전함");
		assertThat(output.getAll()).contains("listMinusDetail=0", "detailMinusList=1", "listMinusConditions=0");
		server.verify();
	}

	private static void expectPage(MockRestServiceServer server, String operation, String body) {
		expectPage(server, operation, 1, 1000, body);
	}

	private static void expectPage(MockRestServiceServer server, String operation, int page, int perPage, String body) {
		server.expect(once(), requestTo(pageUrl(operation, page, perPage)))
			.andExpect(method(GET))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static String pageUrl(String operation) {
		return pageUrl(operation, 1, 1000);
	}

	private static String pageUrl(String operation, int page, int perPage) {
		return BASE_URL + "/" + operation + "?page=" + page + "&perPage=" + perPage + "&serviceKey=" + DUMMY_KEY;
	}

	private static String servicePage(String... rows) {
		return servicePage(rows.length, rows);
	}

	private static String servicePage(int totalCount, String... rows) {
		return "{\"totalCount\":" + totalCount + ",\"data\":[" + String.join(",", rows) + "]}";
	}

	private static String listRow(String serviceId, String userType, String category) {
		return "{\"서비스ID\":\"" + serviceId + "\",\"서비스명\":\"" + serviceId + "\",\"사용자구분\":\"" + userType
				+ "\",\"서비스분야\":\"" + category + "\"}";
	}

	private static String detailRow(String serviceId) {
		return "{\"서비스ID\":\"" + serviceId + "\",\"서비스명\":\"" + serviceId
				+ "\",\"신청기한\":\"상시\",\"지원유형\":\"현금\",\"소관기관명\":\"보건복지부\","
				+ "\"지원내용\":\"월 10만원 지원\",\"지원대상\":\"개인\",\"수정일시\":\"2026-07-01\","
				+ "\"구비서류\":\"해당없음\",\"신청방법\":\"온라인\"}";
	}

	private static String conditionPage(String... serviceIds) {
		return conditionPage(serviceIds.length, serviceIds);
	}

	private static String conditionPage(int totalCount, String... serviceIds) {
		String rows = java.util.Arrays.stream(serviceIds)
			.map(serviceId -> "{\"서비스ID\":\"" + serviceId + "\"}")
			.collect(java.util.stream.Collectors.joining(","));
		return "{\"totalCount\":" + totalCount + ",\"data\":[" + rows + "]}";
	}

	private static String withTotalCount(String json, int totalCount) {
		return json.replaceFirst("\\\"totalCount\\\":\\d+", "\\\"totalCount\\\":" + totalCount);
	}

	private static String read(String fileName) throws IOException {
		return Files.readString(Path.of("fixtures", fileName), StandardCharsets.UTF_8);
	}

}
