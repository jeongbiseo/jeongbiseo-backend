package com.jeongbiseo.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서의 기본 정보와 Bearer 인증 스킴을 정의함. 스킴이 없으면 Swagger UI에 Authorize 버튼 자체가 뜨지 않아 토큰을 넣을
 * 방법이 없고, 모든 Try it out이 무헤더로 나가 보호 경로에서 COMMON401로 거절됨(AUTH-W001).
 *
 * <b>{@code @SecurityScheme}만으로는 부족함.</b> 스킴은 버튼을 띄울 뿐이고, 입력한 토큰을 실제 요청 헤더에 싣는 것은
 * {@code @OpenAPIDefinition}의 글로벌 {@code security} 요구임. 둘 중 하나만 두면 "버튼은 있는데 헤더가 안 나가는"
 * 상태가 됨.
 *
 * 여기서 표시하는 인증 요구는 SecurityConfig가 JwtAuthenticationFilter로 실제 강제하는 값과 일치함(AUTH-W001,
 * {@code .agents/rules/api-versioning.md} 3절). 인증이 불필요한 엔드포인트는 해당 메서드에서
 * {@code @SecurityRequirements}(빈 값)로 글로벌 요구를 해제함.
 */
@Configuration
@OpenAPIDefinition(
		info = @Info(title = "정비서 API", version = "v1", description = "온보딩 정보로 받을 수 있는 정부지원금을 추천하고 예상 총액과 마감 일정을 제공함."),
		security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
		description = "소셜 로그인으로 받은 액세스 토큰을 넣음. Bearer 접두어는 UI가 자동으로 붙임.")
public class OpenApiConfig {

}
