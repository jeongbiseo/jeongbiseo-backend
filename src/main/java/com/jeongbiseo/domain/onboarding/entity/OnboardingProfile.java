package com.jeongbiseo.domain.onboarding.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.global.common.entity.BaseEntity;

/**
 * 온보딩 프로필 엔티티임(데이터모델 3.4 onboarding_profile). Member와 1:1 소유측(member_id UNIQUE FK).
 * member는 조회 응답에 이름이 항상 필요해 기본 EAGER로 둠(open-in-view=false라 지연 로딩이면 컨트롤러 매핑에서
 * LazyInitializationException). region_code는 매칭 정본 컬럼이나 nullable임(결정 D3). RegionCatalog
 * 미등록 sido/sigungu 조합이면 null 저장하고, RecommendationPolicy가 REGIONAL 매칭에서만 자연
 * 탈락시킴(NATIONWIDE는 정상). NOT NULL로 거부하면 카탈로그 밖 지역 사용자의 온보딩이 전면 차단돼 "누락 최대 죄악" 원칙과 충돌하므로
 * nullable을 택함.
 */
@Getter
@Entity
@Table(name = "onboarding_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingProfile extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "member_id", nullable = false, unique = true)
	private Member member;

	@Column(name = "birth_date", nullable = false)
	private LocalDate birthDate;

	// 매칭 정본. nullable(D3). 미해석 조합이면 null이며 REGIONAL 매칭에서만 자연 탈락함
	@Column(name = "region_code", length = 10)
	private String regionCode;

	@Column(name = "sido", nullable = false, length = 20)
	private String sido;

	@Column(name = "sigungu", nullable = false, length = 30)
	private String sigungu;

	@Enumerated(EnumType.STRING)
	@Column(name = "employment_status", nullable = false, length = 20)
	private EmploymentStatus employmentStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "income_bracket", length = 20)
	private IncomeBracket incomeBracket;

	@Column(name = "household_size")
	private Integer householdSize;

	@Builder
	public OnboardingProfile(Member member, LocalDate birthDate, String regionCode, String sido, String sigungu,
			EmploymentStatus employmentStatus, IncomeBracket incomeBracket, Integer householdSize) {
		this.member = member;
		this.birthDate = birthDate;
		this.regionCode = regionCode;
		this.sido = sido;
		this.sigungu = sigungu;
		this.employmentStatus = employmentStatus;
		this.incomeBracket = incomeBracket;
		this.householdSize = householdSize;
	}

	/**
	 * 온보딩 정보를 전체 교체함(PUT 의미론, AUTH-161). 생략된 선택 필드는 호출부가 null로 넘겨 그대로 null로 교체됨. member는
	 * 소유 식별자라 교체 대상이 아님.
	 */
	public void replaceWith(LocalDate birthDate, String regionCode, String sido, String sigungu,
			EmploymentStatus employmentStatus, IncomeBracket incomeBracket, Integer householdSize) {
		this.birthDate = birthDate;
		this.regionCode = regionCode;
		this.sido = sido;
		this.sigungu = sigungu;
		this.employmentStatus = employmentStatus;
		this.incomeBracket = incomeBracket;
		this.householdSize = householdSize;
	}

}
