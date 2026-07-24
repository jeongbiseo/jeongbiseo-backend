package com.jeongbiseo.global.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FixedMemberResolver의 회원 식별 계약을 고정함(AUTH-W001). 토큰 검증은 JwtAuthenticationFilter가 하고 리졸버는
 * SecurityContext의 인증 principal(Long memberId)만 읽음. 인증이 있으면 그 회원, 인증이 없거나 익명이면
 * resolveMemberId는 COMMON401을 던지고 resolveOptionalMemberId는 null을 반환함(무헤더 고정 회원 폴백 백도어
 * 제거).
 */
class FixedMemberResolverTest {

	private final FixedMemberResolver resolver = new FixedMemberResolver();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 인증된_principal이면_그_회원id를_반환한다() {
		authenticate(42L);

		assertThat(resolver.resolveMemberId()).isEqualTo(42L);
		assertThat(resolver.resolveOptionalMemberId()).isEqualTo(42L);
	}

	@Test
	void 인증이_없으면_resolveMemberId는_COMMON401을_던진다() {
		assertThatThrownBy(resolver::resolveMemberId).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode())
			.isEqualTo(CommonErrorCode.UNAUTHENTICATED);
	}

	// 익명 인증(isAuthenticated=true이나 principal이 "anonymousUser" 문자열)도 인증 부재로 취급함.
	@Test
	void 익명_인증이면_resolveMemberId는_COMMON401을_던진다() {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		assertThatThrownBy(resolver::resolveMemberId).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode())
			.isEqualTo(CommonErrorCode.UNAUTHENTICATED);
	}

	@Test
	void 인증이_없으면_resolveOptionalMemberId는_null을_반환한다() {
		assertThat(resolver.resolveOptionalMemberId()).isNull();
	}

	private static void authenticate(Long memberId) {
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
	}

}
