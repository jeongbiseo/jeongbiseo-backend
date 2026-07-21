package com.jeongbiseo.domain.consent.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.dto.response.MarketingConsentResponse;
import com.jeongbiseo.domain.consent.dto.response.TermConsentItem;
import com.jeongbiseo.domain.consent.dto.response.TermConsentsResponse;
import com.jeongbiseo.domain.consent.entity.MemberTermConsent;
import com.jeongbiseo.domain.consent.entity.TermVersion;
import com.jeongbiseo.domain.consent.repository.MemberTermConsentRepository;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.service.MemberReader;
import com.jeongbiseo.global.apiPayload.code.ConsentErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

/**
 * 약관 동의를 기록·조회하고 회원이 필수 약관을 전부 동의했는지 판정하는 도메인 서비스임. 소셜 첫 로그인(회원가입) 흐름에서 필수 3종을 기록하고, 이후
 * 약관 버전이 오르면 회원의 옛 동의가 현재 버전과 달라져 재동의가 필요함을 판정함. 마이페이지 약관 화면(getMyTermConsents)과 마케팅 수신
 * 동의 변경(updateMarketingConsent)의 계약도 여기에 둠(PLAN 19). 마케팅 동의는 버전 없는 가변 상태라 Member 컬럼에 저장하고
 * 이 서비스가 변경을 위임받음.
 */
@Service
public class TermConsentService {

	// 마이페이지에 표시하는 약관 2종임. 만 14세 이상 확인은 필수 약관이나 화면 미표시라 조회 응답에서 제외함(기획 확정, 2026-07-21).
	private static final List<TermType> DISPLAYED_TERMS = List.of(TermType.SERVICE, TermType.PRIVACY);

	private final TermVersionRepository termVersionRepository;

	private final MemberTermConsentRepository memberTermConsentRepository;

	private final MemberReader memberReader;

	private final Clock clock;

	public TermConsentService(TermVersionRepository termVersionRepository,
			MemberTermConsentRepository memberTermConsentRepository, MemberReader memberReader, Clock clock) {
		this.termVersionRepository = termVersionRepository;
		this.memberTermConsentRepository = memberTermConsentRepository;
		this.memberReader = memberReader;
		this.clock = clock;
	}

	/**
	 * 마이페이지 약관 화면 데이터를 조회함. 표시 약관 2종의 동의 상태·동의 시각과 마케팅 수신 동의 상태를 반환함. 동의 이력이 없는 회원은 표시
	 * 약관이 agreed=false, agreedAt=null로 나옴(실제 소셜 가입 흐름의 동의 기록 연결 전까지의 계약).
	 * @param memberId 대상 회원(활성이어야 함)
	 * @return 표시 약관 동의 목록과 마케팅 동의 상태
	 */
	@Transactional(readOnly = true)
	public TermConsentsResponse getMyTermConsents(Long memberId) {
		Member member = this.memberReader.getActiveMember(memberId);
		Map<TermType, LocalDateTime> agreedAtByType = this.memberTermConsentRepository.findByMemberId(memberId)
			.stream()
			.collect(Collectors.toMap(MemberTermConsent::getTermType, MemberTermConsent::getDecidedAt));
		List<TermConsentItem> terms = DISPLAYED_TERMS.stream()
			.map(termType -> TermConsentItem.of(termType, agreedAtByType.get(termType)))
			.toList();
		return new TermConsentsResponse(terms, member.isMarketingConsent(), member.getMarketingConsentUpdatedAt());
	}

	/**
	 * 마케팅 수신 동의를 목표 상태로 설정함(멱등 set). 상태가 실제로 바뀔 때만 변경 시각을 갱신하고 같은 값 재전송은 시각을 보존함. 회원 엔티티가
	 * 영속 상태라 dirty checking으로 반영되며, 응답은 반영 후의 엔티티 상태로 구성함.
	 * @param memberId 대상 회원(활성이어야 함)
	 * @param agreed 설정할 동의 여부
	 * @return 반영 후의 동의 여부와 변경 시각
	 */
	@Transactional
	public MarketingConsentResponse updateMarketingConsent(Long memberId, boolean agreed) {
		Member member = this.memberReader.getActiveMember(memberId);
		member.updateMarketingConsent(agreed, LocalDateTime.now(this.clock));
		return new MarketingConsentResponse(member.isMarketingConsent(), member.getMarketingConsentUpdatedAt());
	}

	/**
	 * 시더 전용으로 회원에게 누락된 필수 약관 동의만 현재 버전으로 추가함(add-missing). 이미 있는 동의는 건드리지 않아 매 기동마다
	 * decidedAt이 갱신되는 것을 피함 — 기존 회원이 있는 배포에서도 마이페이지 약관 조회가 동의 상태로 나오게 하기 위함임.
	 * @param member 대상 회원
	 */
	@Transactional
	public void ensureRequiredConsents(Member member) {
		LocalDateTime decidedAt = LocalDateTime.now(this.clock);
		for (TermType termType : TermType.required()) {
			if (this.memberTermConsentRepository.findByMemberIdAndTermType(member.getId(), termType).isPresent()) {
				continue;
			}
			this.memberTermConsentRepository.save(MemberTermConsent.builder()
				.member(member)
				.termType(termType)
				.versionId(currentVersionId(termType))
				.decidedAt(decidedAt)
				.build());
		}
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
		LocalDateTime asOf = LocalDateTime.now(this.clock);
		return this.termVersionRepository
			.findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(termType, asOf)
			.map(TermVersion::getVersionId)
			.orElseThrow(() -> new CustomException(ConsentErrorCode.TERM_VERSION_NOT_REGISTERED));
	}

}
