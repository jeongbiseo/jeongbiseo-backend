package com.jeongbiseo.domain.subsidy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.onboarding.repository.ReceivedSubsidyRepository;
import com.jeongbiseo.domain.onboarding.service.ReceivedSubsidyService;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.dto.SubsidySort;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지원금 검색·상세·setReceivedSubsidies 종단 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL,
 * Docker 필요). FixedMemberResolver 대신 임의 memberId로 ReceivedSubsidyService를 직접 호출함
 * (RecommendationScopeIntegrationTest 관례, ReceivedSubsidy는 memberId를 값 컬럼으로만 가져 FK 제약이
 * 없으므로 Member 엔티티 시드가 필요 없음). 각 테스트는 트랜잭션 롤백으로 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SubsidySearchDetailIntegrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	private static final LocalDate AS_OF = LocalDate.of(2026, 7, 16);

	private static final Long MEMBER_ID = 42L;

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Autowired
	private SubsidyService subsidyService;

	@Autowired
	private ReceivedSubsidyService receivedSubsidyService;

	@Autowired
	private ReceivedSubsidyRepository receivedSubsidyRepository;

	@Test
	void search_키워드로_이름과_기관을_검색한다() {
		subsidyRepository.save(base("k1").name("청년월세지원").agency("국토교통부").build());
		subsidyRepository.save(base("k2").name("창업지원금").agency("중소벤처기업부").build());

		Page<SubsidySearchResult> page = subsidyService.search("청년", null, null, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::name).containsExactly("청년월세지원");
	}

	@Test
	void search_category로_필터링한다() {
		subsidyRepository.save(base("c1").category(SubsidyCategory.YOUTH).build());
		subsidyRepository.save(base("c2").category(SubsidyCategory.HOUSING).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, SubsidyCategory.HOUSING, null,
				PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::subsidyId).hasSize(1);
		assertThat(page.getContent().get(0).category()).isEqualTo(SubsidyCategory.HOUSING);
	}

	@Test
	void search_융자상품은_제외한다() {
		SubsidyEntity normal = subsidyRepository.save(base("n1").build());
		subsidyRepository.save(base("loan1").loanProduct(true).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, null, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::subsidyId).containsExactly(normal.getId());
	}

	@Test
	void search_페이지네이션이_동작한다() {
		for (int i = 0; i < 5; i++) {
			subsidyRepository.save(base("p" + i).build());
		}

		Page<SubsidySearchResult> firstPage = subsidyService.search(null, null, null, PageRequest.of(0, 2));

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.getTotalElements()).isEqualTo(5);
		assertThat(firstPage.getTotalPages()).isEqualTo(3);
		assertThat(firstPage.isLast()).isFalse();
	}

	@Test
	void search_sort_DEADLINE은_마감임박순이고_마감미상은_뒤로_보낸다() {
		SubsidyEntity far = subsidyRepository.save(base("d_far").deadline(AS_OF.plusDays(30)).build());
		SubsidyEntity near = subsidyRepository.save(base("d_near").deadline(AS_OF.plusDays(3)).build());
		SubsidyEntity noDeadline = subsidyRepository.save(base("d_null").deadline(null).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, SubsidySort.DEADLINE, PageRequest.of(0, 20));

		// 가까운 마감 먼저, 먼 마감 다음, 마감 미상(null)은 항상 맨 뒤
		assertThat(page.getContent()).extracting(SubsidySearchResult::subsidyId)
			.containsExactly(near.getId(), far.getId(), noDeadline.getId());
	}

	@Test
	void search_sort_NAME은_가나다순으로_정렬한다() {
		// utf8mb4 collation 실측을 겸함 — 실 MySQL에서 한글이 가나다로 정렬되지 않으면 이 테스트가 실패함
		subsidyRepository.save(base("n_da").name("다지원금").build());
		subsidyRepository.save(base("n_ga").name("가지원금").build());
		subsidyRepository.save(base("n_na").name("나지원금").build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, SubsidySort.NAME, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::name).containsExactly("가지원금", "나지원금", "다지원금");
	}

	@Test
	void search_sort_NAME_페이지경계에서_중복없이_이어진다() {
		for (int i = 0; i < 5; i++) {
			subsidyRepository.save(base("np" + i).name("동일이름지원금").build());
		}

		Page<SubsidySearchResult> firstPage = subsidyService.search(null, null, SubsidySort.NAME, PageRequest.of(0, 2));
		Page<SubsidySearchResult> secondPage = subsidyService.search(null, null, SubsidySort.NAME,
				PageRequest.of(1, 2));

		// 동명 지원금도 id tie-breaker로 순서가 결정되어 페이지 간 중복이 없어야 함
		assertThat(firstPage.getContent()).extracting(SubsidySearchResult::subsidyId)
			.doesNotContainAnyElementsOf(secondPage.getContent().stream().map(SubsidySearchResult::subsidyId).toList());
	}

	@Test
	void getDetail_존재하면_상세를_반환한다() {
		SubsidyEntity saved = subsidyRepository.save(base("d1").deadline(AS_OF.plusDays(5)).build());

		SubsidyDetailResponse response = subsidyService.getDetail(saved.getId(), null);

		assertThat(response.subsidyId()).isEqualTo(saved.getId());
		assertThat(response.isFavorite()).isFalse();
	}

	@Test
	void getDetail_없는id면_SUBSIDY404_1을_던진다() {
		assertThatThrownBy(() -> subsidyService.getDetail(999_999L, null)).isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode())
				.isEqualTo(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
	}

	@Test
	void setReceivedSubsidies_정상_교체후_존재하지않는id는_404() {
		SubsidyEntity s1 = subsidyRepository.save(base("r1").build());
		SubsidyEntity s2 = subsidyRepository.save(base("r2").build());

		List<Long> replaced = receivedSubsidyService.replaceAll(MEMBER_ID, List.of(s1.getId(), s2.getId()));
		assertThat(replaced).containsExactlyInAnyOrder(s1.getId(), s2.getId());

		assertThatThrownBy(() -> receivedSubsidyService.replaceAll(MEMBER_ID, List.of(999_999L)))
			.isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode())
				.isEqualTo(SubsidyErrorCode.SUBSIDY_NOT_FOUND));
		// 존재 검증 실패는 삭제 전에 던져지므로 직전 교체 결과가 그대로 남아 있어야 함
		assertThat(receivedSubsidyRepository.findSubsidyIdsByMemberId(MEMBER_ID)).containsExactlyInAnyOrder(s1.getId(),
				s2.getId());
	}

	@Test
	void setReceivedSubsidies_같은목록_재PUT은_멱등하다() {
		SubsidyEntity s1 = subsidyRepository.save(base("idem1").build());
		SubsidyEntity s2 = subsidyRepository.save(base("idem2").build());

		receivedSubsidyService.replaceAll(MEMBER_ID, List.of(s1.getId(), s2.getId()));
		List<Long> secondPut = receivedSubsidyService.replaceAll(MEMBER_ID, List.of(s1.getId(), s2.getId()));

		assertThat(secondPut).containsExactlyInAnyOrder(s1.getId(), s2.getId());
	}

	private static SubsidyEntity.SubsidyEntityBuilder base(String externalId) {
		return SubsidyEntity.builder()
			.sourceId("gov24")
			.externalId(externalId)
			.name("테스트 지원금 " + externalId)
			.category(SubsidyCategory.YOUTH)
			.paymentType(PaymentType.CASH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.loanProduct(false);
	}

}
