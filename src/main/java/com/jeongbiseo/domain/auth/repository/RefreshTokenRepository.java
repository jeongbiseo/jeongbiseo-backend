package com.jeongbiseo.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jeongbiseo.domain.auth.entity.RefreshToken;

/**
 * 리프레시 토큰 저장소임(데이터모델 3.3, 회원당 1행). 토큰 값은 SHA-256 해시로만 저장하고(설계 D3, D7 원문 저장 금지), 갱신(회전)은
 * 이전 해시를 조건으로 한 원자적 UPDATE로 처리해 동시 갱신 레이스를 막음(설계 D9).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByMemberId(Long memberId);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByMemberId(Long memberId);

	/**
	 * 이전 토큰 해시를 조건으로 한 원자적 회전임(설계 D9). 영향 행이 0이면 동시 갱신에서 진 것이거나 이미 폐기·회전된 토큰이라 호출부가
	 * AUTH401_2로 거부해야 함.
	 * @return 영향받은 행 수(정상 회전은 정확히 1)
	 */
	@Modifying
	@Query("update RefreshToken r set r.tokenHash = :newTokenHash, r.expiresAt = :newExpiresAt "
			+ "where r.tokenHash = :oldTokenHash")
	int rotateByTokenHash(@Param("oldTokenHash") String oldTokenHash, @Param("newTokenHash") String newTokenHash,
			@Param("newExpiresAt") LocalDateTime newExpiresAt);

}
