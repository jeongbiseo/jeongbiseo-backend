package com.jeongbiseo.global.security;

import org.springframework.stereotype.Component;

/**
 * 개발용 고정 회원(memberId 1)을 주입함. 소셜 로그인 도입 전까지 인증필요 엔드포인트를 개발하기 위한 한 곳짜리 회원 식별 지점임(결정 7번 —
 * 소셜 인증은 마지막). 인증 도입 지점을 한 곳으로 격리해 나중에 갈아끼우기 쉽게 함.
 */
@Component
public class FixedMemberResolver {

	// ponytail: 고정 memberId. 실제 인증(JWT 등) 도입 시 이 메서드 내부만 SecurityContext에서 꺼내도록 교체함.
	// 상한은 개발 목적의 단일 고정 회원 1명.
	private static final Long FIXED_MEMBER_ID = 1L;

	/**
	 * 현재 요청의 회원 id를 반환함(항상 고정값).
	 * @return 고정 회원 id
	 */
	public Long resolveMemberId() {
		return FIXED_MEMBER_ID;
	}

}
