package com.jeongbiseo.domain.recommendation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.AgeCalculator;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.domain.onboarding.service.ReceivedSubsidyService;
import com.jeongbiseo.domain.recommendation.ApplicantProfile;
import com.jeongbiseo.domain.recommendation.RecommendationItem;
import com.jeongbiseo.domain.subsidy.SubsidyReader;

/**
 * 추천 조회 유즈케이스를 조립하는 애플리케이션 서비스임. 컨트롤러가 갖고 있던 오케스트레이션(온보딩 프로필 조회, 신청자 프로필 변환, 기수령 조회, 기준일
 * 산정, 추천 계산, 최신 갱신 시각 조회)을 여기로 모아, 컨트롤러는 HTTP 검증과 응답 변환만 담당하게 함(HANDOFF 2.B-14). 정렬·필터는
 * RecommendationService에 그대로 위임함. 이 서비스는 개수(limit) 정책을 바꾸지 않고 흐름만 조립함.
 */
@Service
public class RecommendationQueryService {

	private final OnboardingService onboardingService;

	private final ReceivedSubsidyService receivedSubsidyService;

	private final RecommendationService recommendationService;

	private final SubsidyReader subsidyReader;

	private final Clock clock;

	// clock은 ClockConfig의 Asia/Seoul 고정 빈을 그대로 주입받음(제약 5.2 시간대 계약, 중복 빈 정의 금지).
	public RecommendationQueryService(OnboardingService onboardingService,
			ReceivedSubsidyService receivedSubsidyService, RecommendationService recommendationService,
			SubsidyReader subsidyReader, Clock clock) {
		this.onboardingService = onboardingService;
		this.receivedSubsidyService = receivedSubsidyService;
		this.recommendationService = recommendationService;
		this.subsidyReader = subsidyReader;
		this.clock = clock;
	}

	/**
	 * 회원의 추천 리스트와 표시에 필요한 부가 정보(기준일, 최신 갱신 시각)를 계산함. 온보딩 미완료면 OnboardingService가
	 * ONB404_1을 던짐(getMyOnboarding과 동일 예외 재사용).
	 * @param memberId 조회 대상 회원
	 * @param limit 노출 개수(null이면 기본값, 정규화는 RecommendationService가 담당). 0 이하 거부는 컨트롤러의 HTTP
	 * 검증이 먼저 처리함
	 * @param includeReceived 기수령 지원금 포함 여부. true면 제외하지 않고 함께 노출함(중복허용). 예상 총액은 이 값과 무관하게
	 * 항상 기수령을 제외함(EstimatedAmountService가 receivedIds를 그대로 씀)
	 * @return 추천 항목·기준일·최신 갱신 시각을 담은 뷰
	 */
	// 조회 전용 유즈케이스라 readOnly 트랜잭션으로 묶어, 온보딩·기수령·후보·표시정보 여러 조회가 한 스냅샷에서 일관되게 읽히게 함(트랜잭션
	// 경계는 유즈케이스를 조립하는 이 애플리케이션 서비스가 소유함).
	@Transactional(readOnly = true)
	public RecommendationView getRecommendations(Long memberId, Integer limit, boolean includeReceived) {
		ApplicantContext ctx = resolveContext(memberId);
		// includeReceived면 제외 집합을 비워 기수령도 후보에 남김. RecommendationService/Policy/Ranking은
		// 무수정이고
		// 제외 여부는 이 분기 한 줄로만 갈림(총액 경로는 이 분기를 타지 않아 항상 제외 유지).
		Set<Long> excludeIds = includeReceived ? Set.of() : ctx.receivedIds();
		List<RecommendationItem> items = recommendationService.recommend(ctx.applicant(), excludeIds, ctx.asOf(),
				limit);
		LocalDateTime dataUpdatedAt = subsidyReader.findLatestDataUpdatedAt();
		return new RecommendationView(items, ctx.asOf(), dataUpdatedAt);
	}

	/**
	 * 회원의 신청자 컨텍스트(프로필·기수령·기준일)를 조립함. 추천과 예상 총액이 공유하는 진입점이며, 온보딩에서 신청자로의 변환을 이 한 곳으로 좁힘.
	 * 온보딩 미완료면 OnboardingService가 ONB404_1을 던짐(getMyOnboarding과 동일 예외).
	 * @param memberId 조회 대상 회원
	 * @return 신청자 프로필·기수령 id·기준일
	 */
	@Transactional(readOnly = true)
	public ApplicantContext resolveContext(Long memberId) {
		OnboardingProfile profile = onboardingService.getMyOnboarding(memberId);
		LocalDate asOf = LocalDate.now(clock);
		ApplicantProfile applicant = toApplicantProfile(profile, asOf);
		Set<Long> receivedIds = Set.copyOf(receivedSubsidyService.findReceivedSubsidyIds(memberId));
		return new ApplicantContext(applicant, receivedIds, asOf);
	}

	// 나이도 asOf 기준으로 계산해 마감 판정 기준일과 통일함. 단일 인자 calculateAge는 내부 wall-clock을 써
	// 자정·생일 경계에서 어긋날 수 있음(Clock 주입 취지).
	private static ApplicantProfile toApplicantProfile(OnboardingProfile profile, LocalDate asOf) {
		int age = AgeCalculator.calculateAge(profile.getBirthDate(), asOf);
		return new ApplicantProfile(age, profile.getRegionCode(), profile.getEmploymentStatus(),
				profile.getIncomeBracket(), profile.getHouseholdSize());
	}

	/** 추천 조회 결과 뷰임. 기준일(asOf)은 컨트롤러가 dDay를 계산하는 데 씀. */
	public record RecommendationView(List<RecommendationItem> items, LocalDate asOf, LocalDateTime dataUpdatedAt) {

	}

	/** 신청자 컨텍스트임(추천과 예상 총액이 공유). asOf는 마감 판정과 나이 계산 기준일임. */
	public record ApplicantContext(ApplicantProfile applicant, Set<Long> receivedIds, LocalDate asOf) {

	}

}
