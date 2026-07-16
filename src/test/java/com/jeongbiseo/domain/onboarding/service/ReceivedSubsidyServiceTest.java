package com.jeongbiseo.domain.onboarding.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ReceivedSubsidyService 단위 테스트임(Mockito). replaceAll의 정상 교체, 빈 배열 전체 해제, 존재하지 않는 id에 대한
 * SUBSIDY404_1, 중복 id 요청의 distinct 저장(H3, UNIQUE 위반 없음)을 고정함.
 */
@ExtendWith(MockitoExtension.class)
class ReceivedSubsidyServiceTest {

	@Mock
	private ReceivedSubsidyRepository receivedSubsidyRepository;

	@Mock
	private SubsidyRepository subsidyRepository;

	@Captor
	private ArgumentCaptor<List<ReceivedSubsidy>> entitiesCaptor;

	private ReceivedSubsidyService receivedSubsidyService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		receivedSubsidyService = new ReceivedSubsidyService(receivedSubsidyRepository, subsidyRepository);
	}

	@Test
	void replaceAll_정상이면_삭제후_저장하고_현재목록을_반환한다() {
		given(subsidyRepository.countByIdIn(List.of(1L, 2L))).willReturn(2L);
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of(1L, 2L));

		List<Long> result = receivedSubsidyService.replaceAll(10L, List.of(1L, 2L));

		verify(receivedSubsidyRepository).deleteByMemberId(10L);
		verify(receivedSubsidyRepository).saveAll(any());
		assertThat(result).containsExactly(1L, 2L);
	}

	@Test
	void replaceAll_빈배열이면_검증통과하고_삭제만한다() {
		given(subsidyRepository.countByIdIn(List.of())).willReturn(0L);
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of());

		List<Long> result = receivedSubsidyService.replaceAll(10L, List.of());

		verify(receivedSubsidyRepository).deleteByMemberId(10L);
		assertThat(result).isEmpty();
	}

	@Test
	void replaceAll_존재하지않는id가_있으면_SUBSIDY404_1을_던지고_삭제하지않는다() {
		given(subsidyRepository.countByIdIn(List.of(1L, 999L))).willReturn(1L);

		assertThatThrownBy(() -> receivedSubsidyService.replaceAll(10L, List.of(1L, 999L)))
			.isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode())
				.isEqualTo(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
		verify(receivedSubsidyRepository, never()).deleteByMemberId(anyLong());
	}

	@Test
	void replaceAll_중복id_요청도_distinct로_저장한다() {
		given(subsidyRepository.countByIdIn(List.of(1L, 2L))).willReturn(2L);
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of(1L, 2L));

		receivedSubsidyService.replaceAll(10L, List.of(1L, 2L, 1L));

		verify(subsidyRepository, times(1)).countByIdIn(List.of(1L, 2L));
		verify(receivedSubsidyRepository).saveAll(entitiesCaptor.capture());
		assertThat(entitiesCaptor.getValue()).hasSize(2)
			.extracting(ReceivedSubsidy::getSubsidyId)
			.containsExactlyInAnyOrder(1L, 2L);
	}

}
