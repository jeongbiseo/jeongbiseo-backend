package com.jeongbiseo.infra.client.youthcenter;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jeongbiseo.infra.client.common.dto.ApplicationMethodFlags;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.infra.client.gov24.Gov24Parser;
import com.jeongbiseo.infra.client.youthcenter.dto.ParsedYouthPolicy;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyDto;
import com.jeongbiseo.infra.client.youthcenter.dto.YouthcenterPolicyListResponseDto;
import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 온통청년(youthcenter) 청년정책 API 원문을 {@link ParsedYouthPolicy}로 파싱함. 실호출 전수
 * 2,648건(2026-07-12, getPlcy pageNum=1..6 pageSize=500)을 근거로 하고, 회귀 스냅샷은
 * {@code fixtures/regression/youthcenter_snapshot.json}(체계적 표집 step=2, n=1,324)임.
 *
 * <p>
 * <b>이 소스의 성격 — gov24와 정반대임.</b> gov24는 자유텍스트를 파싱해야 하는 소스이고, 온통청년은 <b>선언된 코드를 매핑하는</b>
 * 소스임. 마감(aplyPrdSeCd)·지역(zipCd)·소득(earnCndSeCd)·고용(jobCd)이 전부 코드 필드라 정규식이 필요 없고, 대신
 * <b>코드의 의미를 정확히 알아야 함</b>. 코드 의미는 온통청년 오픈API 안내 페이지가 배포하는 공식 코드정의서
 * {@code /downloadform/API코드정보.xlsx}(2026-07-12 내려받아 대조)로 확정했으며, 필요한 표는 아래 상수 주석에 그대로 옮겨
 * 적었음 — 조사 리포트가 "코드정의서 미확인"이라 남겨 둔 항목(jobCd·plcyPvsnMthdCd·earnCndSeCd·sbizCd)이 이걸로 전부
 * 해소됨.
 *
 * <p>
 * <b>이 소스의 한계 — 청년 정책 전용이라 모집단이 좁음.</b> 전수 2,648건이 전부 청년(주로 만 19에서 39세) 대상 정책이고,
 * 노인·아동·장애인 등 다른 생애주기 지원금은 아예 없음. 이 소스만으로 추천 모집단을 구성하면 20대에서 30대 사용자 외에는 결과가 비고, 20대에서
 * 30대 사용자에게도 청년 정책이 아닌 일반 지원금(예 에너지바우처)이 누락됨. <b>gov24를 대체하는 소스가 아니라 보완하는 소스임</b>(지역 코드와
 * 마감 상태를 유일하게 선언으로 주는 곳).
 */
public final class YouthcenterParser {

	private static final Logger log = LoggerFactory.getLogger(YouthcenterParser.class);

	// 신청기간구분코드(aplyPrdSeCd, 공식 코드정의서 0057 계열) — 0057001 특정기간, 0057002 상시, 0057003 마감.
	// 전수 2,648건에서 이 코드가 aplyYmd 채움을 위반 0건으로 예측함(0057001 채움 1,321건, 0057002 공백 438건,
	// 0057003 공백 889건, 그 외 조합 0건).
	private static final String PERIOD_FIXED = "0057001";

	private static final String PERIOD_ALWAYS_OPEN = "0057002";

	private static final String PERIOD_CLOSED = "0057003";

	// aplyYmd 기본 형식임("20260713 ~ 20260729"). 전수 채움 1,321건의 구간 1,323개가 전부 이 형식이고 이형식 0건임.
	private static final Pattern APPLICATION_PERIOD = Pattern.compile("(\\d{8})\\s*~\\s*(\\d{8})");

	// aplyYmd 다중 기간 구분자임 — 자바 문자열로 역슬래시 1개와 대문자 N(원문 바이트 0x5c 0x4e). 전수 2건뿐이지만(0.15%)
	// 이 분기가 없으면 " ~ " 단순 분리가 토큰 3개를 만들어 종료일이 오염됨(20251121005400211919의 원문은
	// "20260101 ~ 20261231\N20270101 ~ 20271231"). 여러 회차 모집을 한 필드에 담은 것이라 **첫 구간만** 취함 —
	// 마감 캘린더가 필요한 건 가장 가까운 마감이기 때문임. 원문은 rawText에 통째로 남으므로 뒤 구간이 사라지지 않음
	// (ponytail: 회차별 구간을 전부 구조화하는 대신 첫 구간 + 원문 보존으로 그침. 전수 2건이라 구조를 늘릴 값이 없음).
	private static final String MULTI_PERIOD_DELIMITER = "\\N";

