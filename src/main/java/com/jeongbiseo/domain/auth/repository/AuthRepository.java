package com.jeongbiseo.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.auth.entity.Auth;
import com.jeongbiseo.domain.auth.entity.Provider;

/**
 * 소셜 계정 연결(social_account) 저장소임(데이터모델 3.2). 콜백 조회는 fetch join으로 N+1을 막음(설계 4장). 회원
 * 탈퇴(Model A)는 deleteByMemberId로 auth 행을 하드 삭제해 같은 소셜 계정 재로그인이 자동 신규가입이 되게 함.
 */
public interface AuthRepository extends JpaRepository<Auth, Long> {

	@Query("select a from Auth a join fetch a.member where a.provider = :provider and a.providerId = :providerId")
	Optional<Auth> findByProviderAndProviderIdWithMember(@Param("provider") Provider provider,
			@Param("providerId") String providerId);

	void deleteByMemberId(Long memberId);

}
