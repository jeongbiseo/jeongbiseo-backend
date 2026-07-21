package com.jeongbiseo.domain.member.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.jeongbiseo.domain.consent.service.TermConsentService;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;

/**
 * local과 prod(데모) 프로필용 고정 회원 시더임. FixedMemberResolver가 항상 memberId=1을 반환하는데(소셜 인증 도입 전
 * 개발용, 결정 7번) member 테이블이 비면 회원 스코프 엔드포인트가 전부 MEMBER404_1이 되므로, 빈 테이블에 한 번 고정 회원 1행을 넣어
 * 개발과 배포 데모를 가능하게 함. IDENTITY 전략이라 빈 DB의 첫 insert가 id=1을 받음. count()==0 가드로 멱등하게 만들고, 이미
 * 회원이 있으면(탈퇴 soft delete 포함) 다시 넣지 않음. local과 prod에만 로드되고 통합 테스트(test 프로필)에는 로드되지 않음.
 *
 * 고정 회원 생성 시 필수 약관 동의도 함께 기록함 — 실제 소셜 가입 흐름이 recordRequiredConsents를 아직 부르지 않아, 이를 안 하면
 * 마이페이지 약관 조회가 데모 회원에게 항상 미동의로 나오기 때문임(시더가 가입 대역, PLAN 19 C1). term_version이 먼저 있어야 하므로
 *
 * @Order로 TermVersionInitializer(@Order(1)) 뒤에 실행함.
 */
// ponytail: 개발과 데모용 단일 회원 시드. 소셜 인증 도입 시 실제 가입 흐름이 회원과 동의를 만들면 이 시더는 제거함.
@Component
@Order(2)
@Profile({ "local", "prod" })
public class LocalMemberSeeder implements ApplicationRunner {

	private final MemberRepository memberRepository;

	private final TermConsentService termConsentService;

	public LocalMemberSeeder(MemberRepository memberRepository, TermConsentService termConsentService) {
		this.memberRepository = memberRepository;
		this.termConsentService = termConsentService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (memberRepository.count() == 0) {
			Member member = memberRepository
				.save(Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build());
			termConsentService.recordRequiredConsents(member);
		}
	}

}
