package com.jeongbiseo.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스 JWT 발급과 검증을 담당함(설계 D7). 클레임은 sub(memberId)·iat·exp 최소만 둠. 서명은 HS256으로 **명시 고정**함 —
 * `signWith(key)`만 쓰면 jjwt가 키 길이로 알고리즘을 골라(32바이트 HS256, 48 HS384, 64 HS512) 런북 권장
 * `openssl rand -base64 48`(64바이트)에서 HS512가 되고 문서의 HS256 서술이 거짓이 됨. 리프레시 토큰은 이 클래스가 다루지
 * 않음(불투명 랜덤 문자열이라 AuthService가 해시로 저장·대조, 설계 D7). 필터 도입(Bearer 강제)은 배포 N+1이라 지금은 소비자가
 * AuthService와 테스트뿐임.
 */
@Component
public final class JwtProvider {

	private static final int MIN_SECRET_BYTES = 32;

	private final Clock clock;

	private final Duration accessTokenTtl;

	private final SecretKey signingKey;

	public JwtProvider(Clock clock, @Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-expiration-minutes:30}") long accessTokenExpirationMinutes) {
		this.clock = clock;
		this.accessTokenTtl = Duration.ofMinutes(accessTokenExpirationMinutes);
		this.signingKey = buildSigningKey(secret);
	}

	private static SecretKey buildSigningKey(String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret은 최소 32바이트여야 함(JWT_SECRET 환경변수 확인, 설계 D10)");
		}
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	/** 액세스 토큰을 발급함(sub=memberId, iat=현재, exp=발급 더하기 만료시간). */
	public String issueAccessToken(Long memberId) {
		Instant now = this.clock.instant();
		return Jwts.builder()
			.subject(String.valueOf(memberId))
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(this.accessTokenTtl)))
			.signWith(this.signingKey, Jwts.SIG.HS256)
			.compact();
	}

	/**
	 * 액세스 토큰을 검증하고 memberId를 반환함. 만료·위조·형식 오류는 jjwt의 JwtException 계열 그대로 전파함(호출부가 구분해
	 * 처리). jjwt 기본 파서는 exp 검증을 시스템 벽시계로 하므로, 발급에 쓴 것과 같은 Clock을 명시로 넘겨 테스트에서 고정 Clock을 써도
	 * 발급·검증이 어긋나지 않게 함.
	 */
	public Long parseMemberId(String token) {
		String subject = Jwts.parser()
			.clock(() -> Date.from(this.clock.instant()))
			.verifyWith(this.signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload()
			.getSubject();
		return Long.valueOf(subject);
	}

}
