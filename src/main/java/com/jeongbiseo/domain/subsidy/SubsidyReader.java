package com.jeongbiseo.domain.subsidy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.jeongbiseo.domain.subsidy.dto.SubsidyCriteria;
import com.jeongbiseo.domain.subsidy.dto.SubsidySummary;

/**
 * 지원금 마스터 조회 인터페이스임(도메인 계층, storage 비의존). SubsidyRepository가 구현함(DIP). 도메인이 영속 타입을
 * import하지 않게 이 포트 하나만 두고 리포지토리가 구현하는 것은 레이어 방향을 위한 의도된 예외임(추측성 추상화 금지 원칙의 예외, AGENTS.md
 * 코드 규칙). 검색 (searchSubsidies)은 순위 4 검색 엔드포인트 소관이라 이 슬라이스에서 제외하고, 추천이 쓰는 3메서드만 둠(PLAN
 * 07-subsidy-recommendation 3.C).
 */
public interface SubsidyReader {

	/**
	 * 추천 후보 지원금 목록을 조회함. 후보 조건은 active AND is_recommendable AND is_loan_product = false
	 * AND duplicate_of_id IS NULL AND (deadline IS NULL OR deadline &gt;= asOf)임. 추천 스코프와
	 * RecommendationPolicy 판정에 필요한 필드만 담은 값 객체로 반환해 storage 타입이 domain 밖으로 새지 않게 함.
	 * @param asOf 신청 가능 여부를 판정할 기준일
	 * @return 매칭 후보 지원금 조건 목록
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
