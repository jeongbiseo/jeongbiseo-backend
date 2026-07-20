package com.jeongbiseo.domain.favorite;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.domain.common.enums.PaymentType;
import com.jeongbiseo.domain.common.enums.RegionScope;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.favorite.repository.FavoriteRepository;
import com.jeongbiseo.domain.favorite.service.FavoriteService;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.subsidy.dto.SubsidyDetailResponse;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.domain.subsidy.service.SubsidyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관심 등록 종단 통합 테스트임(@SpringBootTest 더하기 Testcontainers 실제 MySQL). 등록·해제의 상세 반영, 복합 유니크 제약,
 * 캘린더 대상 필터와 정렬을 실제 JPA 쿼리로 고정함.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class FavoriteIntegrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SubsidyRepository subsidyRepository;

	@Autowired
	private FavoriteRepository favoriteRepository;

	@Autowired
	private FavoriteService favoriteService;

	@Autowired
	private SubsidyService subsidyService;

	@Test
	void 등록하면_상세_isFavorite가_true이고_해제하면_false다() {
		Member member = memberRepository.save(newMember());
		SubsidyEntity subsidy = subsidyRepository.save(subsidy("detail", LocalDate.of(2026, 8, 31)));

		favoriteService.add(member.getId(), subsidy.getId());
		SubsidyDetailResponse favorited = subsidyService.getDetail(subsidy.getId(), member.getId());
		assertThat(favorited.isFavorite()).isTrue();

		favoriteService.remove(member.getId(), subsidy.getId());
		SubsidyDetailResponse removed = subsidyService.getDetail(subsidy.getId(), member.getId());
		assertThat(removed.isFavorite()).isFalse();
	}

	@Test
	void 같은회원과_지원금_중복저장은_유니크제약으로_거부된다() {
		Member member = memberRepository.save(newMember());
		SubsidyEntity subsidy = subsidyRepository.save(subsidy("unique", LocalDate.of(2026, 8, 31)));
		favoriteRepository.saveAndFlush(Favorite.builder().member(member).subsidy(subsidy).build());

		assertThatThrownBy(
				() -> favoriteRepository.saveAndFlush(Favorite.builder().member(member).subsidy(subsidy).build()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 캘린더쿼리는_마감없는건과_지난마감을_제외하고_마감오름차순으로_반환한다() {
		Member member = memberRepository.save(newMember());
		SubsidyEntity noDeadline = subsidyRepository.save(subsidy("none", null));
		SubsidyEntity past = subsidyRepository.save(subsidy("past", LocalDate.of(2026, 7, 19)));
		SubsidyEntity later = subsidyRepository.save(subsidy("later", LocalDate.of(2026, 7, 31)));
		SubsidyEntity sooner = subsidyRepository.save(subsidy("sooner", LocalDate.of(2026, 7, 25)));
		favoriteRepository.saveAllAndFlush(List.of(Favorite.builder().member(member).subsidy(noDeadline).build(),
				Favorite.builder().member(member).subsidy(past).build(),
				Favorite.builder().member(member).subsidy(later).build(),
				Favorite.builder().member(member).subsidy(sooner).build()));

		List<Favorite> targets = favoriteRepository.findCalendarTargets(member.getId(), LocalDate.of(2026, 7, 20),
				LocalDate.of(2026, 7, 31));

		assertThat(targets).extracting(favorite -> favorite.getSubsidy().getId())
			.containsExactly(sooner.getId(), later.getId());
	}

	private static Member newMember() {
		return Member.builder().role(Role.ROLE_USER).onboardingCompleted(true).build();
	}

	private static SubsidyEntity subsidy(String externalId, LocalDate deadline) {
		return SubsidyEntity.builder()
			.sourceId("favorite-test")
			.externalId(externalId)
			.name("관심 테스트 " + externalId)
			.deadline(deadline)
			.paymentType(PaymentType.CASH)
			.duplicationPolicy("ALLOW")
			.targetAudience(TargetAudience.PERSONAL)
			.occupationRestriction(OccupationRestriction.NONE)
			.regionScope(RegionScope.NATIONWIDE)
			.active(true)
			.recommendable(true)
			.loanProduct(false)
			.build();
	}

}