	// 소득조건구분코드(earnCndSeCd, 공식 코드정의서 0043 계열) — 0043001 무관, 0043002 연소득, 0043003 기타.
	// 전수 크로스탭이 이 정의를 뒷받침함: 0043003(326건)은 earnEtcCn(소득 기타 자유서술)이 326건 전부 채워져 있고,
	// 0043002(29건)는 28건이 earnMinAmt·earnMaxAmt를 채움. 0043001(2,291건)은 둘 다 비어 있음.
	private static final String INCOME_UNRESTRICTED = "0043001";

	// 정책취업요건코드(jobCd, 공식 코드정의서 0013 계열) — 0013001 재직자, 0013002 자영업자, 0013003 미취업자,
	// 0013004 프리랜서, 0013005 일용근로자, 0013006 (예비)창업자, 0013007 단기근로자, 0013008 영농종사자,
	// 0013009 기타, **0013010 제한없음**. 0013010은 전수 1,968건 전부 단독 출현이고 다른 코드와 함께 오는 경우가 0건이라
	// 구조적으로도 센티널임(나머지 9종은 자유롭게 콤마 조합됨).
	private static final String JOB_UNRESTRICTED = "0013010";

	// 정책제공방법코드(plcyPvsnMthdCd, 공식 코드정의서 0042 계열, 채움 100%·단일값·distinct 13) 대 PaymentType
	// 매핑표임. gov24 지원유형 45종에 대응하는 이 소스의 유일한 지급유형 필드임. 실측 분포는 프로그램 745(28.13%),
	// 기타 490(18.50%), 보조금 654(24.70%), 계약(위탁운영) 228, 인프라 구축 223, 공공기관 106, 바우처 93,
	// 정보제공 70, 대출보증 19, 직접대출 12, 공적보험 4, 조세지출 2, 경제적 규제 2임.
	//
	// **CASH로 올리는 것은 "보조금" 하나뿐임.** gov24 매핑의 보수 원칙(잘못된 CASH는 예상총액을 부풀리므로 애매하면
	// UNKNOWN)을 그대로 따름 — 직접대출·대출보증은 상환 의무가 있고, 공적보험은 보험료 대납 형태이며, 프로그램·인프라
	// 구축·정보제공·계약(위탁운영)·공공기관·경제적 규제는 애초에 현금이 아님. 조세지출은 세금 감면이라 REDUCTION,
	// 바우처는 VOUCHER로 각각 대응되지만 둘 다 예상총액 합산 대상이 아니라 결과는 같음(합산은 CASH만).
	private static final Map<String, PaymentType> PAYMENT_TYPE_MAP = Map.ofEntries(
			// 0042006 보조금 — 이 소스에서 유일하게 계좌로 돈이 가는 유형임(실물 예: K-패스 환급, 중개보수·이사비
			// 최대 40만원, 창업활동비). 654건 중 71.4%가 지원내용에 금액 표현을 갖고 있어 금액 파서와도 정합함
			Map.entry("0042006", PaymentType.CASH),
			// 0042010 바우처
			Map.entry("0042010", PaymentType.VOUCHER),
			// 0042009 조세지출 — 세금 감면이므로 REDUCTION(gov24의 "현금(감면)"과 같은 자리)
			Map.entry("0042009", PaymentType.REDUCTION),
			// 아래 10종은 현금이 아니거나(인프라 구축·프로그램·공공기관·계약·정보제공·경제적 규제·기타) 상환·대납
			// 구조라(직접대출·대출보증·공적보험) CASH로 올리면 예상총액이 부풀려짐. 보수적으로 UNKNOWN에 둠
			Map.entry("0042001", PaymentType.UNKNOWN), Map.entry("0042002", PaymentType.UNKNOWN),
			Map.entry("0042003", PaymentType.UNKNOWN), Map.entry("0042004", PaymentType.UNKNOWN),
			Map.entry("0042005", PaymentType.UNKNOWN), Map.entry("0042007", PaymentType.UNKNOWN),
			Map.entry("0042008", PaymentType.UNKNOWN), Map.entry("0042011", PaymentType.UNKNOWN),
			Map.entry("0042012", PaymentType.UNKNOWN), Map.entry("0042013", PaymentType.UNKNOWN));

