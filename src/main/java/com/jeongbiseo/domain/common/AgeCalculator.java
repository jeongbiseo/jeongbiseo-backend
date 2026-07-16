package com.jeongbiseo.domain.common;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/**
 * 생년월일로 만 나이를 계산함(2023년 만 나이 통일법 기준). 만 나이는 저장하지 않고 조회 시점마다 계산함 — 매일 값이 바뀌어 저장 시
 * staleness가 생기기 때문임(데이터모델 온보딩 절). 기준 존은 Asia/Seoul임(제약 5.2) — JVM 기본 존을 쓰면 UTC 서버에서 한국
 * 기준 생일 당일에 만 나이가 1살 적게 나와 연령 하한 지원금에서 그날 하루 탈락함(거짓 양성이 아니라 누락이라 더 나쁨).
 */
public final class AgeCalculator {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private AgeCalculator() {
	}

	/**
	 * 생년월일 기준 만 나이를 오늘 날짜로 계산함(운영 진입점, 기준 존 Asia/Seoul).
	 * @param birthDate 생년월일
	 * @return 만 나이
	 */
	public static int calculateAge(LocalDate birthDate) {
		return calculateAge(birthDate, LocalDate.now(SEOUL_ZONE));
	}

	/**
	 * 생년월일 기준 만 나이를 지정한 기준일로 계산함(테스트 결정성을 위해 기준일을 주입받음). 올해 생일이 지나지 않았으면 1살을 뺌.
	 * @param birthDate 생년월일
	 * @param today 기준일
	 * @return 만 나이
	 */
	public static int calculateAge(LocalDate birthDate, LocalDate today) {
		return Period.between(birthDate, today).getYears();
	}

}
