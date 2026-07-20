package com.jeongbiseo.infra.enrichment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 공고 본문 정규화와 해시를 담당함. <b>정규화 규칙이 한 곳에 있어야 하는 이유</b>는 두 소비자가 같은 규칙을 써야 하기 때문임 — 해시는 "원문이
 * 바뀌었는가"를 판정하고 근거 대조는 "이 문장이 원문에 있는가"를 판정하는데, 규칙이 갈리면 본문이 그대로인데 해시만 달라지거나 정상 근거가 폐기됨.
 *
 * <p>
 * NFC 통일이 필수인 것은 한글이 자모 분리(NFD)와 완성형(NFC) 표현을 모두 가질 수 있기 때문이고, 공백 압축이 필요한 것은 공공 API 본문에
 * 줄바꿈·탭·연속 공백이 뒤섞여 있기 때문임.
 * </p>
 */
public final class ContentHasher {

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private ContentHasher() {
	}

	/**
	 * NFC 정규화 후 연속 공백을 하나로 접고 앞뒤를 다듬음.
	 * @param text 원본 문자열(null이면 빈 문자열)
	 * @return 정규화된 문자열
	 */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").trim();
	}

	/**
	 * 정규화한 본문의 SHA-256 hex를 만듦(64자). 정규화를 먼저 하므로 공백·줄바꿈만 달라진 본문은 같은 해시가 되어, 의미 없는 재보강을
	 * 막음.
	 * @param text 공고 본문
	 * @return SHA-256 hex 문자열
	 */
	public static String hash(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(normalize(text).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		}
		catch (NoSuchAlgorithmException exception) {
			// SHA-256은 모든 JVM이 제공해야 하는 알고리즘이라 실제로는 도달하지 않음.
			throw new IllegalStateException("SHA-256을 사용할 수 없음");
		}
	}

}
