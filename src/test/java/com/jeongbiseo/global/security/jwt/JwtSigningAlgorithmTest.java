package com.jeongbiseo.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 액세스 토큰 서명 알고리즘이 시크릿 길이와 무관하게 HS256으로 고정되는지 지킴. jjwt는 `signWith(key)`만 쓰면 키 길이로 알고리즘을
 * 고르므로(32바이트 HS256, 48 HS384, 64 HS512), 배포 런북이 권하는 `openssl rand -base64 48`(64바이트)에서
 * HS512로 바뀌어 설계·명세서·다이어그램의 HS256 서술이 거짓이 됨. 그 회귀를 막음.
 */
class JwtSigningAlgorithmTest {

	@DisplayName("시크릿 길이가 32·48·64바이트여도 헤더 alg는 HS256임")
	@ParameterizedTest(name = "시크릿 {0}바이트")
	@ValueSource(ints = { 32, 48, 64 })
	void 서명_알고리즘은_HS256으로_고정됨(int secretBytes) {
		JwtProvider provider = new JwtProvider(Clock.systemUTC(), "x".repeat(secretBytes), 30);

		String token = provider.issueAccessToken(1L);
		String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
				StandardCharsets.UTF_8);

		assertThat(header).contains("\"alg\":\"HS256\"");
	}

}
