package com.jeongbiseo.domain.consent.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.dto.response.MarketingConsentResponse;
import com.jeongbiseo.domain.consent.dto.response.TermConsentItem;
import com.jeongbiseo.domain.consent.dto.response.TermConsentsResponse;
import com.jeongbiseo.domain.consent.service.TermConsentService;
import com.jeongbiseo.global.security.FixedMemberResolver;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ConsentController 웹 슬라이스 테스트임(@WebMvcTest, MockMvc). 마이페이지 약관 조회 200 계약과 마케팅 수신 동의 변경의
 * 멱등 set 위임·필수 필드 검증 400을 고정함.
 */
@WebMvcTest(ConsentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FixedMemberResolver.class)
@org.junit.jupiter.api.extension.ExtendWith(com.jeongbiseo.support.FixedMemberContextExtension.class)
class ConsentControllerTest {

	private static final LocalDateTime DECIDED_AT = LocalDateTime.of(2026, 7, 16, 9, 0);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TermConsentService termConsentService;

	@Test
	void getMyTermConsents_표시약관_동의상태와_마케팅_상태를_담아_200을_반환한다() throws Exception {
		TermConsentsResponse response = new TermConsentsResponse(
				List.of(TermConsentItem.of(TermType.SERVICE, DECIDED_AT), TermConsentItem.of(TermType.PRIVACY, null)),
				true, DECIDED_AT);
		given(termConsentService.getMyTermConsents(1L)).willReturn(response);

		mockMvc.perform(get("/api/v1/members/me/terms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.terms[0].type").value("SERVICE"))
			.andExpect(jsonPath("$.result.terms[0].agreed").value(true))
			.andExpect(jsonPath("$.result.terms[1].type").value("PRIVACY"))
			.andExpect(jsonPath("$.result.terms[1].agreed").value(false))
			.andExpect(jsonPath("$.result.terms[1].agreedAt").doesNotExist())
			.andExpect(jsonPath("$.result.marketingConsent").value(true));
	}

	@Test
	void updateMarketingConsent_목표상태를_위임하고_200을_반환한다() throws Exception {
		given(termConsentService.updateMarketingConsent(eq(1L), eq(true)))
			.willReturn(new MarketingConsentResponse(true, DECIDED_AT));
		String body = """
				{"agreed":true}""";

		mockMvc
			.perform(post("/api/v1/members/me/terms/marketing").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("200"))
			.andExpect(jsonPath("$.result.agreed").value(true));

		verify(termConsentService).updateMarketingConsent(eq(1L), eq(true));
	}

	@Test
	void updateMarketingConsent_agreed가_없으면_400_VALID400_1() throws Exception {
		String body = "{}";

		mockMvc
			.perform(post("/api/v1/members/me/terms/marketing").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALID400_1"));
	}

}
