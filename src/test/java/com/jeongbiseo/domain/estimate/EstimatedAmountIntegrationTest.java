package com.jeongbiseo.domain.estimate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.estimate.service.EstimatedAmountService;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.repository.OnboardingProfileRepository;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예상 총액 관통 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL, Docker 필요). 온보딩·지원금 시드부터
 * EstimatedAmountService, RecommendationService 후보 선정, EstimatedTotalCalculator 분류까지 한
 * 경로로 검증함. FixedMemberResolver는 고정 회원만 반환하므로 memberId를 통제하기 위해 서비스를 직접 호출함(AGENTS.md 팀 시딩
 * 방식). 각 테스트는
 *
 * @Transactional로 롤백해 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class EstimatedAmountIntegrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	// 사용자 지역코드(관악, 시도 prefix 11). 강등 판정을 켜기 위해 non-null로 둠(지역코드 null이면 강등이 아예 안 걸림).
	private static final String USER_REGION_CODE = "11620";

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OnboardingProfileRepository onboardingProfileRepository;

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Autowired
	private EstimatedAmountService estimatedAmountService;

	private Long memberId;

	@BeforeEach
	void seedApplicant() {
		Member member = memberRepository.save(Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build());
		OnboardingProfile profile = OnboardingProfile.builder()
			.member(member)
			.birthDate(LocalDate.now(SEOUL_ZONE).minusYears(27))
			.regionCode(USER_REGION_CODE)
			.sido("서울특별시")
			.sigungu("관악구")
			.employmentStatus(EmploymentStatus.JOB_SEEKING)
			.incomeBracket(IncomeBracket.UNDER_200)
			.householdSize(1)
			.build();
		onboardingProfileRepository.save(profile);
		this.memberId = member.getId();
	}

	@Test
	void getEstimatedTotal_일시금과_월지급을_각각_합산하고_강등건은_총액에서_제외한다() {
		subsidyRepository
			.save(subsidy("cash", TargetAudience.PERSONAL, PaymentType.CASH, 100_000L, 300_000L, null, null));
		subsidyRepository
			.save(subsidy("monthly", TargetAudience.PERSONAL, PaymentType.MONTHLY, null, null, 500_000L, null));
		subsidyRepository
			.save(subsidy("voucher", TargetAudience.PERSONAL, PaymentType.VOUCHER, null, null, null, null));
		subsidyRepository
			.save(subsidy("cash-no-amount", TargetAudience.PERSONAL, PaymentType.CASH, null, null, null, null));
		// 거주지(11620) 시도 prefix와 안 맞는 지역코드(26110, 부산) 현금건 → 강등 → 총액 제외, 건수 포함
		subsidyRepository
			.save(subsidy("demoted", TargetAudience.PERSONAL, PaymentType.CASH, 999_000L, 999_000L, null, "26110"));
		subsidyRepository
			.save(subsidy("mixed", TargetAudience.MIXED, PaymentType.CASH, 700_000L, 700_000L, null, null));

		EstimatedTotalResult result = estimatedAmountService.getEstimatedTotal(memberId);

		assertThat(result.totalCount()).isEqualTo(6);
		// 일시금 현금은 cash 1건만(강등 현금은 제외). 강등건의 999,000원이 총액에 안 섞임(D-B·R5 핵심 회귀).
		assertThat(result.oneTimeItems()).extracting(item -> item.name()).containsExactly("예상총액 cash");
		assertThat(result.cashTotalMin()).isEqualTo(100_000L);
		assertThat(result.cashTotalMax()).isEqualTo(300_000L);
		// 월 지급은 각각 계산됨(일시금과 분리된 별도 합계, D-C).
		assertThat(result.monthlyItems()).hasSize(1);
		assertThat(result.monthlyTotalMin()).isEqualTo(500_000L);
		assertThat(result.monthlyTotalMax()).isEqualTo(500_000L);
		// 별도 혜택 4건:
		// voucher(NON_CASH)·cash-no-amount(AMOUNT_MISSING)·demoted(REGION_UNVERIFIED)·mixed(MIXED)
		assertThat(result.separateItems()).hasSize(4);
		assertThat(result.separateItems()).filteredOn(item -> item.name().equals("예상총액 demoted"))
			.singleElement()
			.satisfies(item -> assertThat(item.reason()).isEqualTo(EstimateExclusionReason.REGION_UNVERIFIED));
	}

	@Test
	void getEstimatedTotal_모집단은_추천_상위20건으로_캡된다() {
		subsidyRepository.saveAll(IntStream.rangeClosed(1, 25)
			.mapToObj(
					i -> subsidy("cap-" + i, TargetAudience.PERSONAL, PaymentType.CASH, 100_000L, 100_000L, null, null))
			.toList());

		EstimatedTotalResult result = estimatedAmountService.getEstimatedTotal(memberId);

		assertThat(result.totalCount()).isEqualTo(20);
	}

	// 매칭 축(연령·지역 prefix 외·고용·소득·가구)은 열어두고 지급방식·금액·강등·대상만 통제하는 시드 빌더임.
	private static SubsidyEntity subsidy(String externalId, TargetAudience audience, PaymentType paymentType,
			Long estimatedAmountMin, Long estimatedAmountMax, Long monthlyAmount, String regionCodes) {
		return SubsidyEntity.builder()
			.sourceId("estimate-test")
			.externalId(externalId)
			.name("예상총액 " + externalId)
			.category(SubsidyCategory.YOUTH)
			.duplicationPolicy("ALLOW")
			.targetAudience(audience)
			.occupationRestriction(OccupationRestriction.NONE)
			.paymentType(paymentType)
			.estimatedAmountMin(estimatedAmountMin)
			.estimatedAmountMax(estimatedAmountMax)
			.monthlyAmount(monthlyAmount)
			.regionScope(RegionScope.NATIONWIDE)
			.regionCodes(regionCodes)
			.active(true)
			.recommendable(true)
			.build();
	}

}
