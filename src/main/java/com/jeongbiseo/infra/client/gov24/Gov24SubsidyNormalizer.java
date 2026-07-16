package com.jeongbiseo.infra.client.gov24;

import java.util.List;

import com.jeongbiseo.infra.client.common.SubsidyNormalizer;
import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.infra.client.common.dto.DeadlineBasis;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.NormalizedEligibility;
import com.jeongbiseo.infra.client.common.dto.NormalizedRegion;
import com.jeongbiseo.infra.client.common.dto.NormalizedSubsidy;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.infra.client.common.dto.SubsidySource;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ApplicationMethodFlags;
import com.jeongbiseo.infra.client.gov24.dto.ParsedRegion;
import com.jeongbiseo.infra.client.gov24.dto.ParsedSubsidyResult;

/**
 * gov24 파싱 결과({@link ParsedSubsidyResult})를 공통 타깃({@link NormalizedSubsidy})으로 변환함.
 *
 * <p>
 * <b>버려지는 것과 남는 것.</b> gov24 고유 진단 3종은 공통 타깃으로 넘기지 않음 — {@code incomeSignalSource}(JA 플래그
 * 대 원문 출처), {@code incomeConsistencyStatus}(플래그와 원문 대조 결과), {@code incomeTextEvidence}(근거
 * 문구), 그리고 레거시 {@code deadline}(성공·실패 이분법)임. 이것들은 <b>gov24 데이터 품질 진단</b>이지 제품 필드가 아니고, 다른
 * 3개 소스에는 대응물이 아예 없음. 공통 타깃에 넣으면 3개 소스가 영구히 null인 칸 3개를 이고 가는 셈이라(함정 1과 같은 형태)
 * {@link ParsedSubsidyResult}에 남겨 둠 — 필요하면 그쪽을 직접 보면 됨.
 */
public final class Gov24SubsidyNormalizer implements SubsidyNormalizer<ParsedSubsidyResult> {

	@Override
	public SubsidySource source() {
		return SubsidySource.GOV24;
	}

	@Override
	public NormalizedSubsidy normalize(ParsedSubsidyResult raw) {
		return new NormalizedSubsidy(SubsidySource.GOV24, raw.serviceId(), raw.serviceName(), raw.agency(),
				raw.description(), raw.eligibilityText(), raw.categoryRawText(), raw.paymentType(),
				raw.paymentTypeRawText(), raw.amount(), raw.parsedDeadline(), toDeadlineBasis(raw),
				toNormalizedRegion(raw.region()), toNormalizedEligibility(raw),
				toApplicationMethodFlags(raw.applicationMethod()), raw.externalUrl(),
				// gov24는 신청 URL과 별개의 공고 상세 페이지 URL을 주지 않음
				null, raw.requiredDocumentsText(), raw.dataUpdatedAt(),
				// serviceList의 사용자구분으로 판정한 값임. serviceDetail만 읽으면 그 필드가 없어 UNKNOWN이 됨
				raw.targetAudience(),
				// supportConditions의 JA03 계열 17개로 판정한 1차산업 전용 여부임(gov24만 이 근거를 가짐)
				raw.occupationRestriction());
	}

	// gov24 신청기한은 전면 자유텍스트라 원문이 있으면 언제나 PARSED_FROM_TEXT임(판정에 성공했든 UNKNOWN으로
	// 떨어졌든 "텍스트에서 추론했다"는 근거는 같음). 원문 자체가 비면 판정 근거가 없다는 뜻이라 NOT_APPLICABLE로 구분함.
	private static DeadlineBasis toDeadlineBasis(ParsedSubsidyResult raw) {
		String rawText = raw.parsedDeadline().rawText();
		if (rawText == null || rawText.isBlank()) {
			return DeadlineBasis.NOT_APPLICABLE;
		}
		return DeadlineBasis.PARSED_FROM_TEXT;
	}

	// gov24는 지역 코드를 주지 않으므로 regionCodes는 항상 빈 목록임. 시도·시군구 명칭에서 법정동코드를 역산해
	// 채우지 않음 — 없는 코드를 지어내는 것이고, 소관기관명 유추 자체가 이미 추론이라 오차가 곱해짐.
	private static NormalizedRegion toNormalizedRegion(ParsedRegion region) {
		return new NormalizedRegion(List.of(), region.sidoName(), region.sigunguName(), region.regionLevel(),
				region.scopeBasis(), region.confidence());
	}

	// 연령은 JA0110·JA0111 숫자형이라 값이 있으면 RESTRICTED, 없으면 UNKNOWN임. gov24에는 "연령 무관"을
	// 선언하는 필드가 없어 UNRESTRICTED를 만들 수 없음(온통청년 sprtTrgtAgeLmtYn='N'이 그 역할). 고용은
	// JA0326·JA0327이 배타적 상태값이 아니라 현재 파서가 읽지 않으므로 UNKNOWN 고정임.
	private static NormalizedEligibility toNormalizedEligibility(ParsedSubsidyResult raw) {
		boolean hasAge = raw.ageMin() != null || raw.ageMax() != null;
		EligibilitySignal ageSignal = hasAge ? EligibilitySignal.RESTRICTED : EligibilitySignal.UNKNOWN;
		return new NormalizedEligibility(ageSignal, raw.ageMin(), raw.ageMax(), raw.incomeSignal(),
				raw.householdSignal(), EligibilitySignal.UNKNOWN, null);
	}

	// gov24 키워드 플래그 6종을 공통 8종에 옮김. email은 gov24 키워드 어휘에 없어 항상 false이고, 이는 "이메일
	// 접수를 안 한다"가 아니라 "gov24가 그걸 말해 주지 않는다"는 뜻임(ApplicationMethodFlags Javadoc 참조).
	private static ApplicationMethodFlags toApplicationMethodFlags(Gov24ApplicationMethodFlags flags) {
		return new ApplicationMethodFlags(flags.online(), flags.visit(), flags.mail(), false, flags.fax(),
				flags.phone(), flags.autoProvided(), flags.unclassified());
	}

}
