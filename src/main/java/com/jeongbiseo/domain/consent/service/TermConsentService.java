package com.jeongbiseo.domain.consent.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.MemberTermConsent;
import com.jeongbiseo.domain.consent.repository.MemberTermConsentRepository;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;
import com.jeongbiseo.domain.member.entity.Member;

/**
 * 약관 동의를 기록하고 회원이 필수 약관을 전부 동의했는지 판정하는 도메인 서비스임. 소셜 첫 로그인(회원가입) 흐름에서 필수 3종을 기록하고, 이후 약관
 * 버전이 오르면 회원의 옛 동의가 현재 버전과 달라져 재동의가 필요함을 판정함. 컨트롤러·엔드포인트는 두지 않음 — 명세서 계약에 약관 API가 없어 소셜
 * 인증 흐름(팀 레포 Wave 2)에 붙기 전까지는 도메인까지만 구현함(결정 2.B-11, 팀 확인 항목).
 */
@Service
public class TermConsentService {

	private final TermVersionRepository termVersionRepository;

	private final MemberTermConsentRepository memberTermConsentRepository;

	private final Clock clock;

	public TermConsentService(TermVersionRepository termVersionRepository,
			MemberTermConsentRepository memberTermConsentRepository, Clock clock) {
		this.termVersionRepository = termVersionRepository;
		this.memberTermConsentRepository = memberTermConsentRepository;
		this.clock = clock;
	}

	/**
	 * 회원의 필수 약관 3종 동의를 현재 버전으로 기록함(동의 시각은 주입 Clock 기준). 항목당 1건 upsert이라 재동의도 이 메서드로 처리함.
	 * @param member 동의한 회원
	 */
	@Transactional
	public void recordRequiredConsents(Member member) {
		LocalDateTime decidedAt = LocalDateTime.now(this.clock);
		for (TermType termType : TermType.required()) {
			String versionId = currentVersionId(termType);
			this.memberTermConsentRepository.findByMemberIdAndTermType(member.getId(), termType)
				.ifPresentOrElse(consent -> consent.reconsent(versionId, decidedAt),
						() -> this.memberTermConsentRepository.save(MemberTermConsent.builder()
							.member(member)
							.termType(termType)
							.versionId(versionId)
							.decidedAt(decidedAt)
							.build()));
		}
	}

	/**
	 * 회원이 필수 약관 3종을 전부 현재 버전으로 동의했는지 판정함. 한 항목이라도 동의 이력이 없거나 동의 버전이 현재 버전과 다르면(약관이 올라간 뒤
	 * 재동의 안 함) false를 반환함.
	 * @param memberId 대상 회원
	 * @return 필수 약관 전부를 현재 버전으로 동의했으면 true
	 */
	@Transactional(readOnly = true)
	public boolean hasAgreedAllRequired(Long memberId) {
		for (TermType termType : TermType.required()) {
			String currentVersionId = currentVersionId(termType);
			boolean agreedCurrent = this.memberTermConsentRepository.findByMemberIdAndTermType(memberId, termType)
				.map(MemberTermConsent::getVersionId)
				.filter(currentVersionId::equals)
				.isPresent();
			if (!agreedCurrent) {
				return false;
			}
		}
		return true;
	}

	private String currentVersionId(TermType termType) {
		return this.termVersionRepository.findTopByTermTypeOrderByEffectiveAtDescIdDesc(termType)
			.map(version -> version.getVersionId())
			.orElseThrow(() -> new IllegalStateException("현재 약관 버전이 등록되지 않았어요: " + termType));
	}

}
