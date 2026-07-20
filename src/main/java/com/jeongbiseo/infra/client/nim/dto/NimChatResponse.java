package com.jeongbiseo.infra.client.nim.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NVIDIA NIM chat completions 응답 중 이 프로젝트가 쓰는 부분만 담음(OpenAI 호환 스키마임). 응답에는
 * refusal·annotations·logprobs 등 쓰지 않는 필드가 많아 unknown 무시가 필수임. Jackson 어노테이션은 Boot 4에서도
 * {@code com.fasterxml.jackson.annotation}에 있음(databind만 {@code tools.jackson}으로 옮겨감).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NimChatResponse(List<Choice> choices) {

	/**
	 * finishReason이 "length"면 max_tokens에 걸려 본문이 잘린 것임. 잘린 JSON은 파싱에 실패하는데 원인이 모델 오류가 아니라
	 * 토큰 상한이므로 로그에서 구분하려고 담음.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Message(String content) {
	}

	/**
	 * 첫 번째 선택지의 본문을 반환함. 선택지가 없거나 본문이 비면 null을 반환해 호출측이 실패로 처리하게 함.
	 */
	public String firstContent() {
		if (this.choices == null || this.choices.isEmpty()) {
			return null;
		}
		Choice first = this.choices.get(0);
		if (first == null || first.message() == null) {
			return null;
		}
		return first.message().content();
	}

}
