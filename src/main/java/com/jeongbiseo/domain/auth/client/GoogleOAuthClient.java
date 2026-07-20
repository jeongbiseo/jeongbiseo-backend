package com.jeongbiseo.domain.auth.client;

import java.time.Clock;
import java.util.Base64;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jeongbiseo.domain.auth.client.dto.GoogleIdTokenPayload;
import com.jeongbiseo.domain.auth.client.dto.GoogleTokenResponse;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.exception.AuthErrorCode;

/**
 * 구글 OAuth 클라이언트임(설계 4장). code를 구글 토큰과 교환하고 id_token(JWT) payload에서 프로필을 추출함. 서명 전체
 * 검증(JWKS)은 인가코드 플로우 더하기 TLS 신뢰로 갈음하고 iss·aud·exp만 검사함(설계 §13). 이메일 자동병합은 하지 않음 —
 * providerId(sub) 단위로만 회원을 식별함.
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

	private static final Set<String> VALID_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	private final String clientId;

	private final String clientSecret;

	public GoogleOAuthClient(RestClient.Builder builder, ObjectMapper objectMapper, Clock clock,
			@Value("${app.google.client.id:}") String clientId,
			@Value("${app.google.client.secret:}") String clientSecret) {
		this.restClient = builder.clone().build();
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public Provider provider() {
		return Provider.GOOGLE;
	}

	@Override
	public OAuthUserInfo exchange(String code, String codeVerifier, String redirectUri) {
		GoogleTokenResponse token = requestToken(code, codeVerifier, redirectUri);
		GoogleIdTokenPayload payload = decodeIdToken(token.idToken());
		validate(payload);
		// 구글은 id_token payload의 name이 계정 표시명임(profile 스코프 기본 제공). 실명 보장은 없음.
		return new OAuthUserInfo(Provider.GOOGLE, payload.sub(), payload.email(), payload.name());
	}

	private GoogleTokenResponse requestToken(String code, String codeVerifier, String redirectUri) {
		try {
			MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
			body.add("code", code);
			body.add("client_id", this.clientId);
			body.add("client_secret", this.clientSecret);
			body.add("redirect_uri", redirectUri);
			body.add("code_verifier", codeVerifier);
			body.add("grant_type", "authorization_code");
			GoogleTokenResponse response = this.restClient.post()
				.uri(TOKEN_URI)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.retrieve()
				.body(GoogleTokenResponse.class);
			if (response == null || response.idToken() == null) {
				throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
			}
			return response;
		}
		catch (RestClientException e) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED, e);
		}
	}

	private GoogleIdTokenPayload decodeIdToken(String idToken) {
		try {
			String[] parts = idToken.split("\\.");
			if (parts.length < 2) {
				throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
			}
			byte[] payloadJson = Base64.getUrlDecoder().decode(parts[1]);
			return this.objectMapper.readValue(payloadJson, GoogleIdTokenPayload.class);
		}
		catch (JacksonException | IllegalArgumentException e) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED, e);
		}
	}

	private void validate(GoogleIdTokenPayload payload) {
		if (payload == null || payload.sub() == null || payload.iss() == null || !VALID_ISSUERS.contains(payload.iss())
				|| !this.clientId.equals(payload.aud()) || payload.exp() == null
				|| payload.exp() < this.clock.instant().getEpochSecond()) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
		}
	}

}
