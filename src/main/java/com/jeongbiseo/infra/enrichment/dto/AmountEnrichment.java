package com.jeongbiseo.infra.enrichment.dto;

import com.jeongbiseo.infra.client.common.dto.AmountKind;

/**
 * LLM이 공고 한 건에서 구조화한 금액 정보임. 정규식 파서가 산정불가로 남긴 건에만 쓰며, 파서가 이미 값을 낸 건은 재판시키지 않음(판정원칙 3번 —
 * 검증 게이트가 LLM을 검수하는 구조가 맞고 그 반대가 아님).
 *
 * <p>
 * <b>이 값은 후보이지 판정이 아님.</b> 검증기를 통과하기 전에는 저장하지 않고, 저장한 뒤에도 등급 1에서는 상세 화면 표시에만 씀 — 추천
 * 제외·정렬·예상 총액 계산에 넣지 않음(배치 설계 6장 6번).
 * </p>
 *
 * <p>
 * {@code amountKind}는 파서와 같은 {@link AmountKind}를 재사용함. LLM 전용 어휘를 새로 만들면 파서 결과와 비교할 때 매핑
 * 계층이 하나 더 생기고, 같은 개념에 이름이 둘이 되어 문서·화면에서 갈림.
 * </p>
 *
 * <p>
 * <b>modelId와 promptVersion은 이 record에 없음.</b> 배치 설계 5장이 필수 출력으로 적은 항목이지만, 그것은 저장 레코드가
 * 갖춰야 할 필드라는 뜻이지 모델에게 자기 이름을 답하게 하라는 뜻이 아님. 모델은 자기 ID를 틀리게 답하는 일이 잦아(자기보고를 신원 근거로 쓰지 않음)
 * 호출한 코드가 아는 값을 그대로 채우는 쪽이 정확함. 저장 시점에 배치가 붙임(T4).
 * </p>
 *
 * @param amountKind 금액 표현의 성격. 판단이 서지 않으면 NONE이 아니라 abstained로 기권할 것
 * @param paymentPeriod 지급 주기
 * @param amountValue 1회 지급액(원 단위). 모르면 null
 * @param monthlyAmount 월 지급액(원 단위). paymentPeriod가 MONTHLY일 때만 채우고 그 외 null
 * @param durationMonths 지급 개월 수. 종신이거나 모르면 null
 * @param conditionExpression 조건별 차등의 조건 표현(예 "자녀 1명당"). CONDITIONAL이 아니면 null
 * @param evidence 위 판단의 근거가 되는 원문 문장. <b>반드시 원문에 그대로 있는 부분문자열이어야 함</b> — 재서술하면 검증기가 폐기함
 * @param abstained 판단을 포기했는지 여부. true면 나머지 값 필드는 신뢰하지 않음
 * @param abstainReason 기권 사유. abstained가 true일 때만 채움
 */
public record AmountEnrichment(AmountKind amountKind, PaymentPeriod paymentPeriod, Long amountValue, Long monthlyAmount,
		Integer durationMonths, String conditionExpression, String evidence, boolean abstained, String abstainReason) {

}
