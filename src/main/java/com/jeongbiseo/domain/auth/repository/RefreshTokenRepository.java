package com.jeongbiseo.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.jeongbiseo.domain.auth.entity.RefreshToken;

/**
 * 리프레시 토큰 저장소임(데이터모델 3.3, 회원당 1행). 토큰 값은 SHA-256 해시로만 저장하고(설계 D3, D7 원문 저장 금지), 갱신(회전)은
 * 이전 해시를 조건으로 한 원자적 UPDATE로 처리해 동시 갱신 레이스를 막음(설계 D9).
 *
 * 회전과 그 뒤의 조회는 각각 자기 트랜잭션에서 돌아야 함. MySQL InnoDB 기본 격리(REPEATABLE READ)에서는 UPDATE가 최신 행을
 * 보는 반면 같은 트랜잭션의 SELECT는 시작 시점 스냅샷을 읽어, 회전 경쟁에서 진 요청이 이긴 요청이 방금 쓴 prev_token_hash를 못 보기
 * 때문임. 호출부(AuthService.reissue)는 그래서 트랜잭션을 열지 않음.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByMemberId(Long memberId);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByMemberId(Long memberId);

	/**
	 * 이전 토큰 해시를 조건으로 한 원자적 회전임(설계 D9). 폐기되는 해시와 회전 시각을 prev 컬럼에 남겨 유예 조회가 가능하게 함. 만료 판정도
	 * 같은 문장에 넣어 조회와 갱신 사이의 틈을 없앰. 영향 행이 0이면 미존재·만료이거나 동시 갱신에서 진 것이라 호출부가 유예 경로를 거쳐
	 * AUTH401_2를 판단해야 함.
	 * @return 영향받은 행 수(정상 회전은 정확히 1)
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken r set r.prevTokenHash = r.tokenHash, r.prevRotatedAt = :now, "
			+ "r.tokenHash = :newTokenHash, r.expiresAt = :newExpiresAt "
			+ "where r.tokenHash = :oldTokenHash and r.expiresAt > :now")
	int rotateByTokenHash(@Param("oldTokenHash") String oldTokenHash, @Param("newTokenHash") String newTokenHash,
			@Param("newExpiresAt") LocalDateTime newExpiresAt, @Param("now") LocalDateTime now);

	/** 회전 직후 회원 식별용임. 엔티티를 붙들지 않으려고 FK 값만 읽음(LAZY 연관 초기화 회피). */
	@Query("select r.member.id from RefreshToken r where r.tokenHash = :tokenHash")
	Optional<Long> findMemberIdByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * 회전 유예 조회임. 직전 회전에서 폐기된 해시로 들어온 요청이라도 유예창 안이고 현재 토큰이 유효하면 회원을 반환함. 중복 발사에서 진 요청을
	 * 401로 죽이지 않기 위한 경로임.
	 */
	@Query("select r.member.id from RefreshToken r "
			+ "where r.prevTokenHash = :prevTokenHash and r.prevRotatedAt > :graceThreshold and r.expiresAt > :now")
	Optional<Long> findMemberIdByPrevTokenHash(@Param("prevTokenHash") String prevTokenHash,
			@Param("graceThreshold") LocalDateTime graceThreshold, @Param("now") LocalDateTime now);

}
