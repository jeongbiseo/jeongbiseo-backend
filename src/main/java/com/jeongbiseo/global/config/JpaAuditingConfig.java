package com.jeongbiseo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity의 created_at, updated_at 자동 채움을 켜는 설정임. 감사 활성화를 Application 진입점이 아니라 별도 설정으로
 * 분리해, 슬라이스 테스트에서 감사 컨텍스트를 선택적으로 로드하기 쉽게 함.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

}
