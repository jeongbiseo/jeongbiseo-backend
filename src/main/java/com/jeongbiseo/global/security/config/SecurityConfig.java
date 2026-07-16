package com.jeongbiseo.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 시큐리티 뼈대임. 소셜 로그인은 마지막에 붙이므로(결정 7번) 그전까지는 개발용 고정 회원(FixedMemberResolver)으로 동작하고, 여기서는 전면
 * permitAll로 둠.
 *
 * ponytail: 도메인 착수 시 경로별 인증 규칙을 좁혀 지정함(api-versioning 3절 — subsidies 공간처럼 메소드·경로 단위로 분기).
 * blanket permitAll은 인증 도입 전까지의 한시 설정이며, 소셜 로그인 도입 시 JWT 필터와 함께 교체함. REST API라 세션은
 * STATELESS, CSRF는 비활성화함.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

}
