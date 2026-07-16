package com.jeongbiseo.domain.onboarding.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.service.MemberReader;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.repository.OnboardingProfileRepository;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * OnboardingService 단위 테스트임(Mockito, DB 비의존). 제출·조회·수정의 성공·실패 경로와 D3 미해석 지역 null 저장을 고정함.
 * 회원 미존재·탈퇴 판정은 MemberReaderTest가 담당하므로 여기서는 getActiveMember를 스텁으로 통과시킴.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

	private static final Long MEMBER_ID = 1L;

	private static final LocalDate BIRTH_DATE = LocalDate.of(1999, 3, 15);

	@Mock
	private MemberReader memberReader;

	@Mock
	private OnboardingProfileRepository onboardingProfileRepository;

	@InjectMocks
	private OnboardingService onboardingService;

	@Test
	void submit_신규면_저장하고_이름과_완료를_갱신하며_지역코드를_파생한다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(member);
		given(onboardingProfileRepository.existsByMemberId(MEMBER_ID)).willReturn(false);
		given(onboardingProfileRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		OnboardingProfile saved = onboardingService.submit(MEMBER_ID, "홍길동", BIRTH_DATE, "서울특별시", "강남구",
				EmploymentStatus.EMPLOYED, null, 1);

		assertThat(saved.getRegionCode()).isEqualTo("11680");
		assertThat(saved.getSido()).isEqualTo("서울특별시");
		assertThat(member.getName()).isEqualTo("홍길동");
		assertThat(member.isOnboardingCompleted()).isTrue();
	}

	@Test
	void submit_카탈로그_밖_지역이면_regionCode를_null로_저장하고_통과시킨다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(member);
		given(onboardingProfileRepository.existsByMemberId(MEMBER_ID)).willReturn(false);
		given(onboardingProfileRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		OnboardingProfile saved = onboardingService.submit(MEMBER_ID, "홍길동", BIRTH_DATE, "제주특별자치도", "서귀포시",
				EmploymentStatus.JOB_SEEKING, null, null);

		assertThat(saved.getRegionCode()).isNull();
		assertThat(saved.getSido()).isEqualTo("제주특별자치도");
	}

	@Test
	void submit_이미_온보딩했으면_ONB409_1을_던진다() {
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(activeMember());
		given(onboardingProfileRepository.existsByMemberId(MEMBER_ID)).willReturn(true);

		assertThatThrownBy(() -> onboardingService.submit(MEMBER_ID, "홍길동", BIRTH_DATE, "서울특별시", "강남구",
				EmploymentStatus.EMPLOYED, null, 1))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("ONB409_1");
	}

	@Test
	void getMyOnboarding_온보딩_미완료면_ONB404_1을_던진다() {
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(activeMember());
		given(onboardingProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> onboardingService.getMyOnboarding(MEMBER_ID)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("ONB404_1");
	}

	@Test
	void getMyOnboarding_있으면_프로필을_반환한다() {
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(activeMember());
		OnboardingProfile profile = profileOf(activeMember());
		given(onboardingProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(profile));

		assertThat(onboardingService.getMyOnboarding(MEMBER_ID)).isSameAs(profile);
	}

	@Test
	void update_없으면_ONB404_1을_던진다() {
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(activeMember());
		given(onboardingProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> onboardingService.update(MEMBER_ID, "김철수", BIRTH_DATE, "서울특별시", "관악구",
				EmploymentStatus.STUDENT, null, null))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("ONB404_1");
	}

	@Test
	void update_있으면_전체교체하고_이름을_갱신한다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(member);
		OnboardingProfile profile = profileOf(member);
		given(onboardingProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(profile));

		OnboardingProfile updated = onboardingService.update(MEMBER_ID, "김철수", BIRTH_DATE, "서울특별시", "관악구",
				EmploymentStatus.STUDENT, null, null);

		assertThat(updated.getSigungu()).isEqualTo("관악구");
		assertThat(updated.getRegionCode()).isEqualTo("11620");
		assertThat(updated.getIncomeBracket()).isNull();
		assertThat(member.getName()).isEqualTo("김철수");
	}

	private static Member activeMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build();
	}

	private static OnboardingProfile profileOf(Member member) {
		return OnboardingProfile.builder()
			.member(member)
			.birthDate(BIRTH_DATE)
			.regionCode("11680")
			.sido("서울특별시")
			.sigungu("강남구")
			.employmentStatus(EmploymentStatus.EMPLOYED)
			.build();
	}

}
