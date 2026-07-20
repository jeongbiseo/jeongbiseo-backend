package com.jeongbiseo.domain.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청자 소득구간임(온보딩·추천 매칭 공용 프로필 속성). 화면정의서 기준 5구간임(ONB-221, 건너뛰기 가능).
 */
// 상수명만으로는 월 소득인지 연 소득인지 드러나지 않아 Swagger에 한국어 라벨을 함께 실음(라벨 정본은 API명세서
// IncomeBracket 절). 응답에 라벨 필드를 더하는 방식은 계약 변경이라 쓰지 않음.
@Schema(description = """
		월 소득 구간(선택, 건너뛰면 판정 불가로 처리함).
		UNDER_200: 월 200만원 미만(소득이 0원인 경우도 여기)
		FROM_200_TO_300: 월 200만원 이상 300만원 미만
		FROM_300_TO_400: 월 300만원 이상 400만원 미만
		FROM_400_TO_600: 월 400만원 이상 600만원 미만
		OVER_600: 월 600만원 이상""")
public enum IncomeBracket {

	UNDER_200, FROM_200_TO_300, FROM_300_TO_400, FROM_400_TO_600, OVER_600

}
