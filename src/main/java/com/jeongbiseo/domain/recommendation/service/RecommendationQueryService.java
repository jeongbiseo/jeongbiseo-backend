package com.jeongbiseo.domain.recommendation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

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
 * RecommendationService에 그대로 위임함 — 이 서비스는 개수(limit) 정책을 바꾸지 않고 흐름만 조립함.
 */
@Service
public class RecommendationQueryService {

	private final OnboardingService onboardingService;

	private final ReceivedSubsidyService receivedSubsidyService;

	private final RecommendationService recommendationService;

	private final SubsidyReader subsidyReader;

	private final Clock clock;

	// clock은 ClockConfig의 Asia/Seoul 고정 빈을 그대로 주입받음(제약 5.2 시간대 계약 — 중복 빈 정의 금지).
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
	 * @return 추천 항목·기준일·최신 갱신 시각을 담은 뷰
	 */
	public RecommendationView getRecommendations(Long memberId, Integer limit) {
		OnboardingProfile profile = onboardingService.getMyOnboarding(memberId);
		ApplicantProfile applicant = toApplicantProfile(profile);
		Set<Long> receivedIds = Set.copyOf(receivedSubsidyService.findReceivedSubsidyIds(memberId));
		LocalDate asOf = LocalDate.now(clock);

		List<RecommendationItem> items = recommendationService.recommend(applicant, receivedIds, asOf, limit);
		LocalDateTime dataUpdatedAt = subsidyReader.findLatestDataUpdatedAt();
		return new RecommendationView(items, asOf, dataUpdatedAt);
	}

	// ADAPTATION (F3): lab은 record 접근자(profile.birthDate())였으나 팀 OnboardingProfile은 JPA
	// 엔티티라 getter로 접근함(getBirthDate 등).
	private static ApplicantProfile toApplicantProfile(OnboardingProfile profile) {
		int age = AgeCalculator.calculateAge(profile.getBirthDate());
		return new ApplicantProfile(age, profile.getRegionCode(), profile.getEmploymentStatus(),
				profile.getIncomeBracket(), profile.getHouseholdSize());
	}

	/** 추천 조회 결과 뷰임. 기준일(asOf)은 컨트롤러가 dDay를 계산하는 데 씀. */
	public record RecommendationView(List<RecommendationItem> items, LocalDate asOf, LocalDateTime dataUpdatedAt) {

	}

}
