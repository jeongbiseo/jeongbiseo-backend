package com.jeongbiseo.domain.auth.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GoogleOAuthClient 단위 테스트임(MockRestServiceServer, 실제 구글 서버 호출 없음). 방식 B 전환으로 인가 URL 생성은
 * 프론트가 소유하므로 클라이언트는 code 대 프로필 교환만 담당함. id_token(JWT) 서명 전체 검증은 하지 않고(TLS 신뢰로 갈음, 설계 §13)
 * iss·aud·exp만 검사하는 경로를 고정함.
 */
class GoogleOAuthClientTest {

	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

	private static final String CLIENT_ID = "google-client-id";

	private static final String CODE_VERIFIER = "pkce-code-verifier";

	private static final String REDIRECT_URI = "https://front/auth/google/callback";

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void exchange는_idToken_payload에서_프로필을_추출한다() {
		String idToken = fakeIdToken("https://accounts.google.com", CLIENT_ID, "google-sub-1", "user@example.com",
				FIXED_CLOCK.instant().plusSeconds(3600).getEpochSecond());
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withSuccess("{\"access_token\":\"a\",\"id_token\":\"" + idToken + "\"}",
					MediaType.APPLICATION_JSON));
		GoogleOAuthClient client = new GoogleOAuthClient(builder, objectMapper, FIXED_CLOCK, CLIENT_ID, "secret");

		OAuthUserInfo result = client.exchange("code", CODE_VERIFIER, REDIRECT_URI);

		assertThat(result.providerId()).isEqualTo("google-sub-1");
		assertThat(result.email()).isEqualTo("user@example.com");
		// 구글은 id_token payload의 name을 표시용 이름으로 씀
		assertThat(result.name()).isEqualTo("홍길동");
	}

	@Test
	void issuer가_구글이_아니면_AUTH401_1을_던진다() {
		String idToken = fakeIdToken("https://evil.example.com", CLIENT_ID, "sub", "e@example.com",
				FIXED_CLOCK.instant().plusSeconds(3600).getEpochSecond());
		assertThatThrownBy(() -> exchangeWith(idToken)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void audience가_다르면_AUTH401_1을_던진다() {
		String idToken = fakeIdToken("https://accounts.google.com", "other-client-id", "sub", "e@example.com",
				FIXED_CLOCK.instant().plusSeconds(3600).getEpochSecond());
		assertThatThrownBy(() -> exchangeWith(idToken)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void 만료된_id_token은_AUTH401_1을_던진다() {
		String idToken = fakeIdToken("https://accounts.google.com", CLIENT_ID, "sub", "e@example.com",
				FIXED_CLOCK.instant().minusSeconds(3600).getEpochSecond());
		assertThatThrownBy(() -> exchangeWith(idToken)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void sub가_없으면_AUTH401_1을_던진다() {
		long exp = FIXED_CLOCK.instant().plusSeconds(3600).getEpochSecond();
		String idToken = fakeIdTokenRaw("{\"iss\":\"https://accounts.google.com\",\"aud\":\"" + CLIENT_ID
				+ "\",\"email\":\"e@example.com\",\"exp\":" + exp + "}");
		assertThatThrownBy(() -> exchangeWith(idToken)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void exp가_없으면_AUTH401_1을_던진다() {
		String idToken = fakeIdTokenRaw("{\"iss\":\"https://accounts.google.com\",\"aud\":\"" + CLIENT_ID
				+ "\",\"sub\":\"s\",\"email\":\"e@example.com\"}");
		assertThatThrownBy(() -> exchangeWith(idToken)).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void id_token_형식이_잘못되면_AUTH401_1을_던진다() {
		assertThatThrownBy(() -> exchangeWith("only-one-part")).isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	@Test
	void 토큰응답에_id_token이_없으면_AUTH401_1을_던진다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withSuccess("{\"access_token\":\"a\"}", MediaType.APPLICATION_JSON));
		GoogleOAuthClient client = new GoogleOAuthClient(builder, objectMapper, FIXED_CLOCK, CLIENT_ID, "secret");

		assertThatThrownBy(() -> client.exchange("code", CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(CustomException.class)
			.extracting(e -> ((CustomException) e).getErrorCode().getCode())
			.isEqualTo("AUTH401_1");
	}

	private void exchangeWith(String idToken) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withSuccess("{\"access_token\":\"a\",\"id_token\":\"" + idToken + "\"}",
					MediaType.APPLICATION_JSON));
		GoogleOAuthClient client = new GoogleOAuthClient(builder, objectMapper, FIXED_CLOCK, CLIENT_ID, "secret");
		client.exchange("code", CODE_VERIFIER, REDIRECT_URI);
	}

	// 서명은 검증하지 않으므로(설계 §13) header·signature는 더미로 채우고 payload만 실제 JSON을 담음.
	// name은 구글 계정 표시명임(profile 스코프 기본 제공). 표시용 이름 추출 경로를 함께 고정하려고 payload에 넣음.
	private String fakeIdToken(String iss, String aud, String sub, String email, long exp) {
		String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
		String payload = encode(String.format(
				"{\"iss\":\"%s\",\"aud\":\"%s\",\"sub\":\"%s\",\"email\":\"%s\",\"name\":\"홍길동\",\"exp\":%d}", iss, aud,
				sub, email, exp));
		return header + "." + payload + ".dummy-signature";
	}

	// payload에 특정 필드를 뺀 변형을 만들기 위한 raw 헬퍼임(sub·exp 누락 분기 검증).
	private String fakeIdTokenRaw(String payloadJson) {
		String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
		return header + "." + encode(payloadJson) + ".dummy-signature";
	}

	private static String encode(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
