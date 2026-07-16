package com.jeongbiseo.infra.client.gov24;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jeongbiseo.infra.client.common.SubsidySourceCollector;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceListResponseDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionsResponseDto;

/** gov24 3개 오퍼레이션 전량을 서비스ID로 결합해 정규화하는 운영 수집기임. */
@Order(1)
@Component
public final class Gov24SubsidyCollector implements SubsidySourceCollector {

	private static final Logger log = LoggerFactory.getLogger(Gov24SubsidyCollector.class);

	private static final String BASE_URL = "https://api.odcloud.kr/api/gov24/v3";

	private static final String SERVICE_LIST = "serviceList";

	private static final String SERVICE_DETAIL = "serviceDetail";

	private static final String SUPPORT_CONDITIONS = "supportConditions";

	private final RestClient restClient;

	private final String serviceKey;

	private final int pageSize;

	private final int maxPages;

	private final Gov24Parser parser;

	private final Gov24SubsidyNormalizer normalizer;

	@Autowired
	public Gov24SubsidyCollector(RestClient.Builder builder,
			@Value("${app.ingestion.gov24.service-key:}") String serviceKey,
			@Value("${app.ingestion.gov24.page-size:1000}") int pageSize,
			@Value("${app.ingestion.gov24.max-pages:50}") int maxPages) {
		this(builder.clone().baseUrl(BASE_URL).build(), serviceKey, pageSize, maxPages);
	}

	Gov24SubsidyCollector(RestClient restClient, String serviceKey, int pageSize, int maxPages) {
		this.restClient = restClient;
		this.serviceKey = serviceKey;
		this.pageSize = pageSize;
		this.maxPages = maxPages;
		this.parser = new Gov24Parser();
		this.normalizer = new Gov24SubsidyNormalizer();
	}

	@Override
	public SubsidySource source() {
		return SubsidySource.GOV24;
	}

	@Override
	public List<NormalizedSubsidy> collect() {
		requireServiceKey();
		List<Gov24ServiceItemDto> serviceList = fetchAll(SERVICE_LIST, this::parseServicePage,
				Gov24ServiceItemDto::serviceId);
		List<Gov24ServiceItemDto> details = fetchAll(SERVICE_DETAIL, this::parseServicePage,
				Gov24ServiceItemDto::serviceId);
		List<Gov24SupportConditionDto> conditions = fetchAll(SUPPORT_CONDITIONS, this::parseSupportConditionsPage,
				Gov24SupportConditionDto::serviceId);
		reportJoinDifferences(serviceList, details, conditions);

		Map<String, String> userTypeById = new LinkedHashMap<>();
		Map<String, String> categoryRawTextById = new LinkedHashMap<>();
		for (Gov24ServiceItemDto item : serviceList) {
			userTypeById.put(item.serviceId(), item.userTypeText());
			categoryRawTextById.put(item.serviceId(), item.categoryRawText());
		}
		Map<String, Gov24SupportConditionDto> conditionsById = new LinkedHashMap<>();
		for (Gov24SupportConditionDto condition : conditions) {
			conditionsById.put(condition.serviceId(), condition);
		}

		List<NormalizedSubsidy> normalized = new ArrayList<>(details.size());
		for (Gov24ServiceItemDto detail : details) {
			if (!userTypeById.containsKey(detail.serviceId())) {
				throw new IllegalStateException("gov24 serviceList 조인이 불완전함");
			}
			normalized.add(this.normalizer
				.normalize(this.parser.toParsedSubsidy(detail, conditionsById, userTypeById, categoryRawTextById)));
		}
		return List.copyOf(normalized);
	}

	private Gov24Page<Gov24ServiceItemDto> parseServicePage(String json) throws IOException {
		Gov24ServiceListResponseDto response = this.parser.parseServicePage(json);
		return new Gov24Page<>(response.totalCount(), nullToEmpty(response.data()));
	}

	private Gov24Page<Gov24SupportConditionDto> parseSupportConditionsPage(String json) throws IOException {
		Gov24SupportConditionsResponseDto response = this.parser.parseSupportConditionsPage(json);
		return new Gov24Page<>(response.totalCount(), nullToEmpty(response.data()));
	}

