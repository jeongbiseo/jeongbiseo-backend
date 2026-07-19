package com.jeongbiseo.domain.auth.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.auth.entity.Auth;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;

/**
 * 소셜 첫 로그인 자동가입을 별도 트랜잭션(REQUIRES_NEW)으로 격리하는 컴포넌트임(설계 11장 "동시 첫 로그인" 보강). 같은 (provider,
 * providerId) 동시 가입 레이스에서 uk_social_provider_provider_id UNIQUE 충돌이 나면 이 REQUIRES_NEW
 * 트랜잭션만 롤백되고 바깥 AuthService 트랜잭션은 영향받지 않아, 호출부(AuthService)가 곧바로 기존 auth를 재조회할 수 있음(트랜잭션
 * 롤백 후 재조회 패턴).
 */
@Component
public class AuthMemberProvisioner {

	private final MemberRepository memberRepository;

	private final AuthRepository authRepository;

	public AuthMemberProvisioner(MemberRepository memberRepository, AuthRepository authRepository) {
		this.memberRepository = memberRepository;
		this.authRepository = authRepository;
	}

	/**
	 * 회원과 소셜 계정 연결을 한 트랜잭션으로 생성함. UNIQUE 충돌 시 DataIntegrityViolationException이 그대로
	 * 전파되며(REQUIRES_NEW라 이 트랜잭션만 롤백), 호출부가 기존 auth를 재조회해야 함.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Member createMemberWithAuth(Provider provider, String providerId, String email) {
		Member member = this.memberRepository
			.save(Member.builder().email(email).role(Role.ROLE_USER).onboardingCompleted(false).build());
		this.authRepository.save(Auth.builder().provider(provider).providerId(providerId).member(member).build());
		return member;
	}

}
