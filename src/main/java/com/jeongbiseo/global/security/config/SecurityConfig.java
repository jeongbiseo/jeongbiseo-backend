package com.jeongbiseo.global.security.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.jeongbiseo.global.security.SecurityErrorResponder;
import com.jeongbiseo.global.security.jwt.JwtAuthenticationFilter;
import com.jeongbiseo.global.security.jwt.JwtProvider;

/**
 * 인증 강제화 시큐리티 설정임(AUTH-W001, 배포 N+1). REST API라 세션은 STATELESS, CSRF는 비활성화함. Bearer 액세스
 * 토큰은 JwtAuthenticationFilter가 검증해 SecurityContext에 심고, 경로·메소드별 인증 요구는 아래
 * authorizeHttpRequests가 .agents/rules/api-versioning.md 3절 표와 1대1로 강제함. 미인증·인가 거부 응답은
 * SecurityErrorResponder가 CustomResponse 봉투(COMMON401·COMMON403)로 반환함.
 *
 * 공개(permitAll) 대상: 인프라(springdoc·actuator health)와 계약상 인증 불필요 API — 로그인(POST
 * /api/v1/auth/{provider}), 재발급(reissue), getRegions, getSubsidyCategories, 그리고 선택 인증인
 * getSubsidyDetail. 그 외 /api/v1/** 전부와 나머지 요청은 인증 필요함. requestMatchers는 위에서부터 먼저 매칭되므로,
 * 같은 경로 공간의 예외(auth/logout, subsidies/favorites·search)를 넓은 와일드카드보다 앞에 둠.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			@Qualifier("apiConfigurationSource") CorsConfigurationSource corsConfigurationSource,
			JwtProvider jwtProvider) throws Exception {
		SecurityErrorResponder errorResponder = new SecurityErrorResponder();
		http.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// 인프라: 문서·헬스체크는 공개
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**")
				.permitAll()
				// auth: logout만 인증 필요, 나머지(login {provider}·reissue)는 공개. logout을 와일드카드보다
				// 먼저 지정
				// 관례: 앞으로 인증이 필요한 auth POST 엔드포인트를 추가하면(예: 탈퇴 전 재인증) 반드시 아래
				// /api/v1/auth/*
				// 와일드카드
				// permitAll보다 위에 그 경로의 .authenticated() 규칙을 명시함. requestMatchers는 위에서부터
				// 매칭되므로 와일드카드
				// 아래에 두면 permitAll이 먼저 잡혀 무인증으로 노출됨(api-versioning.md 3절)
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
				.authenticated()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/*")
				.permitAll()
				// subsidies: 검색·관심목록은 인증, 카테고리·상세(선택 인증)는 공개. 관심 등록·해제(POST·DELETE)는 아래
				// anyRequest로 인증 필요
				.requestMatchers(HttpMethod.GET, "/api/v1/subsidies")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/v1/subsidies/categories")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/subsidies/favorites")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/v1/subsidies/*")
				.permitAll()
				// regions: 공개
				.requestMatchers(HttpMethod.GET, "/api/v1/regions")
				.permitAll()
				// 그 외 전부 인증 필요
				.anyRequest()
				.authenticated())
			.exceptionHandling(
					handling -> handling.authenticationEntryPoint(errorResponder).accessDeniedHandler(errorResponder))
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

}