	private <T> List<T> fetchAll(String operation, PageParser<T> pageParser, Function<T, String> idExtractor) {
		List<T> all = new ArrayList<>();
		Integer totalCount = null;
		for (int page = 1; page <= this.maxPages; page++) {
			Gov24Page<T> response;
			try {
				response = pageParser.parse(request(operation, page));
			}
			catch (IOException exception) {
				throw new IllegalStateException("gov24 응답 파싱 실패");
			}
			if (response.totalCount() == null) {
				throw new IllegalStateException("gov24 전체 건수 누락");
			}
			if (totalCount == null) {
				totalCount = response.totalCount();
			}
			all.addAll(response.items());
			if (all.size() >= totalCount) {
				break;
			}
		}
		if (totalCount == null || all.size() != totalCount) {
			throw new IllegalStateException("gov24 전량 수집 실패");
		}
		Set<String> uniqueIds = new LinkedHashSet<>();
		for (T item : all) {
			uniqueIds.add(idExtractor.apply(item));
		}
		if (uniqueIds.size() != totalCount) {
			log.error("gov24 고유 ID 완전성 불일치: operation={}, rows={}, uniqueIds={}, totalCount={}, duplicateRows={}",
					operation, all.size(), uniqueIds.size(), totalCount, all.size() - uniqueIds.size());
		}
		return all;
	}

	private static void reportJoinDifferences(List<Gov24ServiceItemDto> serviceList, List<Gov24ServiceItemDto> details,
			List<Gov24SupportConditionDto> conditions) {
		Set<String> listIds = serviceIds(serviceList);
		Set<String> detailIds = serviceIds(details);
		Set<String> conditionIds = conditionIds(conditions);
		int listMinusDetail = differenceSize(listIds, detailIds);
		int detailMinusList = differenceSize(detailIds, listIds);
		int listMinusConditions = differenceSize(listIds, conditionIds);

		log.info("gov24 ID 집합 차이: listMinusDetail={}, detailMinusList={}, listMinusConditions={}", listMinusDetail,
				detailMinusList, listMinusConditions);
		if (listMinusDetail > 0) {
			log.error("gov24 serviceList 항목이 serviceDetail에 없어 결과에서 제외됨: count={}", listMinusDetail);
		}
		if (detailMinusList > 0) {
			log.error("gov24 serviceDetail 항목이 serviceList에 없어 조인할 수 없음: count={}", detailMinusList);
		}
		if (listMinusConditions > 0) {
			log.warn("gov24 serviceList 항목의 supportConditions가 없어 자격조건 근거 없이 통과함: count={}", listMinusConditions);
		}
	}

	private static Set<String> serviceIds(List<Gov24ServiceItemDto> items) {
		Set<String> ids = new LinkedHashSet<>();
		for (Gov24ServiceItemDto item : items) {
			ids.add(item.serviceId());
		}
		return ids;
	}

	private static Set<String> conditionIds(List<Gov24SupportConditionDto> conditions) {
		Set<String> ids = new LinkedHashSet<>();
		for (Gov24SupportConditionDto condition : conditions) {
			ids.add(condition.serviceId());
		}
		return ids;
	}

	private static int differenceSize(Set<String> left, Set<String> right) {
		int count = 0;
		for (String id : left) {
			if (!right.contains(id)) {
				count++;
			}
		}
		return count;
	}

	private String request(String operation, int page) {
		try {
			String body = this.restClient.get()
				.uri(uriBuilder -> uriBuilder.pathSegment(operation)
					.queryParam("page", page)
					.queryParam("perPage", this.pageSize)
					.queryParam("serviceKey", this.serviceKey)
					.build())
				.retrieve()
				.body(String.class);
			if (body == null) {
				throw new IllegalStateException("gov24 빈 응답");
			}
			return body;
		}
		catch (RestClientException exception) {
			// 인증키가 쿼리에 있으므로 예외 메시지나 요청 URI를 상위 로그로 전파하지 않음.
			throw new IllegalStateException("gov24 요청 실패: " + exception.getClass().getSimpleName());
		}
	}

	private void requireServiceKey() {
		if (this.serviceKey == null || this.serviceKey.isBlank()) {
			throw new IllegalStateException("gov24 API 키가 설정되지 않음");
		}
	}

	private static <T> List<T> nullToEmpty(List<T> items) {
		return items == null ? List.of() : items;
	}

	@FunctionalInterface
	private interface PageParser<T> {

		Gov24Page<T> parse(String json) throws IOException;

	}

	private record Gov24Page<T>(Integer totalCount, List<T> items) {

	}

}
