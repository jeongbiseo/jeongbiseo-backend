package com.jeongbiseo.domain.favorite.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 관심 등록 조인 엔티티임(데이터모델 3.5). 회원과 지원금을 연결하며 복합 유니크 제약으로 중복 등록을 막음.
 */
@Getter
@Entity
@Builder
@Table(name = "favorite",
		uniqueConstraints = @UniqueConstraint(name = "uk_favorite_member_subsidy",
				columnNames = { "member_id", "subsidy_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Favorite extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subsidy_id", nullable = false)
	private SubsidyEntity subsidy;

}
