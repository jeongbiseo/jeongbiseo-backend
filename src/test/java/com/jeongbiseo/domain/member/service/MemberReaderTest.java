package com.jeongbiseo.domain.member.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.global.apiPayload.code.MemberErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * MemberReader 단위 테스트임(Mockito, DB 비의존). 활성 회원 반환·미존재 404·탈퇴 400 세 경로를 고정함.
 */
@ExtendWith(MockitoExtension.class)
class MemberReaderTest {

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MemberReader memberReader;

	@Test
	void 활성_회원이면_그대로_반환한다() {
		Member member = activeMember();
		given(memberRepository.findById(1L)).willReturn(Optional.of(member));

		Member result = memberReader.getActiveMember(1L);

		assertThat(result).isSameAs(member);
	}

	@Test
	void 회원이_없으면_MEMBER404_1을_던진다() {
		given(memberRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> memberReader.getActiveMember(99L)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.getCode());
	}

	@Test
	void 탈퇴한_회원이면_MEMBER400_1을_던진다() {
		Member member = activeMember();
		member.softDelete(LocalDateTime.of(2026, 7, 16, 0, 0));
		given(memberRepository.findById(1L)).willReturn(Optional.of(member));

		assertThatThrownBy(() -> memberReader.getActiveMember(1L)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo(MemberErrorCode.MEMBER_DELETED.getCode());
	}

	private static Member activeMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(false).build();
	}

}
