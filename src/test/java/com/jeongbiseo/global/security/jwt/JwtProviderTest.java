package com.jeongbiseo.global.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtProvider 단위 테스트임(설계 D7, D10). 발급-파싱 왕복, 만료·위조 서명 거부, 32바이트 미만 시크릿 부팅 실패를 고정함.
 */
class JwtProviderTest {

	private static final String SECRET_A = "unit-test-only-jwt-secret-key-must-be-at-least-32-bytes-long-A";

	private static final String SECRET_B = "unit-test-only-jwt-secret-key-must-be-at-least-32-bytes-long-B";

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	@Test
	void 발급한_토큰을_파싱하면_memberId가_복원된다() {
		JwtProvider jwtProvider = new JwtProvider(FIXED_CLOCK, SECRET_A, 30);

		String token = jwtProvider.issueAccessToken(1L);

		assertThat(jwtProvider.parseMemberId(token)).isEqualTo(1L);
	}

	@Test
	void 만료된_토큰은_거부한다() {
		JwtProvider issuer = new JwtProvider(FIXED_CLOCK, SECRET_A, 30);
		String token = issuer.issueAccessToken(1L);

		Clock afterExpiry = Clock.offset(FIXED_CLOCK, Duration.ofMinutes(31));
		JwtProvider laterProvider = new JwtProvider(afterExpiry, SECRET_A, 30);

		assertThatThrownBy(() -> laterProvider.parseMemberId(token)).isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void 다른_키로_서명검증하면_위조로_거부한다() {
		JwtProvider issuer = new JwtProvider(FIXED_CLOCK, SECRET_A, 30);
		String token = issuer.issueAccessToken(1L);

		JwtProvider otherKeyProvider = new JwtProvider(FIXED_CLOCK, SECRET_B, 30);

		assertThatThrownBy(() -> otherKeyProvider.parseMemberId(token)).isInstanceOf(SignatureException.class);
	}

	@Test
	void 변조된_토큰은_거부한다() {
		JwtProvider issuer = new JwtProvider(FIXED_CLOCK, SECRET_A, 30);
		String token = issuer.issueAccessToken(1L);
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

		assertThatThrownBy(() -> issuer.parseMemberId(tampered)).isInstanceOfAny(SignatureException.class,
				MalformedJwtException.class);
	}

	@Test
	void 시크릿이_32바이트_미만이면_생성시_즉시_실패한다() {
		assertThatThrownBy(() -> new JwtProvider(FIXED_CLOCK, "short-secret", 30))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 시크릿이_null이면_생성시_즉시_실패한다() {
		assertThatThrownBy(() -> new JwtProvider(FIXED_CLOCK, null, 30)).isInstanceOf(IllegalStateException.class);
	}

}
