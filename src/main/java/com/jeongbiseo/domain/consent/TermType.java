package com.jeongbiseo.domain.consent;

import java.util.List;

/**
 * 필수 약관 항목임. MVP는 필수 3종만 두고 선택 약관(마케팅 수신·제3자 제공 등)은 제거함(결정 2.B-12). 약관 본문은 DB에 넣지 않고 버전
 * 고정 파일과 해시로 관리하며, 이 enum은 어떤 항목을 동의받아야 하는지의 계약만 담음.
 */
public enum TermType {

	/** 서비스 이용약관. */
	SERVICE("서비스 이용약관"),

	/** 개인정보 수집·이용 동의. */
	PRIVACY("개인정보 수집·이용"),

	/** 만 14세 이상 확인. */
	AGE_OVER_14("만 14세 이상");

	private final String label;

	TermType(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

	/** 회원가입에 필요한 필수 약관 전체임. MVP에서는 모든 항목이 필수라 전 값과 같음. */
	public static List<TermType> required() {
		return List.of(values());
	}

}
