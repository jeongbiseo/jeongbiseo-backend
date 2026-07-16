package com.jeongbiseo.domain.region;

import java.util.List;
import java.util.Map;

/**
 * 거주지 시/도와 시군구 목록의 고정 참조 데이터임(getRegions 응답과 온보딩 제출 시 regionCode 파생에 함께 씀). 지역 코드 체계(법정동
 * 대 자체)는 매칭이 문자열 일치라 체계가 무엇이든 동작하므로, getRegions 예시 값(11620, 11680 형식)을 고정 목록으로 둠.
 */
// ponytail: 실제 행정구역 전체를 시딩하는 것은 범위를 넘어섬. 상한은 서울특별시 2개 구(강남구, 관악구)로 고정하며,
// 확대가 필요하면 이 Map에 항목을 추가하는 것이 대안임(별도 REGION 테이블은 만들지 않음).
public final class RegionCatalog {

	private static final Map<String, List<Sigungu>> SIDO_TO_SIGUNGU = Map.of("서울특별시",
			List.of(new Sigungu("11680", "강남구"), new Sigungu("11620", "관악구")));

	private RegionCatalog() {
	}

	/**
	 * 등록된 시 또는 도 목록을 반환함.
	 * @return 시 또는 도 이름 목록
	 */
	public static List<String> sidoList() {
		return List.copyOf(SIDO_TO_SIGUNGU.keySet());
	}

	/**
	 * 지정한 시 또는 도의 시군구 목록을 반환함. 등록되지 않은 시 또는 도면 빈 목록을 반환함.
	 * @param sido 시 또는 도 이름
	 * @return 시군구 목록
	 */
	public static List<Sigungu> sigunguListOf(String sido) {
		return SIDO_TO_SIGUNGU.getOrDefault(sido, List.of());
	}

	/**
	 * 시/도와 시군구 이름으로 매칭용 지역코드를 조회함. 등록되지 않은 조합이면 null을 반환함. 온보딩 제출 시 sido·sigungu를
	 * region_code로 변환하는 데 씀(데이터모델 온보딩 절).
	 * @param sido 시 또는 도 이름
	 * @param sigungu 시군구 이름
	 * @return 지역코드(없으면 null)
	 */
	public static String regionCodeOf(String sido, String sigungu) {
		return sigunguListOf(sido).stream()
			.filter(item -> item.name().equals(sigungu))
			.map(Sigungu::code)
			.findFirst()
			.orElse(null);
	}

	/**
	 * 시군구 코드와 이름임(값 객체).
	 *
	 * @param code 지역코드
	 * @param name 시군구 이름
	 */
	public record Sigungu(String code, String name) {

	}

}
