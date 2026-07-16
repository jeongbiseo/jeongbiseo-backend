package com.jeongbiseo.domain.onboarding.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.service.MemberReader;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.repository.OnboardingProfileRepository;
import com.jeongbiseo.domain.region.RegionCatalog;
import com.jeongbiseo.global.apiPayload.code.OnboardingErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 온보딩 프로필 제출·조회·수정을 담당하는 도메인 서비스임. 활성 회원을 MemberReader로 먼저 검증(미존재 MEMBER404_1·탈퇴
 * MEMBER400_1)한 뒤, sido·sigungu에서 RegionCatalog로 매칭용 region_code를 파생하고 이름을 Member에
 * 저장함(v1.4, D6 — 이름은 온보딩 입력이 정본). 미해석 지역은 region_code를 null로 두되 warn 로그를 남김(D3).
 */
@Service
public class OnboardingService {

	private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

	private final MemberReader memberReader;

	private final OnboardingProfileRepository onboardingProfileRepository;

	public OnboardingService(MemberReader memberReader, OnboardingProfileRepository onboardingProfileRepository) {
		this.memberReader = memberReader;
		this.onboardingProfileRepository = onboardingProfileRepository;
	}

	/**
	 * 온보딩을 최초 제출함. 이미 완료된 회원이면 ONB409_1을 던짐(ONB-200). 이름을 Member에 저장하고 온보딩 완료로 표시함.
	 * @return 저장된 프로필(member는 이름·완료 상태가 갱신됨)
	 */
	@Transactional
	public OnboardingProfile submit(Long memberId, String name, LocalDate birthDate, String sido, String sigungu,
			EmploymentStatus employmentStatus, IncomeBracket incomeBracket, Integer householdSize) {
		Member member = memberReader.getActiveMember(memberId);
		if (onboardingProfileRepository.existsByMemberId(memberId)) {
			throw new CustomException(OnboardingErrorCode.ONBOARDING_ALREADY_COMPLETED);
		}
		String regionCode = resolveRegionCode(sido, sigungu);
		member.updateName(name);
		member.completeOnboarding();
		OnboardingProfile profile = OnboardingProfile.builder()
			.member(member)
			.birthDate(birthDate)
			.regionCode(regionCode)
			.sido(sido)
			.sigungu(sigungu)
			.employmentStatus(employmentStatus)
			.incomeBracket(incomeBracket)
			.householdSize(householdSize)
			.build();
		try {
			return onboardingProfileRepository.save(profile);
		}
		catch (DataIntegrityViolationException e) {
			// exists 검사와 insert 사이 동시 이중 제출 레이스 시 member_id UNIQUE 위반이 나므로, 최종 안전망의 500이
			// 아니라
			// 계약대로 ONB409_1로 변환함(원인 스택은 보존).
			throw new CustomException(OnboardingErrorCode.ONBOARDING_ALREADY_COMPLETED, e);
		}
	}

	/**
	 * 내 온보딩 정보를 조회함. 회원이 없거나 탈퇴면 MEMBER404_1·MEMBER400_1, 온보딩 미완료면 ONB404_1을 던짐.
	 */
	@Transactional(readOnly = true)
	public OnboardingProfile getMyOnboarding(Long memberId) {
		memberReader.getActiveMember(memberId);
		return onboardingProfileRepository.findByMemberId(memberId)
			.orElseThrow(() -> new CustomException(OnboardingErrorCode.ONBOARDING_NOT_FOUND));
	}

	/**
	 * 온보딩 정보를 전체 교체함(PUT 의미론, 생략 필드는 null로 교체). 프로필이 없으면 ONB404_1을 던짐(AUTH-161). 이름도 함께
	 * 갱신함(D6).
	 */
	@Transactional
	public OnboardingProfile update(Long memberId, String name, LocalDate birthDate, String sido, String sigungu,
			EmploymentStatus employmentStatus, IncomeBracket incomeBracket, Integer householdSize) {
		Member member = memberReader.getActiveMember(memberId);
		OnboardingProfile profile = onboardingProfileRepository.findByMemberId(memberId)
			.orElseThrow(() -> new CustomException(OnboardingErrorCode.ONBOARDING_NOT_FOUND));
		String regionCode = resolveRegionCode(sido, sigungu);
		member.updateName(name);
		profile.replaceWith(birthDate, regionCode, sido, sigungu, employmentStatus, incomeBracket, householdSize);
		return profile;
	}

	// RegionCatalog 미등록 조합이면 null을 반환하되 warn 로그를 남김(D3) — sido·sigungu는 계약상 자유 문자열이라 미해석도
	// 유효 입력이며, region_code null은 REGIONAL 매칭에서만 자연 탈락하고 NATIONWIDE·온보딩 제출 자체에는 영향이 없음.
	private String resolveRegionCode(String sido, String sigungu) {
		String regionCode = RegionCatalog.regionCodeOf(sido, sigungu);
		if (regionCode == null) {
			log.warn("온보딩 지역 미해석: sido={}, sigungu={} — region_code를 null로 저장함(REGIONAL 매칭만 탈락)", sido, sigungu);
		}
		return regionCode;
	}

}
