package com.jeongbiseo.domain.subsidy.ingestion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jeongbiseo.infra.client.common.SubsidySourceCollector;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;

/** 운영 수집기들을 소스별로 격리 실행하고 성공한 소스만 적재하는 조정자임. */
@Component
public class SubsidyIngestionCoordinator {

	private static final Logger log = LoggerFactory.getLogger(SubsidyIngestionCoordinator.class);

	private final List<SubsidySourceCollector> collectors;

	private final SubsidyIngestionAdapter ingestionAdapter;

	private final Clock clock;

	public SubsidyIngestionCoordinator(List<SubsidySourceCollector> collectors,
			SubsidyIngestionAdapter ingestionAdapter, Clock clock) {
		this.collectors = collectors;
		this.ingestionAdapter = ingestionAdapter;
		this.clock = clock;
	}

	/** 소스별 수집·정규화·적재를 실행함. 한 소스 실패는 다음 소스와 기존 DB 행에 영향을 주지 않음. */
	public void ingestAll() {
		int succeeded = 0;
		LocalDateTime fetchedAt = LocalDateTime.now(this.clock);
		log.info("지원금 기동 적재 시작: sources={}", this.collectors.size());
		for (SubsidySourceCollector collector : this.collectors) {
			try {
				List<NormalizedSubsidy> subsidies = collector.collect();
				this.ingestionAdapter.ingest(subsidies, fetchedAt);
				succeeded++;
				log.info("지원금 소스 적재 완료: sourceId={}, count={}", collector.source().sourceId(), subsidies.size());
			}
			catch (RuntimeException exception) {
				// API 키가 요청 URI에 포함되므로 예외 메시지와 스택을 로그에 남기지 않음.
				log.warn("지원금 소스 적재 실패, 기존 데이터 보존: sourceId={}, failureType={}", collector.source().sourceId(),
						exception.getClass().getSimpleName());
			}
		}
		log.info("지원금 기동 적재 종료: succeeded={}, failed={}", succeeded, this.collectors.size() - succeeded);
	}

}
