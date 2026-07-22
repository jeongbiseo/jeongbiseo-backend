package com.jeongbiseo.domain.subsidy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.SubsidyCategory;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지원금 영속성 통합 테스트임(SpringBootTest 더하기 Testcontainers 실제 MySQL, Docker 필요). 후보 쿼리의
 * 마감·융자·중복·비활성 제외(마감 미상·당일 유지)와 toCriteria/toSummary 매퍼의 필드 보존, 최신 갱신 시각 집계를 실제 DB에서 고정함.
 * 각 테스트는 트랜잭션 롤백으로 격리함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SubsidyPersistenceIntegrationTest {

	// testcontainers-junit-jupiter 의존성 없이 컨테이너를 수동 기동함(@ServiceConnection이 연결 정보를 주입).
	// Ryuk가 JVM 종료 시 컨테이너를 정리함.
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	private static final LocalDate AS_OF = LocalDate.of(2026, 7, 16);

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Test
	void 후보_쿼리는_활성_추천가능_비융자_대표행_신청가능만_반환한다() {
		SubsidyEntity candidate = base("s1").deadline(AS_OF.plusDays(10)).build();
		SubsidyEntity dueToday = base("today").deadline(AS_OF).build();
		SubsidyEntity openEnded = base("open").deadline(null).build();
		subsidyRepository.saveAll(List.of(candidate, dueToday, openEnded));

		subsidyRepository.save(base("inactive").active(false).build());
		subsidyRepository.save(base("nonRec").recommendable(false).build());
		subsidyRepository.save(base("loan").loanProduct(true).build());
		subsidyRepository.save(base("expired").deadline(AS_OF.minusDays(1)).build());
		// 대표 행(duplicateOfId null)은 후보이고, 이를 가리키는 중복 자식(dupChild)만 제외됨
		SubsidyEntity representative = base("rep").deadline(AS_OF.plusDays(2)).build();
		representative = subsidyRepository.save(representative);
		subsidyRepository.save(base("dupChild").duplicateOfId(representative.getId()).build());

		List<SubsidyCriteria> found = subsidyRepository.findCandidates(AS_OF);

		assertThat(found).extracting(SubsidyCriteria::subsidyId)
			.containsExactlyInAnyOrder(candidate.getId(), dueToday.getId(), openEnded.getId(), representative.getId());
	}

	@Test
	void 후보_쿼리_상한_절단은_마감_임박순으로_앞에서부터_채운다() {
		SubsidyEntity later = subsidyRepository.save(base("later").deadline(AS_OF.plusDays(10)).build());
		SubsidyEntity soon = subsidyRepository.save(base("soon").deadline(AS_OF.plusDays(1)).build());
		SubsidyEntity openEnded = subsidyRepository.save(base("open").deadline(null).build());

		// MAX_CANDIDATES 실물 대신 페이지 크기 2로 같은 쿼리의 절단 순서를 고정함(마감 미상이 먼저 잘림)
		List<SubsidyCriteria> capped = subsidyRepository.findCandidateCriteria(AS_OF, PageRequest.of(0, 2));

		assertThat(capped).extracting(SubsidyCriteria::subsidyId).containsExactly(soon.getId(), later.getId());
		assertThat(capped).extracting(SubsidyCriteria::subsidyId).doesNotContain(openEnded.getId());
	}

	@Test
	void 후보_쿼리_프리필터는_기업대상과_1차산업만_제외하고_미상은_통과시킨다() {
		// inScope 정본과 같은 방향의 DB 프리필터 회귀 고정: 확실 탈락 2종만 빠지고,
		// 개인 대상·조건 미상(UNKNOWN)은 잘못 제외되지 않아야 함(AGENTS.md 반대 방향 회귀 원칙)
		SubsidyEntity personal = subsidyRepository.save(base("personal").build());
		SubsidyEntity unknownAudience = subsidyRepository
			.save(base("unknownAud").targetAudience(TargetAudience.UNKNOWN).build());
		SubsidyEntity mixedAudience = subsidyRepository
			.save(base("mixedAud").targetAudience(TargetAudience.MIXED).build());
		subsidyRepository.save(base("business").targetAudience(TargetAudience.BUSINESS).build());
		subsidyRepository
			.save(base("primary").occupationRestriction(OccupationRestriction.PRIMARY_INDUSTRY_ONLY).build());

		List<SubsidyCriteria> found = subsidyRepository.findCandidates(AS_OF);

		assertThat(found).extracting(SubsidyCriteria::subsidyId)
			.containsExactlyInAnyOrder(personal.getId(), unknownAudience.getId(), mixedAudience.getId());
	}

	@Test
	void toCriteria_매퍼가_지역과_신호_필드를_보존한다() {
		SubsidyEntity regional = base("reg").regionScope(RegionScope.REGIONAL)
			.regionCode("11620")
			.ageSignal(EligibilitySignal.RESTRICTED)
			.ageMin(19)
			.ageMax(34)
			.incomeSignal(EligibilitySignal.UNKNOWN)
			.deadline(AS_OF.plusDays(3))
			.build();
		subsidyRepository.save(regional);

		SubsidyCriteria criteria = subsidyRepository.findCandidates(AS_OF)
			.stream()
			.filter(c -> c.subsidyId().equals(regional.getId()))
			.findFirst()
			.orElseThrow();

		assertThat(criteria.regionScope()).isEqualTo(RegionScope.REGIONAL);
		assertThat(criteria.regionCode()).isEqualTo("11620");
		assertThat(criteria.ageSignal()).isEqualTo(EligibilitySignal.RESTRICTED);
		assertThat(criteria.ageMin()).isEqualTo(19);
		assertThat(criteria.ageMax()).isEqualTo(34);
		assertThat(criteria.incomeSignal()).isEqualTo(EligibilitySignal.UNKNOWN);
		assertThat(criteria.sourceId()).isEqualTo("gov24");
		assertThat(criteria.externalId()).isEqualTo("reg");
	}

	@Test
	void toSummary_매퍼가_표시필드를_원문_그대로_담는다() {
		SubsidyEntity subsidy = base("sum").name("청년월세지원")
			.agency("국토교통부")
			.eligibilityText("만 19~34세 무주택 청년")
			.estimatedAmountMin(100_000L)
			.estimatedAmountMax(200_000L)
			.deadline(AS_OF.plusDays(5))
			.build();
		subsidy = subsidyRepository.save(subsidy);

		List<SubsidySummary> summaries = subsidyRepository.findSummaries(List.of(subsidy.getId()));

		assertThat(summaries).singleElement().satisfies(s -> {
			assertThat(s.name()).isEqualTo("청년월세지원");
			assertThat(s.agency()).isEqualTo("국토교통부");
			assertThat(s.eligibilitySummary()).isEqualTo("만 19~34세 무주택 청년");
			assertThat(s.estimatedAmountMin()).isEqualTo(100_000L);
			assertThat(s.estimatedAmountMax()).isEqualTo(200_000L);
			// 엔티티에서 표시용 값 객체까지 지급 유형이 이어지는지 고정함(추천 응답 배지 분기의 원천)
			assertThat(s.paymentType()).isEqualTo(PaymentType.CASH);
		});
	}

	@Test
	void findSummaries_는_입력_id_순서대로_반환한다() {
		SubsidyEntity first = subsidyRepository.save(base("a").build());
		SubsidyEntity second = subsidyRepository.save(base("b").build());
		SubsidyEntity third = subsidyRepository.save(base("c").build());

		// 저장 순서(=id 오름차순)와 다른 순서로 조회해도 입력 순서를 따라야 함
		List<Long> requested = List.of(third.getId(), first.getId(), second.getId());
		List<SubsidySummary> summaries = subsidyRepository.findSummaries(requested);

		assertThat(summaries).extracting(SubsidySummary::subsidyId).containsExactlyElementsOf(requested);
	}

	@Test
	void findLatestDataUpdatedAt_는_소스_갱신시각_최대값을_반환한다() {
		subsidyRepository.save(base("old").dataUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build());
		subsidyRepository.save(base("new").dataUpdatedAt(LocalDateTime.of(2026, 7, 15, 12, 0)).build());

		assertThat(subsidyRepository.findLatestDataUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 12, 0));
	}

	// 필수 NOT NULL 컬럼을 채운 후보 기본형 빌더임. 각 테스트가 externalId를 키로 구분하고 필요한 축만 덮어씀.
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
