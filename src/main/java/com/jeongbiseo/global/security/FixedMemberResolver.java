package com.jeongbiseo.global.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.exception.AuthErrorCode;
import com.jeongbiseo.global.security.jwt.JwtProvider;

/**
 * 현재 요청의 회원을 식별함. Authorization Bearer 액세스 토큰이 있으면 그 토큰의 회원으로, 없으면 개발용 고정 회원(memberId
 * 1)으로 해석함.
 *
 * 헤더가 없을 때 401이 아니라 고정 회원으로 떨어뜨리는 것은 배포 N의 의도된 백도어임. SecurityConfig가 아직
 * anyRequest().permitAll()이고 무토큰 데모(고정 회원 시드)와 웹 슬라이스 테스트가 이 전제로 돌기 때문임. 사내 레퍼런스
 * (mind-signal의 authenticate·optionalAuthenticate, truthscope의 oauth2ResourceServer와
 * ADR-027)는 공통적으로 "인증 부재는 null 신원이지 특정 회원 대입이 아니다"를 지키므로, 이 폴백은 그 불변식의 의도적 예외이며 배포
 * N+1(JWT 필터와 permitAll 축소)에서 제거함.
 *
 * 반면 헤더가 있는데 토큰이 만료·위조·손상이면 폴백하지 않고 401(AUTH401_2)을 던짐. 조용히 고정 회원으로 떨어뜨리면 (가) 액세스 토큰 30분
 * 만료 후 서버가 401을 주지 않아 프론트의 reissue가 영원히 트리거되지 않고 모든 사용자가 소리 없이 회원 1이 되며, (나) 만료 토큰으로 회원
 * 탈퇴를 호출하면 고정 회원 1이 soft delete되어 이후 무토큰 데모 호출이 전부 MEMBER400_1로 죽기 때문임.
 */
@Component
public class FixedMemberResolver {

	// ponytail: 무헤더 폴백용 고정 memberId. 배포 N+1에서 JWT 필터가 붙으면 이 폴백째로 제거함.
	private static final Long FIXED_MEMBER_ID = 1L;

	private static final String BEARER_PREFIX = "Bearer ";

	// 슬라이스 테스트 6개가 @Import(FixedMemberResolver.class)로 이 빈만 올리므로, JwtProvider를 직접 주입받으면 그
	// 컨텍스트들이 전부 깨짐. ObjectProvider는 대상 빈이 없어도 주입되고 getIfAvailable()이 null을 주므로 무수정으로 보존됨.
	private final ObjectProvider<JwtProvider> jwtProviderProvider;

	public FixedMemberResolver(ObjectProvider<JwtProvider> jwtProviderProvider) {
		this.jwtProviderProvider = jwtProviderProvider;
	}

	/**
	 * 현재 요청의 회원 id를 반환함. Bearer 토큰이 있으면 그 회원, 없으면 고정 회원임.
	 * @return 회원 id
	 * @throws CustomException 토큰이 있으나 만료·위조·형식 오류일 때 AUTH401_2
	 */
	public Long resolveMemberId() {
		String token = extractBearerToken();
		if (token == null) {
			return FIXED_MEMBER_ID;
		}
		JwtProvider jwtProvider = this.jwtProviderProvider.getIfAvailable();
		if (jwtProvider == null) {
			return FIXED_MEMBER_ID;
		}
		try {
			return jwtProvider.parseMemberId(token);
		}
		catch (JwtException | IllegalArgumentException e) {
			// 만료·위조·형식 오류를 사유 구분 없이 재로그인 안내로 통합함(설계 4장 AUTH401_2). NumberFormatException은
			// IllegalArgumentException 하위라 sub가 숫자가 아닌 토큰도 여기서 걸림.
			throw new CustomException(AuthErrorCode.REFRESH_TOKEN_FAILED, e);
		}
	}

	// 현재 요청의 Authorization 헤더에서 Bearer 토큰을 꺼냄. 요청 컨텍스트가 없거나(비웹 호출) 헤더가 없거나 접두가 다르면 null을
	// 반환함. 회원 스코프 호출부 11곳은 전부 컨트롤러 요청 스레드 안이라 컨텍스트가 항상 존재함.
	private static String extractBearerToken() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
			return null;
		}
		HttpServletRequest request = servletAttributes.getRequest();
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}

}
