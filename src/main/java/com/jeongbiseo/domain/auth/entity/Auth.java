package com.jeongbiseo.domain.auth.entity;

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

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 소셜 계정 연결 엔티티임(데이터모델 3.2 social_account, 클래스명은 domain/auth 패키지 관례에 맞춰 Auth로 둠).
 * provider와 providerId 복합 UNIQUE가 같은 IdP 계정의 중복 가입을 DB 레벨에서 막고, 콜백 조회 키도 이 유니크와
 * 동일함(AuthRepository.findByProviderAndProviderIdWithMember). MVP는 회원당 1행만 생성함(member_id
 * UNIQUE는 두지 않아 후속 다중 연결 확장 여지를 남김). Model A 탈퇴 정책상 회원 탈퇴 시 이 행을 하드 삭제해 같은 소셜 계정 재로그인이
 * 자동으로 신규 가입이 되게 함(MemberService.delete).
 */
@Getter
@Entity
@Table(name = "social_account",
		uniqueConstraints = @UniqueConstraint(name = "uk_social_provider_provider_id",
				columnNames = { "provider", "provider_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auth extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private Provider provider;

	@Column(name = "provider_id", nullable = false, length = 100)
	private String providerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Builder
	public Auth(Provider provider, String providerId, Member member) {
		this.provider = provider;
		this.providerId = providerId;
		this.member = member;
	}

}
