package com.jeongbiseo.global.security.jwt;

import java.io.IOException;
import java.util.List;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization Bearer 액세스 토큰을 검증해 SecurityContext에 인증을 심는 필터임(AUTH-W001). 토큰이 유효하면
 * principal에 memberId(Long)를 담은 인증을 세팅해 이후 authorizeHttpRequests가 인증 여부를 판정하고
 * FixedMemberResolver가 그 memberId를 읽음.
 *
 * 토큰이 없으면 아무것도 하지 않고 익명으로 흘려보냄 — 보호 경로는 뒤의 authorizeHttpRequests가 걸러 EntryPoint가
 * COMMON401을 반환하고, 선택 인증 경로(getSubsidyDetail)는 익명 그대로 통과함. 토큰이 있으나 만료·위조·형식 오류면 인증을 심지 않고
 * 통과시킴 — 보호 경로에서 401이 나가 프론트 reissue가 트리거되고, 조용히 특정 회원으로 떨어뜨리지 않음(배포 N의
 * FixedMemberResolver 무헤더 폴백 백도어를 이 필터 도입으로 제거함).
 *
 * ponytail: @Component로 두면 Boot가 서블릿 필터로 자동 등록하면서 SecurityConfig의 addFilterBefore와 이중
 * 등록되므로, 빈으로 만들지 않고 SecurityConfig에서 직접 생성함.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = extractBearerToken(request);
		if (token != null) {
			authenticate(token, request);
		}
		filterChain.doFilter(request, response);
	}

	// 유효 토큰이면 principal=memberId 인증을 심음. 만료·위조·형식 오류(sub가 숫자가 아니면 NumberFormatException,
	// IllegalArgumentException 하위)는 인증을 심지 않고 넘어감 — 보호 경로에서 authz가 401로 거름.
	private void authenticate(String token, HttpServletRequest request) {
		try {
			Long memberId = this.jwtProvider.parseMemberId(token);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(memberId, null,
					List.of());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (JwtException | IllegalArgumentException e) {
			SecurityContextHolder.clearContext();
		}
	}

	// Authorization 헤더에서 Bearer 토큰을 꺼냄. 헤더가 없거나 접두가 다르거나 값이 비면 null(무토큰 취급)임.
	private static String extractBearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}

}
