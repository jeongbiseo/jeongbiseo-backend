package com.jeongbiseo.domain.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 기수령 지원금 엔티티임(ERD RECEIVED_SUBSIDY). member와 subsidy를 잇는 조인 엔티티이며 연관관계 대신 값 컬럼(memberId,
 * subsidyId)으로 둬 storage 계층을 단순화함(ponytail). UNIQUE(member_id, subsidy_id)로 중복 등록을 막음.
 * 프로필과 독립된 소형 애그리게이트로 취급함(DISCUSS.md 3.2, PUT 전체 교체가 프로필과 다른 쓰기 계약). 팀 컨벤션에 맞춰 "Entity"
 * 접미어 없이 명명함(Member, OnboardingProfile과 동일 관례).
 */
@Getter
@Entity
@Table(name = "received_subsidy",
		uniqueConstraints = @UniqueConstraint(name = "uk_received_subsidy_member_subsidy",
				columnNames = { "member_id", "subsidy_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedSubsidy extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "subsidy_id", nullable = false)
	private Long subsidyId;

	@Builder
	public ReceivedSubsidy(Long memberId, Long subsidyId) {
		this.memberId = memberId;
		this.subsidyId = subsidyId;
	}

}
