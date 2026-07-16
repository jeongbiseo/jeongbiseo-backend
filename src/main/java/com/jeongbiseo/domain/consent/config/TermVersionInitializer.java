package com.jeongbiseo.domain.consent.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.jeongbiseo.domain.consent.TermType;
import com.jeongbiseo.domain.consent.entity.TermVersion;
import com.jeongbiseo.domain.consent.repository.TermVersionRepository;

/**
 * 필수 약관 3종의 현재 버전을 기동 시 term_version에 멱등 seed하는 러너임. 약관 본문은 버전 고정 파일이 정본이라 저장하지 않고, 여기서는
 * 버전 식별자와 발효 시각만 심음. terms_hash는 실제 약관 파일이 아직 없어 (항목|버전)의 SHA-256을 파일 다이제스트 대체로 계산함 — 실제
 * 약관 파일이 확정되면 그 파일의 해시로 교체함(ponytail, 파일은 프론트·제품 관할).
 */
@Component
public class TermVersionInitializer implements ApplicationRunner {

	private static final String CURRENT_VERSION_ID = "v1.0";

	// 발효 시각 고정값임(Asia/Seoul). 벽시계에 의존하지 않아 재기동 사이 seed가 흔들리지 않음.
	private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 15, 0, 0);

	private final TermVersionRepository termVersionRepository;

	public TermVersionInitializer(TermVersionRepository termVersionRepository) {
		this.termVersionRepository = termVersionRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (TermType termType : TermType.required()) {
			if (this.termVersionRepository.findByTermTypeAndVersionId(termType, CURRENT_VERSION_ID).isPresent()) {
				continue;
			}
			this.termVersionRepository.save(TermVersion.builder()
				.termType(termType)
				.versionId(CURRENT_VERSION_ID)
				.termsHash(termsHash(termType, CURRENT_VERSION_ID))
				.effectiveAt(EFFECTIVE_AT)
				.build());
		}
	}

	private static String termsHash(TermType termType, String versionId) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((termType.name() + "|" + versionId).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없음", e);
		}
	}

}