	// 법정시군구코드 5자리 검증용임. 전수 코드 인스턴스 122,789개가 전부 이 형식이라 걸러지는 값이 없지만, 형식이 아닌 값이
	// 조용히 지역 필터에 들어가는 것을 막는 방어선으로 둠(xx000 형태 시도 코드는 응답에 0건이라 프리픽스 매칭 로직은 만들지 않음).
	private static final Pattern SIGUNGU_CODE = Pattern.compile("\\d{5}");

	// 연령 미입력 센티널임. sprtTrgtMinAge·sprtTrgtMaxAge는 값이 없으면 "0"으로 옴(전수 min "0" 730건, max "0"
	// 687건). 만 0세를 뜻하는 게 아니라 미입력이므로 null로 정규화함 — 0을 그대로 두면 "만 0세부터"라는 없는 조건이 생김.
	private static final String AGE_NOT_ENTERED = "0";

	// 제출서류가 "없음"을 표현하는 원문 센티널임(전수 실측 "해당없음" 24건, "해당 없음" 8건, "-" 9건). 화면에 낼 내용이
	// 없다는 뜻이라 null로 정규화함. "별도 문의"(39건)·"공고문 참조"(9건)는 빈약해도 실제 안내라 남김.
	private static final List<String> NO_DOCUMENTS_SENTINELS = List.of("해당없음", "해당 없음", "-");

	// lastMdfcnDt 실측 형식임(전수 2,648/2,648이 "yyyy-MM-dd HH:mm:ss").
	private static final DateTimeFormatter LAST_MODIFIED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	// 신청방법(plcyAplyMthdCn) 키워드 분류 어휘임. gov24 어휘를 재사용하되 **이메일을 추가함** — gov24 원문에는 없는
	// 채널인데 온통청년에는 실재함(전수 채움분 1,419건 중 188건, 13.25%. 예 "이메일: espoirbr@korea.kr").
	private static final String[] ONLINE_KEYWORDS = { "온라인", "인터넷" };

	private static final String[] VISIT_KEYWORDS = { "방문" };

	private static final String[] MAIL_KEYWORDS = { "우편" };

	// "메일"만으로 "이메일"까지 걸림. 영문 표기는 대소문자 변형을 방어적으로 함께 봄.
	private static final String[] EMAIL_KEYWORDS = { "메일", "E-mail", "e-mail", "Email", "email" };

	private static final String[] FAX_KEYWORDS = { "팩스", "FAX", "Fax", "fax" };

	// gov24와 같은 한계가 있음 — "전화 문의" 같은 안내 문장에도 걸려 과대 집계됨. 참고 지표로만 쓸 것.
	private static final String[] PHONE_KEYWORDS = { "전화" };

	private static final String[] AUTO_PROVIDED_KEYWORDS = { "신청없이", "신청 없이", "신청절차 없음", "신청 절차 없음", "별도 신청 불필요",
			"자동적으로 제공", "자동으로 제공", "신청 불필요" };

	private final ObjectMapper objectMapper = new ObjectMapper();

