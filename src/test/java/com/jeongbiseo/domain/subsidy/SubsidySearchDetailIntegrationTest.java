package com.jeongbiseo.domain.subsidy;

import com.jeongbiseo.support.MySqlContainerSupport;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

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
class SubsidySearchDetailIntegrationTest extends MySqlContainerSupport {

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

	// ClockConfig가 제공하는 고정 빈임. RecommendationScopeIntegrationTest 관례를 따라, 테스트가 "오늘"을
	// 직접 가정하지 않고 서비스가 실제로 쓰는 Clock에서 기준일을 파생함.
	@Autowired
	private Clock clock;

	@Test
	void search_키워드로_이름과_기관을_검색한다() {
		subsidyRepository.save(base("k1").name("청년월세지원").agency("국토교통부").build());
		subsidyRepository.save(base("k2").name("창업지원금").agency("중소벤처기업부").build());

		Page<SubsidySearchResult> page = subsidyService.search("청년", null, null, false, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::name).containsExactly("청년월세지원");
	}

	@Test
	void search_결과에_예상금액이_실려온다() {
		// constructor expression이 estimatedAmountMin/Max를 DB에서 채워 응답에 싣는지 고정함(금액 없는 행은
		// null).
		SubsidyEntity withAmount = subsidyRepository
			.save(base("amt").name("금액있음지원금").estimatedAmountMin(200000L).estimatedAmountMax(500000L).build());
		SubsidyEntity noAmount = subsidyRepository.save(base("noamt").name("금액없음지원금").build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, null, false, PageRequest.of(0, 20));

		assertThat(page.getContent()).filteredOn(r -> r.subsidyId().equals(withAmount.getId()))
			.singleElement()
			.satisfies(r -> {
				assertThat(r.estimatedAmountMin()).isEqualTo(200000L);
				assertThat(r.estimatedAmountMax()).isEqualTo(500000L);
			});
		assertThat(page.getContent()).filteredOn(r -> r.subsidyId().equals(noAmount.getId()))
			.singleElement()
			.satisfies(r -> assertThat(r.estimatedAmountMin()).isNull());
	}

	@Test
	void search_category로_필터링한다() {
		subsidyRepository.save(base("c1").category(SubsidyCategory.YOUTH).build());
		subsidyRepository.save(base("c2").category(SubsidyCategory.HOUSING).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, SubsidyCategory.HOUSING, null, false,
				PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::subsidyId).hasSize(1);
		assertThat(page.getContent().get(0).category()).isEqualTo(SubsidyCategory.HOUSING);
	}

	@Test
	void search_융자상품은_제외한다() {
		SubsidyEntity normal = subsidyRepository.save(base("n1").build());
		subsidyRepository.save(base("loan1").loanProduct(true).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, null, false, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::subsidyId).containsExactly(normal.getId());
	}

