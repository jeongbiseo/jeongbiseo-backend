package com.jeongbiseo.domain.subsidy.ingestion;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/**
 * 소스별 원문 분류를 화면 필터용 {@link SubsidyCategory} 7종으로 매핑함. DB·스프링 비의존 순수 정적 매퍼라 적재 시점에
 * {@code SubsidyIngestionAdapter}가 부름.
 *
 * <p>
 * <b>매핑 근거 정본은 {@code docs/research/카테고리-원문분류-매핑표-초안-2026-07-21.md}임.</b> gov24 원문 분류는 전수
 * 10,979건(서비스분야 10종)을 실측해 확정함.
 *
 * <p>
 * <b>확정 결정.</b> (나) YOUTH 부여 기준은 <b>Y-A(소스 기준)</b>임 — 온통청년은 전건이 청년 대상이라 원문 분류와 무관하게
 * YOUTH로 둠(팀 확정). 그 결과 온통청년의 주거·교육·고용은 도메인 칩이 아니라 청년 칩에 들어가고, 도메인 6칩은 gov24 서비스분야로만 채움.
 * (가) 원문 null·미매핑 → ETC(누락이 최대 죄악). (다) gov24 {@code 고용·창업}은 고용과 창업이 한 분류로 묶여 분리 불가라
 * EMPLOYMENT로 둠 — Y-A에서는 온통청년 창업도 YOUTH로 가므로 <b>STARTUP 칩은 비어 있음</b>(팀 인지). (라) gov24
 * {@code 문화·환경} → WELFARE.
 */
public final class CategoryMapper {

	private static final Logger log = LoggerFactory.getLogger(CategoryMapper.class);

	// gov24 서비스분야 10종 → enum(전수 10,979건 실측, null 0). 결정표라 exact match이고, 모르는 값은 ETC로 두고
	// log.warn으로 남김.
	private static final Map<String, SubsidyCategory> GOV24_SERVICE_FIELD = Map.ofEntries(
			Map.entry("주거·자립", SubsidyCategory.HOUSING), Map.entry("보육·교육", SubsidyCategory.EDUCATION),
			Map.entry("생활안정", SubsidyCategory.WELFARE), Map.entry("보건·의료", SubsidyCategory.WELFARE),
			Map.entry("임신·출산", SubsidyCategory.WELFARE), Map.entry("보호·돌봄", SubsidyCategory.WELFARE),
			Map.entry("문화·환경", SubsidyCategory.WELFARE), Map.entry("고용·창업", SubsidyCategory.EMPLOYMENT),
			Map.entry("농림축산어업", SubsidyCategory.ETC), Map.entry("행정·안전", SubsidyCategory.ETC));

	private CategoryMapper() {
	}

	/**
	 * 소스와 원문 분류로 카테고리를 판정함.
	 * @param source 수집 출처
	 * @param categoryRawText 소스 원문 분류(gov24 서비스분야). 온통청년은 원문과 무관하게 YOUTH라 참조하지 않음
	 * @return 매핑된 카테고리
	 */
	public static SubsidyCategory map(SubsidySource source, String categoryRawText) {
		// Y-A: 온통청년은 전건 청년 대상이라 원문 분류·null과 무관하게 YOUTH.
		if (source == SubsidySource.YOUTHCENTER) {
			return SubsidyCategory.YOUTH;
		}
		// gov24만 서비스분야로 도메인 매핑함. bizinfo·kstartup(기업 대상)·seed(수기)는 추천 모집단 밖이라 방어적
		// ETC(대량·예상된
		// 경우라 로그로 남기지 않음).
		if (source != SubsidySource.GOV24) {
			return SubsidyCategory.ETC;
		}
		if (categoryRawText == null || categoryRawText.isBlank()) {
			// 실측상 gov24 서비스분야 null·공백은 0건이라, 나타나면 소스 변화 신호로 남김.
			log.warn("gov24 서비스분야가 null·공백이라 ETC로 처리함");
			return SubsidyCategory.ETC;
		}
		return mapGov24(categoryRawText.strip());
	}

	private static SubsidyCategory mapGov24(String raw) {
		SubsidyCategory mapped = GOV24_SERVICE_FIELD.get(raw);
		if (mapped == null) {
			log.warn("gov24 서비스분야 매핑표에 없는 신규 값 감지, ETC로 처리함(원문: \"{}\")", raw);
			return SubsidyCategory.ETC;
		}
		return mapped;
	}

}
