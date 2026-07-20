package com.jeongbiseo.domain.subsidy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원금 상세에 붙는 AI 금액 해석임(등급 1).
 *
 * <p>
 * <b>이 record가 LLM을 모르는 것은 의도임.</b> 모델 이름·프롬프트 버전·검증 사유 같은 인프라 사정을 담지 않고, 화면이 쓸 값과 근거 문장만
 * 담음. 덕분에 {@code domain} 패키지가 LLM 구현을 import하지 않고도 AI 해석을 다룰 수 있음(등급 1~2 불변식).
 * </p>
 *
 * <p>
 * <b>이 값은 검증을 통과한 것만 옴.</b> 근거가 원문에 없거나, 금액이 근거와 어긋나거나, 대출 한도·사업예산·자부담을 지급액으로 판정한 결과는 저장
 * 단계에서 이미 걸러졌음. 그래도 <b>조건 오분류 같은 의미 오류는 결정론적으로 잡히지 않으므로</b> 화면은 반드시 {@code evidence}를 함께
 * 보여 사용자가 원문과 대조할 수 있게 할 것.
 * </p>
 *
 * @param amountValue 1회 지급액(원). 모르면 null
 * @param monthlyAmount 월 지급액(원). 월 지급이 아니면 null
 * @param durationMonths 지급 개월 수. 종신이거나 모르면 null
 * @param conditionExpression 금액이 달라지는 조건(예 "1인당"). 조건부가 아니면 null
 * @param evidence 판단 근거가 된 공고 원문 문장. 항상 채워짐
 */
public record AiExplanation(
		@Schema(description = "AI가 읽은 1회 지급액(원). 월 지급이거나 값을 특정하지 못하면 null임", nullable = true) Long amountValue,
		@Schema(description = "AI가 읽은 월 지급액(원). 월 지급이 아니면 null임", nullable = true) Long monthlyAmount,
		@Schema(description = "지급 개월 수. 종신 지급이거나 원문에 기간이 없으면 null임(임의로 환산하지 않음)",
				nullable = true) Integer durationMonths,
		@Schema(description = "금액이 달라지는 조건(예 1인당, 가구당). 조건부 차등이 아니면 null임", nullable = true,
				example = "1인당") String conditionExpression,
		@Schema(description = "위 값의 근거가 된 공고 원문 문장. 원문에 그대로 있는 문장만 실림",
				example = "월 20만원을 최대 12개월간 지원합니다.") String evidence) {

}
