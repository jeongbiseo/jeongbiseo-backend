package com.jeongbiseo.domain.subsidy.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SubsidyIngestionAdapter의 융자 상품 판별 단위 테스트임. gov24 선언 지원유형에 "융자"가 들면 이자·보증 지원까지 융자로 보고
 * 서비스에서 제외함(팀 판정 — 순수 대출과 이자 지원을 구분하지 않음). 온통청년 제공방법코드(숫자)는 융자를 포함하지 않아 걸리지 않음(gov24 전용
 * 회차).
 */
class SubsidyIngestionAdapterTest {

	@Test
	void isLoanProduct_trueWhenTypeDeclaresLoan() {
		assertThat(SubsidyIngestionAdapter.isLoanProduct("현금(융자)")).isTrue();
		assertThat(SubsidyIngestionAdapter.isLoanProduct("기타(융자)")).isTrue();
		assertThat(SubsidyIngestionAdapter.isLoanProduct("현금||현금(융자)")).isTrue();
	}

	@Test
	void isLoanProduct_falseForNonLoanTypes_andYouthcenterCode_andNull() {
		assertThat(SubsidyIngestionAdapter.isLoanProduct("현금")).isFalse();
		assertThat(SubsidyIngestionAdapter.isLoanProduct("현금(감면)")).isFalse();
		assertThat(SubsidyIngestionAdapter.isLoanProduct("보조금")).isFalse();
		// 온통청년 제공방법코드는 숫자라 "융자" 미포함 → 이번 회차 자연 보류
		assertThat(SubsidyIngestionAdapter.isLoanProduct("0042006")).isFalse();
		assertThat(SubsidyIngestionAdapter.isLoanProduct(null)).isFalse();
	}

}
