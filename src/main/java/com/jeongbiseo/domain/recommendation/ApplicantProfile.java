package com.jeongbiseo.domain.recommendation;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;

/**
 * 매칭용 신청자 프로필 스냅샷임(값 객체). age는 생년월일에서 계산된 값이며 온보딩 컨텍스트와의 결합을 이 변환 한 곳으로 좁힘(DISCUSS.md
 * 3.3).
 *
 * @param age 만 나이(계산값)
 * @param regionCode 신청자 거주 지역코드
 * @param employmentStatus 신청자 고용상태
 * @param incomeBracket 신청자 소득구간(건너뛰기 시 null)
 * @param householdSize 신청자 가구원 수(건너뛰기 시 null)
 */
public record ApplicantProfile(int age, String regionCode, EmploymentStatus employmentStatus,
		IncomeBracket incomeBracket, Integer householdSize) {

}
