package com.jeongbiseo.domain.onboarding.controller;

import java.time.LocalDate;
import java.util.List;

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
import com.jeongbiseo.domain.onboarding.service.ReceivedSubsidyService;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OnboardingController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 서비스는 목이고 FixedMemberResolver만
 * 실제로 로드함(고정 회원). 제출 201 계약, 필드 검증 400(VALID400_1), setReceivedSubsidies 200·빈배열·
 * VALID400_1·SUBSIDY404_1을 고정함.
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

	@MockitoBean
	private ReceivedSubsidyService receivedSubsidyService;

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

	// 아래 3건은 본문 역직렬화 실패(HttpMessageNotReadableException) 경로를 고정함. 이 경로는 `@Valid`에 도달하지 못해
	// 예전에는 COMMON500으로 나갔고, 프론트가 enum 매핑을 틀렸을 때 원인을 못 찾던 지점임.
	@Test
	void submitOnboarding_소득구간이_계약밖_문자열이면_400_VALID400_1과_허용값을_반환한다() throws Exception {
		String body = """
				{"name":"홍길동","birthDate":"1999-03-15","sido":"서울특별시","sigungu":"강남구",\
				"employmentStatus":"EMPLOYED","incomeBracket":"200~300만원"}""";

		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("VALID400_1"))
			.andExpect(jsonPath("$.result.incomeBracket").value(org.hamcrest.Matchers.containsString("UNDER_200")));
	}

	@Test
	void submitOnboarding_생년월일_형식이_깨졌으면_400_VALID400_1과_필드명을_반환한다() throws Exception {
		String body = """
				{"name":"홍길동","birthDate":"1999-13-45","sido":"서울특별시","sigungu":"강남구",\
				"employmentStatus":"EMPLOYED"}""";

		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"))
			.andExpect(jsonPath("$.result.birthDate").exists());
	}

	@Test
	void submitOnboarding_JSON_문법이_깨졌으면_400_VALID400_1을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"))
			.andExpect(jsonPath("$.result.body").exists());
	}

	@Test
	void setReceivedSubsidies_유효요청이면_200과_교체된목록을_반환한다() throws Exception {
		given(receivedSubsidyService.replaceAll(anyLong(), any())).willReturn(List.of(1L, 2L));

		mockMvc
			.perform(put("/api/v1/onboarding/received-subsidies").contentType(MediaType.APPLICATION_JSON)
				.content("{\"subsidyIds\":[1,2]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.receivedSubsidyIds[0]").value(1))
			.andExpect(jsonPath("$.result.receivedSubsidyIds[1]").value(2));
	}

	@Test
	void setReceivedSubsidies_빈배열이면_200과_빈목록을_반환한다() throws Exception {
		given(receivedSubsidyService.replaceAll(anyLong(), any())).willReturn(List.of());

		mockMvc
			.perform(put("/api/v1/onboarding/received-subsidies").contentType(MediaType.APPLICATION_JSON)
				.content("{\"subsidyIds\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.receivedSubsidyIds").isEmpty());
	}

	@Test
	void setReceivedSubsidies_subsidyIds가_null이면_400_VALID400_1() throws Exception {
		mockMvc
			.perform(put("/api/v1/onboarding/received-subsidies").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"));
	}

	@Test
	void setReceivedSubsidies_존재하지않는id면_404_SUBSIDY404_1() throws Exception {
		given(receivedSubsidyService.replaceAll(anyLong(), any()))
			.willThrow(new CustomException(SubsidyErrorCode.SUBSIDY_NOT_FOUND));

		mockMvc
			.perform(put("/api/v1/onboarding/received-subsidies").contentType(MediaType.APPLICATION_JSON)
				.content("{\"subsidyIds\":[999]}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SUBSIDY404_1"));
	}

}
