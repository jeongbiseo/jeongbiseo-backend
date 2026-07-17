package com.jeongbiseo.domain.consent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.MemberTermConsent;
import com.jeongbiseo.domain.consent.entity.TermVersion;
import com.jeongbiseo.domain.consent.repository.MemberTermConsentRepository;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;
import com.jeongbiseo.domain.member.entity.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TermConsentService 단위 테스트임(Mockito, 고정 Clock, DB 비의존). 필수 3종 신규 기록·재동의 갱신·필수 충족 판정 세 축을
 * 고정함.
 */
@ExtendWith(MockitoExtension.class)
class TermConsentServiceTest {

	private static final String CURRENT_VERSION = "v1.0";

	// 고정 Clock(Asia/Seoul, UTC+9). 2026-07-16T00:00Z → KST 09:00
	private static final LocalDateTime EXPECTED_DECIDED_AT = LocalDateTime.of(2026, 7, 16, 9, 0);

	@Mock
	private TermVersionRepository termVersionRepository;

	@Mock
	private MemberTermConsentRepository memberTermConsentRepository;

	private TermConsentService termConsentService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		this.termConsentService = new TermConsentService(termVersionRepository, memberTermConsentRepository, clock);
	}

	@Test
	void recordRequiredConsents_신규회원이면_필수3종을_현재버전으로_저장한다() {
		Member member = memberWithId(1L);
		givenCurrentVersionForAll();
		given(memberTermConsentRepository.findByMemberIdAndTermType(eq(1L), any())).willReturn(Optional.empty());

		termConsentService.recordRequiredConsents(member);

		ArgumentCaptor<MemberTermConsent> captor = ArgumentCaptor.forClass(MemberTermConsent.class);
		verify(memberTermConsentRepository, times(3)).save(captor.capture());
		assertThat(captor.getAllValues()).hasSize(3).allSatisfy(consent -> {
			assertThat(consent.getVersionId()).isEqualTo(CURRENT_VERSION);
			assertThat(consent.getDecidedAt()).isEqualTo(EXPECTED_DECIDED_AT);
		})
			.extracting(MemberTermConsent::getTermType)
			.containsExactlyInAnyOrder(TermType.SERVICE, TermType.PRIVACY, TermType.AGE_OVER_14);
	}

	@Test
	void recordRequiredConsents_기존동의가_있으면_저장없이_재동의로_갱신한다() {
		Member member = memberWithId(1L);
		givenCurrentVersionForAll();
		MemberTermConsent stale = MemberTermConsent.builder()
			.member(member)
			.termType(TermType.SERVICE)
			.versionId("v0.9")
			.decidedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
			.build();
		given(memberTermConsentRepository.findByMemberIdAndTermType(eq(1L), any())).willReturn(Optional.of(stale));

		termConsentService.recordRequiredConsents(member);

		verify(memberTermConsentRepository, never()).save(any());
		assertThat(stale.getVersionId()).isEqualTo(CURRENT_VERSION);
		assertThat(stale.getDecidedAt()).isEqualTo(EXPECTED_DECIDED_AT);
	}

	@Test
	void hasAgreedAllRequired_모두_현재버전_동의면_true() {
		givenCurrentVersionForAll();
		given(memberTermConsentRepository.findByMemberIdAndTermType(eq(1L), any()))
			.willReturn(Optional.of(consentWithVersion(CURRENT_VERSION)));

		assertThat(termConsentService.hasAgreedAllRequired(1L)).isTrue();
	}

	@Test
	void hasAgreedAllRequired_한_항목이라도_동의_이력이_없으면_false() {
		given(termVersionRepository.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(any(),
				any()))
			.willReturn(Optional.of(termVersion(CURRENT_VERSION)));
		given(memberTermConsentRepository.findByMemberIdAndTermType(eq(1L), any())).willReturn(Optional.empty());

		assertThat(termConsentService.hasAgreedAllRequired(1L)).isFalse();
	}

	@Test
	void hasAgreedAllRequired_동의버전이_구버전이면_false() {
		given(termVersionRepository.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(any(),
				any()))
			.willReturn(Optional.of(termVersion(CURRENT_VERSION)));
		given(memberTermConsentRepository.findByMemberIdAndTermType(eq(1L), any()))
			.willReturn(Optional.of(consentWithVersion("v0.9")));

		assertThat(termConsentService.hasAgreedAllRequired(1L)).isFalse();
	}

	private void givenCurrentVersionForAll() {
		given(termVersionRepository.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(any(),
				any()))
			.willReturn(Optional.of(termVersion(CURRENT_VERSION)));
	}

	private static TermVersion termVersion(String versionId) {
		return TermVersion.builder()
			.termType(TermType.SERVICE)
			.versionId(versionId)
			.termsHash("hash")
			.effectiveAt(LocalDateTime.of(2026, 7, 15, 0, 0))
			.build();
	}

	private static MemberTermConsent consentWithVersion(String versionId) {
		return MemberTermConsent.builder()
			.member(Member.builder().role(null).onboardingCompleted(false).build())
			.termType(TermType.SERVICE)
			.versionId(versionId)
			.decidedAt(LocalDateTime.of(2026, 7, 16, 9, 0))
			.build();
	}

	// 회원 식별은 id로만 하므로(recordRequiredConsents가 member.getId() 사용) id만 있는 목이면 충분함.
	private static Member memberWithId(Long id) {
		Member member = mock(Member.class);
		lenient().when(member.getId()).thenReturn(id);
		return member;
	}

}
