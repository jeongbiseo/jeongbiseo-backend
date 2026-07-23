package com.jeongbiseo.domain.region;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCatalogTest {

	@Test
	void sidoList는_공식_코드순으로_전국_16개_시도를_반환한다() {
		assertThat(RegionCatalog.sidoList()).containsExactly("서울특별시", "전남광주통합특별시", "부산광역시", "대구광역시", "인천광역시", "대전광역시",
				"울산광역시", "세종특별자치시", "경기도", "충청북도", "충청남도", "경상북도", "경상남도", "제주특별자치도", "강원특별자치도", "전북특별자치도");
	}

	@Test
	void sigunguListOf는_서울_25개_자치구를_코드순으로_반환한다() {
		assertThat(RegionCatalog.sigunguListOf("서울특별시")).extracting(RegionCatalog.Sigungu::name)
			.hasSize(25)
			.startsWith("종로구", "중구", "용산구")
			.endsWith("강남구", "송파구", "강동구");
	}

	@Test
	void sigunguListOf는_비서울_시군구와_세종_자체코드를_반환한다() {
		assertThat(RegionCatalog.sigunguListOf("제주특별자치도")).containsExactly(new RegionCatalog.Sigungu("50110", "제주시"),
				new RegionCatalog.Sigungu("50130", "서귀포시"));
		assertThat(RegionCatalog.sigunguListOf("세종특별자치시"))
			.containsExactly(new RegionCatalog.Sigungu("36110", "세종특별자치시"));
	}

	@Test
	void sigunguListOf는_등록되지_않은_시도면_빈_목록을_반환한다() {
		assertThat(RegionCatalog.sigunguListOf("없는시도")).isEmpty();
	}

	@Test
	void 전체_지역코드는_269개이며_5자리이고_중복되지_않는다() {
		List<String> codes = RegionCatalog.sidoList()
			.stream()
			.flatMap(sido -> RegionCatalog.sigunguListOf(sido).stream())
			.map(RegionCatalog.Sigungu::code)
			.toList();

		assertThat(codes).hasSize(269).allMatch(code -> code.matches("\\d{5}")).doesNotHaveDuplicates();
	}

	@Test
	void regionCodeOf는_시도와_시군구_이름을_매칭_지역코드로_변환한다() {
		assertThat(RegionCatalog.regionCodeOf("서울특별시", "강남구")).isEqualTo("11680");
		assertThat(RegionCatalog.regionCodeOf("제주특별자치도", "서귀포시")).isEqualTo("50130");
		assertThat(RegionCatalog.regionCodeOf("전남광주통합특별시", "목포시")).isEqualTo("12110");
	}

	@Test
	void regionCodeOf는_등록되지_않은_조합이면_null을_반환한다() {
		assertThat(RegionCatalog.regionCodeOf("서울특별시", "없는구")).isNull();
	}

}
