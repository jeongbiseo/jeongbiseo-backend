package com.jeongbiseo.infra.client.common.dto;

/**
 * 신청 채널 플래그임(조사 리포트 3장 G4 권고 — "enum 단일값 금지, 한 지원금이 온라인과 방문을 동시에 지원하는 경우가 실제로 흔함". gov24
 * 스냅샷 n=1097에서 방문 71.56%와 온라인 22.88%가 겹쳐서 나타남).
 *
 * <p>
 * <b>플래그 false의 의미는 "지원하지 않음"이 아니라 "근거 없음"임.</b> 이 구분이 이 레코드에서 가장 중요함. 4종 소스 중 어느 것도 "이
 * 채널로는 신청할 수 없다"를 <b>선언</b>하지 않고, 다들 "이 채널로 신청할 수 있다"만 말하기 때문임(gov24·온통청년은 자유텍스트 키워드,
 * K-Startup은 필드 non-null, 기업마당은 URL 유무). 따라서 false를 "불가"로 읽어 화면에 "방문 불가"라고 내면 안 됨 — 없는
 * 정보를 지어내는 것임(함정 1 "필드가 있는 것과 값이 채워지는 것은 다르다").
 *
 * <p>
 * 소스마다 <b>주장할 수 있는 채널 자체가 다름</b>도 같은 이유로 주의할 것. gov24는 email을 절대 켜지 못하고(키워드 어휘에 이메일이 없음),
 * K-Startup은 phone·autoProvided를 절대 켜지 못함(해당 필드가 없음). 즉 어떤 플래그가 false인 것은 <b>그 소스가 그 채널을
 * 말할 능력이 없어서</b>일 수도 있음. 소스별 주장 가능 범위는 {@link SubsidySource} 참조.
 *
 * @param online 온라인·인터넷 신청 근거 있음
 * @param visit 방문 신청 근거 있음(gov24 최빈 71.56%)
 * @param mail 우편 신청 근거 있음
 * @param email 이메일 접수 근거 있음(K-Startup 전용 — gov24 키워드 어휘에 없어 gov24는 항상 false)
 * @param fax 팩스 접수 근거 있음
 * @param phone 전화 신청 근거 있음. <b>과대 집계 주의</b> — "자세한 사항은 전화 문의"처럼 신청 채널이 아니라 안내 문장에도
 * 걸림(gov24 72건/6.56%). 참고 지표로만 쓸 것
 * @param autoProvided 신청 절차 없이 자동 제공됨("신청없이 자격대상자에게 자동적으로 제공됩니다" 계열, gov24 64건/5.83%). 다른
 * 플래그와 성격이 다름 — 신청 <b>채널</b>이 아니라 신청이 <b>불필요</b>하다는 뜻이라, 이 값이 true면 화면에서 신청 안내 대신 자동 지급
 * 안내를 내야 함
 * @param unclassified 어느 채널에도 분류되지 않음(gov24 136건/12.40%). 근거 없이 방문으로 단정하지 않기 위해 명시 상태로 남김
 */
public record ApplicationMethodFlags(boolean online, boolean visit, boolean mail, boolean email, boolean fax,
		boolean phone, boolean autoProvided, boolean unclassified) {

	/**
	 * 원문 자체가 없어 어떤 채널도 주장할 수 없는 상태임({@code unclassified}만 true). 팩토리 이름을 컴포넌트명과 다르게 둔 것은
	 * 자바 레코드 문법 제약임 — 컴포넌트와 같은 이름의 무인자 메서드는 접근자 override로 해석돼 컴파일이 막힘.
	 * @return 미분류 플래그
	 */
	public static ApplicationMethodFlags noEvidence() {
		return new ApplicationMethodFlags(false, false, false, false, false, false, false, true);
	}

}
