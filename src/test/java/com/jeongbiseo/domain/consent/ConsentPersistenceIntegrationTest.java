package com.jeongbiseo.domain.consent;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.consent.entity.MemberTermConsent;
import com.jeongbiseo.domain.consent.entity.TermVersion;
import com.jeongbiseo.domain.consent.repository.MemberTermConsentRepository;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 약관 영속성 통합 테스트임(@SpringBootTest + Testcontainers 실제 MySQL, Docker 필요). 현재 버전 선택이 미래 발효
 * 버전을 제외하는지와 member_term_consent (member_id, term_type) UNIQUE 제약을 실제 DB에서 고정함. 기동 시 시더가
 * 필수 3종의 v1.0(2026-07-15 발효)을 심어두므로 이를 기준으로 검증함. 각 테스트는 @Transactional로 롤백해 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ConsentPersistenceIntegrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	@Autowired
	private TermVersionRepository termVersionRepository;

	@Autowired
	private MemberTermConsentRepository memberTermConsentRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Test
	void 현재_버전_조회는_미래_발효_버전을_제외하고_기준시각_이하_최신을_고른다() {
		// 시더가 심은 SERVICE v1.0(2026-07-15 발효) 위에 미래 발효 v2.0을 추가함
		termVersionRepository.save(TermVersion.builder()
			.termType(TermType.SERVICE)
			.versionId("v2.0")
			.termsHash("hash")
			.effectiveAt(LocalDateTime.of(2999, 1, 1, 0, 0))
			.build());

		assertThat(termVersionRepository.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(
				TermType.SERVICE, LocalDateTime.of(2026, 7, 16, 0, 0)))
			.get()
			.extracting(TermVersion::getVersionId)
			.isEqualTo("v1.0");

		assertThat(termVersionRepository.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(
				TermType.SERVICE, LocalDateTime.of(2999, 6, 1, 0, 0)))
			.get()
			.extracting(TermVersion::getVersionId)
			.isEqualTo("v2.0");
	}

	@Test
	void 같은_회원_같은_항목에_동의_2건이면_UNIQUE_제약으로_거부된다() {
		Member member = memberRepository.save(Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build());
		memberTermConsentRepository.saveAndFlush(consentOf(member, "v1.0"));

		assertThatThrownBy(() -> memberTermConsentRepository.saveAndFlush(consentOf(member, "v1.1")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static MemberTermConsent consentOf(Member member, String versionId) {
		return MemberTermConsent.builder()
			.member(member)
			.termType(TermType.SERVICE)
			.versionId(versionId)
			.decidedAt(LocalDateTime.of(2026, 7, 16, 9, 0))
			.build();
	}

}
