package com.jeongbiseo.domain.estimate;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.estimate.EstimatedTotalResult.SeparateItem;
import com.jeongbiseo.infra.client.common.dto.AmountKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EstimatedTotalCalculator 단위 테스트임(순수 JUnit, 프레임워크 없음). 분류 우선순위·강등 제외(D-B)·월 지급 각각
 * 계산(D-C)·null paymentType 폴스루(M2)를 박제함. 테스트 안에서 분류 규칙을 재구현하지 않고 공개 메서드 결과만 단언함.
 */
class EstimatedTotalCalculatorTest {

	private final EstimatedTotalCalculator calculator = new EstimatedTotalCalculator();

	@Test
	void calculate_classifiesEachCandidateIntoExactlyOneBucket_andSumsCashAndMonthlySeparately() {
		List<EstimateCandidate> candidates = List.of(cash("cash-a", 100L, 300L, false),
				cash("cash-b", 200L, 200L, false), monthly("monthly-a", 500L), monthly("monthly-b", 100L),
				cash("demoted-cash", 999L, 999L, true), payment("voucher", PaymentType.VOUCHER),
				payment("in-kind", PaymentType.IN_KIND), payment("reduction", PaymentType.REDUCTION),
				payment("unknown-payment", PaymentType.UNKNOWN), payment("null-payment", null),
				audience("mixed", TargetAudience.MIXED), audience("unknown-audience", TargetAudience.UNKNOWN),
				cash("cash-no-amount", null, null, false), monthly("monthly-no-amount", null));

		EstimatedTotalResult result = calculator.calculate(candidates);

		assertThat(result.oneTimeItems()).extracting(item -> item.name()).containsExactly("cash-a", "cash-b");
		assertThat(result.cashTotalMin()).isEqualTo(300L);
		assertThat(result.cashTotalMax()).isEqualTo(500L);
		assertThat(result.monthlyItems()).hasSize(2);
		assertThat(result.monthlyTotalMin()).isEqualTo(600L);
		assertThat(result.monthlyTotalMax()).isEqualTo(600L);
		assertThat(result.separateItems()).hasSize(10);
		assertThat(result.totalCount()).isEqualTo(candidates.size());
		// 강등 현금건은 현금 총액에 안 들어가고 별도 혜택으로만 계상됨(D-B·R5 핵심 회귀).
		assertThat(result.oneTimeItems()).noneMatch(item -> item.name().equals("demoted-cash"));
		assertReason(result, "demoted-cash", EstimateExclusionReason.REGION_UNVERIFIED);
	}

	@Test
	void calculate_assignsEveryExclusionReason_andNoteMatchesReason() {
		List<EstimateCandidate> candidates = List.of(cash("demoted", 1L, 1L, true),
				audience("mixed", TargetAudience.MIXED), audience("unknown-audience", TargetAudience.UNKNOWN),
				payment("voucher", PaymentType.VOUCHER), payment("unknown-payment", PaymentType.UNKNOWN),
				payment("null-payment", null), cash("no-amount", null, null, false), monthly("monthly-null", null),
				business("business"), cashWithKind("conditional", AmountKind.CONDITIONAL, 100L, 300L),
				cashWithKind("multiple", AmountKind.MULTIPLE, 100L, 300L),
				cashWithKind("none", AmountKind.NONE, null, null), cashWithKind("null-kind", null, 100L, 100L));

		EstimatedTotalResult result = calculator.calculate(candidates);

		assertReason(result, "demoted", EstimateExclusionReason.REGION_UNVERIFIED);
		assertReason(result, "mixed", EstimateExclusionReason.MIXED);
		assertReason(result, "unknown-audience", EstimateExclusionReason.UNKNOWN_AUDIENCE);
		assertReason(result, "voucher", EstimateExclusionReason.NON_CASH);
		assertReason(result, "unknown-payment", EstimateExclusionReason.PAYMENT_TYPE_UNKNOWN);
		assertReason(result, "null-payment", EstimateExclusionReason.PAYMENT_TYPE_UNKNOWN);
		assertReason(result, "no-amount", EstimateExclusionReason.AMOUNT_MISSING);
		assertReason(result, "monthly-null", EstimateExclusionReason.AMOUNT_MISSING);
		assertReason(result, "business", EstimateExclusionReason.BUSINESS);
		assertReason(result, "conditional", EstimateExclusionReason.CONDITIONAL_AMOUNT);
		assertReason(result, "multiple", EstimateExclusionReason.MULTIPLE_AMOUNT);
		assertReason(result, "none", EstimateExclusionReason.AMOUNT_MISSING);
		assertReason(result, "null-kind", EstimateExclusionReason.AMOUNT_MISSING);
		assertThat(result.separateItems()).allSatisfy(item -> assertThat(item.note()).isEqualTo(item.reason().note()));
	}

