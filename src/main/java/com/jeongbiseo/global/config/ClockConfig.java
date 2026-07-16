package com.jeongbiseo.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 시각 기준을 Asia/Seoul로 고정하는 Clock 빈임(제약 5.2 "시간은 Asia/Seoul 고정"). 도메인 서비스는 존 없는
 * now()를 직접 부르지 않고 이 Clock을 주입받아 시각을 만들어, 서버 로케일과 무관하게 한국 시간으로 동작하고 테스트에서 시각을 고정하기 쉽게 함.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
