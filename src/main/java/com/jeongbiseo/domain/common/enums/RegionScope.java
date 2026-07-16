package com.jeongbiseo.domain.common.enums;

/**
 * 지원금의 지역 적용 범위임. NATIONWIDE는 매칭에서 탈락시키지 않음. 단 적재 시 다중 지역(2개 이상)이 단일 컬럼에 안 담겨 NATIONWIDE로
 * 붕괴된 산물일 수 있어, region_codes가 채워져 있으면 추천 지역 강등 판정에 그 목록을 씀 (09-region-demotion,
 * RecommendationPolicy.regionDemoted). 진짜 전국은 region_codes가 비어 강등되지 않음.
 */
public enum RegionScope {

	NATIONWIDE, REGIONAL

}