	@Test
	void calculate_includesOnlySingleCashAmount() {
		EstimateCandidate single = cashWithKind("single", AmountKind.SINGLE, 100L, 300L);

		EstimatedTotalResult result = calculator.calculate(List.of(single));

		assertThat(result.oneTimeItems()).extracting(item -> item.name()).containsExactly("single");
		assertThat(result.cashTotalMin()).isEqualTo(100L);
		assertThat(result.cashTotalMax()).isEqualTo(300L);
		assertThat(result.separateItems()).isEmpty();
	}

	@Test
	void calculate_regionDemotedWinsOverAudienceAndPayment() {
		// 강등이 최우선이라, MIXED이면서 강등인 현금건은 MIXED가 아니라 REGION_UNVERIFIED로 분류됨.
		EstimateCandidate demotedMixedCash = new EstimateCandidate(1L, "demoted-mixed", PaymentType.CASH,
				TargetAudience.MIXED, AmountKind.CONDITIONAL, 100L, 100L, null, true);

		EstimatedTotalResult result = calculator.calculate(List.of(demotedMixedCash));

		assertReason(result, "demoted-mixed", EstimateExclusionReason.REGION_UNVERIFIED);
	}

	@Test
	void calculate_emptyInput_returnsZeroTotalsAndEmptyLists() {
		EstimatedTotalResult result = calculator.calculate(List.of());

		assertThat(result.oneTimeItems()).isEmpty();
		assertThat(result.monthlyItems()).isEmpty();
		assertThat(result.separateItems()).isEmpty();
		assertThat(result.cashTotalMin()).isZero();
		assertThat(result.cashTotalMax()).isZero();
		assertThat(result.monthlyTotalMin()).isZero();
		assertThat(result.monthlyTotalMax()).isZero();
		assertThat(result.totalCount()).isZero();
	}

	private static void assertReason(EstimatedTotalResult result, String name, EstimateExclusionReason expected) {
		assertThat(result.separateItems()).filteredOn(item -> item.name().equals(name))
			.singleElement()
			.satisfies((SeparateItem item) -> {
				assertThat(item.reason()).isEqualTo(expected);
				assertThat(item.note()).isEqualTo(expected.note());
			});
	}

	private static long nextId = 0L;

	private static EstimateCandidate cash(String name, Long min, Long max, boolean demoted) {
		AmountKind amountKind = min == null || max == null ? AmountKind.NONE : AmountKind.SINGLE;
		return new EstimateCandidate(++nextId, name, PaymentType.CASH, TargetAudience.PERSONAL, amountKind, min, max,
				null, demoted);
	}

	private static EstimateCandidate cashWithKind(String name, AmountKind amountKind, Long min, Long max) {
		return new EstimateCandidate(++nextId, name, PaymentType.CASH, TargetAudience.PERSONAL, amountKind, min, max,
				null, false);
	}

	private static EstimateCandidate monthly(String name, Long monthlyAmount) {
		return new EstimateCandidate(++nextId, name, PaymentType.MONTHLY, TargetAudience.PERSONAL, null, null,
				monthlyAmount, false);
	}

	private static EstimateCandidate payment(String name, PaymentType paymentType) {
		return new EstimateCandidate(++nextId, name, paymentType, TargetAudience.PERSONAL, AmountKind.SINGLE, 100L,
				100L, null, false);
	}

	private static EstimateCandidate audience(String name, TargetAudience targetAudience) {
		return new EstimateCandidate(++nextId, name, PaymentType.CASH, targetAudience, AmountKind.SINGLE, 100L, 100L,
				null, false);
	}

	private static EstimateCandidate business(String name) {
		return new EstimateCandidate(++nextId, name, PaymentType.CASH, TargetAudience.BUSINESS, AmountKind.SINGLE, 100L,
				100L, null, false);
	}

}
