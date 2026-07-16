package com.jeongbiseo.domain.member.dto.request;

/**
 * 회원 탈퇴 요청 본문임(API명세서 deleteMember). 탈퇴 사유는 선택이며 본문 없이 호출해도 됨. 사유는 DB에 저장하지 않고 서버 로그로만
 * 남김(명세서 서술 — 저장 필요가 생기면 탈퇴 로그 테이블 신설로 처리).
 *
 * @param reason 탈퇴 사유(선택, 서버 로그 전용)
 */
public record DeleteMemberRequest(String reason) {

}
