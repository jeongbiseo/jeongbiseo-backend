package com.jeongbiseo.infra.client.youthcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.jeongbiseo.infra.client.common.dto.DeadlineBasis;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.infra.client.youthcenter.dto.ParsedYouthPolicy;

/**
 * 온통청년 파싱 결과를 4종 소스 공통 타깃({@link NormalizedSubsidy})으로 변환하는 어댑터의 회귀 테스트임. 스냅샷 n=1,324 전량을
 * 변환해 <b>불변식</b>을 고정함 — 어댑터의 값은 "값을 지어내지 않는다"에 있으므로 지어내지 않았는지를 전수로 확인함.
 */
class YouthcenterSubsidyNormalizerTest {

	private static final Path SNAPSHOT = Path.of("fixtures", "regression", "youthcenter_snapshot.json");

	private final YouthcenterParser parser = new YouthcenterParser();

	private final YouthcenterSubsidyNormalizer normalizer = new YouthcenterSubsidyNormalizer();

	@Test
	void source_isYouthcenter() {
		assertThat(this.normalizer.source()).isEqualTo(SubsidySource.YOUTHCENTER);
		assertThat(SubsidySource.YOUTHCENTER.sourceId()).isEqualTo("youthcenter");
	}

	@Test
	void normalize_carriesRawFieldsWithoutLoss() throws IOException {
		ParsedYouthPolicy raw = normalizeSource().get(0);

		NormalizedSubsidy normalized = this.normalizer.normalize(raw);

		assertThat(normalized.source()).isEqualTo(SubsidySource.YOUTHCENTER);
		assertThat(normalized.externalId()).isEqualTo(raw.policyId());
		assertThat(normalized.name()).isEqualTo(raw.name());
		assertThat(normalized.agency()).isEqualTo(raw.agency());
		assertThat(normalized.description()).isEqualTo(raw.description());
		assertThat(normalized.eligibilityText()).isEqualTo(raw.eligibilityText());
		assertThat(normalized.categoryRawText()).isEqualTo(raw.categoryRawText());
		assertThat(normalized.paymentType()).isEqualTo(raw.paymentType());
		assertThat(normalized.paymentTypeRawText()).isEqualTo(raw.provisionMethodCode());
		assertThat(normalized.amount()).isEqualTo(raw.amount());
		assertThat(normalized.deadline()).isEqualTo(raw.deadline());
		assertThat(normalized.applicationMethod()).isEqualTo(raw.applicationMethod());
		assertThat(normalized.applicationUrl()).isEqualTo(raw.applicationUrl());
		assertThat(normalized.referenceUrl()).isEqualTo(raw.referenceUrl());
		assertThat(normalized.requiredDocumentsText()).isEqualTo(raw.requiredDocumentsText());
		assertThat(normalized.dataUpdatedAt()).isEqualTo(raw.dataUpdatedAt());
	}

	// 마감 근거는 항상 구조화 코드임(aplyPrdSeCd 채움 100%). gov24가 전량 PARSED_FROM_TEXT인 것과 정확히 대비됨 —
	// 같은 DeadlineKind라도 신뢰도가 다르다는 사실이 이 값 하나로 남음.
	@Test
	void normalize_deadlineBasisIsAlwaysDeclaredField() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		Map<DeadlineBasis, Long> counts = all.stream()
			.collect(Collectors.groupingBy(NormalizedSubsidy::deadlineBasis, Collectors.counting()));

