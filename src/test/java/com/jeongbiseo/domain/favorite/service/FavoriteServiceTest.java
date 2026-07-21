package com.jeongbiseo.domain.favorite.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.jeongbiseo.domain.favorite.entity.Favorite;
import com.jeongbiseo.domain.favorite.repository.FavoriteRepository;
import com.jeongbiseo.domain.member.entity.Member;
import com.jeongbiseo.domain.member.entity.Role;
import com.jeongbiseo.domain.member.repository.MemberRepository;
import com.jeongbiseo.domain.subsidy.dto.SubsidySearchResult;
import com.jeongbiseo.domain.subsidy.entity.SubsidyEntity;
import com.jeongbiseo.domain.subsidy.repository.SubsidyRepository;
import com.jeongbiseo.global.apiPayload.code.FavoriteErrorCode;
import com.jeongbiseo.global.apiPayload.code.MemberErrorCode;
import com.jeongbiseo.global.apiPayload.code.SubsidyErrorCode;
import com.jeongbiseo.global.apiPayload.exception.CustomException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * FavoriteService 단위 테스트임. 등록·해제 성공과 도메인 오류 변환을 고정함.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

	private static final Long MEMBER_ID = 1L;

	private static final Long SUBSIDY_ID = 10L;

	@Mock
	private FavoriteRepository favoriteRepository;

	@Mock
	private SubsidyRepository subsidyRepository;

	@Mock
	private MemberRepository memberRepository;

	private FavoriteService favoriteService;

	private Member member;

	private SubsidyEntity subsidy;

	@BeforeEach
	void setUp() {
		favoriteService = new FavoriteService(favoriteRepository, subsidyRepository, memberRepository);
		member = Member.builder().role(Role.ROLE_USER).build();
		subsidy = SubsidyEntity.builder().id(SUBSIDY_ID).build();
	}

	@Test
	void add_정상이면_관심등록을_저장한다() {
		given(subsidyRepository.findById(SUBSIDY_ID)).willReturn(Optional.of(subsidy));
		given(favoriteRepository.existsByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(false);
		given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

		assertThatCode(() -> favoriteService.add(MEMBER_ID, SUBSIDY_ID)).doesNotThrowAnyException();

		verify(favoriteRepository).saveAndFlush(any(Favorite.class));
	}

	@Test
	void add_이미등록됐으면_FAVORITE409_1을_던진다() {
		given(subsidyRepository.findById(SUBSIDY_ID)).willReturn(Optional.of(subsidy));
		given(favoriteRepository.existsByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(true);

		assertErrorCode(() -> favoriteService.add(MEMBER_ID, SUBSIDY_ID), FavoriteErrorCode.FAVORITE_ALREADY_EXISTS);
	}

	@Test
	void add_지원금이없으면_중복검사보다_SUBSIDY404_1을_먼저_던진다() {
		given(subsidyRepository.findById(SUBSIDY_ID)).willReturn(Optional.empty());

		assertErrorCode(() -> favoriteService.add(MEMBER_ID, SUBSIDY_ID), SubsidyErrorCode.SUBSIDY_NOT_FOUND);
	}

	@Test
	void add_회원ID가유효하지않으면_MEMBER404_1을_던진다() {
		given(subsidyRepository.findById(SUBSIDY_ID)).willReturn(Optional.of(subsidy));
		given(favoriteRepository.existsByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(false);
		given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		assertErrorCode(() -> favoriteService.add(MEMBER_ID, SUBSIDY_ID), MemberErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void add_유니크제약경합은_FAVORITE409_1로_변환한다() {
		given(subsidyRepository.findById(SUBSIDY_ID)).willReturn(Optional.of(subsidy));
		given(favoriteRepository.existsByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(false);
		given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		given(favoriteRepository.saveAndFlush(any(Favorite.class)))
			.willThrow(new DataIntegrityViolationException("duplicate"));

		assertErrorCode(() -> favoriteService.add(MEMBER_ID, SUBSIDY_ID), FavoriteErrorCode.FAVORITE_ALREADY_EXISTS);
	}

	@Test
	void remove_등록건이있으면_삭제한다() {
		Favorite favorite = Favorite.builder().member(member).subsidy(subsidy).build();
		given(favoriteRepository.findByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(Optional.of(favorite));

		favoriteService.remove(MEMBER_ID, SUBSIDY_ID);

		verify(favoriteRepository).delete(favorite);
	}

	@Test
	void remove_미등록이면_FAVORITE404_1을_던진다() {
		given(favoriteRepository.findByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(Optional.empty());

		assertErrorCode(() -> favoriteService.remove(MEMBER_ID, SUBSIDY_ID), FavoriteErrorCode.FAVORITE_NOT_FOUND);
	}

	@Test
	void isFavorite_리포지토리결과를_반환한다() {
		given(favoriteRepository.existsByMemberIdAndSubsidyId(MEMBER_ID, SUBSIDY_ID)).willReturn(true);

		assertThat(favoriteService.isFavorite(MEMBER_ID, SUBSIDY_ID)).isTrue();
	}

	@Test
	void getFavorites_리포지토리결과를_그대로_반환한다() {
		List<SubsidySearchResult> favorites = List.of(
				new SubsidySearchResult(10L, "청년 월세 특별지원", null, null, null, 200000L, 200000L),
				new SubsidySearchResult(11L, "청년 구직활동 지원금", null, null, null, null, null));
		given(favoriteRepository.findFavoriteSubsidies(MEMBER_ID)).willReturn(favorites);

		assertThat(favoriteService.getFavorites(MEMBER_ID)).isEqualTo(favorites);
	}

	@Test
	void getFavorites_없으면_빈목록을_반환한다() {
		given(favoriteRepository.findFavoriteSubsidies(MEMBER_ID)).willReturn(List.of());

		assertThat(favoriteService.getFavorites(MEMBER_ID)).isEmpty();
	}

	private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
			Object expectedErrorCode) {
		assertThatThrownBy(callable).isInstanceOf(CustomException.class)
			.satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(expectedErrorCode));
	}

}
