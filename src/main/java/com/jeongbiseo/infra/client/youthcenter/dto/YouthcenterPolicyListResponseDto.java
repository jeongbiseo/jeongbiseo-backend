package com.jeongbiseo.infra.client.youthcenter.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 온통청년 getPlcy 응답 최상위 래퍼임(실호출 봉투는 {@code {resultCode, resultMessage, result:{pagging,
 * youthPolicyList}}}). 회귀 스냅샷 {@code fixtures/regression/youthcenter_snapshot.json}도 같은
 * {@code result.youthPolicyList} 경로를 쓰도록 저장해, 실호출 응답과 스냅샷을 <b>같은 파서 메서드</b>로 읽음(스냅샷 전용 파싱
 * 경로를 따로 두면 회귀 테스트가 프로덕션 경로를 검증하지 못함). 스냅샷이 덧붙인
 * {@code totalCount}·{@code sampleSize}·{@code samplingMethod} 키는 Jackson이 무시함.
 *
 * @param result 응답 본문
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouthcenterPolicyListResponseDto(@JsonProperty("result") Result result) {

	/**
	 * 응답 본문임(페이징 정보 {@code pagging}은 파싱에 쓰지 않아 읽지 않음).
	 *
	 * @param pagging 페이징 정보(외부 API의 원문 오탈자 유지)
	 * @param youthPolicyList 청년정책 목록
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(@JsonProperty("pagging") Pagging pagging,
			@JsonProperty("youthPolicyList") List<YouthcenterPolicyDto> youthPolicyList) {

	}

	/**
	 * 외부 API 페이징 정보임.
	 *
	 * @param totalCount 전체 정책 수
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Pagging(@JsonProperty("totCount") Integer totalCount) {

	}

}
