package com.jeongbiseo.global.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.domain.subsidy.ingestion.SubsidyIngestionCoordinator;

/**
 * 지원금 운영 적재의 기동 트리거를 구성함. 시간 기준은 {@link ClockConfig}의 Asia/Seoul Clock 빈을 그대로 공유함(수집·추천
 * 기준일이 같은 Clock 빈을 쓰도록 중복 빈을 두지 않음).
 */
@Configuration
public class SubsidyIngestionConfiguration {

	@Bean
	public RestClient.Builder ingestionRestClientBuilder() {
		return RestClient.builder();
	}

	/**
	 * 앱 준비 직후 수집·정규화·적재를 한 번 실행하는 기동 트리거임.
	 * @param coordinator 적재 조정자
	 * @return 기동 러너
	 */
	@Bean
	@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
	public ApplicationRunner subsidyIngestionRunner(SubsidyIngestionCoordinator coordinator) {
		return arguments -> coordinator.ingestAll();
	}

}
