package com.jeongbiseo.domain.member.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;

/**
 * local 프로필 전용 개발 회원 시더임. FixedMemberResolver가 항상 memberId=1을 반환하는데(소셜 인증 도입 전 개발용, 결정
 * 7번) member 테이블이 비면 회원 스코프 엔드포인트가 전부 MEMBER404_1이 되므로, 빈 테이블에 한 번 고정 회원 1행을 넣어 개발을 가능하게
 * 함. IDENTITY 전략이라 빈 DB의 첫 insert가 id=1을 받음 — count()==0 가드로 멱등하게 만들고, 이미 회원이 있으면(탈퇴 soft
 * delete 포함) 다시 넣지 않음. local 전용이라 통합 테스트(test 프로필)에는 로드되지 않음.
 */
// ponytail: 개발용 단일 회원 시드. 소셜 인증 도입 시 실제 가입 흐름이 회원을 만들면 이 시더는 제거함.
@Component
@Profile("local")
public class LocalMemberSeeder implements ApplicationRunner {

	private final MemberRepository memberRepository;

	public LocalMemberSeeder(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (memberRepository.count() == 0) {
			memberRepository.save(Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build());
		}
	}

}
