package com.jeongbiseo.infra.client.common;

import java.util.List;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/** 외부 API 한 소스의 전량을 수집하고 공통 타깃으로 정규화하는 운영 경계임. */
public interface SubsidySourceCollector {

	/**
	 * 수집 소스를 반환함.
	 * @return 수집 소스
	 */
	SubsidySource source();

	/**
	 * 한 소스의 전량을 수집하고 정규화함. 일부 페이지만 받은 경우 실패해야 함.
	 * @return 정규화 지원금 전량
	 */
	List<NormalizedSubsidy> collect();

}
