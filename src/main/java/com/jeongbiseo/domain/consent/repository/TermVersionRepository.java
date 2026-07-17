package com.jeongbiseo.domain.consent.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.TermVersion;

/**
 * 약관 버전 저장소임. 현재 유효 버전은 항목별로 발효 시각이 기준 시각 이하인 것 중 가장 최근인 행이며, 재동의 판정과 시더 멱등성에 쓰임.
 */
public interface TermVersionRepository extends JpaRepository<TermVersion, Long> {

	/** 항목의 특정 버전 존재를 조회함(시더 멱등성 판단용). */
	Optional<TermVersion> findByTermTypeAndVersionId(TermType termType, String versionId);

	/**
	 * 항목의 현재(기준 시각 이하로 발효된 것 중 가장 최근) 버전을 조회함. effective_at 동률이면 최근 id를 취함. 미래 발효 버전은
	 * 제외해, 약관을 사전 등록해도 발효 전까지는 현재 버전으로 선택되지 않음.
	 */
	Optional<TermVersion> findTopByTermTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(TermType termType,
			LocalDateTime asOf);

}
