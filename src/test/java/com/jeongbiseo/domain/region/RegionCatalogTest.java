package com.jeongbiseo.domain.region;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCatalogTest {

	@Test
	void sidoList는_등록된_시도를_담는다() {
		assertThat(RegionCatalog.sidoList()).contains("서울특별시");
	}

	@Test
	void sigunguListOf는_해당_시도의_시군구를_반환한다() {
		assertThat(RegionCatalog.sigunguListOf("서울특별시")).extracting(RegionCatalog.Sigungu::name)
			.containsExactlyInAnyOrder("강남구", "관악구");
	}

	@Test
	void sigunguListOf는_등록되지_않은_시도면_빈_목록을_반환한다() {
		assertThat(RegionCatalog.sigunguListOf("제주특별자치도")).isEmpty();
	}

	@Test
	void regionCodeOf는_시도와_시군구_이름을_매칭_지역코드로_변환한다() {
		assertThat(RegionCatalog.regionCodeOf("서울특별시", "강남구")).isEqualTo("11680");
	}

	@Test
	void regionCodeOf는_등록되지_않은_조합이면_null을_반환한다() {
		assertThat(RegionCatalog.regionCodeOf("서울특별시", "없는구")).isNull();
	}

}
