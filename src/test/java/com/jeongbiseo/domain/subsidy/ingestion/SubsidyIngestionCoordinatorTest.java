package com.jeongbiseo.domain.subsidy.ingestion;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.common.SubsidySourceCollector;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/** 소스별 부분 실패가 다른 소스 적재와 기존 데이터에 영향을 주지 않는지 검증함. */
class SubsidyIngestionCoordinatorTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Test
	void ingestAll_continuesAfterOneSourceFailsAndOnlyPersistsSuccessfulSource() {
		SubsidySourceCollector successful = mock(SubsidySourceCollector.class);
		SubsidySourceCollector failed = mock(SubsidySourceCollector.class);
		SubsidyIngestionAdapter adapter = mock(SubsidyIngestionAdapter.class);
		NormalizedSubsidy subsidy = mock(NormalizedSubsidy.class);
		List<NormalizedSubsidy> collected = List.of(subsidy);
		when(successful.source()).thenReturn(SubsidySource.GOV24);
		when(successful.collect()).thenReturn(collected);
		when(failed.source()).thenReturn(SubsidySource.YOUTHCENTER);
		when(failed.collect()).thenThrow(new IllegalStateException("비밀값을 포함할 수 있는 원본 오류"));
		Clock clock = Clock.fixed(Instant.parse("2026-07-14T03:34:56Z"), SEOUL_ZONE);
		SubsidyIngestionCoordinator coordinator = new SubsidyIngestionCoordinator(List.of(successful, failed), adapter,
				clock);

		coordinator.ingestAll();

		LocalDateTime expectedFetchedAt = LocalDateTime.of(2026, 7, 14, 12, 34, 56);
		verify(adapter).ingest(eq(collected), eq(expectedFetchedAt));
		verify(adapter, never()).ingest(eq(List.of()), eq(expectedFetchedAt));
		verify(failed).collect();
	}

}
