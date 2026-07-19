package com.jeongbiseo.global.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.domain.subsidy.ingestion.SubsidyIngestionCoordinator;

/**
 * 지원금 운영 적재의 기동 트리거를 구성함. 시간 기준은 {@link ClockConfig}의 Asia/Seoul Clock 빈을 그대로 공유함(수집·추천
 * 기준일이 같은 Clock 빈을 쓰도록 중복 빈을 두지 않음).
 */
@Configuration
public class SubsidyIngestionConfiguration {

	// ponytail: 연결·읽기 상한만 걸고 재시도·서킷브레이커는 두지 않음. 상한 없이는 상대 서버가 응답하지 않을 때 호출 스레드가 무기한
	// 붙잡히는데, 이 빌더는 소셜 IdP(카카오·구글 토큰 교환)와 공공 API 수집이 함께 쓰므로 로그인 지연이 서버 전체 지연으로 번짐.
	// 읽기 상한을 30초로 넉넉히 둔 것은 gov24·온통청년 페이지 응답이 크기 때문임(IdP 호출은 소형이라 여유가 남음).
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * 외부 HTTP 호출 공용 빌더임. 소셜 IdP 클라이언트와 지원금 수집 클라이언트가 이 하나를 복제해 씀. 테스트는 이 빈 대신 각자
	 * {@code RestClient.builder()}에 MockRestServiceServer를 바인딩하므로 여기 설정은 실행 경로에만 적용됨.
	 */
	@Bean
	public RestClient.Builder ingestionRestClientBuilder() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(requestFactory);
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
