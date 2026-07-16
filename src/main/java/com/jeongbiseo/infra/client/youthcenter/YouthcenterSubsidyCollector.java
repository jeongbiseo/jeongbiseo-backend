package com.jeongbiseo.infra.client.youthcenter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jeongbiseo.infra.client.common.SubsidySourceCollector;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyDto;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyListResponseDto;

/** 온통청년 getPlcy 전량을 수집해 정규화하는 운영 수집기임. */
@Order(2)
@Component
public final class YouthcenterSubsidyCollector implements SubsidySourceCollector {

	private static final String BASE_URL = "https://www.youthcenter.go.kr";

	private static final String POLICY_PATH = "/go/ythip/getPlcy";

	private final RestClient restClient;

	private final String apiKey;

	private final int pageSize;

	private final int maxPages;

	private final YouthcenterParser parser;

	private final YouthcenterSubsidyNormalizer normalizer;

	@Autowired
	public YouthcenterSubsidyCollector(RestClient.Builder builder,
			@Value("${app.ingestion.youthcenter.api-key:}") String apiKey,
			@Value("${app.ingestion.youthcenter.page-size:500}") int pageSize,
			@Value("${app.ingestion.youthcenter.max-pages:50}") int maxPages) {
		this(builder.clone().baseUrl(BASE_URL).build(), apiKey, pageSize, maxPages);
	}

	YouthcenterSubsidyCollector(RestClient restClient, String apiKey, int pageSize, int maxPages) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.pageSize = pageSize;
		this.maxPages = maxPages;
		this.parser = new YouthcenterParser();
		this.normalizer = new YouthcenterSubsidyNormalizer();
	}

	@Override
	public SubsidySource source() {
		return SubsidySource.YOUTHCENTER;
	}

	@Override
	public List<NormalizedSubsidy> collect() {
		requireApiKey();
		List<YouthcenterPolicyDto> policies = new ArrayList<>();
		Integer totalCount = null;
		for (int page = 1; page <= this.maxPages; page++) {
			YouthcenterPolicyListResponseDto response = parsePage(request(page));
			if (response.result() == null || response.result().pagging() == null
					|| response.result().pagging().totalCount() == null) {
				throw new IllegalStateException("온통청년 전체 건수 누락");
			}
			if (totalCount == null) {
				totalCount = response.result().pagging().totalCount();
			}
			List<YouthcenterPolicyDto> pageItems = response.result().youthPolicyList();
			policies.addAll(pageItems == null ? List.of() : pageItems);
			if (policies.size() >= totalCount) {
				break;
			}
		}
		if (totalCount == null || policies.size() != totalCount) {
			throw new IllegalStateException("온통청년 전량 수집 실패");
		}
		return policies.stream().map(this.parser::toParsedPolicy).map(this.normalizer::normalize).toList();
	}

	private YouthcenterPolicyListResponseDto parsePage(String json) {
		try {
			return this.parser.parsePolicyPage(json);
		}
		catch (IOException exception) {
			throw new IllegalStateException("온통청년 응답 파싱 실패");
		}
	}

	private String request(int page) {
		try {
			String body = this.restClient.get()
				.uri(uriBuilder -> uriBuilder.path(POLICY_PATH)
					.queryParam("apiKeyNm", this.apiKey)
					.queryParam("pageNum", page)
					.queryParam("pageSize", this.pageSize)
					.queryParam("rtnType", "json")
					.build())
				.retrieve()
				.body(String.class);
			if (body == null) {
				throw new IllegalStateException("온통청년 빈 응답");
			}
			return body;
		}
		catch (RestClientException exception) {
			// 인증키가 쿼리에 있으므로 예외 메시지나 요청 URI를 상위 로그로 전파하지 않음.
			throw new IllegalStateException("온통청년 요청 실패: " + exception.getClass().getSimpleName());
		}
	}

	private void requireApiKey() {
		if (this.apiKey == null || this.apiKey.isBlank()) {
			throw new IllegalStateException("온통청년 API 키가 설정되지 않음");
		}
	}

}
