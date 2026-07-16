package com.jeongbiseo.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.member.entity.Member;

/**
 * 회원 탈퇴를 담당하는 도메인 서비스임. soft delete(deletedAt 갱신)만 하며 온보딩·즐겨찾기·기수령 등 의존 데이터는 보존함(데이터모델 삭제
 * 정책 — 조회 경로가 member 비탈퇴 검증을 통과하므로 외부 노출 없음). 탈퇴 사유는 저장하지 않고 서버 로그로만 남김(명세서). 리프레시 토큰 삭제는
 * 소셜 인증 미구현이라 대상 없음(향후 Wave 2). 탈퇴 시각은 Asia/Seoul Clock 기준임.
 */
@Service
public class MemberService {

	private static final Logger log = LoggerFactory.getLogger(MemberService.class);

	private final MemberReader memberReader;

	private final Clock clock;

	public MemberService(MemberReader memberReader, Clock clock) {
		this.memberReader = memberReader;
		this.clock = clock;
	}

	/**
	 * 회원을 탈퇴 처리함(soft delete). 회원이 없으면 MEMBER404_1, 이미 탈퇴면 MEMBER400_1을 던짐(MemberReader).
	 * @param memberId 탈퇴할 회원
	 * @param reason 탈퇴 사유(선택, 로그 전용, null 허용)
	 */
	@Transactional
	public void delete(Long memberId, String reason) {
		Member member = memberReader.getActiveMember(memberId);
		member.softDelete(LocalDateTime.now(this.clock));
		log.info("회원 탈퇴: memberId={}, reason={}", memberId, reason);
	}

}
