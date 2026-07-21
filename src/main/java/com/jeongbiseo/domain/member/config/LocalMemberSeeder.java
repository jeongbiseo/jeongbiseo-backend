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
 * 개발용, 결정 7번) member 테이블이 비면 회원 스코프 엔드포인트가 전부 MEMBER404_1이 되므로, 고정 회원 1행을 보장해 개발과 배포 데모를
 * 가능하게 함. 고정 회원이 없으면 만들고(IDENTITY 전략이라 빈 DB의 첫 insert가 id=1을 받음) 이미 있으면 그대로 씀. local과
 * prod에만 로드되고 통합 테스트(test 프로필)에는 로드되지 않음.
 *
 * 고정 회원의 필수 약관 동의도 매 기동 보장함 — 실제 소셜 가입 흐름이 아직 동의를 기록하지 않아, 이를 안 하면 마이페이지 약관 조회가 데모 회원에게
 * 미동의로 나오기 때문임(시더가 가입 대역, PLAN 19 C1). 신규 회원뿐 아니라 이미 회원이 있던 기존 배포에도 적용되도록 회원 생성 분기 밖에서
 * 부르며, add-missing 멱등 경로(ensureRequiredConsents)라 기존 동의의 decidedAt을 매 기동 흔들지 않음.
 * term_version이 먼저 있어야 하므로 @Order로 TermVersionInitializer(@Order(1)) 뒤에 실행함.
 */
// ponytail: 개발과 데모용 단일 회원 시드. 소셜 인증 도입 시 실제 가입 흐름이 회원과 동의를 만들면 이 시더는 제거함.
@Component
@Order(2)
@Profile({ "local", "prod" })
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
