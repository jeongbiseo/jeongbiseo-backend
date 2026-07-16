package com.jeongbiseo.infra.client.common;

import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;

/**
 * 소스 원문 파싱 결과를 공통 타깃 {@link NormalizedSubsidy}로 수렴시키는 계약임. 4종 소스가 각각 하나씩 구현함.
 *
 * <p>
 * <b>이 인터페이스가 버는 값은 다형성이 아니라 컴파일 강제임.</b> 솔직히 말해 현재 이걸 다형적으로 호출하는 코드는 없고, 앞으로도 수집 배치는 소스별
 * 클라이언트가 자기 원문 타입을 가져와 자기 정규화기를 부르는 모양이 될 것임(공용 이음매는 이 인터페이스가 아니라
 * {@link NormalizedSubsidy} 자신임). 그럼에도 두는 이유는 하나임 — 다음 3개 소스를 구현할 사람이 <b>제멋대로 다른 반환 타입을
 * 만들지 못하게</b> 컴파일러가 막아 주기 때문임. 이번 회차가 존재하는 이유가 바로 "4개 구현이 각자 다른 모양을 만드는 것"을 막는 것이라, 5줄짜리
 * 보험은 값을 함.
 *
 * <p>
 * <b>구현자가 반드시 지킬 것.</b>
 * <ul>
 * <li>없는 정보를 지어내지 말 것. 소스가 안 주는 축은 UNKNOWN·null·빈 목록으로 남기고, 그럴듯한 기본값(예 미분류를 "방문"으로, 코드 없는
 * 지역을 명칭에서 역산)으로 메우지 말 것</li>
 * <li>{@code categoryRawText}는 원문 그대로 넣고 {@code SubsidyCategory}로 매핑하지 말 것(매핑표 미확정 — 소스별로
 * 지어내면 카테고리가 어긋남)</li>
 * <li>금액은 새로 짜지 말고 gov24의 검증된 파서를 재사용할 것({@link NormalizedSubsidy#amount()} 참조)</li>
 * </ul>
 *
 * @param <R> 소스 원문 파싱 결과 타입(gov24는 {@code ParsedSubsidyResult})
 */
public interface SubsidyNormalizer<R> {

	/**
	 * 이 정규화기가 담당하는 출처임.
	 * @return 수집 출처
	 */
	SubsidySource source();

	/**
	 * 소스 원문 파싱 결과 1건을 공통 타깃으로 변환함.
	 * @param raw 소스 원문 파싱 결과
	 * @return 공통 타깃 레코드
	 */
	NormalizedSubsidy normalize(R raw);

}
