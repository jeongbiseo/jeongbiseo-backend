package com.jeongbiseo.domain.consent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.TermVersion;

/**
 * 약관 버전 저장소임. 현재 유효 버전은 항목별로 effective_at이 가장 최근인 행이며, 재동의 판정과 시더 멱등성에 쓰임.
 */
public interface TermVersionRepository extends JpaRepository<TermVersion, Long> {

	/** 항목의 특정 버전 존재를 조회함(시더 멱등성 판단용). */
	Optional<TermVersion> findByTermTypeAndVersionId(TermType termType, String versionId);

	/** 항목의 현재(가장 최근 발효) 버전을 조회함. effective_at 동률이면 최근 id를 취함. */
	Optional<TermVersion> findTopByTermTypeOrderByEffectiveAtDescIdDesc(TermType termType);

}
