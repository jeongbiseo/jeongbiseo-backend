package com.jeongbiseo.infra.client.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.gov24.Gov24Parser;
import com.jeongbiseo.infra.client.gov24.Gov24SubsidyNormalizer;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.youthcenter.YouthcenterParser;
import com.jeongbiseo.infra.client.youthcenter.YouthcenterSubsidyNormalizer;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyDto;

/**
 * gov24·온통청년 2종 소스의 회귀 스냅샷을 <b>실제 운영 파서와 정규화기를 그대로 태워</b> 공통
 * 타깃({@link NormalizedSubsidy}) 목록으로 싣는 테스트 전용 로더임.
 *
 * <p>
 * <b>여기서 파싱 규칙을 다시 짜지 않는 것이 핵심임.</b> 스냅샷 전용 읽기 경로를 새로 만들면 회귀 테스트가 프로덕션 경로를 검증하지 못함(각 소스
 * 파서가 실호출 응답과 스냅샷을 같은 메서드로 읽도록 이미 맞춰져 있음). 따라서 이 클래스는 파일을 읽어 소스별 {@code parseXxx} 더하기
 * {@code normalize}를 호출하는 배선만 함.
 *
 * <p>
 * lab 원본은 기업마당·K-Startup을 포함한 4종이었으나, DEC-10(개인 추천 모집단은 gov24·온통청년만 사용)에 따라 이번 이식에서
 * bizinfo·kstartup 파서를 가져오지 않아 2종으로 줄임. 네트워크를 쓰지 않음 — 스냅샷 파일만 읽음.
 */
final class AllSourcesSnapshotFixture {

	static final Path SNAPSHOT_DIR = Path.of("fixtures", "regression");

	static final int GOV24_COUNT = 1097;

	static final int YOUTHCENTER_COUNT = 1324;

	static final int TOTAL_COUNT = GOV24_COUNT + YOUTHCENTER_COUNT;

	private AllSourcesSnapshotFixture() {
	}

	/**
	 * gov24·온통청년 스냅샷 전량을 공통 타깃으로 싣음(소스 순서: gov24, 온통청년).
	 * @return 공통 타깃 레코드 목록
	 * @throws IOException 스냅샷 파일을 읽지 못하면 던짐
	 */
	static List<NormalizedSubsidy> loadAll() throws IOException {
		List<NormalizedSubsidy> all = new ArrayList<>();
		all.addAll(loadGov24());
		all.addAll(loadYouthcenter());
		return all;
	}

	static List<NormalizedSubsidy> loadGov24() throws IOException {
		Gov24Parser parser = new Gov24Parser();
		Gov24SubsidyNormalizer normalizer = new Gov24SubsidyNormalizer();
		Map<String, Gov24SupportConditionDto> conditionsById = new LinkedHashMap<>();
		String conditionsJson = read("gov24_supportConditions_snapshot.json");
		for (Gov24SupportConditionDto condition : parser.parseSupportConditions(conditionsJson)) {
			conditionsById.put(condition.serviceId(), condition);
		}
		// 사용자구분과 서비스분야는 serviceList에만 있음. detail 아이템에 두 필드가 없으므로 서비스ID로 join해 넘김.
		Map<String, String> userTypeById = new LinkedHashMap<>();
		Map<String, String> categoryRawTextById = new LinkedHashMap<>();
		for (Gov24ServiceItemDto item : parser.parseServiceItems(read("gov24_serviceList_snapshot.json"))) {
			userTypeById.put(item.serviceId(), item.userTypeText());
			categoryRawTextById.put(item.serviceId(), item.categoryRawText());
		}
		List<NormalizedSubsidy> result = new ArrayList<>();
		for (Gov24ServiceItemDto item : parser.parseServiceItems(read("gov24_serviceDetail_snapshot.json"))) {
			result.add(normalizer
				.normalize(parser.toParsedSubsidy(item, conditionsById, userTypeById, categoryRawTextById)));
		}
		return result;
	}

	static List<NormalizedSubsidy> loadYouthcenter() throws IOException {
		return loadYouthcenter("youthcenter_snapshot.json");
	}

	static List<NormalizedSubsidy> loadYouthcenterBusinessRegression() throws IOException {
		return loadYouthcenter("youthcenter_business_kstartup.json");
	}

	private static List<NormalizedSubsidy> loadYouthcenter(String fileName) throws IOException {
		YouthcenterParser parser = new YouthcenterParser();
		YouthcenterSubsidyNormalizer normalizer = new YouthcenterSubsidyNormalizer();
		List<NormalizedSubsidy> result = new ArrayList<>();
		for (YouthcenterPolicyDto dto : parser.parsePolicies(read(fileName))) {
			result.add(normalizer.normalize(parser.toParsedPolicy(dto)));
		}
		return result;
	}

	private static String read(String fileName) throws IOException {
		return Files.readString(SNAPSHOT_DIR.resolve(fileName), StandardCharsets.UTF_8);
	}

}
