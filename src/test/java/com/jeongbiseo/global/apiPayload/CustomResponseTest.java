package com.jeongbiseo.global.apiPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

class CustomResponseTest {

	@Test
	void ok는_isSuccess_true와_code_200과_결과를_담는다() {
		CustomResponse<String> response = CustomResponse.ok("data");

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.code()).isEqualTo("200");
		assertThat(response.message()).isEqualTo("OK");
		assertThat(response.result()).isEqualTo("data");
	}

	@Test
	void created는_code_201을_담는다() {
		CustomResponse<String> response = CustomResponse.created("x");

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.code()).isEqualTo("201");
	}

	@Test
	void fail은_에러코드의_code와_message를_담고_result는_null이다() {
		CustomResponse<Void> response = CustomResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);

		assertThat(response.isSuccess()).isFalse();
		assertThat(response.code()).isEqualTo("COMMON500");
		assertThat(response.message()).isEqualTo("서버 내부 오류가 발생했습니다");
		assertThat(response.result()).isNull();
	}

	@Test
	void 직렬화하면_isSuccess_필드명이_유지되고_필드_순서가_고정된다() throws Exception {
		String json = new ObjectMapper().writeValueAsString(CustomResponse.ok("data"));

		// is-접두어 스트리핑으로 "success"가 나가면 프론트 계약이 깨지므로 "isSuccess"가 그대로 유지돼야 함
		assertThat(json).contains("\"isSuccess\":true");
		assertThat(json).doesNotContain("\"success\"");
		// 필드 순서 isSuccess, code, message, result 고정(@JsonPropertyOrder)
		assertThat(json.indexOf("isSuccess")).isLessThan(json.indexOf("code"));
		assertThat(json.indexOf("code")).isLessThan(json.indexOf("message"));
		assertThat(json.indexOf("message")).isLessThan(json.indexOf("result"));
	}

}
