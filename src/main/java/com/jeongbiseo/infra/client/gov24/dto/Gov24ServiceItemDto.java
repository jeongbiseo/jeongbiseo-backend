package com.jeongbiseo.infra.client.gov24.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보조금24 serviceList와 serviceDetail 응답 공통 항목 DTO임(swagger_gov24.json definitions의
 * serviceList_model, serviceDetail_model 교집합). 두 모델의 프로퍼티를 대조한 결과 아래 11필드는 이름까지 동일하게
 * 공유되고, {@link #externalUrl()}·{@link #requiredDocumentsText()}만 serviceDetail
 * 전용임(serviceList로 파싱하면 null로 남음 — Jackson이 없는 키를 null로 채우는 동작을 그대로 씀). serviceList가 쓰는
 * {@link #categoryRawText()}·{@link #userTypeText()}는 serviceList 전용임. 이름이 다른 유사 필드(접수기관
 * 대 접수기관명, 전화문의 대 문의처)는 이번 매핑 범위 밖이라 다루지 않음(외부API-부족분-조사-2026-07-12.md 3장 G1·G4).
 *
 * @param serviceId 서비스ID(gov24 원문 식별자, 앞자리 0 보존을 위해 String)
 * @param serviceName 서비스명
 * @param applicationDeadlineText 신청기한 원문(자유텍스트, 파싱은 Gov24Parser가 담당)
 * @param paymentTypeText 지원유형 원문(예 "현금", "현금(감면)", "기타") — 채움 100%, distinct 45(스냅샷
 * n=1097 실측)
 * @param agency 소관기관명 — 채움 100%
 * @param description 지원내용 원문(자유텍스트, 금액이 섞여 있음) — 채움 100%
 * @param eligibilitySummaryText 지원대상 원문 — 채움 100%. Gov24Parser가 selectionCriteriaText와 합쳐
 * eligibilityText를 만듦
 * @param selectionCriteriaText 선정기준 원문 — 채움 9.75%(스냅샷 n=1097 실측, 나머지는 지원대상에 조건이 다 들어 있는
 * 경우)
 * @param externalUrl 온라인신청사이트URL — 채움 18.23%(스냅샷 n=1097 실측). serviceDetail 전용 필드라
 * serviceList만으로는 항상 null
 * @param dataUpdatedAtText 수정일시 원문 — serviceDetail 실측(1097/1097)은 "YYYY-MM-DD" 10자리 고정이나,
 * 로컬 픽스처 sample_serviceList.json은 "YYYYMMDDHHmmss" 14자리를 씀(엔드포인트별 표기 차이로 추정, 미확인) —
 * Gov24Parser가 두 형식을 모두 시도함
 * @param requiredDocumentsText 구비서류 원문 — 채움 100%이나 41.66%가 "해당없음"(스냅샷 n=1097 실측).
 * serviceDetail 전용 필드
 * @param applicationMethodText 신청방법 원문(자유텍스트) — 채움 100%, Gov24Parser가 키워드로 플래그 분류함
 * @param categoryRawText 서비스분야 원문. serviceList 전용 필드라 serviceDetail만으로는 null임
 * @param userTypeText 사용자구분 원문. serviceList 전용 필드라 serviceDetail만으로는 null임
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Gov24ServiceItemDto(@JsonProperty("서비스ID") String serviceId, @JsonProperty("서비스명") String serviceName,
		@JsonProperty("신청기한") String applicationDeadlineText, @JsonProperty("지원유형") String paymentTypeText,
		@JsonProperty("소관기관명") String agency, @JsonProperty("지원내용") String description,
		@JsonProperty("지원대상") String eligibilitySummaryText, @JsonProperty("선정기준") String selectionCriteriaText,
		@JsonProperty("온라인신청사이트URL") String externalUrl, @JsonProperty("수정일시") String dataUpdatedAtText,
		@JsonProperty("구비서류") String requiredDocumentsText, @JsonProperty("신청방법") String applicationMethodText,
		// 소스 자체 분류 원문임. SubsidyCategory 매핑 없이 serviceDetail과 서비스ID로 join해 보존함.
		@JsonProperty("서비스분야") String categoryRawText,
		// 지원 대상이 개인인지 사업자인지임(개인 / 가구 / 소상공인 / 법인·시설·단체와 그 "||" 조합).
		// **serviceList에만 있고 serviceDetail에는 없음** — 그래서 detail만 읽으면 항상 null임. 두 오퍼레이션을
		// 서비스ID로 join해야 채워지며, 그 join은 Gov24ServiceListSnapshotJoinTest가 고정함.
		@JsonProperty("사용자구분") String userTypeText) {

}
