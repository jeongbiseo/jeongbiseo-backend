package com.jeongbiseo.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 구글 id_token(JWT)의 payload임. 서명 전체 검증(JWKS)은 인가코드 플로우로 백엔드가 TLS로 직접 토큰을 받으므로 TLS 신뢰로
 * 갈음하고, iss·aud·exp만 검사함(설계 §13 Fable 메타검토).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleIdTokenPayload(String iss, String aud, String sub, String email, String name, Long exp) {
}
