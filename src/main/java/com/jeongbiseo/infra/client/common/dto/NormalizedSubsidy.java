package com.jeongbiseo.infra.client.common.dto;

import java.time.LocalDateTime;

import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.TargetAudience;

/**
 * <b>4종 소스(보조금24·온통청년·기업마당·K-Startup)가 공통으로 도달해야 하는 정규화 타깃임.</b> 소스별 파서는 각자의 원문 모양을 이 레코드
 * 하나로 수렴시키고, 이후 upsert·매칭·화면은 원문 모양을 두 번 다시 보지 않음. 이 레코드가 <b>유일한 합류 지점</b>이라 여기 없는 정보는
 * 하류에서 복구할 수 없음.
 *
 * <p>
 * <b>왜 소스별 결과를 이 하나로 모으는가.</b> gov24 파서만 있을 때는 {@code ParsedSubsidyResult}가 곧 결과였지만, 소스가
 * 4개가 되면 각자 다른 모양을 만들 위험이 실재함. 다만 소스별 원문 결과를 <b>없애지는 않음</b> —
 * {@code ParsedSubsidyResult}는 gov24의 원문 파싱 결과이자 데이터 품질 진단(JA 플래그 대 원문 대조 등)을 담고 있고 그건
 * gov24 밖에서 의미가 없음. 즉 <b>2층 구조</b>임: 1층은 소스 원문 결과(소스 고유 진단 포함), 2층이 이 공통 타깃(제품이 실제로 쓰는
 * 것). 변환은 {@code SubsidyNormalizer}가 담당함.
 *
 * <p>
 * <b>필드별 출처(provenance)를 어디까지 남기는가 — 3개 축만 남김.</b> 전 필드에 출처 래퍼를 씌우는 것은 과함. 출처가 필요한 조건은 "둘
 * 이상의 소스가 같은 의미를 <b>다른 신뢰도</b>로 주고, 그 차이에 따라 <b>하류 결정이 바뀐다</b>"인데, 이걸 만족하는 축은 셋뿐임.
 * <ul>
 * <li><b>지역</b> — {@link NormalizedRegion#scopeBasis()}·{@code confidence()}. 온통청년은 코드를
 * 선언하고 gov24는 소관기관명에서 유추함. 하드 지역 필터에 쓸 수 있는지가 갈림</li>
 * <li><b>마감</b> — {@link #deadlineBasis()}. K-Startup은 YYYYMMDD 전용 필드, gov24는 자유텍스트 파싱.
 * 캘린더 D-day를 그대로 믿을지가 갈림</li>
 * <li><b>금액</b> — {@link ParsedAmount#parseStatus()}. 예산·자부담을 걸러냈는지, 아예 못 찾았는지가 갈림(예상총액
 * 자동 채움 대상 판정)</li>
 * </ul>
 * 나머지 필드(지원금명·기관·설명·URL·갱신시각)는 4종 소스가 전부 <b>선언된 필드로 직접</b> 주므로 레코드 단위
 * {@link #source()}만으로 충분함. 자격조건은 값 대신 {@link EligibilitySignal} 3분류가 이미 근거 역할을 함.
 *
 * <p>
 * <b>수혜 단위 축(개인 대 사업자)을 2026-07-13에 승격함 — {@link #targetAudience()}.</b> 이전 회차는 이 축이 없어
 * SINGLE + CASH 합산 후보에 기업 지원금(스포츠 액셀러레이팅 5,500만원 등)이 섞였음. 금액 표현으로는 갈리지 않아 금액 파서로 풀 수
 * 없었고(마커를 넓히면 빈집재생 3천만원 같은 진짜 개인 지급액이 함께 죽음), <b>gov24 {@code serviceList}의 {@code 사용자구분}
 * 필드를 발견</b>하면서 풀렸음. 그 필드는 {@code serviceDetail}에 없어서 상세만 읽던 파서가 놓치고 있었음.
 *
 * <p>
 * <b>제품 타깃 스코프 축 — {@link #occupationRestriction()}.</b> 위 축과 <b>별개</b>임. 농업인 전용 지원금은 개인이
 * 받는 돈이라 {@code targetAudience}는 PERSONAL이 맞지만, 우리 타깃(20~30대 청년·사회초년생)의 범위 밖이라 추천 스코프에서
 * 자름. 두 축을 하나로 합치면 "농업인 전용은 개인 대상이 아니다"라는 거짓 명제를 데이터 모델에 박게 됨.
 *
 * @param source 수집 출처(레코드 단위 provenance의 1차 축)
 * @param externalId 소스 내 공고 고유 ID. {@code (source, externalId)}가 upsert 키임(데이터모델 4.1)
 * @param name 지원금명
 * @param agency 소관·주관 기관명
 * @param description 지원내용·상세설명 원문
 * @param eligibilityText 자격조건 원문(사람이 읽는 문장). 구조화 신호는 {@link #eligibility()}에 따로 있음
 * @param categoryRawText 소스 자체 분류 원문(gov24 서비스분야, 온통청년 {@code lclsfNm}·{@code mclsfNm},
 * 기업마당 지원분야, K-Startup {@code supt_biz_clsfc}). <b>{@code SubsidyCategory} 7종으로 매핑하지 않고
 * 원문 그대로 둠</b> — 4종 소스가 전부 자체 taxonomy라 1대1로 안 맞고, 매핑표는 회의에서 정할 사항임. 소스별 파서가 각자 매핑표를 지어내면
 * 카테고리가 소스마다 어긋나므로 <b>여기서 매핑을 금지하는 것 자체가 설계임</b>
 * @param paymentType 지급유형. 예상총액(AMT-622) 합산 대상은 CASH뿐이고 UNKNOWN은 합산에서 제외함(데이터모델 4장)
 * @param paymentTypeRawText 지급유형 원문(gov24 지원유형 45종 enum 등). 매핑 근거 추적용
 * @param amount 금액 파싱 결과. 소스와 무관하게 <b>같은 파서를 재사용함</b>(재사용 판단은 {@code SubsidyNormalizer}
 * Javadoc 참조)
 * @param deadline 신청기한 판정 결과(종류·시작일·종료일·원문). 신청 시작일은 별도 필드가 아니라 이 안의
 * {@link ParsedDeadline#startDate()}임
 * @param deadlineBasis 마감 판정의 근거(구조화 필드 대 자유텍스트 파싱)
 * @param region 지역 정보(코드 목록·명칭·근거·신뢰도)
 * @param eligibility 자격조건 4축(연령·소득·가구·고용)과 축별 신호
 * @param applicationMethod 신청 채널 플래그. false는 "불가"가 아니라 "근거 없음"임
 * @param applicationUrl 온라인 신청 URL(gov24 온라인신청사이트URL 18.23%, 기업마당
 * {@code rceptEngnHmpgUrl}). 없으면 null
 * @param referenceUrl 공고·상세 페이지 URL(기업마당 {@code pblancUrl}, K-Startup
 * {@code detl_pg_url}). 신청 URL과 구분함 — 전자는 "신청하는 곳", 후자는 "읽는 곳"이라 화면 동작이 다름. 없으면 null
 * @param requiredDocumentsText 구비서류 원문. gov24는 채움 100%이나 41.66%가 "해당없음" 리터럴이라 <b>null로
 * 정규화한 뒤</b> 넣을 것(원문 "해당없음"을 그대로 화면에 내지 말 것)
 * @param dataUpdatedAt 소스가 알려주는 데이터 갱신 시각. 48시간 초과 시 추천 하향 판정에 씀(REC-311). 소스가 안 주면
 * null이고, 이때는 수집 시각({@code fetched_at})으로 보조 판정함
 * @param targetAudience 수혜 주체(개인 대 사업자). 추천에서 BUSINESS를 제외하고, 예상 총액은 PERSONAL만 합산함.
 * <b>제외는 결과에서 0건이라는 뜻이지 레코드를 지우라는 뜻이 아님</b>
 * @param occupationRestriction 직업군 스코프. 1차산업 전용이면 제품 타깃 밖이라 추천에서 제외함. gov24 외 소스는 판정 근거가
 * 없어 전부 {@link OccupationRestriction#NONE}임
 */
public record NormalizedSubsidy(SubsidySource source, String externalId, String name, String agency, String description,
		String eligibilityText, String categoryRawText, PaymentType paymentType, String paymentTypeRawText,
		ParsedAmount amount, ParsedDeadline deadline, DeadlineBasis deadlineBasis, NormalizedRegion region,
		NormalizedEligibility eligibility, ApplicationMethodFlags applicationMethod, String applicationUrl,
		String referenceUrl, String requiredDocumentsText, LocalDateTime dataUpdatedAt, TargetAudience targetAudience,
		OccupationRestriction occupationRestriction) {

}
