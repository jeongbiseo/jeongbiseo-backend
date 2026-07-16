package com.jeongbiseo.domain.onboarding.controller;

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
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.onboarding.entity.OnboardingProfile;
import com.jeongbiseo.domain.onboarding.service.OnboardingService;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OnboardingController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 서비스는 목이고 FixedMemberResolver만
 * 실제로 로드함(고정 회원). 제출 201 계약과 필드 검증 400(VALID400_1)을 고정함.
 */
@WebMvcTest(OnboardingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
class OnboardingControllerTest {

	private static final String VALID_BODY = """
			{"name":"홍길동","birthDate":"1999-03-15","sido":"서울특별시","sigungu":"강남구",\
			"employmentStatus":"EMPLOYED","incomeBracket":"UNDER_200","householdSize":1}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OnboardingService onboardingService;

	@Test
	void submitOnboarding_유효요청이면_201과_완료응답을_반환한다() throws Exception {
		Member member = Member.builder().name("홍길동").role(Role.ROLE_USER).onboardingCompleted(true).build();
		OnboardingProfile profile = OnboardingProfile.builder()
			.member(member)
			.birthDate(LocalDate.of(1999, 3, 15))
			.regionCode("11680")
			.sido("서울특별시")
			.sigungu("강남구")
			.employmentStatus(EmploymentStatus.EMPLOYED)
			.householdSize(1)
			.build();
		given(onboardingService.submit(any(), any(), any(), any(), any(), any(), any(), any())).willReturn(profile);

		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("201"))
			.andExpect(jsonPath("$.result.onboardingCompleted").value(true))
			.andExpect(jsonPath("$.result.age").isNumber());
	}

	@Test
	void submitOnboarding_이름이_없으면_400_VALID400_1() throws Exception {
		String body = """
				{"birthDate":"1999-03-15","sido":"서울특별시","sigungu":"강남구","employmentStatus":"EMPLOYED"}""";

		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("VALID400_1"));
	}

	@Test
	void submitOnboarding_생년월일이_미래면_400_VALID400_1() throws Exception {
		String body = """
				{"name":"홍길동","birthDate":"3000-01-01","sido":"서울특별시","sigungu":"강남구","employmentStatus":"EMPLOYED"}""";

		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"));
	}

}
