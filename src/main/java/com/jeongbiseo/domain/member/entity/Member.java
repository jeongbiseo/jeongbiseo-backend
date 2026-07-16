package com.jeongbiseo.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 회원 엔티티임(데이터모델 3.1 member). 소셜 로그인 전용 계정이라 비밀번호 계열 없음. 이름은 실명(2~12자)이며 UNIQUE 없음(동명이인
 * 허용, v1.4) — 온보딩 최초 제출의 사용자 입력이 정본이고 소셜 프로필명을 자동 저장하지 않음. 소셜 인증 도입 전까지는 local 시더가 넣는 고정
 * 회원 1명으로 개발함(결정 7번, FixedMemberResolver). 탈퇴는 soft delete(deletedAt 갱신)로 처리하고 의존 데이터는
 * 보존함(AUTH-172).
 */
@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// IdP 제공 이메일(참고 정보, 미제공 가능). 로그인 식별자가 아니라 NOT NULL·UNIQUE 없음(데이터모델 v1.1)
	@Column(name = "email", length = 100)
	private String email;

	// 실명 2~12자. UNIQUE 없음(동명이인 허용). 온보딩 전에는 null임(소셜 첫 로그인 시 회원만 생성됨) v1.4
	@Column(name = "name", length = 12)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	@Column(name = "onboarding_completed", nullable = false)
	private boolean onboardingCompleted;

	// soft delete 시각(회원 탈퇴). null이면 활성 회원임(데이터모델 삭제 정책, AUTH-172)
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder
	public Member(String email, String name, Role role, boolean onboardingCompleted) {
		this.email = email;
		this.name = name;
		this.role = (role == null) ? Role.ROLE_USER : role;
		this.onboardingCompleted = onboardingCompleted;
	}

	/** 탈퇴 처리함(soft delete, deletedAt 갱신). 이미 탈퇴했으면 시각을 덮어쓰지 않음(멱등). */
	public void softDelete(LocalDateTime deletedAt) {
		if (this.deletedAt == null) {
			this.deletedAt = deletedAt;
		}
	}

	/** 이미 탈퇴한 회원인지 판정함. */
	public boolean isDeleted() {
		return this.deletedAt != null;
	}

	/** 이름을 설정함(온보딩 제출·수정 시점의 사용자 입력이 정본, v1.4). */
	public void updateName(String name) {
		this.name = name;
	}

	/** 온보딩 완료로 표시함(submitOnboarding 성공 시). */
	public void completeOnboarding() {
		this.onboardingCompleted = true;
	}

}
