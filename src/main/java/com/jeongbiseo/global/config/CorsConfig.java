package com.jeongbiseo.global.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 정책 빈 설정임. 리프레시 토큰을 쿠키로 주고받으므로 allowCredentials를 켬. 자격 증명을 허용하면 origin에 와일드카드("*")를
 * 쓸 수 없어, 허용 origin을 프로퍼티(콤마 구분)로 명시함. 프로퍼티가 비면 아무 origin도 허용하지 않아 배포 URL 확정 전에는 안전하게 닫혀
 * 있음.
 */
@Configuration
public class CorsConfig {

	/** 허용 origin 목록임(콤마 구분). 비면 빈 리스트로 두어 아무 origin도 허용하지 않음. */
	@Value("${app.cors.allowed-origins:}")
	private String allowedOriginsCsv;

	@Bean
	public CorsConfigurationSource apiConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(parseAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	/** 콤마 구분 프로퍼티를 origin 리스트로 파싱함. 공백 항목은 버리고, 비면 빈 리스트를 반환함. */
	private List<String> parseAllowedOrigins() {
		if (!StringUtils.hasText(allowedOriginsCsv)) {
			return List.of();
		}
		return Arrays.stream(allowedOriginsCsv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
	}

}
