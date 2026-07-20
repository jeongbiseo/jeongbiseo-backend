package com.jeongbiseo.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.jeongbiseo.domain.member.entity.Member;

/**
 * 내 회원 정보 조회 응답임(operationId getMe). 앱 시작이나 새로고침 직후 액세스 토큰을 복구한 프론트가 로그인 상태와 표시용 회원 정보를
 * 되살리는 용도임. onboardingCompleted는 Member의 플래그를 그대로 실음 — 온보딩 프로필 레코드 존재 여부로 판정하는
 * getMyOnboarding과 달리 온보딩 전 회원에게도 200을 반환해야 하기 때문임(getMyOnboarding은 이 경우 ONB404_1을 던져 인증
 * 상태 복구에 쓸 수 없음).
 *
 * @param memberId 회원 id
 * @param name 표시용 이름. 소셜 프로필에서 받아 저장한 값이며 실명이 아님. 소셜이 미제공이면 null임
 * @param email IdP 제공 이메일. 미제공이면 null임
 * @param onboardingCompleted 온보딩 완료 여부
 */
public record MemberProfileResponse(Long memberId,
		@Schema(description = "표시용 이름. 소셜 프로필에서 받아 저장한 값이며 실명이 아님(구글 name, 카카오 닉네임)", nullable = true) String name,
		@Schema(description = "IdP 제공 이메일. 현재 프론트 화면에서는 쓰지 않음", nullable = true) String email,
		boolean onboardingCompleted) {

	/** 회원 엔티티를 응답으로 변환함. */
	public static MemberProfileResponse from(Member member) {
		return new MemberProfileResponse(member.getId(), member.getName(), member.getEmail(),
				member.isOnboardingCompleted());
	}

}
