package com.jeongbiseo.infra.enrichment;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContentHasher 단위 테스트임. 이 규칙이 흔들리면 두 곳이 동시에 망가짐 — 본문이 그대로인데 해시가 달라져 무의미한 재보강이 돌거나, 정상
 * 근거가 원문에 없다고 폐기됨.
 */
class ContentHasherTest {

	@Test
	void 공백과_줄바꿈만_다른_본문은_같은_해시가_된다() {
		String a = "지원내용: 월 20만원을\n최대 12개월간 지원합니다.";
		String b = "지원내용:  월 20만원을   최대 12개월간 지원합니다.";

		assertThat(ContentHasher.hash(a)).isEqualTo(ContentHasher.hash(b));
	}

	/**
	 * 한글은 완성형(NFC)과 자모 분리(NFD) 표현이 공존함. 같은 글자를 다르게 인코딩한 본문이 다른 해시가 되면 재보강이 무한히 돌므로 NFC로
	 * 통일함.
	 */
	@Test
	void 자모_분리된_한글도_같은_해시가_된다() {
		String composed = "월 20만원 지원";
		String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

		// 입력 자체는 서로 다른 문자열임을 먼저 확인함(테스트가 자기충족적이지 않도록)
		assertThat(decomposed).isNotEqualTo(composed);
		assertThat(ContentHasher.hash(decomposed)).isEqualTo(ContentHasher.hash(composed));
	}

	@Test
	void 내용이_다르면_해시가_다르다() {
		assertThat(ContentHasher.hash("월 20만원 지원")).isNotEqualTo(ContentHasher.hash("월 30만원 지원"));
	}

	@Test
	void 해시는_SHA_256_hex_64자다() {
		assertThat(ContentHasher.hash("아무 본문")).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	void null과_빈_문자열도_해시가_난다() {
		assertThat(ContentHasher.hash(null)).hasSize(64);
		assertThat(ContentHasher.normalize(null)).isEmpty();
	}

}
