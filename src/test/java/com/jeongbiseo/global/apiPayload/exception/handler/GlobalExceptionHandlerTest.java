package com.jeongbiseo.global.apiPayload.exception.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jeongbiseo.global.apiPayload.CustomResponse;
import com.jeongbiseo.global.apiPayload.code.CommonErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void handleCustomException은_에러코드의_상태와_실패봉투를_반환한다() {
		ResponseEntity<CustomResponse<Void>> response = handler
			.handleCustomException(new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().code()).isEqualTo("COMMON500");
	}

	@Test
	void handleUnexpected는_예외종류와_무관하게_COMMON500_봉투를_반환한다() {
		ResponseEntity<CustomResponse<Void>> response = handler.handleUnexpected(new RuntimeException("boom"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("COMMON500");
	}

}
