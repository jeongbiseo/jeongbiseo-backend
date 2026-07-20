package com.jeongbiseo.domain.subsidy;

import java.util.Optional;

import com.jeongbiseo.domain.subsidy.dto.AiExplanation;

/**
 * 지원금 상세가 AI 해석을 읽는 포트임. 구현은 {@code infra.enrichment}에 있음.
 *
 * <p>
 * <b>포트를 둔 이유</b>: 등급 1~2에서 {@code domain} 패키지에 LLM import가 0건이어야 한다는 불변식(HANDOFF 3장)
 * 때문임. {@code SubsidyService}가 보강 저장소를 직접 주입받으면 도메인이 NIM 클라이언트·프롬프트·검증기가 있는 패키지를 알게 됨. 이
 * 인터페이스는 "AI 해석을 읽는다"는 것만 알고 그것이 어떻게 만들어졌는지는 모름. {@code SubsidyReader}가 추천 도메인의 읽기 경계를 가른
 * 것과 같은 패턴임.
 * </p>
 */
public interface AiExplanationReader {

	/**
	 * 지원금의 유효한 AI 해석을 봄.
	 *
	 * <p>
	 * <b>공고 본문을 함께 받는 것은 필수임.</b> 구현이 본문 해시로 유효성을 판정하기 때문이며, 본문이 바뀐 뒤에는 옛 해석이 나오지 않아야 함.
	 * 지원금 id만으로 조회하는 메서드를 추가하지 말 것 — 낡은 AI 해석이 화면에 남는 사고를 타입 수준에서 막는 장치임.
	 * </p>
	 * @param subsidyId 지원금 id
	 * @param description 현재 공고 본문
	 * @return 유효한 해석. 없으면 빈 Optional(화면은 기존 산정불가 배지를 유지함)
	 */
	Optional<AiExplanation> findFor(Long subsidyId, String description);

}
