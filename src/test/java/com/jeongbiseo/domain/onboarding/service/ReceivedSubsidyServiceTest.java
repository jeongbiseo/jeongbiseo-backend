package com.jeongbiseo.domain.onboarding.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jeongbiseo.domain.onboarding.dto.response.ReceivedSubsidyItem;
import com.jeongbiseo.domain.onboarding.entity.ReceivedSubsidy;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
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

	@Test
	void findReceivedSubsidies_id와_이름을_원래순서로_반환한다() {
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of(2L, 1L));
		// findAllById는 입력 순서를 보장하지 않으므로 뒤섞인 순서로 돌려줘도 결과가 원래 id 순서(2,1)로 재구성됨을 고정함.
		given(subsidyRepository.findAllById(List.of(2L, 1L)))
			.willReturn(List.of(SubsidyEntity.builder().id(1L).name("청년 구직활동 지원금").build(),
					SubsidyEntity.builder().id(2L).name("청년 월세 특별지원").build()));

		List<ReceivedSubsidyItem> result = receivedSubsidyService.findReceivedSubsidies(10L);

		assertThat(result).containsExactly(new ReceivedSubsidyItem(2L, "청년 월세 특별지원"),
				new ReceivedSubsidyItem(1L, "청년 구직활동 지원금"));
	}

	@Test
	void findReceivedSubsidies_없으면_조회없이_빈목록을_반환한다() {
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of());

		assertThat(receivedSubsidyService.findReceivedSubsidies(10L)).isEmpty();
		verify(subsidyRepository, never()).findAllById(any());
	}

	@Test
	void findReceivedSubsidies_참조가_사라진id는_제외한다() {
		given(receivedSubsidyRepository.findSubsidyIdsByMemberId(10L)).willReturn(List.of(1L, 2L));
		// 2L 지원금이 삭제돼 findAllById가 1L만 돌려주는 경우, 결과에서 2L을 방어적으로 제외함.
		given(subsidyRepository.findAllById(List.of(1L, 2L)))
			.willReturn(List.of(SubsidyEntity.builder().id(1L).name("청년 구직활동 지원금").build()));

		List<ReceivedSubsidyItem> result = receivedSubsidyService.findReceivedSubsidies(10L);

		assertThat(result).containsExactly(new ReceivedSubsidyItem(1L, "청년 구직활동 지원금"));
	}

}
