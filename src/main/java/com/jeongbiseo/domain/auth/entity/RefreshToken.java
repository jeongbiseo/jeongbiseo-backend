package com.jeongbiseo.domain.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * D7 원문 저장 금지). 로그인 시 재발급은 이 엔티티의 {@link #rotate}로 표현하되, 재발급(reissue)의 동시 갱신 레이스를 막는 원자적
 * 조건부 UPDATE 자체는 RefreshTokenRepository.rotateByTokenHash가 담당함(설계 D9). 회전 유예를 위해 직전 해시와
 * 회전 시각을 함께 보관함.
 */
@Getter
@Entity
@Table(name = "refresh_token",
		indexes = @Index(name = "idx_refresh_token_prev_token_hash", columnList = "prev_token_hash"))
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

	/**
	 * 직전 회전에서 폐기된 해시임(회전 유예용). 기존 행에도 붙어야 해 nullable임. 프론트의 중복 발사로 같은 쿠키가 동시에 두 번 들어오면 원자
	 * 회전에서 진 쪽이 이 값으로 조회돼 유예창 안에서 액세스 토큰만 재발급받음(쿠키는 이긴 쪽 것을 유지).
	 */
	@Column(name = "prev_token_hash", length = 255)
	private String prevTokenHash;

	/** 직전 회전 시각임. 유예창 판정 기준이며 기존 행에도 붙어야 해 nullable임. */
	@Column(name = "prev_rotated_at")
	private LocalDateTime prevRotatedAt;

	@Builder
	public RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	/** 로그인 시 재발급함(구 해시 폐기, 새 해시·만료로 교체). 새 로그인은 유예 대상이 아니라 직전 해시를 지움. */
	public void rotate(String newTokenHash, LocalDateTime newExpiresAt) {
		this.tokenHash = newTokenHash;
		this.expiresAt = newExpiresAt;
		this.prevTokenHash = null;
		this.prevRotatedAt = null;
	}

}