		assertThat(counts.get(DeadlineBasis.DECLARED_FIELD)).isEqualTo(1324L);
		assertThat(counts.getOrDefault(DeadlineBasis.PARSED_FROM_TEXT, 0L)).isZero();
	}

	// 이 소스만 지역을 선언된 코드로 줌 — DECLARED_REGION_CODE와 HIGH를 쓰는 유일한 소스임.
	@Test
	void normalize_regionIsDeclaredNotInferred() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		long declared = all.stream()
			.filter(n -> n.region().scopeBasis() == RegionScopeBasis.DECLARED_REGION_CODE)
			.count();

		assertThat(declared).isEqualTo(1316L);
		assertThat(all).allSatisfy(n -> {
			boolean hasCodes = !n.region().regionCodes().isEmpty();
			assertThat(n.region().scopeBasis())
				.isEqualTo(hasCodes ? RegionScopeBasis.DECLARED_REGION_CODE : RegionScopeBasis.NOT_APPLICABLE);
			assertThat(n.region().confidence()).isEqualTo(hasCodes ? RegionConfidence.HIGH : RegionConfidence.LOW);
			assertThat(n.region().regionLevel()).isEqualTo(hasCodes ? RegionLevel.SIGUNGU : RegionLevel.NATIONAL);
		});
		// 코드에서 명칭을 역산하지 않음(법정동코드 시드 테이블이 아직 없음 — 없는 정보를 지어내지 않음)
		assertThat(all).allSatisfy(n -> assertThat(n.region().sidoName()).isNull());
		assertThat(all).allSatisfy(n -> assertThat(n.region().sigunguName()).isNull());
	}

	// 온통청년이 줄 수 없는 것을 지어내지 않았는지 전수로 고정함.
	@Test
	void normalize_neverInventsWhatYouthcenterCannotGive() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		assertThat(all).hasSize(1324);
		// (가) 가구 조건 — sbizCd(정책특화요건)는 채움 100%지만 축이 가구가 아님(여성·장애인·중소기업이 섞임).
		// 채움률이 아니라 의미가 안 맞아서 안 씀
		assertThat(all)
			.allSatisfy(n -> assertThat(n.eligibility().householdSignal()).isEqualTo(EligibilitySignal.UNKNOWN));
		// (나) 연령 무관 — 이를 선언하는 믿을 만한 필드가 없음(sprtTrgtAgeLmtYn은 값과 어긋남)
		assertThat(all).noneMatch(n -> n.eligibility().ageSignal() == EligibilitySignal.UNRESTRICTED);
		// (다) 지급유형 IN_KIND — 정책제공방법코드 13종에 현물이라는 값 자체가 없음("불가"가 아니라 "말할 수 없음")
		assertThat(all).noneMatch(n -> n.paymentType() == com.jeongbiseo.domain.common.enums.PaymentType.IN_KIND);
	}

	// 이 소스가 gov24보다 나은 3축(지역 코드·카테고리·고용 신호)을 실제로 채우는지 고정함. 공통 타깃이 소스들의 강점을 합류시키는
	// 자리라는 것이 이 테스트의 요지임.
	@Test
	void normalize_fillsWhatGov24CannotGive() throws IOException {
		List<NormalizedSubsidy> all = normalizeSnapshot();

		assertThat(all.stream().filter(n -> !n.region().regionCodes().isEmpty()).count()).isEqualTo(1316L);
		assertThat(all).allSatisfy(n -> assertThat(n.categoryRawText()).isNotBlank());
		assertThat(all.stream().filter(n -> n.eligibility().employmentSignal() != EligibilitySignal.UNKNOWN).count())
			.isEqualTo(1324L);
		assertThat(all).allSatisfy(n -> assertThat(n.eligibility().employmentRawCode()).isNotBlank());
		// 이메일 접수는 gov24가 주장할 수 없는 채널임
		assertThat(all.stream().filter(n -> n.applicationMethod().email()).count()).isEqualTo(102L);
	}

	private List<NormalizedSubsidy> normalizeSnapshot() throws IOException {
		return normalizeSource().stream().map(this.normalizer::normalize).toList();
	}

	private List<ParsedYouthPolicy> normalizeSource() throws IOException {
		return this.parser.parsePolicies(Files.readString(SNAPSHOT, StandardCharsets.UTF_8))
			.stream()
			.map(this.parser::toParsedPolicy)
			.toList();
	}

}
