package com.jeongbiseo.global.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.jeongbiseo.domain.subsidy.ingestion.SubsidyIngestionCoordinator;
import com.jeongbiseo.infra.enrichment.EnrichmentBatch;

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
	 *
	 * <b>이 빈을 지우지 말 것.</b> Boot 4는 모듈이 분리돼 {@code spring-boot-starter-webmvc}가 RestClient
	 * 자동구성을 끌고 오지 않으며, 이 프로젝트 classpath에 {@code spring-boot-restclient}가 없음(2026-07-19
	 * 실측). 따라서 자동구성 빌더가 존재하지 않고 이 빈이 유일한 공급원이라, 삭제하면 주입받는 4개 클라이언트가 기동 시
	 * NoSuchBeanDefinitionException으로 죽음. 같은 이유로 {@code spring.http.clients.*} 프로퍼티와
	 * RestClientCustomizer도 이 앱에는 적용되지 않음(쓰려면 의존성 추가가 선행 조건).
	 *
	 * 하나의 JDK HttpClient를 네 소비자가 공유하는 것은 의도된 것임 — 불변·스레드 안전이고 자체 커넥션 풀을 가져 공유가 권장 패턴임.
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
	// 보강 러너보다 먼저 돌아야 하므로 순서를 명시함. 이 값이 없으면 두 러너가 기본 순서로 동률이 되어 실행 순서가 보장되지
	// 않고, 보강이 먼저 돌면 낡은 데이터를 대상으로 삼음.
	@Bean
	@Order(0)
	@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
	public ApplicationRunner subsidyIngestionRunner(SubsidyIngestionCoordinator coordinator) {
		return arguments -> coordinator.ingestAll();
	}

	/**
	 * LLM 금액 보강 배치의 기동 트리거임. 수집 러너보다 뒤에 돌도록 순서를 낮게(숫자를 크게) 둠 — 보강은 원천 수집이 끝난 데이터를 대상으로 하기
	 * 때문임(배치 설계 3장 "수집 성공 뒤 이어지는 후속 작업"). <b>수집 러너 쪽에도 {@code @Order(0)}이 명시돼 있어야 이 순서가
	 * 성립함</b> — 양쪽 다 순서를 안 주면 동률이 되어 실행 순서가 부수 사정에 좌우됨.
	 *
	 * <p>
	 * <b>{@code app.llm.enrichment.enabled}가 true가 아니면 이 빈 자체가 만들어지지 않음.</b> 플래그를 코드 안에서
	 * 검사하지 않고 빈 조건으로 둔 것은, 꺼진 상태에서 NIM 호출 경로가 아예 존재하지 않게 하려는 것임. 배포 첫 회차는 false로 넣음.
	 * </p>
	 *
	 * <p>
	 * 보강 실패는 위로 던지지 않음({@code EnrichmentBatch.run}이 예외 대신 요약을 반환함). 보강이 앱 기동을 막거나 원천 스냅샷
	 * 게시를 되돌리면 안 되기 때문임(판정원칙 5번).
	 * </p>
	 * @param batch 보강 배치
	 * @return 기동 러너
	 */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	@ConditionalOnProperty(name = "app.llm.enrichment.enabled", havingValue = "true")
	public ApplicationRunner enrichmentBatchRunner(EnrichmentBatch batch) {
		return arguments -> batch.run();
	}

}
