package com.jeongbiseo.domain.consent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 회원별 약관 동의 엔티티임(member_term_consent). 약관 본문은 저장하지 않고 동의한 버전 식별자와 동의 시각(decidedAt)만
 * 남김(항목별 동의 행과 결정 시각 명시 저장, 결정 2.B-11). (member_id, term_type) 조합이 UNIQUE라 회원의 항목당 최신 동의
 * 1건만 유지하며, 재동의는 버전과 동의 시각을 덮어씀. 필수 3종만 다뤄 동의·거부 구분(agreed)과 철회(withdrawn_at)는 두지 않음 — 선택
 * 약관 제거(2.B-12)로 그 축이 필요 없어짐.
 */
@Getter
@Entity
@Table(name = "member_term_consent",
		uniqueConstraints = @UniqueConstraint(name = "uk_member_term_consent_member_type",
				columnNames = { "member_id", "term_type" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTermConsent extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "term_type", nullable = false, length = 20)
	private TermType termType;

	@Column(name = "version_id", nullable = false, length = 30)
	private String versionId;

	@Column(name = "decided_at", nullable = false)
	private LocalDateTime decidedAt;

	@Builder
	public MemberTermConsent(Member member, TermType termType, String versionId, LocalDateTime decidedAt) {
		this.member = member;
		this.termType = termType;
		this.versionId = versionId;
		this.decidedAt = decidedAt;
	}

	/** 재동의 시 동의 버전과 동의 시각을 갱신함(항목당 1건 유지). */
	public void reconsent(String versionId, LocalDateTime decidedAt) {
		this.versionId = versionId;
		this.decidedAt = decidedAt;
	}

}
