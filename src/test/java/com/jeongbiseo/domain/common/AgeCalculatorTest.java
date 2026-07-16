package com.jeongbiseo.domain.common;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgeCalculator 단위 테스트임. 만 나이 통일법 기준으로 올해 생일 전후 경계를 고정함(기준일 주입으로 결정적).
 */
class AgeCalculatorTest {

	@Test
	void 올해_생일이_지났으면_만나이_그대로() {
		int age = AgeCalculator.calculateAge(LocalDate.of(2000, 3, 15), LocalDate.of(2026, 7, 16));

		assertThat(age).isEqualTo(26);
	}

	@Test
	void 올해_생일_전이면_한살_적다() {
		int age = AgeCalculator.calculateAge(LocalDate.of(2000, 12, 31), LocalDate.of(2026, 7, 16));

		assertThat(age).isEqualTo(25);
	}

	@Test
	void 생일_당일이면_만나이_그대로() {
		int age = AgeCalculator.calculateAge(LocalDate.of(2000, 7, 16), LocalDate.of(2026, 7, 16));

		assertThat(age).isEqualTo(26);
	}

}
