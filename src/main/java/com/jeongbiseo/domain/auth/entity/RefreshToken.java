package com.jeongbiseo.domain.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 리프레시 토큰 저장 엔티티임(데이터모델 3.3, 회원당 1행 — member_id UNIQUE). 원문이 아니라 SHA-256 해시만 저장함(설계 D3,
 * D7 원문 저장 금지). 갱신(회전)은 이 엔티티의 {@link #rotate}로 표현하되, 동시 갱신 레이스를 막는 원자적 조건부 UPDATE 자체는
 * RefreshTokenRepository.rotateByTokenHash가 담당함(설계 D9).
 */
@Getter
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false, unique = true)
	private Member member;

	@Column(name = "token_hash", nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Builder
	public RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	/** 갱신 시 회전함(구 해시 폐기, 새 해시·만료로 교체). */
	public void rotate(String newTokenHash, LocalDateTime newExpiresAt) {
		this.tokenHash = newTokenHash;
		this.expiresAt = newExpiresAt;
	}

}
