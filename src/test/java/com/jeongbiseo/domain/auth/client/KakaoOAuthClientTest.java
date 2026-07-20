package com.jeongbiseo.domain.auth.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * KakaoOAuthClient 단위 테스트임(MockRestServiceServer, 실제 카카오 서버 호출 없음). 방식 B 전환으로 인가 URL 생성은
 * 프론트가 소유하므로 클라이언트는 토큰 교환과 사용자 정보 조회 두 호출을 묶는 exchange()의 정상·실패 경로만 고정함.
 */
class KakaoOAuthClientTest {

	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";

	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

	private static final String CODE_VERIFIER = "pkce-code-verifier";

	private static final String REDIRECT_URI = "https://front/auth/kakao/callback";

	@Test
	void exchange는_토큰교환_더하기_사용자조회로_프로필을_반환한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI))
			.andExpect(method(POST))
			.andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(USER_INFO_URI))
			.andExpect(method(GET))
			.andRespond(withSuccess(
					"{\"id\":123456,\"kakao_account\":{\"email\":\"user@example.com\",\"profile\":{\"nickname\":\"아기삼자\"}}}",
					MediaType.APPLICATION_JSON));
		KakaoOAuthClient client = new KakaoOAuthClient(builder, "client-id", "client-secret");

		OAuthUserInfo result = client.exchange("code", CODE_VERIFIER, REDIRECT_URI);

		assertThat(result.providerId()).isEqualTo("123456");
		assertThat(result.email()).isEqualTo("user@example.com");
		// 카카오는 실명이 아니라 profile.nickname을 표시용 이름으로 씀
		assertThat(result.name()).isEqualTo("아기삼자");
	}

	@Test
	void 이메일_동의가_없으면_email이_null이다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("{\"id\":999}", MediaType.APPLICATION_JSON));
		KakaoOAuthClient client = new KakaoOAuthClient(builder, "client-id", "");

		OAuthUserInfo result = client.exchange("code", CODE_VERIFIER, REDIRECT_URI);

		assertThat(result.email()).isNull();
		// kakao_account 자체가 없으면 프로필도 못 받으므로 이름도 null임
		assertThat(result.name()).isNull();
	}

	@Test
	void 토큰응답에_access_token이_없으면_AUTH401_1을_던진다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		KakaoOAuthClient client = new KakaoOAuthClient(builder, "client-id", "client-secret");

		assertThatThrownBy(() -> client.exchange("code", CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

}
