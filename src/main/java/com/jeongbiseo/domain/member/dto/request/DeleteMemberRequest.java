package com.jeongbiseo.domain.member.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 요청 본문임(API명세서 deleteMember). 탈퇴 사유는 선택이며 본문 없이 호출해도 됨. 사유는 DB에 저장하지 않고 서버 로그로만
 * 남김(명세서 서술 — 저장 필요가 생기면 탈퇴 로그 테이블 신설로 처리). 로그 적재를 막기 위해 길이를 제한하고, 개행 정제는 서비스 로깅 시점에 함.
 *
 * @param reason 탈퇴 사유(선택, 200자 이하, 서버 로그 전용)
 */
public record DeleteMemberRequest(@Size(max = 200, message = "탈퇴 사유는 200자 이하여야 해요") String reason) {

}
