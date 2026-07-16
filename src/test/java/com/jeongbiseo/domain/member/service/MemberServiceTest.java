package com.jeongbiseo.domain.member.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * MemberService 단위 테스트임(Mockito, 고정 Clock). 활성 회원 소프트삭제와 reason null 허용을 고정함. 미존재·이미탈퇴
 * 판정은 MemberReaderTest가 담당하므로 여기서는 getActiveMember를 스텁으로 통과시킴.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	private static final Long MEMBER_ID = 1L;

	// 고정 Clock(Asia/Seoul, UTC+9). 2026-07-16T00:00Z → KST 09:00
	private static final LocalDateTime EXPECTED_DELETED_AT = LocalDateTime.of(2026, 7, 16, 9, 0);

	@Mock
	private MemberReader memberReader;

	private MemberService memberService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		this.memberService = new MemberService(memberReader, clock);
	}

	@Test
	void delete_활성회원을_소프트삭제하고_탈퇴시각을_남긴다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(member);

		memberService.delete(MEMBER_ID, "필요한 지원금을 찾지 못했어요");

		assertThat(member.isDeleted()).isTrue();
		assertThat(member.getDeletedAt()).isEqualTo(EXPECTED_DELETED_AT);
	}

	@Test
	void delete_reason이_null이어도_소프트삭제한다() {
		Member member = activeMember();
		given(memberReader.getActiveMember(MEMBER_ID)).willReturn(member);

		memberService.delete(MEMBER_ID, null);

		assertThat(member.isDeleted()).isTrue();
	}

	private static Member activeMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
	}

}
