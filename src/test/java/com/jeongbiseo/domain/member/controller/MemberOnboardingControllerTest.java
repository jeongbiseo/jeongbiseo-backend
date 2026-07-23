package com.jeongbiseo.domain.member.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.common.enums.EmploymentStatus;
import com.jeongbiseo.domain.common.enums.IncomeBracket;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.service.MemberService;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MemberController 온보딩 조회·수정 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 응답에 이름이 담기는지(D6)와 200 계약,
 * 수정 필드 검증 400을 고정함.
 */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
@org.junit.jupiter.api.extension.ExtendWith(com.jeongbiseo.support.FixedMemberContextExtension.class)
class MemberOnboardingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OnboardingService onboardingService;

	@MockitoBean
	private MemberService memberService;

	// 온보딩 전 회원도 200이어야 함을 고정함. getMyOnboarding은 이 경우 ONB404_1이라 인증 상태 복구에 쓸 수 없어 getMe를
	// 신설한 것이므로, 이 단언이 깨지면 신설 이유 자체가 사라짐(프론트 요청, 2026-07-19).
	@Test
	void getMe_온보딩_전_회원도_200과_onboardingCompleted_false를_반환한다() throws Exception {
		given(memberService.getMe(any())).willReturn(member(null, false));

		mockMvc.perform(get("/api/v1/members/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.email").value("user@example.com"))
			.andExpect(jsonPath("$.result.name").doesNotExist())
			.andExpect(jsonPath("$.result.onboardingCompleted").value(false));
	}

	@Test
	void getMe_온보딩_완료_회원은_이름과_true를_반환한다() throws Exception {
		given(memberService.getMe(any())).willReturn(member("홍길동", true));

		mockMvc.perform(get("/api/v1/members/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.name").value("홍길동"))
			.andExpect(jsonPath("$.result.onboardingCompleted").value(true));
	}

	private static Member member(String name, boolean onboardingCompleted) {
		return Member.builder()
			.email("user@example.com")
			.name(name)
			.role(Role.ROLE_USER)
			.onboardingCompleted(onboardingCompleted)
			.build();
	}

	@Test
	void getMyOnboarding_이름과_만나이를_담아_200을_반환한다() throws Exception {
		given(onboardingService.getMyOnboarding(any())).willReturn(profile("홍길동"));

		mockMvc.perform(get("/api/v1/members/me/onboarding"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.name").value("홍길동"))
			.andExpect(jsonPath("$.result.sigungu").value("강남구"))
			.andExpect(jsonPath("$.result.age").isNumber());
	}

	@Test
	void updateMyOnboarding_유효요청이면_200을_반환한다() throws Exception {
		given(onboardingService.update(any(), any(), any(), any(), any(), any(), any())).willReturn(profile("김철수"));
		String body = """
				{"birthDate":"1999-03-15","sido":"서울특별시","sigungu":"강남구","employmentStatus":"STUDENT"}""";

		mockMvc.perform(put("/api/v1/members/me/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.name").value("김철수"));
	}

	@Test
	void updateMyOnboarding_고용상태가_없으면_400_VALID400_1() throws Exception {
		String body = """
				{"birthDate":"1999-03-15","sido":"서울특별시","sigungu":"강남구"}""";

		mockMvc.perform(put("/api/v1/members/me/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"));
	}

	@Test
	void deleteMember_본문_없이_호출하면_사유_없이_삭제를_위임하고_200을_반환한다() throws Exception {
		mockMvc.perform(delete("/api/v1/members/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result").value("회원 탈퇴 성공"));

		verify(memberService).delete(eq(1L), isNull());
	}

	@Test
	void deleteMember_사유_본문이_있으면_그_사유로_삭제를_위임하고_200을_반환한다() throws Exception {
		String body = """
				{"reason":"필요한 지원금을 찾지 못했어요"}""";

		mockMvc.perform(delete("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("회원 탈퇴 성공"));

		verify(memberService).delete(eq(1L), eq("필요한 지원금을 찾지 못했어요"));
	}

	private static OnboardingProfile profile(String name) {
		Member member = Member.builder().name(name).role(Role.ROLE_USER).onboardingCompleted(true).build();
		return OnboardingProfile.builder()
			.member(member)
			.birthDate(LocalDate.of(1999, 3, 15))
			.regionCode("11680")
			.sido("서울특별시")
			.sigungu("강남구")
			.employmentStatus(EmploymentStatus.EMPLOYED)
			.incomeBracket(IncomeBracket.UNDER_200)
			.householdSize(1)
			.build();
	}

}
