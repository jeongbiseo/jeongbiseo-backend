package com.jeongbiseo.domain.consent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 약관 버전 엔티티임(term_version). 약관 본문은 버전 고정 파일이 정본이라 저장하지 않고, 항목별 버전 식별자와 무결성 검증용 해시, 발효 시각만
 * 담음. (term_type, version_id) 조합이 UNIQUE라 같은 항목의 같은 버전은 1건만 존재하며, 현재 유효 버전은 effective_at이
 * 가장 최근인 행임.
 */
@Getter
@Entity
@Table(name = "term_version",
		uniqueConstraints = @UniqueConstraint(name = "uk_term_version_type_version",
				columnNames = { "term_type", "version_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermVersion extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "term_type", nullable = false, length = 20)
	private TermType termType;

	@Column(name = "version_id", nullable = false, length = 30)
	private String versionId;

	@Column(name = "terms_hash", nullable = false, length = 64)
	private String termsHash;

	@Column(name = "effective_at", nullable = false)
	private LocalDateTime effectiveAt;

	@Builder
	public TermVersion(TermType termType, String versionId, String termsHash, LocalDateTime effectiveAt) {
		this.termType = termType;
		this.versionId = versionId;
		this.termsHash = termsHash;
		this.effectiveAt = effectiveAt;
	}

}
