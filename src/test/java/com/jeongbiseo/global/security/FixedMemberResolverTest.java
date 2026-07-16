package com.jeongbiseo.global.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedMemberResolverTest {

	@Test
	void resolveMemberId는_고정값_1을_반환한다() {
		assertThat(new FixedMemberResolver().resolveMemberId()).isEqualTo(1L);
	}

}
