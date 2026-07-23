package com.jeongbiseo.support;

import java.util.List;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 웹 슬라이스 테스트(@WebMvcTest, addFilters=false)가 실제 FixedMemberResolver 빈을 통해 고정 회원 1로 동작하게,
 * 각 테스트 전 SecurityContext에 principal=1L 인증을 심는 확장임(AUTH-W001). 인증 강제화로 리졸버가 무헤더 폴백을
 * 버렸으므로, addFilters=false라 JwtAuthenticationFilter가 돌지 않는 슬라이스에서는 이 확장이 그 필터의 인증 세팅을 대신함.
 * 생산 코드는 필터가 하는 일을 테스트에서 미러함.
 */
public class FixedMemberContextExtension implements BeforeEachCallback, AfterEachCallback {

	private static final Long FIXED_MEMBER_ID = 1L;

	@Override
	public void beforeEach(ExtensionContext context) {
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(FIXED_MEMBER_ID, null, List.of()));
	}

	@Override
	public void afterEach(ExtensionContext context) {
		SecurityContextHolder.clearContext();
	}

}