	// 금액 파서 재사용임. 새로 짜지 않는 이유는 parseAmount의 배제 규칙 6종(사업예산 문맥·이용자 자부담·per-unit·문턱
	// 표현·다단 차등·bulk divisor)이 gov24 JSON의 성질이 아니라 **한국어 행정 산문의 성질**이기 때문임 — 온통청년
	// plcySprtCn도 똑같은 문장을 쓴다("창업활동비 최대 20백만원", "1인당 100만원 복지포인트"). 각자 짜면 "사업예산을 개인
	// 지급액으로" 사고가 소스 수만큼 반복됨.
	// ponytail: gov24 파서 인스턴스를 그대로 들고 있는 것은 임시 조치임. 후속 작업 T1이
	// Gov24Parser.parseAmount를 clients.common.AmountTextParser로 추출하면 이 필드를 그 타입으로 바꾸는
	// 1줄 변경만 남음(public 시그니처가 보존되므로 호출부는 그대로). T1을 이 회차에서 하지 않은 이유는 다른 에이전트가 같은
	// 공통 패키지를 동시에 고치고 있어 충돌하기 때문임.
	private final Gov24Parser amountParser = new Gov24Parser();

	/**
	 * getPlcy 응답 JSON(또는 같은 봉투를 쓰는 회귀 스냅샷)을 파싱함.
	 * @param json 원문 JSON 문자열
	 * @return 청년정책 항목 목록(본문이 비면 빈 목록)
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐
	 */
	public List<YouthcenterPolicyDto> parsePolicies(String json) throws IOException {
		YouthcenterPolicyListResponseDto response = parsePolicyPage(json);
		if (response.result() == null || response.result().youthPolicyList() == null) {
			return List.of();
		}
		return response.result().youthPolicyList();
	}

	/**
	 * getPlcy의 페이징 메타와 항목을 함께 파싱함.
	 * @param json 원문 JSON 문자열
	 * @return 페이징 응답
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐
	 */
	public YouthcenterPolicyListResponseDto parsePolicyPage(String json) throws IOException {
		return this.objectMapper.readValue(json, YouthcenterPolicyListResponseDto.class);
	}

	/**
	 * 원문 항목 1건을 파싱 결과로 변환함.
	 * @param dto 원문 항목
	 * @return 파싱 결과
	 */
	public ParsedYouthPolicy toParsedPolicy(YouthcenterPolicyDto dto) {
		ParsedAmount amount = this.amountParser.parseAmount(dto.policySupportContent());
		return new ParsedYouthPolicy(dto.policyId(), dto.policyName(), blankToNull(dto.supervisingAgencyName()),
				buildDescription(dto), buildEligibilityText(dto), buildCategoryRawText(dto),
				mapPaymentType(dto.provisionMethodCode()), blankToNull(dto.provisionMethodCode()), amount,
				classifyDeadline(dto.applicationPeriodCode(), dto.applicationPeriodText()),
				blankToNull(dto.applicationPeriodCode()), parseRegionCodes(dto.regionCodesText()),
				parseAgeBound(dto.supportTargetMinAge()), parseAgeBound(dto.supportTargetMaxAge()),
				parseAgeSignal(dto.supportTargetMinAge(), dto.supportTargetMaxAge()), blankToNull(dto.ageLimitYn()),
				parseIncomeSignal(dto.incomeConditionCode()), parseEmploymentSignal(dto.jobCode()),
				blankToNull(dto.jobCode()), blankToNull(dto.specializedRequirementCode()),
				blankToNull(dto.schoolCode()), blankToNull(dto.majorCode()), blankToNull(dto.maritalStatusCode()),
				parseApplicationMethod(dto.applicationMethodContent()), blankToNull(dto.applicationUrl()),
				blankToNull(dto.referenceUrl()), normalizeRequiredDocuments(dto.submissionDocumentContent()),
				parseLastModifiedAt(dto.lastModifiedAt()));
	}

