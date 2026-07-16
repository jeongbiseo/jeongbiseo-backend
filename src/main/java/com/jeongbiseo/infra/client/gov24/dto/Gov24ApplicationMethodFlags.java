package com.jeongbiseo.infra.client.gov24.dto;

/**
 * 신청방법 자유텍스트를 키워드로 분류한 플래그 조합임(외부API-부족분-조사-2026-07-12.md 3장 G4 권고 — "apply_method를 enum
 * 단일값이 아니라 플래그로 두고, 근거가 없는 소스는 NULL로 남긴다"). 한 지원금이 방문과 온라인을 동시에 지원하는 경우가 실제로 있어 (예 "읍면동
 * 주민센터 방문 또는 인터넷을 이용하여 온라인 신청") enum 단일값으로는 표현할 수 없음.
 *
 * <p>
 * 스키마(데이터모델.md 4장 Subsidy)에는 이 정보를 담을 컬럼이 없음 — DB 반영 여부는 회의 결정 대상이라 이 레코드는 파서 결과
 * ({@link ParsedSubsidyResult})에만 담고, 엔티티 매핑은 하지 않음.
 *
 * @param online 온라인 신청 가능("온라인"·"인터넷" 키워드)
 * @param visit 방문 신청 가능("방문" 키워드)
 * @param mail 우편 신청 가능("우편" 키워드)
 * @param fax 팩스 신청 가능("팩스"·"FAX" 계열 키워드)
 * @param phone 전화 신청 가능("전화" 키워드)
 * @param autoProvided 별도 신청 없이 자격대상자에게 자동 제공됨("신청없이"·"자동적으로 제공" 등 키워드)
 * @param unclassified 위 6개 키워드 어디에도 걸리지 않아 분류 실패함(원문 자체는 있음)
 */
public record Gov24ApplicationMethodFlags(boolean online, boolean visit, boolean mail, boolean fax, boolean phone,
		boolean autoProvided, boolean unclassified) {

}
