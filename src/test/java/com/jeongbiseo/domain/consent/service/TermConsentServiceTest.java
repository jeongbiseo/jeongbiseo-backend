package com.jeongbiseo.domain.consent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.dto.response.MarketingConsentResponse;
import com.jeongbiseo.domain.consent.dto.response.TermConsentsResponse;
import com.jeongbiseo.domain.consent.entity.MemberTermConsent;
import com.jeongbiseo.domain.consent.entity.TermVersion;
import com.jeongbiseo.domain.consent.repository.MemberTermConsentRepository;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.service.MemberReader;

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
 * TermConsentService 단위 테스트임(Mockito, 고정 Clock, DB 비의존). 필수 3종 신규 기록·재동의 갱신·필수 충족 판정에 더해
 * 마이페이지 약관 조회와 마케팅 수신 동의 변경을 고정함.
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

	@Mock
	private MemberReader memberReader;

	private TermConsentService termConsentService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		this.termConsentService = new TermConsentService(termVersionRepository, memberTermConsentRepository,
				memberReader, clock);
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

	@Test
	void getMyTermConsents_동의한_약관은_동의시각을_미동의는_null을_반환한다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(1L)).willReturn(member);
		// SERVICE만 동의 이력이 있고 PRIVACY는 없음
		MemberTermConsent service = MemberTermConsent.builder()
			.member(member)
			.termType(TermType.SERVICE)
			.versionId(CURRENT_VERSION)
			.decidedAt(EXPECTED_DECIDED_AT)
			.build();
		given(memberTermConsentRepository.findByMemberId(1L)).willReturn(List.of(service));

		TermConsentsResponse response = termConsentService.getMyTermConsents(1L);

		assertThat(response.terms()).hasSize(2)
			.extracting(item -> item.type(), item -> item.agreed(), item -> item.agreedAt())
			.containsExactly(org.assertj.core.groups.Tuple.tuple(TermType.SERVICE, true, EXPECTED_DECIDED_AT),
					org.assertj.core.groups.Tuple.tuple(TermType.PRIVACY, false, null));
		assertThat(response.marketingConsent()).isFalse();
		assertThat(response.marketingConsentUpdatedAt()).isNull();
	}

	@Test
	void getMyTermConsents_마케팅_동의가_켜져_있으면_상태와_시각을_반환한다() {
		Member member = activeMember();
		member.updateMarketingConsent(true, EXPECTED_DECIDED_AT);
		given(memberReader.getActiveMember(1L)).willReturn(member);
		given(memberTermConsentRepository.findByMemberId(1L)).willReturn(List.of());

		TermConsentsResponse response = termConsentService.getMyTermConsents(1L);

		assertThat(response.marketingConsent()).isTrue();
		assertThat(response.marketingConsentUpdatedAt()).isEqualTo(EXPECTED_DECIDED_AT);
	}

	@Test
	void updateMarketingConsent_목표상태로_설정하고_변경시각을_갱신한다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(1L)).willReturn(member);

		MarketingConsentResponse response = termConsentService.updateMarketingConsent(1L, true);

		assertThat(response.agreed()).isTrue();
		assertThat(response.updatedAt()).isEqualTo(EXPECTED_DECIDED_AT);
		assertThat(member.isMarketingConsent()).isTrue();
		assertThat(member.getMarketingConsentUpdatedAt()).isEqualTo(EXPECTED_DECIDED_AT);
	}

	@Test
	void updateMarketingConsent_on_off_on_반복해도_최종_목표상태가_멱등하게_반영된다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(1L)).willReturn(member);

		termConsentService.updateMarketingConsent(1L, true);
		termConsentService.updateMarketingConsent(1L, false);
		MarketingConsentResponse last = termConsentService.updateMarketingConsent(1L, true);

		assertThat(last.agreed()).isTrue();
		assertThat(member.isMarketingConsent()).isTrue();
		assertThat(member.getMarketingConsentUpdatedAt()).isEqualTo(EXPECTED_DECIDED_AT);
	}

	// 마케팅 상태 왕복 검증용 실제 회원(목이 아니라 setter가 필요함). id는 조회 파라미터로만 쓰여 별도 주입 불필요함.
	private static Member activeMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build();
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
