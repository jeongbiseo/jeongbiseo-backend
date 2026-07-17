package com.jeongbiseo.infra.client.common.dto;

/**
 * {@link ParsedDeadline}의 값이 어디서 왔는지임. {@link DeadlineKind}가 "무엇인가"(의미)를 말한다면 이 값은 "얼마나
 * 믿을 수 있는가"(근거)를 말함.
 *
 * <p>
 * 이 축을 분리한 이유는 <b>같은 DeadlineKind.DATE_RANGE라도 소스에 따라 신뢰도가 완전히 다르기 때문</b>임. K-Startup의
 * {@code pbanc_rcpt_end_dt}는 YYYYMMDD 전용 필드라 800건 표본에서 이형식·센티널이 0건인 반면, gov24의 같은 판정은
 * 자유텍스트 정규식 매칭 결과라 오독 가능성이 늘 남아 있음(조사 리포트 3장 G3). 캘린더 D-day 표시(CAL-511)를 그대로 믿고 낼지, "확인
 * 필요" 배지를 달지가 이 값 하나로 갈림.
 */
public enum DeadlineBasis {

	// 소스가 날짜를 전용 구조화 필드로 선언함 — K-Startup pbanc_rcpt_bgng_dt·pbanc_rcpt_end_dt(YYYYMMDD),
	// 온통청년 aplyPrdSeCd(상태 코드) 및 aplyYmd. 파싱이 아니라 매핑이므로 오독 위험이 사실상 없음
	DECLARED_FIELD,

	// 자유텍스트에서 파싱·분류함 — gov24 신청기한(전면 자유서술), 기업마당 reqstBeginEndDe의 자유텍스트
	// 12.8%(예산 소진시까지 등 8종 폐쇄 어휘). 결과가 맞을 수도 있지만 근거는 추론임
	PARSED_FROM_TEXT,

	// 마감 관련 원문 자체가 비어 있음 — 판정 근거가 아예 없다는 뜻이라 "파싱했는데 모르겠음"(PARSED_FROM_TEXT
	// 더하기 DeadlineKind.UNKNOWN)과 구분함(함정 1 "필드가 있는 것과 값이 채워지는 것은 다르다")
	NOT_APPLICABLE

}
