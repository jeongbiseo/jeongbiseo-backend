package com.jeongbiseo.domain.onboarding;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.repository.OnboardingProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 온보딩 영속성 통합 테스트임(@SpringBootTest + Testcontainers 실제 MySQL, Docker 필요). 컨텍스트 기동만으로 모든 파생
 * 쿼리명이 파싱되는지 검증하고, member_id 파생 조회·UNIQUE 제약·D3 regionCode null 저장을 실제 DB에서 고정함. 각
 * 테스트는 @Transactional로 롤백해 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OnboardingPersistenceIntegrationTest {

	// testcontainers-junit-jupiter 의존성을 추가하지 않고 컨테이너를 수동 기동함(@ServiceConnection이 연결 정보를
	// 주입).
	// Ryuk가 JVM 종료 시 컨테이너를 정리함.
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OnboardingProfileRepository onboardingProfileRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 회원별_온보딩_존재_확인과_조회가_동작한다() {
		Member member = memberRepository.save(newMember());
		onboardingProfileRepository.save(profileOf(member, "11680", "서울특별시", "강남구"));

		assertThat(onboardingProfileRepository.existsByMemberId(member.getId())).isTrue();
		assertThat(onboardingProfileRepository.findByMemberId(member.getId())).isPresent();
		assertThat(onboardingProfileRepository.existsByMemberId(999L)).isFalse();
	}

	@Test
	void 비서울_5자리_지역코드와_명칭이_왕복된다() {
		Member member = memberRepository.save(newMember());
		onboardingProfileRepository.save(profileOf(member, "50130", "제주특별자치도", "서귀포시"));
		onboardingProfileRepository.flush();
		entityManager.clear();

		assertThat(onboardingProfileRepository.findByMemberId(member.getId())).get().satisfies(profile -> {
			assertThat(profile.getRegionCode()).isEqualTo("50130");
			assertThat(profile.getSido()).isEqualTo("제주특별자치도");
			assertThat(profile.getSigungu()).isEqualTo("서귀포시");
		});
	}

	@Test
	void D3_카탈로그_밖_지역이면_regionCode_null도_저장된다() {
		Member member = memberRepository.save(newMember());
		onboardingProfileRepository.save(profileOf(member, null, "없는시도", "없는시군구"));

		assertThat(onboardingProfileRepository.findByMemberId(member.getId())).get()
			.extracting(OnboardingProfile::getRegionCode)
			.isNull();
	}

	@Test
	void 같은_회원에_온보딩_프로필_2건이면_UNIQUE_제약으로_거부된다() {
		Member member = memberRepository.save(newMember());
		onboardingProfileRepository.saveAndFlush(profileOf(member, "11680", "서울특별시", "강남구"));

		assertThatThrownBy(() -> onboardingProfileRepository.saveAndFlush(profileOf(member, "11620", "서울특별시", "관악구")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static Member newMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build();
	}

	private static OnboardingProfile profileOf(Member member, String regionCode, String sido, String sigungu) {
		return OnboardingProfile.builder()
			.member(member)
			.birthDate(LocalDate.of(1999, 3, 15))
			.regionCode(regionCode)
			.sido(sido)
			.sigungu(sigungu)
			.employmentStatus(EmploymentStatus.EMPLOYED)
			.build();
	}

}
