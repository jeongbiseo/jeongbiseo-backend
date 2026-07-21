package com.jeongbiseo.domain.subsidy.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/**
 * {@link CategoryMapper} 매핑 고정. gov24 서비스분야 10종(전수 10,979건)은 결정표로, 온통청년은 Y-A라 원문과 무관하게
 * YOUTH임을 고정함. 값 정본은 {@code docs/research/카테고리-원문분류-매핑표-초안-2026-07-21.md}.
 */
class CategoryMapperTest {

	// gov24 서비스분야 10종(exact 결정표). 콤마가 원문에 없어도 구분자는 파이프로 통일함.
	@ParameterizedTest(name = "[{index}] {0} \"{1}\" -> {2}")
	@CsvSource(delimiter = '|', textBlock = """
			GOV24 | 주거·자립   | HOUSING
			GOV24 | 보육·교육   | EDUCATION
			GOV24 | 생활안정    | WELFARE
			GOV24 | 보건·의료   | WELFARE
			GOV24 | 임신·출산   | WELFARE
			GOV24 | 보호·돌봄   | WELFARE
			GOV24 | 문화·환경   | WELFARE
			GOV24 | 고용·창업   | EMPLOYMENT
			GOV24 | 농림축산어업 | ETC
			GOV24 | 행정·안전   | ETC
			""")
	void mapsGov24ServiceField(SubsidySource source, String raw, SubsidyCategory expected) {
		assertThat(CategoryMapper.map(source, raw)).isEqualTo(expected);
	}

	// Y-A: 온통청년은 원문 분류가 무엇이든(도메인처럼 보여도), null·공백이어도 YOUTH.
	@Test
	void youthcenterAlwaysMapsToYouth() {
		assertThat(CategoryMapper.map(SubsidySource.YOUTHCENTER, "일자리 > 창업")).isEqualTo(SubsidyCategory.YOUTH);
		assertThat(CategoryMapper.map(SubsidySource.YOUTHCENTER, "주거 > 주택 및 거주지")).isEqualTo(SubsidyCategory.YOUTH);
		assertThat(CategoryMapper.map(SubsidySource.YOUTHCENTER, "복지문화 > 건강")).isEqualTo(SubsidyCategory.YOUTH);
		assertThat(CategoryMapper.map(SubsidySource.YOUTHCENTER, null)).isEqualTo(SubsidyCategory.YOUTH);
		assertThat(CategoryMapper.map(SubsidySource.YOUTHCENTER, "   ")).isEqualTo(SubsidyCategory.YOUTH);
	}

	// gov24 원문 앞뒤 공백은 strip 후 매칭됨(회귀 방지).
	@Test
	void gov24RawTextIsStripped() {
		assertThat(CategoryMapper.map(SubsidySource.GOV24, "  생활안정  ")).isEqualTo(SubsidyCategory.WELFARE);
	}

	// (가) gov24 null·공백·미매핑은 지어내지 않고 ETC(로그로 드러냄).
	@Test
	void gov24BlankOrUnknownMapsToEtc() {
		assertThat(CategoryMapper.map(SubsidySource.GOV24, null)).isEqualTo(SubsidyCategory.ETC);
		assertThat(CategoryMapper.map(SubsidySource.GOV24, "   ")).isEqualTo(SubsidyCategory.ETC);
		assertThat(CategoryMapper.map(SubsidySource.GOV24, "미확인분야")).isEqualTo(SubsidyCategory.ETC);
	}

	// 추천 모집단 밖 소스(bizinfo·kstartup)와 수기 시드는 방어적으로 ETC.
	@Test
	void nonPopulationSourcesMapToEtc() {
		assertThat(CategoryMapper.map(SubsidySource.BIZINFO, "경영||컨설팅")).isEqualTo(SubsidyCategory.ETC);
		assertThat(CategoryMapper.map(SubsidySource.KSTARTUP, "사업화")).isEqualTo(SubsidyCategory.ETC);
		assertThat(CategoryMapper.map(SubsidySource.SEED, null)).isEqualTo(SubsidyCategory.ETC);
	}

	// 어떤 소스로도 예외 없이 반드시 카테고리를 돌려줌(적재가 카테고리 때문에 깨지지 않음).
	@ParameterizedTest
	@EnumSource(SubsidySource.class)
	void neverThrowsAndAlwaysReturnsCategory(SubsidySource source) {
		assertThat(CategoryMapper.map(source, "고용·창업")).isInstanceOf(SubsidyCategory.class);
		assertThat(CategoryMapper.map(source, null)).isNotNull();
	}

}
