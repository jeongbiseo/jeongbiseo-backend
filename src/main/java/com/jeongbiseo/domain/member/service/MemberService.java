package com.jeongbiseo.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.auth.repository.AuthRepository;
import com.jeongbiseo.domain.auth.repository.RefreshTokenRepository;
import com.jeongbiseo.domain.member.entity.Member;

/**
 * 회원 탈퇴를 담당하는 도메인 서비스임. soft delete(deletedAt 갱신)에 더해 Model A 탈퇴 정책(팀 확정)으로
 * auth(social_account)와 refresh_token 행을 하드 삭제함 — auth 행이 없어지므로 같은 소셜 계정 재로그인은
 * AuthService.handleCallback에서 자동으로 신규 가입이 됨(콜백에는 별도 deletedAt 가드를 두지 않음). 온보딩·즐겨찾기·기수령 등
 * 나머지 의존 데이터는 보존함(조회 경로가 member 비탈퇴 검증을 통과하므로 외부 노출 없음, 데이터모델 삭제 정책). 탈퇴 사유는 저장하지 않고 서버
 * 로그로만 남김(명세서). 탈퇴 시각은 Asia/Seoul Clock 기준임.
 */
@Service
public class MemberService {

	private static final Logger log = LoggerFactory.getLogger(MemberService.class);

	private final MemberReader memberReader;

	private final AuthRepository authRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	private final Clock clock;

	public MemberService(MemberReader memberReader, AuthRepository authRepository,
			RefreshTokenRepository refreshTokenRepository, Clock clock) {
		this.memberReader = memberReader;
		this.authRepository = authRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.clock = clock;
	}

	/**
	 * 내 회원 정보를 조회함(getMe). 회원이 없으면 MEMBER404_1, 탈퇴면 MEMBER400_1을 던짐(MemberReader). 온보딩 완료
	 * 여부는 Member 플래그를 그대로 쓰므로 온보딩 전 회원도 정상 반환함.
	 * @param memberId 조회할 회원
	 * @return 활성 회원
	 */
	@Transactional(readOnly = true)
	public Member getMe(Long memberId) {
		return this.memberReader.getActiveMember(memberId);
	}

	/**
	 * 회원을 탈퇴 처리함(soft delete 더하기 Model A auth·refresh 하드 삭제). 회원이 없으면 MEMBER404_1, 이미 탈퇴면
	 * MEMBER400_1을 던짐(MemberReader).
	 * @param memberId 탈퇴할 회원
	 * @param reason 탈퇴 사유(선택, 로그 전용, null 허용)
	 */
	@Transactional
	public void delete(Long memberId, String reason) {
		Member member = memberReader.getActiveMember(memberId);
		member.softDelete(LocalDateTime.now(this.clock));
		this.refreshTokenRepository.deleteByMemberId(memberId);
		this.authRepository.deleteByMemberId(memberId);
		log.info("회원 탈퇴: memberId={}, reason={}", memberId, sanitizeForLog(reason));
	}

	// 자유 입력 사유를 로그에 남기기 전 개행(CR·LF)을 공백으로 치환해 로그 위조를 막음(사유는 명세상 로그 전용, DB 미저장). 길이는 DTO
	// @Size로 이미 제한됨. 사유 원문의 PII 우려로 "로그 자체 금지"는 명세("서버 로그로만 남김")와 상충해 팀 확인 대상임.
	private static String sanitizeForLog(String reason) {
		return (reason == null) ? null : reason.replaceAll("[\\r\\n]", " ");
	}

}
