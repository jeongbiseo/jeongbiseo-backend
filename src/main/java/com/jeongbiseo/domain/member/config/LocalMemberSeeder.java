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
 * local 프로필용 고정 회원 시더임. 인증 강제화(AUTH-W001) 전에는 prod에도 로드해 무헤더 데모의 고정 회원 1을 보장했으나, 인증 강제화로
 * prod는 실제 소셜 로그인이 회원과 동의를 만들므로 prod에서 제거하고 local 개발 편의로만 남김. 고정 회원이 없으면 만들고(IDENTITY
 * 전략이라 빈 DB의 첫 insert가 id=1을 받음) 이미 있으면 그대로 씀. 통합 테스트(test 프로필)에는 로드되지 않음.
 *
 * 고정 회원의 필수 약관 동의도 매 기동 보장함 — local에서 마이페이지 약관 조회가 미동의로 나오지 않게 함(시더가 가입 대역, PLAN 19 C1).
 * add-missing 멱등 경로(ensureRequiredConsents)라 기존 동의의 decidedAt을 매 기동 흔들지 않음. term_version이
 * 먼저 있어야 하므로 @Order로 TermVersionInitializer(@Order(1)) 뒤에 실행함.
 */
// ponytail: local 개발용 단일 회원 시드. prod는 실제 소셜 가입 흐름이 회원과 동의를 만듦.
@Component
@Order(2)
@Profile("local")
public class LocalMemberSeeder implements ApplicationRunner {

	// 고정 회원 id. 빈 DB의 첫 insert가 IDENTITY로 1을 받으며 FixedMemberResolver의 고정 id와 정합함.
	private static final Long FIXED_MEMBER_ID = 1L;

	private final MemberRepository memberRepository;

	private final TermConsentService termConsentService;

	public LocalMemberSeeder(MemberRepository memberRepository, TermConsentService termConsentService) {
		this.memberRepository = memberRepository;
		this.termConsentService = termConsentService;
	}

	@Override
	public void run(ApplicationArguments args) {
		Member member = this.memberRepository.findById(FIXED_MEMBER_ID)
			.orElseGet(() -> this.memberRepository
				.save(Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build()));
		this.termConsentService.ensureRequiredConsents(member);
	}

}
