package com.jeongbiseo.domain.member.entity;

/**
 * 회원 권한임(데이터모델 3.1 member.role, enum STRING).
 */
// ponytail: MVP는 일반 사용자 1종만 두며 관리자 등은 필요해질 때 추가함.
public enum Role {

	/** 일반 사용자(기본값). */
	ROLE_USER

}
