package com.jeongbiseo.domain.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청자 고용상태임(온보딩·추천 매칭 공용 프로필 속성). 화면정의서 기준 6개 값임(ONB-213).
 */
// 라벨 정본은 API명세서 EmploymentStatus 절임. DB에는 상수명을 저장하고 한국어 라벨은 화면 표시용임.
@Schema(description = """
		고용상태(필수).
		EMPLOYED: 재직 / JOB_SEEKING: 구직 / STUDENT: 학생
		FREELANCER: 프리랜서 / SELF_EMPLOYED: 자영업 / OTHER: 기타""")
public enum EmploymentStatus {

	EMPLOYED, JOB_SEEKING, STUDENT, FREELANCER, SELF_EMPLOYED, OTHER

}