	@Test
	void search_페이지네이션이_동작한다() {
		for (int i = 0; i < 5; i++) {
			subsidyRepository.save(base("p" + i).build());
		}

		Page<SubsidySearchResult> firstPage = subsidyService.search(null, null, null, false, PageRequest.of(0, 2));

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.getTotalElements()).isEqualTo(5);
		assertThat(firstPage.getTotalPages()).isEqualTo(3);
		assertThat(firstPage.isLast()).isFalse();
	}

	@Test
	void search_sort_DEADLINE은_마감임박순이고_마감미상은_뒤로_보낸다() {
		// AS_OF가 이미 과거라 includeClosed=false로는 고정 날짜를 쓸 수 없음 -- 실제 Clock 기준 상대 날짜로 계산함
		// (RecommendationScopeIntegrationTest 관례).
		LocalDate asOf = LocalDate.now(clock);
		SubsidyEntity far = subsidyRepository.save(base("d_far").deadline(asOf.plusDays(30)).build());
		SubsidyEntity near = subsidyRepository.save(base("d_near").deadline(asOf.plusDays(3)).build());
		SubsidyEntity noDeadline = subsidyRepository.save(base("d_null").deadline(null).build());

		Page<SubsidySearchResult> page = subsidyService.search(null, null, SubsidySort.DEADLINE, false,
				PageRequest.of(0, 20));

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

		Page<SubsidySearchResult> page = subsidyService.search(null, null, SubsidySort.NAME, false,
				PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(SubsidySearchResult::name).containsExactly("가지원금", "나지원금", "다지원금");
	}

	@Test
	void search_sort_NAME_페이지경계에서_중복없이_이어진다() {
		for (int i = 0; i < 5; i++) {
			subsidyRepository.save(base("np" + i).name("동일이름지원금").build());
		}

		Page<SubsidySearchResult> firstPage = subsidyService.search(null, null, SubsidySort.NAME, false,
				PageRequest.of(0, 2));
		Page<SubsidySearchResult> secondPage = subsidyService.search(null, null, SubsidySort.NAME, false,
				PageRequest.of(1, 2));

		// 동명 지원금도 id tie-breaker로 순서가 결정되어 페이지 간 중복이 없어야 함
		assertThat(firstPage.getContent()).extracting(SubsidySearchResult::subsidyId)
			.doesNotContainAnyElementsOf(secondPage.getContent().stream().map(SubsidySearchResult::subsidyId).toList());
	}

	// sort 3변형(미지정·DEADLINE·NAME)에 공백 무시 매칭이 다 동작하는지 고정함 — WHERE 3벌 replace 복붙의 드리프트 보험임.
	@ParameterizedTest(name = "sort={0}")
	@NullSource
	@EnumSource(SubsidySort.class)
	void search_키워드와_이름의_공백을_무시하고_매칭한다(SubsidySort sort) {
		subsidyRepository.save(base("ws_hit").name("청년 월세 특별지원").build());
		subsidyRepository.save(base("ws_miss").name("창업 도약 자금").build());

		// 키워드에 공백이 없어도(청년월세) 이름의 공백을 지우고 매칭됨
		Page<SubsidySearchResult> spaced = subsidyService.search("청년월세", null, sort, false, PageRequest.of(0, 20));
		// 키워드에 공백이 있어도(청년 월세) 동일하게 매칭됨
		Page<SubsidySearchResult> reversed = subsidyService.search("청년 월세", null, sort, false, PageRequest.of(0, 20));

		assertThat(spaced.getContent()).extracting(SubsidySearchResult::name).containsExactly("청년 월세 특별지원");
		assertThat(reversed.getContent()).extracting(SubsidySearchResult::name).containsExactly("청년 월세 특별지원");
	}

	@Test
	void search_키워드가_공백만이면_null로_취급해_전체를_반환한다() {
		// " "는 전처리 후 빈 문자열이 되는데 그대로 두면 like '%%'가 전건 일치가 됨. null 정규화로 "키워드 없음"과 동일 취급함.
		subsidyRepository.save(base("blank1").build());
		subsidyRepository.save(base("blank2").build());

		Page<SubsidySearchResult> page = subsidyService.search("   ", null, null, false, PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(2);
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

	@Test
	void search_includeClosed_기본은_마감지난건을_제외하고_상시는_포함한다() {
		LocalDate asOf = LocalDate.now(clock);
		SubsidyEntity future = subsidyRepository.save(base("ic_future").deadline(asOf.plusDays(10)).build());
		SubsidyEntity past = subsidyRepository.save(base("ic_past").deadline(asOf.minusDays(1)).build());
		SubsidyEntity ongoing = subsidyRepository.save(base("ic_null").deadline(null).build());

		Page<SubsidySearchResult> excluded = subsidyService.search(null, null, null, false, PageRequest.of(0, 20));
		Page<SubsidySearchResult> included = subsidyService.search(null, null, null, true, PageRequest.of(0, 20));

		// 기본(false)은 마감 지난 past를 빼고 future·ongoing만 남김
		assertThat(excluded.getContent()).extracting(SubsidySearchResult::subsidyId)
			.containsExactlyInAnyOrder(future.getId(), ongoing.getId());
		// true면 마감 지난 것도 포함해 셋 다 남김
		assertThat(included.getContent()).extracting(SubsidySearchResult::subsidyId)
			.containsExactlyInAnyOrder(future.getId(), past.getId(), ongoing.getId());
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
