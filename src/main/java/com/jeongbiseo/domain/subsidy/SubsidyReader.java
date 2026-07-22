package com.jeongbiseo.domain.subsidy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;

/**
 * 지원금 마스터 조회 인터페이스임(도메인 계층, storage 비의존). SubsidyRepository가 구현함(DIP). 도메인이 영속 타입을
 * import하지 않게 이 포트 하나만 두고 리포지토리가 구현하는 것은 레이어 방향을 위한 의도된 예외임(추측성 추상화 금지 원칙의 예외, AGENTS.md
 * 코드 규칙). 검색(searchSubsidies)은 검색 엔드포인트 소관이라 이 슬라이스에서 제외하고, 추천이 쓰는 3메서드만 둠(PLAN
 * 07-subsidy-recommendation 3.C).
 */
public interface SubsidyReader {

	/**
	 * 후보 조회 개수 상한임. 요청마다 후보 전 건을 엔티티 통째로 메모리에 올리다 힙 OOM으로 서버가 죽은 2026-07-22 장애의 재발 방지
	 * 캡임(리서치 문서 docs/md/추천-후보조회-힙-OOM-AWS-대응-리서치-2026-07-22.md 4장). 메모리 절감의 본체는 DTO
	 * 프로젝션이고 이 값은 데이터 폭증 대비 최후 안전장치라, 현 운영 모집단(2026-07-22 실측 8,647건, 그중 상시모집 8,244건) 위에
	 * 여유를 두고 잡음. 절단 순서(마감 임박순, 마감 미상 뒤)는 노출 정렬 1차 키(지역 비강등 우선)와 달라서, 캡에 실제로 닿으면 상시모집이 계통
	 * 탈락함 — 모집단이 이 값에 근접하면 값을 올리지 말고 지역 우선 정렬 또는 매칭 조건의 DB 이관을 검토할 것.
	 */
	int MAX_CANDIDATES = 10_000;

	/**
	 * 추천 후보 지원금 목록을 조회함. 후보 조건은 active AND is_recommendable AND is_loan_product = false
	 * AND duplicate_of_id IS NULL AND (deadline IS NULL OR deadline &gt;= asOf)에 더해, 확실
	 * 탈락인 기업 대상·1차산업 전용을 DB에서 프리필터함(판정 정본은 RecommendationPolicy.inScope, null은 UNKNOWN 통과
	 * 원칙 유지). 추천 스코프와 RecommendationPolicy 판정에 필요한 필드만 담은 값 객체로 반환해 storage 타입이 domain
	 * 밖으로 새지 않게 함. 반환은 최대 MAX_CANDIDATES건이며, 초과 시 마감 임박순(마감일 미상은 뒤) 정렬로 앞에서부터 채움.
	 * @param asOf 신청 가능 여부를 판정할 기준일
	 * @return 매칭 후보 지원금 조건 목록(최대 MAX_CANDIDATES건)
	 */
	List<SubsidyCriteria> findCandidates(LocalDate asOf);

	/**
	 * 추천 응답 조립에 쓸 지원금 표시 정보를 조회함. RecommendationService가 매칭·정렬·limit을 마친 subsidyId만 넘겨
	 * 필요한 만큼만 조회함. 반환 순서는 입력 subsidyIds 순서에 대응함(존재하지 않는 id는 결과에서 빠짐).
	 * @param subsidyIds 조회할 지원금 id 목록(정렬 순서)
	 * @return 지원금 표시 정보 목록(입력 순서 대응)
	 */
	List<SubsidySummary> findSummaries(List<Long> subsidyIds);

	/**
	 * 지원금 마스터가 보존한 소스 데이터 갱신 시각 중 가장 최근 값을 조회함. 응답 전체 수준의 dataUpdatedAt 대표값으로 사용하며 수집
	 * 시각(fetchedAt)과 구분함.
	 * @return 가장 최근 소스 데이터 갱신 시각(저장된 값이 없으면 null)
	 */
	LocalDateTime findLatestDataUpdatedAt();

}
