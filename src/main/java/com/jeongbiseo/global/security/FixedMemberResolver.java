package com.jeongbiseo.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 현재 요청의 회원을 SecurityContext에서 식별함(AUTH-W001). 토큰 검증과 컨텍스트 세팅은 JwtAuthenticationFilter가
 * 하고, 여기서는 인증된 principal(memberId)만 읽음. 배포 N의 무헤더 고정 회원 폴백 백도어는 인증 강제화로 제거함 — 이제 인증이 없으면
 * 특정 회원으로 대입하지 않고 COMMON401을 던짐(사내 레퍼런스의 "인증 부재는 null 신원" 불변식 정합).
 *
 * ponytail: 클래스명은 배포 N 시절의 "고정 회원"에서 유래했으나, 광범위한 리네임(컨트롤러·테스트 import 다수) 리스크를 피해 이름은 유지하고
 * 동작만 SecurityContext 기반으로 바꿈. 후속 정리에서 AuthMemberResolver 등으로 개명 가능함.
 */
@Component
public class FixedMemberResolver {

	/**
	 * 현재 인증된 회원 id를 반환함(인증 필요 엔드포인트용). 보호 경로는 authorizeHttpRequests가 미인증을 먼저 걸러
	 * EntryPoint가 401을 주므로 여기까지 무인증으로 오는 일은 없으나, 방어적으로 무인증이면 COMMON401을 던짐.
	 * @return 회원 id
	 * @throws CustomException 인증이 없으면 COMMON401
	 */
	public Long resolveMemberId() {
		Long memberId = currentMemberId();
		if (memberId == null) {
			throw new CustomException(CommonErrorCode.UNAUTHENTICATED);
		}
		return memberId;
	}

	/**
	 * 현재 인증된 회원 id를 반환하되, 인증이 없으면 null을 반환함(선택 인증 엔드포인트용, getSubsidyDetail). 비로그인·만료 토큰이면
	 * 회원 없이 진행함(isFavorite=false).
	 * @return 회원 id 또는 인증이 없으면 null
	 */
	public Long resolveOptionalMemberId() {
		return currentMemberId();
	}

	// SecurityContext에서 memberId를 읽음. 인증이 없거나 익명(principal이 "anonymousUser" 문자열)이면 null임
	// —
	// JwtAuthenticationFilter는 유효 토큰일 때만 principal에 Long memberId를 담으므로 instanceof로 구분됨.
	private static Long currentMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return (authentication.getPrincipal() instanceof Long memberId) ? memberId : null;
	}

}
