package com.jeongbiseo.domain.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.jeongbiseo.domain.auth.client.dto.KakaoTokenResponse;
import com.jeongbiseo.domain.auth.client.dto.KakaoUserInfoResponse;
import com.jeongbiseo.domain.auth.entity.Provider;
import com.jeongbiseo.global.apiPayload.exception.CustomException;
import com.jeongbiseo.global.security.exception.AuthErrorCode;

/**
 * 카카오 OAuth 클라이언트임(설계 4장). code를 카카오 토큰과 교환하고 v2/user/me로 프로필을 조회함. 실패 사유는 구분하지 않고
 * AUTH401_1로 통합함(사유 비노출, 설계 2장).
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";

	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

	private final RestClient restClient;

	private final String clientId;

	private final String clientSecret;

	public KakaoOAuthClient(RestClient.Builder builder, @Value("${app.kakao.client.id:}") String clientId,
			@Value("${app.kakao.client.secret:}") String clientSecret) {
		this.restClient = builder.clone().build();
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public Provider provider() {
		return Provider.KAKAO;
	}

	@Override
	public OAuthUserInfo exchange(String code, String codeVerifier, String redirectUri) {
		KakaoTokenResponse token = requestToken(code, codeVerifier, redirectUri);
		KakaoUserInfoResponse userInfo = requestUserInfo(token.accessToken());
		String email = (userInfo.kakaoAccount() != null) ? userInfo.kakaoAccount().email() : null;
		// 표시용 이름은 profile.nickname임. 동의항목 미제공 시 kakao_account나 profile이 통째로 안 오므로 단계마다
		// null을 확인함.
		String nickname = (userInfo.kakaoAccount() != null && userInfo.kakaoAccount().profile() != null)
				? userInfo.kakaoAccount().profile().nickname() : null;
		return new OAuthUserInfo(Provider.KAKAO, String.valueOf(userInfo.id()), email, nickname);
	}

	private KakaoTokenResponse requestToken(String code, String codeVerifier, String redirectUri) {
		try {
			MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
			body.add("grant_type", "authorization_code");
			body.add("client_id", this.clientId);
			body.add("redirect_uri", redirectUri);
			body.add("code", code);
			body.add("code_verifier", codeVerifier);
			if (this.clientSecret != null && !this.clientSecret.isBlank()) {
				body.add("client_secret", this.clientSecret);
			}
			KakaoTokenResponse response = this.restClient.post()
				.uri(TOKEN_URI)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.retrieve()
				.body(KakaoTokenResponse.class);
			if (response == null || response.accessToken() == null) {
				throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
			}
			return response;
		}
		catch (RestClientException e) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED, e);
		}
	}

	private KakaoUserInfoResponse requestUserInfo(String accessToken) {
		try {
			KakaoUserInfoResponse response = this.restClient.get()
				.uri(USER_INFO_URI)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(KakaoUserInfoResponse.class);
			if (response == null || response.id() == null) {
				throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
			}
			return response;
		}
		catch (RestClientException e) {
			throw new CustomException(AuthErrorCode.SOCIAL_LOGIN_FAILED, e);
		}
	}

}
