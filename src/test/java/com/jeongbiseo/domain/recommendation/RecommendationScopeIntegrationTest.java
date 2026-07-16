package com.jeongbiseo.domain.recommendation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;
import com.jeongbiseo.domain.onboarding.repository.OnboardingProfileRepository;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService;
import com.jeongbiseo.domain.recommendation.service.RecommendationQueryService.RecommendationView;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 조회 관통 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL, Docker 필요). 온보딩 시드부터
 * RecommendationQueryService, RecommendationPolicy 스코프 판정, 결과 subsidyId까지 한 경로로 검증함.
 * FixedMemberResolver는 인증 도입 전 고정 회원만 반환하므로, memberId를 테스트가 직접 통제하기 위해
 * RecommendationQueryService를 컨트롤러 대신 직접 호출함(AGENTS.md 팀 시딩 방식). 각 테스트는 @Transactional로
 * 롤백해 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RecommendationScopeIntegrationTest {

	// testcontainers-junit-jupiter 의존성 없이 컨테이너를 수동 기동함(@ServiceConnection이 연결 정보를 주입).
	// Ryuk가 JVM 종료 시 컨테이너를 정리함.
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OnboardingProfileRepository onboardingProfileRepository;

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Autowired
	private ReceivedSubsidyRepository receivedSubsidyRepository;

	@Autowired
	private RecommendationQueryService recommendationQueryService;

	// ClockConfig가 제공하는 Asia/Seoul 고정 빈임. asOf 파생 기준을 실제 서비스가 쓰는 시각과 일치시켜 날짜 드리프트에
	// 흔들리지 않게 함(테스트가 "오늘"을 직접 가정하지 않고 서비스가 실제로 쓰는 Clock에서 계산함).
	@Autowired
	private Clock clock;

	private Long memberId;

	@BeforeEach
	void seedApplicant() {
		Member member = memberRepository.save(newMember());
		LocalDate birthDate = LocalDate.now(SEOUL_ZONE).minusYears(27);
		// D3: RegionCatalog 밖 지역이면 regionCode는 null로 저장됨. 사용자 지역코드가 null이면 강등 판정 불가라
		// 지역형·전국형 모두 정상 노출함(09-region-demotion 반전, 종전 REGIONAL 탈락 폐기).
		OnboardingProfile profile = OnboardingProfile.builder()
			.member(member)
			.birthDate(birthDate)
			.regionCode(null)
			.sido("제주특별자치도")
			.sigungu("서귀포시")
			.employmentStatus(EmploymentStatus.JOB_SEEKING)
			.incomeBracket(IncomeBracket.UNDER_200)
			.householdSize(1)
			.build();
		onboardingProfileRepository.save(profile);
		this.memberId = member.getId();
	}

	private static Member newMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
	}

	@Test
	void recommend_필터가_기업과_1차산업전용만_제외하고_비현금성과_UNKNOWN_지급방식도_추천한다() {
		SubsidyEntity personal = subsidyRepository.save(alwaysMatching("scope-personal", TargetAudience.PERSONAL,
				OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity business = subsidyRepository.save(alwaysMatching("scope-business", TargetAudience.BUSINESS,
				OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity mixed = subsidyRepository
			.save(alwaysMatching("scope-mixed", TargetAudience.MIXED, OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity unknownAudience = subsidyRepository.save(
				alwaysMatching("scope-unknown", TargetAudience.UNKNOWN, OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity primaryIndustry = subsidyRepository.save(alwaysMatching("scope-primary", TargetAudience.PERSONAL,
				OccupationRestriction.PRIMARY_INDUSTRY_ONLY, PaymentType.CASH));
		// 핵심 불변식: 비현금성(VOUCHER)과 paymentType UNKNOWN도 추천에는 노출돼야 함(총액 합산과는 다른 축, AGENTS.md
		// 1장)
		SubsidyEntity voucher = subsidyRepository.save(alwaysMatching("scope-voucher", TargetAudience.PERSONAL,
				OccupationRestriction.NONE, PaymentType.VOUCHER));
		SubsidyEntity unknownPayment = subsidyRepository.save(alwaysMatching("scope-unknown-payment",
				TargetAudience.PERSONAL, OccupationRestriction.NONE, PaymentType.UNKNOWN));

		RecommendationView view = recommendationQueryService.getRecommendations(memberId, 10);

		List<Long> ids = view.items().stream().map(item -> item.summary().subsidyId()).toList();
		assertThat(ids).contains(personal.getId(), mixed.getId(), unknownAudience.getId(), voucher.getId(),
				unknownPayment.getId());
		assertThat(ids).doesNotContain(business.getId(), primaryIndustry.getId());
	}

	@Test
	void recommend_limit이_null이면_기본값3건_초과요청은_20건으로_클램프한다() {
		List<SubsidyEntity> saved = subsidyRepository.saveAll(IntStream.rangeClosed(1, 25)
			.mapToObj(i -> alwaysMatching("clamp-" + i, TargetAudience.PERSONAL, OccupationRestriction.NONE,
					PaymentType.CASH))
			.toList());
		assertThat(saved).hasSize(25);

		RecommendationView defaultView = recommendationQueryService.getRecommendations(memberId, null);
		RecommendationView clampedView = recommendationQueryService.getRecommendations(memberId, 100);

		assertThat(defaultView.items()).hasSize(3);
		assertThat(clampedView.items()).hasSize(20);
	}

	@Test
	void recommend_기수령_지원금은_후보에서_제외된다() {
		SubsidyEntity received = subsidyRepository
			.save(alwaysMatching("received-1", TargetAudience.PERSONAL, OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity notReceived = subsidyRepository.save(alwaysMatching("not-received-1", TargetAudience.PERSONAL,
				OccupationRestriction.NONE, PaymentType.CASH));
		receivedSubsidyRepository
			.save(ReceivedSubsidy.builder().memberId(memberId).subsidyId(received.getId()).build());

		RecommendationView view = recommendationQueryService.getRecommendations(memberId, 10);

		List<Long> ids = view.items().stream().map(item -> item.summary().subsidyId()).toList();
		assertThat(ids).contains(notReceived.getId());
		assertThat(ids).doesNotContain(received.getId());
	}

	@Test
	void recommend_D3_지역코드_null이면_전국형과_지역형_둘_다_노출된다() {
		// D6 조건1: 신청자 지역코드가 null이면 강등 판정 자체가 불가하므로 지역형도 강등 없이 노출됨(누락-안전 처리, AGENTS.md 1장)
		SubsidyEntity nationwide = subsidyRepository.save(alwaysMatching("region-nationwide", TargetAudience.PERSONAL,
				OccupationRestriction.NONE, PaymentType.CASH));
		SubsidyEntity regional = subsidyRepository.save(SubsidyEntity.builder()
			.sourceId("scope-test")
			.externalId("region-regional")
			.name("지역형 지원금")
			.category(SubsidyCategory.YOUTH)
			.paymentType(PaymentType.CASH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.regionScope(RegionScope.REGIONAL)
			.regionCode("11680")
			.regionCodes("11680")
			.active(true)
			.recommendable(true)
			.build());

		RecommendationView view = recommendationQueryService.getRecommendations(memberId, 10);

		List<Long> ids = view.items().stream().map(item -> item.summary().subsidyId()).toList();
		assertThat(ids).contains(nationwide.getId(), regional.getId());
	}

	@Test
	void recommend_asOf기준_당일마감은_포함하고_마감지난건은_제외한다() {
		LocalDate asOf = LocalDate.now(clock);
		SubsidyEntity dueToday = subsidyRepository.save(withDeadline("deadline-today", asOf));
		SubsidyEntity expired = subsidyRepository.save(withDeadline("deadline-expired", asOf.minusDays(1)));

		RecommendationView view = recommendationQueryService.getRecommendations(memberId, 10);

		List<Long> ids = view.items().stream().map(item -> item.summary().subsidyId()).toList();
		assertThat(ids).contains(dueToday.getId());
		assertThat(ids).doesNotContain(expired.getId());
	}

	// 나머지 매칭 축(연령·지역·고용·소득·가구)은 항상 통과하도록 열어두고 스코프·기수령·limit·마감 축만 짚는 시드 빌더임.
	private static SubsidyEntity alwaysMatching(String externalId, TargetAudience targetAudience,
			OccupationRestriction occupationRestriction, PaymentType paymentType) {
		return SubsidyEntity.builder()
			.sourceId("scope-test")
			.externalId(externalId)
			.name("스코프 회귀 지원금 " + externalId)
			.category(SubsidyCategory.YOUTH)
			.paymentType(paymentType)
			.duplicationPolicy("ALLOW")
			.targetAudience(targetAudience)
			.occupationRestriction(occupationRestriction)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.build();
	}

	private static SubsidyEntity withDeadline(String externalId, LocalDate deadline) {
		return SubsidyEntity.builder()
			.sourceId("scope-test")
			.externalId(externalId)
			.name("마감 경계 지원금 " + externalId)
			.category(SubsidyCategory.YOUTH)
			.paymentType(PaymentType.CASH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.regionScope(RegionScope.NATIONWIDE)
			.deadline(deadline)
			.active(true)
			.recommendable(true)
			.build();
	}

}