	/**
	 * 신청기간을 {@link DeadlineKind}로 판정함. <b>텍스트 파싱이 아니라 코드 매핑임</b> — 그래서 이 소스의
	 * {@code DeadlineBasis}는 DECLARED_FIELD임.
	 * <ul>
	 * <li>0057002(상시) -&gt; ALWAYS_OPEN. 전수 438건(16.54%), aplyYmd는 전부 공백임</li>
	 * <li>0057003(마감) -&gt; <b>CLOSED</b>. 전수 889건(33.57%). 이 값이 없으면 이 소스의 3분의 1이
	 * UNKNOWN으로 뭉개져 "마감된 것"과 "모르는 것"이 섞임</li>
	 * <li>0057001(특정기간) -&gt; DATE_RANGE. 전수 1,321건(49.89%), aplyYmd가 전부 채워져 있음</li>
	 * </ul>
	 * rawText에는 aplyYmd가 있으면 그것을, 없으면 신청기간구분코드를 담음 — 상시·마감은 코드 자체가 원문 근거이기 때문임.
	 * @param periodCode 신청기간구분코드(null 가능)
	 * @param periodText 신청기간 원문(null 가능)
	 * @return 판정 결과
	 */
	public ParsedDeadline classifyDeadline(String periodCode, String periodText) {
		String code = periodCode == null ? "" : periodCode.strip();
		String rawText = isBlank(periodText) ? blankToNull(code) : periodText.strip();
		if (PERIOD_ALWAYS_OPEN.equals(code)) {
			return new ParsedDeadline(DeadlineKind.ALWAYS_OPEN, null, null, rawText);
		}
		if (PERIOD_CLOSED.equals(code)) {
			return new ParsedDeadline(DeadlineKind.CLOSED, null, null, rawText);
		}
		if (PERIOD_FIXED.equals(code)) {
			return classifyFixedPeriod(rawText);
		}
		if (!code.isEmpty()) {
			log.warn("온통청년 신청기간구분코드 매핑표에 없는 신규 값 감지, UNKNOWN으로 처리함(원문: \"{}\")", code);
		}
		return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, rawText);
	}

	// 특정기간(0057001)의 aplyYmd를 날짜 범위로 읽음. 다중 기간이면 첫 구간만 취함(원문은 rawText에 통째로 남김).
	// 날짜가 유효하지 않거나 형식이 다르면 UNKNOWN으로 떨어뜨림 — 없는 날짜를 지어내지 않음(전수 실측 0건이지만 소스가 바뀌면
	// 조용히 틀리는 대신 UNKNOWN으로 드러나야 함).
	private ParsedDeadline classifyFixedPeriod(String rawText) {
		if (rawText == null) {
			return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, null);
		}
		int delimiterAt = rawText.indexOf(MULTI_PERIOD_DELIMITER);
		String firstPeriod = delimiterAt < 0 ? rawText : rawText.substring(0, delimiterAt);
		Matcher matcher = APPLICATION_PERIOD.matcher(firstPeriod);
		if (!matcher.find()) {
			return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, rawText);
		}
		try {
			LocalDate start = LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
			LocalDate end = LocalDate.parse(matcher.group(2), DateTimeFormatter.BASIC_ISO_DATE);
			return new ParsedDeadline(DeadlineKind.DATE_RANGE, start, end, rawText);
		}
		catch (DateTimeException ex) {
			return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, rawText);
		}
	}

	/**
	 * 정책신청지역코드(zipCd)를 법정시군구코드 5자리 목록으로 분해함. 전수 2,648건의 코드 인스턴스 122,789개가 전부 5자리이고
	 * xx000(시도 전역) 형태는 0건임 — 시도 전역 정책은 그 시도의 전 시군구 코드를 나열하는 방식으로 표현되므로 프리픽스 매칭 로직을 만들지
	 * 않음. 10개 이상 나열이 41.58%로 최대 버킷이라 <b>목록을 자르지 않음</b>.
	 * @param regionCodesText zipCd 원문(null 가능)
	 * @return 5자리 코드 목록(원문 순서 유지). 원문이 비면 빈 목록
	 */
	public List<String> parseRegionCodes(String regionCodesText) {
		if (isBlank(regionCodesText)) {
			return List.of();
		}
		List<String> codes = new ArrayList<>();
		for (String token : regionCodesText.split(",")) {
			String code = token.strip();
			if (code.isEmpty()) {
				continue;
			}
			if (!SIGUNGU_CODE.matcher(code).matches()) {
				log.warn("온통청년 zipCd에 5자리가 아닌 값 감지, 지역 코드에서 제외함(원문: \"{}\")", code);
				continue;
			}
			codes.add(code);
		}
		return List.copyOf(codes);
	}

	/**
	 * 정책제공방법코드를 PaymentType으로 매핑함({@link #PAYMENT_TYPE_MAP}).
	 * @param provisionMethodCode 정책제공방법코드 원문(null 가능)
	 * @return 매핑된 PaymentType(모르는 코드는 UNKNOWN)
	 */
	public PaymentType mapPaymentType(String provisionMethodCode) {
		if (isBlank(provisionMethodCode)) {
			return PaymentType.UNKNOWN;
		}
		PaymentType mapped = PAYMENT_TYPE_MAP.get(provisionMethodCode.strip());
		if (mapped == null) {
			log.warn("온통청년 정책제공방법코드 매핑표에 없는 신규 값 감지, UNKNOWN으로 처리함(원문: \"{}\")", provisionMethodCode);
			return PaymentType.UNKNOWN;
		}
		return mapped;
	}

	/**
	 * 연령 신호를 판정함. <b>이 소스는 UNRESTRICTED를 만들지 않음</b> — 임무 지시가 상정한
	 * "{@code sprtTrgtAgeLmtYn='N'}이면 연령 무관"은 실데이터에서 성립하지 않기 때문임.
	 *
	 * <p>
	 * 전수 2,648건 크로스탭(연령 채움 여부 대 sprtTrgtAgeLmtYn):
	 * <ul>
	 * <li>N인데 연령이 채워져 있음 — 1,428건(예 국민취업지원제도 15~69세, 사회연대경제 청년일경험 19~39세)</li>
	 * <li>Y인데 연령이 비어 있음 — 635건(예 K-패스)</li>
	 * <li>Y이고 연령이 채워져 있음 — 541건 / N이고 연령이 비어 있음 — 44건</li>
	 * </ul>
	 * 어느 쪽으로 읽어도 모순이 22%(Y=무관 가설) 또는 78%(Y=제한 가설)라 이 플래그는 판정 근거가 못 됨. 형제 필드쌍
	 * {@code sprtSclLmtYn} 대 {@code sprtSclCnt}에서도 같은 어긋남이 나옴(N인데 규모 있음 795건, Y인데 규모 없음
	 * 559건)이라, 이 API의 "~Yn" 플래그 계열 자체가 관리되지 않는 필드로 보임. 원문은
	 * {@link ParsedYouthPolicy#ageLimitYnRawText()}에 남겨 두되 판정에는 쓰지 않음.
	 *
	 * <p>
	 * 따라서 신호는 <b>값의 존재</b>로만 판정함 — 상·하한 중 하나라도 있으면 RESTRICTED, 둘 다 미입력("0")이면 UNKNOWN(무관이
	 * 아니라 <b>모름</b>임. 실제로 K-패스는 연령 필드가 0인데 설명 원문에는 청년 연령 구간이 있음).
	 * @param minAgeText 지원대상최소연령 원문(null 가능)
	 * @param maxAgeText 지원대상최대연령 원문(null 가능)
	 * @return 연령 신호(RESTRICTED 또는 UNKNOWN)
	 */
	public EligibilitySignal parseAgeSignal(String minAgeText, String maxAgeText) {
		boolean hasBound = parseAgeBound(minAgeText) != null || parseAgeBound(maxAgeText) != null;
		return hasBound ? EligibilitySignal.RESTRICTED : EligibilitySignal.UNKNOWN;
	}

	/**
	 * 연령 상·하한 1개를 파싱함. "0"과 빈 값은 미입력이라 null임(만 0세가 아님).
	 * @param ageText 연령 원문(null 가능)
	 * @return 연령(미입력이면 null)
	 */
	public Integer parseAgeBound(String ageText) {
		if (isBlank(ageText)) {
			return null;
		}
		String trimmed = ageText.strip();
		if (AGE_NOT_ENTERED.equals(trimmed)) {
			return null;
		}
		try {
			return Integer.valueOf(trimmed);
		}
		catch (NumberFormatException ex) {
			log.warn("온통청년 연령 필드가 숫자가 아님, null로 처리함(원문: \"{}\")", trimmed);
			return null;
		}
	}

	/**
	 * 소득 신호를 판정함(소득조건구분코드 0043 계열). 0043001(무관)은 소스가 <b>명시적으로 선언한</b> 무관이라 UNRESTRICTED임
	 * — "데이터 없음"과 구분되는 이 선언이 이 필드의 값임(전수 86.52%). 0043002(연소득)와 0043003(기타)은 조건이 존재한다는
	 * 뜻이라 RESTRICTED이고, 실제 조건 내용은 earnEtcCn 원문이 eligibilityText에 실려 감.
	 *
	 * <p>
	 * <b>단, 이 신호를 gov24 소득 신호(JA0201에서 JA0205 중위소득 5구간)와 같은 급으로 쓰면 안 됨.</b> gov24는 구간을
	 * 주지만 이건 있다·없다뿐이라, 사용자의 소득 구간과 대조할 수 있는 정보가 아님. 하드 필터가 아니라 "이 정책엔 소득 조건이 붙는다"는 표시로만 쓸
	 * 것.
	 * @param incomeConditionCode 소득조건구분코드 원문(null 가능)
	 * @return 소득 신호
	 */
	public EligibilitySignal parseIncomeSignal(String incomeConditionCode) {
		if (isBlank(incomeConditionCode)) {
			return EligibilitySignal.UNKNOWN;
		}
		return INCOME_UNRESTRICTED.equals(incomeConditionCode.strip()) ? EligibilitySignal.UNRESTRICTED
				: EligibilitySignal.RESTRICTED;
	}

	/**
	 * 고용 신호를 판정함(정책취업요건코드 0013 계열). 0013010(제한없음) 단독이면 UNRESTRICTED, 그 외 코드가 하나라도 있으면
	 * RESTRICTED임. <b>개별 코드를 {@code EmploymentStatus}로 매핑하지는 않음</b> — 공통 타깃이 그 매핑을 금지하고
	 * 있고, 원문 코드는 {@link ParsedYouthPolicy#employmentRawCode()}에 그대로 보존되므로 필요해지면 그때 매핑하면
	 * 됨.
	 * @param jobCode 정책취업요건코드 원문(콤마 나열 가능, null 가능)
	 * @return 고용 신호
	 */
	public EligibilitySignal parseEmploymentSignal(String jobCode) {
		if (isBlank(jobCode)) {
			return EligibilitySignal.UNKNOWN;
		}
		List<String> codes = new ArrayList<>();
		for (String token : jobCode.split(",")) {
			String code = token.strip();
			if (!code.isEmpty()) {
				codes.add(code);
			}
		}
		if (codes.isEmpty()) {
			return EligibilitySignal.UNKNOWN;
		}
		boolean unrestrictedOnly = codes.size() == 1 && JOB_UNRESTRICTED.equals(codes.get(0));
		return unrestrictedOnly ? EligibilitySignal.UNRESTRICTED : EligibilitySignal.RESTRICTED;
	}

	/**
	 * 신청방법 원문을 키워드로 분류함. gov24와 같은 방식이되 <b>이메일 채널이 추가됨</b>(gov24 원문에는 없는 채널). 원문 채움이
	 * 53.59%뿐이라 나머지 46.41%는 근거 없음(noEvidence)임 — 이 false를 "그 채널로 신청 불가"로 읽으면 안 됨.
	 * @param rawText 신청방법 원문(null 가능)
	 * @return 채널 플래그
	 */
	public ApplicationMethodFlags parseApplicationMethod(String rawText) {
		if (isBlank(rawText)) {
			return ApplicationMethodFlags.noEvidence();
		}
		boolean online = containsAny(rawText, ONLINE_KEYWORDS);
		boolean visit = containsAny(rawText, VISIT_KEYWORDS);
		boolean mail = containsAny(rawText, MAIL_KEYWORDS);
		boolean email = containsAny(rawText, EMAIL_KEYWORDS);
		boolean fax = containsAny(rawText, FAX_KEYWORDS);
		boolean phone = containsAny(rawText, PHONE_KEYWORDS);
		boolean autoProvided = containsAny(rawText, AUTO_PROVIDED_KEYWORDS);
		boolean unclassified = !(online || visit || mail || email || fax || phone || autoProvided);
		return new ApplicationMethodFlags(online, visit, mail, email, fax, phone, autoProvided, unclassified);
	}

	/**
	 * 제출서류 원문을 정규화함. "해당없음"·"해당 없음"·"-"는 화면에 낼 내용이 없다는 뜻이라 null로 둠.
	 * @param rawText 제출서류 원문(null 가능)
	 * @return 정규화된 원문(내용이 없으면 null)
	 */
	public String normalizeRequiredDocuments(String rawText) {
		if (isBlank(rawText)) {
			return null;
		}
		String trimmed = rawText.strip();
		return NO_DOCUMENTS_SENTINELS.contains(trimmed) ? null : trimmed;
	}

	/**
	 * 최종수정일시를 파싱함("yyyy-MM-dd HH:mm:ss", 전수 100%). 형식이 다르면 예외를 던지지 않고 null로 둠 — 이 필드의 파싱
	 * 실패가 전체 파싱을 막으면 안 됨.
	 * @param rawText 최종수정일시 원문(null 가능)
	 * @return 파싱 결과(실패하면 null)
	 */
	public LocalDateTime parseLastModifiedAt(String rawText) {
		if (isBlank(rawText)) {
			return null;
		}
		try {
			return LocalDateTime.parse(rawText.strip(), LAST_MODIFIED_AT);
		}
		catch (DateTimeException ex) {
			return null;
		}
	}

	// 정책설명(무엇을 하는 정책인가)과 지원내용(무엇을 주는가)을 합쳐 상세 화면용 본문을 만듦. 금액 파싱은 이 결합 텍스트가 아니라
	// 지원내용 원문에만 걸어야 함(toParsedPolicy 참조) — 정책설명에는 사업 규모·목적 문장이 섞여 있어 금액 후보가 오염됨.
	private static String buildDescription(YouthcenterPolicyDto dto) {
		boolean hasExplanation = !isBlank(dto.policyExplanation());
		boolean hasSupport = !isBlank(dto.policySupportContent());
		if (hasExplanation && hasSupport) {
			return dto.policyExplanation().strip() + "\n\n[지원내용] " + dto.policySupportContent().strip();
		}
		if (hasExplanation) {
			return dto.policyExplanation().strip();
		}
		return hasSupport ? dto.policySupportContent().strip() : null;
	}

	// 자격조건 원문을 3개 필드에서 모음. 어느 것도 없으면 null임(빈 문자열을 만들지 않음).
	private static String buildEligibilityText(YouthcenterPolicyDto dto) {
		List<String> parts = new ArrayList<>();
		if (!isBlank(dto.additionalQualificationContent())) {
			parts.add("[추가 자격조건] " + dto.additionalQualificationContent().strip());
		}
		if (!isBlank(dto.participationRestrictionContent())) {
			parts.add("[참여 제한대상] " + dto.participationRestrictionContent().strip());
		}
		if (!isBlank(dto.incomeEtcContent())) {
			parts.add("[소득 조건] " + dto.incomeEtcContent().strip());
		}
		return parts.isEmpty() ? null : String.join("\n\n", parts);
	}

	// 대분류와 중분류를 합침. SubsidyCategory로 매핑하지 않음(공통 타깃 규칙) — 원문 그대로 둠.
	// 주의: 실데이터에는 신구 taxonomy가 섞여 있음(공식 코드정의서는 대분류 5종인데 응답에는 "금융･복지･문화"·"참여･기반"
	// 같은 구 명칭과 "참여권리,참여권리" 같은 중복 나열이 함께 나타남, distinct 18). 정규화하지 않고 원문을 보존해 이 사실이
	// 하류에서 보이게 함.
	private static String buildCategoryRawText(YouthcenterPolicyDto dto) {
		boolean hasLarge = !isBlank(dto.largeCategoryName());
		boolean hasMedium = !isBlank(dto.mediumCategoryName());
		if (hasLarge && hasMedium) {
			return dto.largeCategoryName().strip() + " > " + dto.mediumCategoryName().strip();
		}
		if (hasLarge) {
			return dto.largeCategoryName().strip();
		}
		return hasMedium ? dto.mediumCategoryName().strip() : null;
	}

	private static boolean containsAny(String text, String[] keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static String blankToNull(String text) {
		return isBlank(text) ? null : text.strip();
	}

}
