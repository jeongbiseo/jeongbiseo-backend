package com.jeongbiseo.infra.client.gov24;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jeongbiseo.infra.client.common.dto.AmountKind;
import com.jeongbiseo.infra.client.common.dto.AmountParseStatus;
import com.jeongbiseo.infra.client.gov24.dto.DeadlineFailureReason;
import com.jeongbiseo.infra.client.common.dto.DeadlineKind;
import com.jeongbiseo.infra.client.gov24.dto.DeadlineParseResult;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ApplicationMethodFlags;
import com.jeongbiseo.domain.common.enums.EligibilitySignal;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceItemDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24ServiceListResponseDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionDto;
import com.jeongbiseo.infra.client.gov24.dto.Gov24SupportConditionsResponseDto;
import com.jeongbiseo.infra.client.gov24.dto.IncomeConsistencyStatus;
import com.jeongbiseo.infra.client.gov24.dto.IncomeSignalSource;
import com.jeongbiseo.domain.common.enums.OccupationRestriction;
import com.jeongbiseo.infra.client.common.dto.ParsedAmount;
import com.jeongbiseo.infra.client.common.dto.ParsedDeadline;
import com.jeongbiseo.domain.common.enums.TargetAudience;
import com.jeongbiseo.infra.client.gov24.dto.ParsedRegion;
import com.jeongbiseo.infra.client.gov24.dto.ParsedSubsidyResult;
import com.jeongbiseo.infra.client.common.dto.RegionConfidence;
import com.jeongbiseo.infra.client.common.dto.RegionLevel;
import com.jeongbiseo.infra.client.common.dto.RegionScopeBasis;
import com.jeongbiseo.domain.common.enums.PaymentType;

/**
 * 보조금24(gov24) 원문 JSON에서 Subsidy 매칭 필드를 뽑아내는 파서 PoC임(PLAN.md 3장 W4 절). 핵심 검증 대상은 신청기한
 * 자유텍스트(예 "5.1.~5.31.", "2025년 8월 30일까지", "예산 소진 시까지")를 LocalDate로 파싱할 수 있는가이고, 이 결과가
 * 미결-05(apply_start_date 컬럼 도입 필요 여부)의 판단 근거가 됨. 소관기관·지원내용·자격조건·외부URL·수정일시(agency,
 * description, eligibilityText, externalUrl, dataUpdatedAt) 5필드 판독, 지원유형(paymentType) 45종
 * 매핑, 신청방법 키워드 플래그 분류는 임무 지시(외부API-부족분-조사-2026-07-12.md 후속 임무)로 추가됨.
 */
public final class Gov24Parser {

	private static final Logger log = LoggerFactory.getLogger(Gov24Parser.class);

	// "5.1.~5.31." 같은 M.D.~M.D. 범위 형식임. 마감일은 뒤쪽(종료) 월일로 봄. 원문에 연도가 없어 파싱
	// 시점의 올해로 가정함(ponytail: 연도 추정이 필요한 형식은 이 하나뿐이고 상한도 이 형식 하나임. 실제
	// 연동 시에는 API 응답의 등록일시·수정일시 연도를 기준 연도로 넘겨받는 것이 대안임).
	private static final Pattern DATE_RANGE = Pattern
		.compile("(\\d{1,2})\\.(\\d{1,2})\\.\\s*~\\s*(\\d{1,2})\\.(\\d{1,2})\\.");

	// "2025년 8월 30일까지" 같은 절대 연월일 형식임.
	private static final Pattern ABSOLUTE_DATE = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일\\s*까지");

	// gov24 원문이 한 필드에 여러 값을 나열할 때 쓰는 구분자임(전화문의·법령·지원유형 등에서 공통 관찰).
	private static final String COMBO_DELIMITER = "||";

	// 구비서류가 "없음"을 표현하는 원문 리터럴임(스냅샷 n=1097 실측 41.66%).
	private static final String NO_DOCUMENTS_REQUIRED = "해당없음";

	// serviceDetail 실측(스냅샷 n=1097, 1097/1097)은 "YYYY-MM-DD" 10자리 고정임.
	private static final DateTimeFormatter DATA_UPDATED_AT_DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;

	// 로컬 픽스처 sample_serviceList.json은 "YYYYMMDDHHmmss" 14자리를 씀(gov24 엔드포인트별 표기
	// 차이로 추정 — 실호출로 재확인하지 못했으나, 방어적으로 함께 지원함).
	private static final DateTimeFormatter DATA_UPDATED_AT_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	// 신청방법 키워드 분류 어휘임(스냅샷 n=1097 실측 근거, 임무 지시 3장). "인터넷"은 "온라인"의 동의어로 함께
	// 봄(예 "인터넷을 이용하여 온라인 신청"). FAX 대소문자 변형은 원문에서 관찰되지 않았으나 방어적으로 포함함.
	private static final String[] ONLINE_KEYWORDS = { "온라인", "인터넷" };

	private static final String[] VISIT_KEYWORDS = { "방문" };

	private static final String[] MAIL_KEYWORDS = { "우편" };

	private static final String[] FAX_KEYWORDS = { "팩스", "FAX", "Fax", "fax" };

	private static final String[] PHONE_KEYWORDS = { "전화" };

	// "신청없이 자격대상자에게 자동적으로 제공됩니다" 계열 어휘임(스냅샷 실측 표현 그대로 채집).
	private static final String[] AUTO_PROVIDED_KEYWORDS = { "신청없이", "신청 없이", "신청절차 없음", "신청 절차 없음", "별도 신청 불필요",
			"자동적으로 제공", "자동으로 제공", "신청 불필요" };

	// 지원유형(45종, 스냅샷 n=1097 실측) 대 PaymentType 매핑표임. 실측 기준 CASH 39.47%(433건),
	// IN_KIND 12.22%(134건), VOUCHER 5.47%(60건), REDUCTION 4.83%(53건), 나머지는 전부
	// UNKNOWN(38.01%, 417건) — 외부API-부족분-조사-2026-07-12.md 후속 임무 2장. 잘못된 CASH 분류는
	// 예상총액(AMT-622)을 부풀리므로 애매한 값은 보수적으로 UNKNOWN에 둠(억지 분류 금지). "||" 콤보 값(45종
	// 중 30종)은 이 표에 넣지 않고 mapPaymentType에서 별도 규칙으로 처리함(원문이 유형별 금액을 안 나눠 어느
	// 유형이 현금 몫인지 알 수 없기 때문).
	private static final Map<String, PaymentType> PAYMENT_TYPE_MAP = Map.ofEntries(
			// 현금 단독(433건) — 계좌 입금 등으로 환산 가능한 순수 현금
			Map.entry("현금", PaymentType.CASH),
			// 현물 단독(134건)
			Map.entry("현물", PaymentType.IN_KIND),
			// 이용권(60건) — 바우처
			Map.entry("이용권", PaymentType.VOUCHER),
			// 감면(53건) — "현금"이라는 단어가 있어도 계좌 입금이 아니라 요금 차감이라 REDUCTION으로 분류함
			Map.entry("현금(감면)", PaymentType.REDUCTION),
			// 아래 11종은 전부 모호하거나(기타·서비스류·시설이용) 상환 의무가 있거나(융자는 대출이라 무상
			// 지원금과 다름) 지급 형태가 불확실해(장학금·보험은 현금 입금이 아니라 학비 대납·보험료 대납 형태로
			// 운영되는 사례가 흔함) CASH로 잘못 분류하면 예상총액이 부풀려짐. 보수적으로 UNKNOWN 처리함
			Map.entry("기타", PaymentType.UNKNOWN), Map.entry("서비스(의료)", PaymentType.UNKNOWN),
			Map.entry("시설이용", PaymentType.UNKNOWN), Map.entry("기타(교육)", PaymentType.UNKNOWN),
			Map.entry("현금(장학금)", PaymentType.UNKNOWN), Map.entry("현금(보험)", PaymentType.UNKNOWN),
			Map.entry("현금(융자)", PaymentType.UNKNOWN), Map.entry("기타(상담)", PaymentType.UNKNOWN),
			Map.entry("서비스(돌봄)", PaymentType.UNKNOWN), Map.entry("서비스(일자리)", PaymentType.UNKNOWN),
			Map.entry("기술지원", PaymentType.UNKNOWN));

	// 신청기한 7분류(DeadlineKind) 대상 날짜 범위 정규식임. "YYYY.MM.DD~YYYY.MM.DD" 기본형에 더해 둘째
	// 날짜의 연도 생략("2026. 5. 11.(월) ~ 12. 4.(금)" -> 12.4.는 2026년으로 봄), 요일 괄호,
	// 공백·하이픈 구분자 혼용을 스냅샷(n=1097) 실측으로 확인해 포괄함(자세한 근거는 classifyDeadlineKind
	// Javadoc). 일(day)이 없는 "YYYY.MM~YYYY.MM" 형식은 의도적으로 매칭하지 않음 — 실제 LocalDate를 만들 수
	// 없는 값을 지어내지 않기 위함(임무 지시 1장 "상상 금지").
	private static final Pattern DEADLINE_DATE_RANGE_WITH_YEAR = Pattern
		.compile("(\\d{4})\\s*[.\\-]\\s*(\\d{1,2})\\s*[.\\-]\\s*(\\d{1,2})\\s*\\.?\\s*(?:\\([^)]{0,10}\\))?\\s*~\\s*"
				+ "(?:(\\d{4})\\s*[.\\-]\\s*)?(\\d{1,2})\\s*[.\\-]\\s*(\\d{1,2})\\s*\\.?\\s*(?:\\([^)]{0,10}\\))?");

	// "소진" — 예산 소진 시까지(고정 날짜 없음). "상시"보다 먼저 검사함 — "상시신청(예산 소진시 당해
	// 사업종료)"처럼 둘 다 있으면 실제 마감 조건인 예산 소진 쪽이 더 유용한 정보이기 때문임(스냅샷 실측 11건).
	private static final String[] DEADLINE_KIND_BUDGET_KEYWORDS = { "소진" };

	// "상시"·"연중" — 마감 개념이 없는 정상 상태(스냅샷 실측 673건, 상시신청 계열이 최빈).
	private static final String[] DEADLINE_KIND_ALWAYS_OPEN_KEYWORDS = { "상시", "연중" };

	// "매년"·"반기"·"분기"·"정기"·"매월" — 반복 주기가 원문에 명시됨(스냅샷 실측 79건). "공고" 키워드보다 먼저
	// 검사함 — "매년 1월 중 K-Startup 홈페이지를 통해 모집공고 게시"처럼 둘 다 있으면 매년 반복된다는 정보가 더
	// 안정적이기 때문임.
	private static final String[] DEADLINE_KIND_PERIODIC_KEYWORDS = { "매년", "반기", "분기", "정기", "매월" };

	// "공고"·"공모"·"모집"·"규정에 따름"·"규정에 의함" — 확정 시점 대신 별도 공고·규정을 참조하라는 안내(스냅샷
	// 실측 37건). "규정에 따름"·"규정에 의함"은 기존 parseDeadline의 EXTERNAL_REGULATION_REFERENCE와
	// 같은 어휘를 재사용함(스냅샷 실측 0건이라 결과에 영향 없음, 방어적으로 포함).
	private static final String[] DEADLINE_KIND_ANNOUNCEMENT_KEYWORDS = { "공고", "공모", "모집", "규정에 따름", "규정에 의함" };

	// 금액 표현 정규식임(임무 지시 2장). "숫자(,숫자)*" 또는 순수 숫자 뒤에 만/천/백만/천만/억 단위 접두(선택)와
	// "원"이 붙는 형태만 금액으로 인정함 — "215일"·"ha"·"50%"처럼 원 단위가 없는 숫자는 배제해 오탐을 줄임.
	private static final Pattern AMOUNT_TOKEN = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(억|천만|백만|만|천)?\\s*원");

	// 금액 앞 몇 글자 안에 조건 표현이 있는지 볼 범위임. "1인당 연간 35만원"처럼 마커와 숫자 사이에 수식어가 낄 수
	// 있어 실측 근거로 10자를 잡음(스냅샷 실측 — 8절 참조).
	private static final int AMOUNT_CONDITION_CONTEXT_WINDOW = 10;

	// 조건 표현 중 오탐 위험이 낮은 것들임(2글자 이상 복합어이거나, "당"처럼 스냅샷 실측으로 접미사 용법만 관찰됨).
	private static final String[] AMOUNT_CONDITION_SIMPLE_MARKERS = { "당", "별", "이상", "이하", "경우", "기준" };

	// "시"는 단독 1글자라 "시간"·"시설"·"시민"·"시장"처럼 무관한 단어의 접두 음절로도 걸리는 오탐이 스냅샷
	// 실측으로 확인됨(49건 중 15건 이상 무관). "시" 바로 뒤가 한글이 아니면(공백·쉼표·괄호·문자열 끝)만 조건부
	// 어미로 인정함 — "사망 시"·"전입신고 시"·"결제 시" 같은 관용구는 잡고 "시간"·"시설"은 거름. 다만
	// "사망시에"·"납입시마다"처럼 조사가 바로 붙는 경우는 뒤 글자가 한글이라 놓침(리콜 손실, 알려진 한계 —
	// PHONE_KEYWORDS 과대 집계 주석과 같은 성격의 트레이드오프).
	private static final Pattern AMOUNT_CONDITION_SI_MARKER = Pattern.compile("시(?=[^가-힣]|$)");

	// 금액 오분류 수정 임무(2026-07-12) 유형A — 사업 전체 예산이나 대회 총상금을 개인 지급액으로 오인하는
	// 문제의 배제 마커임. 스냅샷 실측(금액 앞 15자 창 기준 전수 스캔)으로 정한 3개뿐임 — "총"을 단독 마커로
	// 넣으면 "총5회"(328000000105)·"총 10만원 한도"(452000000308, 개인 상한액)·"총 300만원 2년간
	// 분할 지원"(475000000212, 개인 수령 총액)처럼 실제 개인 금액을 오탐 배제하는 사례가 확인돼 상상으로
	// 추가하지 않음(임무 지시 1장 "상상 금지"). "예산"은 "사업예산"·"소요예산" 복합어와 "예산 158억 원
	// 내외"(155000000018) 같은 단독 표기를 모두 포함하고, "사업비"는 "총사업비"·"사업비지원"·"사업비:
	// 120백만원"을 모두 포함함. "상금총"은 대회 총상금 표기(559000000440, 원문은 "상금 총 19,000천원")
	// 전용임 — 원문의 "상금"만 단독 마커로 쓰면 "보상금"(571000000107 "ha당 50만원 보상금 지급",
	// 154300000156)·"지연배상금"(383000000191)처럼 실제 지급액을 오탐 배제하므로 "총"까지 붙여 좁힘.
	// 검사 시 창의 공백을 제거하고 대조함 — 행정 원문은 "예 산 액"(402000000148)·"사 업 비"(650000000330)
	// 처럼 자간을 띄우는 표기를 쓰고, 공백을 그대로 두면 이 두 건의 사업예산이 개인 지급액으로 새어 들어옴
	// (수정 전 402000000148의 maxAmount가 5천만원, 650000000330이 SINGLE 2.5억원이었음).
	private static final String[] BUDGET_CONTEXT_MARKERS = { "예산", "사업비", "상금총" };

	// 예산 문맥 마커 검사 범위임. 스냅샷 실측 최대 간격은 "(사업비지원) 1년차 48백만원"(154300000061)의
	// 9자라 15자면 여유 있게 커버함. 15자를 **넘기면 안 되는** 상한이기도 함 — "총사업비의 50% 이내 / 최대
	// 4천만원"(142000000061)에서 "4천만원"은 실제 개인(기업) 상한액인데, 창을 17자 이상으로 넓히면 앞의
	// "사업비"가 창에 들어와 이 진짜 금액이 배제됨(과잉 배제). 즉 이 상수는 위아래 양쪽에서 실측으로 막혀
	// 있음(parseAmount_singleFlatAmountWithManwonUnit_convertsToWon 테스트가 하한을 고정함).
	private static final int BUDGET_CONTEXT_WINDOW = 15;

	// 공백 제거용임(BUDGET_CONTEXT_MARKERS·SELF_PAY_CONTEXT_MARKERS 대조 전 창을 정규화함).
	private static final Pattern WHITESPACE = Pattern.compile("\\s");

	// 금액 오분류 수정 임무 유형G(2026-07-12 적대 검증 High) — 이용자가 **내는** 돈(자부담금)을 지급액으로
	// 오인하는 문제의 배제 마커임. 예산 문맥(유형A)과 방향이 정반대임: 예산은 정부가 쓰는 돈이라 개인 지급액보다
	// 크고, 자부담은 이용자가 내는 돈이라 지급액과 크기가 비슷해 더 안 보임. 실제 오염 사례가
	// 383000000172(미니태양광)로, 원문 "자부담금 : 19만원 내외"가 SINGLE 190,000원·CASH로 잡혀 예상총액
	// 자동 채움 경로에 있었음(실제 보조금은 "설치금액의 80%"라 금액이 아예 없음).
	//
	// 마커를 "자부담" 계열로 **좁힌** 것이 이 규칙의 핵심임. 스냅샷 n=1097 전수 스캔 결과:
	// (1) "자부담"·"자기부담"·"자담"이 금액 앞 창에 걸리는 곳은 7레코드 10금액뿐이고 전부 이용자 부담액이거나
	// 자부담 비율이 붙은 자재 기준단가임 — 진짜 지급액과의 충돌 0건.
	// (2) 반면 "부담금"·"본인부담"을 마커로 쓰면 **혜택 형태인 본인부담금 지원**을 통째로 배제함 — "약제비
	// 본인부담금 1천원 지원"(458000000115), "산후조리도우미 본인부담금 ... 최대 40만원"(454000000102),
	// "본인부담금 지원(연 150만원)"(641000000147), "본인부담금 지원(3,000만원)"(B37003100009),
	// "본인부담금 지원(24만원)"(648000001029), "비급여항목의 본인부담금 3만원"(319000000191)이 전부 진짜
	// 지급액임(창에 걸리는 22곳 중 7곳). 즉 "본인부담"은 지급 방향이 양쪽이라 분리 불가지만 "자부담"은 한
	// 방향뿐이라 분리 가능함 — 직전 보고서의 "마커 분리 불가" 자인을 전수 실측으로 뒤집은 지점임.
	// (3) "자기부담"은 "자부담"의 부분문자열이 아니라 따로 넣어야 함("자기부담금 5천원", 392000000164).
	private static final String[] SELF_PAY_CONTEXT_MARKERS = { "자부담", "자기부담", "자담" };

	// 자부담 마커 검사 범위임. 위아래 양쪽이 실측으로 막혀 있음.
	// 하한 — "○ 검진비용 지원: 22만원 기준 자부담 2만원"(650000001099)의 12자가 실측 최대 간격이라 15자면
	// 커버함(12자에서 15자까지는 결과 동일).
	// 상한 — 16자로 넓히면 516000000123의 "과수전용소형농기계(군비 40%, 자부담 60%, 복숭아적화기 50만원/대"에서
	// **진짜 기준단가** 50만원과 700만원이 앞의 "자부담 60%"에 걸려 배제됨(과잉 배제). 즉 15자가 상한임
	// (parseAmount_typeG_selfPayWindowUpperBound_doesNotEatRealUnitPrice 테스트가 이 상한을 고정함).
	private static final int SELF_PAY_CONTEXT_WINDOW = 15;

	// 유형A 보강 — 예산·사업비 같은 어휘 없이 "총 규모 수량"과 금액을 나란히 적어 총액을 표기하는 형태임.
	// 스냅샷 전수 스캔 결과 이 형태는 "선발규모 : 총94명, 86,224천원"(O00081200001) 1건뿐이고, 개인
	// 지급액이 이 패턴 뒤에 오는 사례는 0건임. "총"을 단독 마커로 쓸 수 없는 이유(위 BUDGET_CONTEXT_MARKERS
	// 주석)를 우회하려고 "총+수량+집합단위"까지 붙여 좁힘. 집합단위에 "대"·"건"·"회"를 넣지 않은 것은
	// "통근버스 40대(... 300만원 한도)"(452000000326)·"100만원씩 총5회"(328000000105)처럼 실제 개인
	// 금액과 충돌하기 때문임.
	private static final Pattern TOTAL_SCALE_HEADCOUNT = Pattern.compile("총\\s*\\d[\\d,]*\\s*(?:명|가구|세대|개소)");

	// 유형A 보강 — 금액 뒤에 "/"와 **천단위 콤마가 있는 수량**이 붙는 형태임("799,920천원/3,333ha",
	// 430000000135). 총액을 사업 총규모로 나눠 적은 표기이지 단가가 아님. 스냅샷 전수 스캔으로 이 형태는
	// 1건이고, 같은 자리에 오는 단가 표기 61건("278백만원/km", "15천원/인", "500,000원/1군", "8천원/두",
	// "50천원/1회" 등)은 전부 나눗값이 맨 단위이거나 한 자리 수("1군"·"1회")라 콤마가 없음 — 두 집합이
	// 구조적으로 완전히 갈려 오탐 0건임. 수정 전 430000000135는 SINGLE 7.99억원으로 스냅샷에서 가장 큰
	// 개인 지급액 오염원이었음.
	private static final Pattern BULK_DIVISOR = Pattern.compile("^\\s*/\\s*\\d{1,3}(?:,\\d{3})+");

	// 금액 오분류 수정 임무 유형H(2026-07-12) — 대출·융자·보증 **한도**를 개인 지급액으로 오인하는 문제의 배제
	// 마커임. 예산(정부가 쓰는 돈)·자부담(이용자가 내는 돈)과 또 다른 세 번째 방향임: 대출 한도는 **아무도 주지
	// 않는 돈**(빌린 뒤 갚아야 하는 채무 상한)이라 예상총액에 들어가면 안 됨. 실제 오염 사례가
	// 20250502005400210779(부산청년 머물자리론)로, "대출최대한도 : 1억 원"이 SINGLE 1억원·CASH로 잡혀
	// 예상총액 자동 채움 경로에 있었음.
	//
	// <b>이 규칙의 핵심은 "대출이라는 낱말의 유무"가 아니라 "이 금액이 대출 원금·한도인가 지원받는 금액인가"임.</b>
	// 낱말만 보고 배제하면 <b>진짜 현금 지원까지 날아감</b> — "대출이자 납부액(월 최대 25만 원)"(486000000133),
	// "대출 잔액의 1.5% 지원 (최대 100만원)"(537000000110), "가구 당 최대 100만원 한도의 대출 이자
	// 지원"(338000000419)의 금액은 전부 진짜 지급액임. 그래서 대출 어휘가 **한도 어휘와 붙어 하나의 명사구를
	// 이룰 때만**(대출한도·융자한도·보증한도·대부한도·대출잔액·보증금액) 그 뒤의 금액을 배제함. 스냅샷 전수
	// (gov24 1,097 더하기 온통청년 1,324)에서 이 융합 마커가 걸리는 금액 30개를 하나씩 원문으로 확인했고 전부
	// 대출·보증 한도이거나 예시로 든 대출 잔액임 — 진짜 지급액과의 충돌 0건.
	private static final Pattern LOAN_LIMIT_MARKER = Pattern.compile("(대출|융자|보증|대부|대환)(최대)?(한도|잔액|가능금액|금액)");

	// LOAN_LIMIT_MARKER 역방향 검사 범위임. 실측 최대 간격은 "융자한도 - 농어업인 : 5천만 원"(644000000860)의
	// 12자라 20자면 커버함. 20자를 넘기면 안 되는 상한이기도 함 — "대출잔액 5,000만원 한도 × 이자율 3% = 150만
	// 원"(20251218005400212025)에서 뒤의 150만원은 **진짜 이자 지원액**인데, 창을 넓히면 앞의 "대출잔액"이
	// 들어와 배제됨(과잉 배제).
	private static final int LOAN_LIMIT_BACKWARD_WINDOW = 20;

	// 유형H 과잉 배제 차단 장치임. 융합 마커와 금액 사이(gap)에 이 어휘가 하나라도 있으면 배제하지 않음 — 그 금액은
	// 대출 자체가 아니라 대출에서 **파생된 지원금**이기 때문임. 실측 근거 2종.
	// (1) 수혜 어휘 — "주택 임차보증금 대출잔액의 이자 지원(최대 150만 원)"(20251218005400212025,
	// 20251217005400212015)에서 150만원은 진짜 지급액인데 "대출잔액"이 20자 창에 들어옴. gap의 "이자·지원"이
	// 이걸 막음.
	// (2) 퍼센트 기호 — "전월세 보증금 대출잔액의 2%, 가구당 최대 300만원"(20260318005400212197)에서 300만원은
	// 진짜 지급액임. 대출 잔액에 **비율**을 곱해 나온 금액이라는 뜻이므로, 마커와 금액 사이에 "%"가 끼면 배제하지
	// 않음. 반대로 진짜 한도 30개의 gap에는 "%"가 단 하나도 없음(전부 "최대"·"연간"·"업체당"·"기준으로" 같은
	// 수식어뿐) — 두 집합이 구조적으로 갈림.
	private static final String[] LOAN_LIMIT_GAP_BLOCKERS = { "지원", "지급", "보전", "환급", "납부", "이자", "보증료", "상환액", "%" };

	// 유형H 순방향 마커임. 한도 어휘 없이 "N원 융자"처럼 금액 뒤에 대출 어휘가 바로 붙는 형태를 잡음("최대 5억원
	// 융자지원해주고" 119200000192, "5억원 융자" 20260422005400212868, "1억원 한도 융자지원"
	// 20260330005400212318, "최대 5억 원 한도 융자 지원" 20260513005400213183). 부정 선읽기(?!이자|금리|
	// 이차|보증료|잔액)가 이 규칙의 안전장치임 — 이게 없으면 "가구당 연간 100만원 이내 **대출이자** 지원"
	// (20260504005400213064)의 진짜 지급액 100만원이 "이내 대출"에 걸려 날아감(전수 스캔으로 확인한 유일한
	// 충돌). 전수에서 이 패턴이 걸리는 금액은 5개이고 전부 융자 원금임.
	private static final Pattern LOAN_FORWARD_MARKER = Pattern
		.compile("^\\s*(?:이내|이상|까지)?\\s*(?:한도)?\\s*(?:융자|대출|대부)(?!이자|금리|이차|보증료|잔액)");

	// LOAN_FORWARD_MARKER 검사 범위임("최대 5억 원 한도 융자 지원"의 " 한도 융자" 8자가 실측 최대라 14자면 충분함).
	private static final int LOAN_FORWARD_WINDOW = 14;

	// 금액 오분류 수정 임무 유형I(2026-07-12) — 창업 사업화 자금임. 배제가 아니라 **CONDITIONAL 강등**인 것이
	// 핵심임: 이 돈은 실제로 지급되므로(선정 기업이 받음) 거짓 값이 아니지만, **개인 생활 지원금이 아니라 기업 단위
	// 사업비**이고 대개 자부담 매칭과 사업비 정산이 붙어 있어(B55400900023 "최대 1억원 이내(총 사업비의 70%)")
	// 개인 예상총액에 그대로 합산하면 안 됨. 금액은 보존하고 합산 경로에서만 뺌.
	// 스냅샷 전수에서 이 마커가 금액 앞 25자에 걸리는 곳은 24금액 17레코드이고, 하나씩 원문을 확인한 결과 전부
	// 기업·창업팀 단위 사업화 자금임(기업당 4,000만원, 1개사당 10,000,000원, 선정기업 15백만원 등) — 개인 생활
	// 지원금과의 충돌 0건. 오염 사례 3건이 여기서 잡힘: B55273500013(초기창업패키지 "사업화 자금(최대 1억원)"),
	// B55307700024(강한 소상공인 "사업화 자금 지원 (최대 1억원 이내)"), 20250221005400110545(창업도약패키지
	// "사업화자금(최대 2억원)").
	private static final String[] BUSINESS_FUND_MARKERS = { "사업화" };

	private static final int BUSINESS_FUND_BACKWARD_WINDOW = 25;

	// 마커가 금액 **뒤**에 오는 표기도 있어 순방향도 봄("1천만원 사업화 지원금 지원" 20250114005400210260,
	// "연간 20백만원 창업활동비(사업화자금) 지원" 20250829005400211538). 전수 스캔으로 이 순방향 창에서만 새로
	// 걸리는 금액은 이 2건뿐이고 둘 다 창업기업 사업비임 — 개인 지급액 충돌 0건.
	private static final int BUSINESS_FUND_FORWARD_WINDOW = 15;

	// 금액 오분류 수정 임무 유형J(2026-07-12) — 고액 안전망임. 후보 금액이 1억원 이상이면 CONDITIONAL로 강등함
	// (**배제 아님 — 금액은 보존**). 근거는 전수 실측임: 4종 6,840건에서 SINGLE이면서 1억원 이상인 11건 중
	// **개인 지급액인 것이 0건**이었고 전부 대출 한도·사업 총예산·기업 사업비였음(기업마당도 같은 실측으로
	// {@code BizinfoAmountParser.PROGRAM_SCALE_THRESHOLD}에 같은 경계를 이미 두고 있어 그 규칙을 공통 파서로
	// 올린 것임). 마커 규칙(유형A·H·I)이 놓친 새로운 표현 형태를 붙잡는 최후 그물임.
	//
	// **경계 사례를 정직하게 적어 둠**(과대 주장 금지). 642000000719(청년농업인 육성지원)은 "창업기반 : 3,700만
	// 원 ~ 10,000만 원/명"이라 1억원이 **1인당 금액**임 — 즉 "1억원 이상은 개인 지급액일 수 없다"는 명제는
	// SINGLE(자동 합산 경로)에서만 참이고 일반 명제로는 거짓임. 그럼에도 이 레코드에 강등을 걸어도 손실이 0인 이유는
	// (가) 원래 구간 표기(3,700만~1억)라 단순 합산 금지 대상이 맞고, (나) 강등은 금액을 지우지 않아 화면·수기 검토에
	// 그대로 남기 때문임. 배제가 아니라 강등을 고른 것이 바로 이 경계 사례를 안전하게 흡수하려는 선택임.
	private static final long PROGRAM_SCALE_THRESHOLD = 100_000_000L;

	// 유형A 보강(기업마당 규칙 이식) — "지원규모"·"사업규모"·"편성규모"처럼 사업 총규모를 적은 표기임. "규모"라는
	// 낱말만으로는 기업·개인 단위 상한에도 쓰이므로 **1억원 경계와 함께**만 배제함(BizinfoAmountParser의 B3와
	// 같은 규칙). 스냅샷 전수에서 이 조합이 걸리는 4금액은 전부 사업 편성 예산임 — 20250517005400210846
	// ("지원규모 : 100,000천원", 청년예술인 30명 사업비), 20250114005400210256("사업규모 : 100백만원(시비) /
	// 150가구"), 20251114005400211890("편성규모 : 총300백만원(행사운영비 210백만원)") — 개인 지급액 0건.
	private static final String PROGRAM_SCALE_MARKER = "규모";

	private static final int PROGRAM_SCALE_BACKWARD_WINDOW = 20;

	// 금액 오분류 수정 임무 유형E(전수 재검사에서 새로 찾음) — 금액 **뒤**에 오는 문턱 표현임. "22,340원
	// 이하인 가구"(308000000120)·"승소가액 3억원 이상 ... 제외"(B55307700005)처럼 자격 기준선을 적은
	// 숫자가 개인 지급액(SINGLE)으로 잡혀 예상총액을 오염시키는 문제를 막음. 기존
	// AMOUNT_CONDITION_SIMPLE_MARKERS에도 "이상"·"이하"가 있으나 금액 **앞**만 보므로 이 형태를 못 잡았음.
	// 스냅샷 전수 스캔으로 이 마커가 걸리는 22곳을 전부 눈으로 확인함 — 20곳이 자격 기준선·조건이고, 나머지
	// 2곳("셋째 500만원, 넷째이상 1,000만원" 453000000201, "1인 경영체 60만원, 2인이상 ..."
	// 645000000147)은 다음 구간 라벨의 "이상"이 걸린 것인데 두 레코드 모두 실제로 조건별 차등 지급이라
	// CONDITIONAL 판정이 맞음. 즉 실제 오탐 0건임. 배제(EXCLUDED)가 아니라 CONDITIONAL로만 내리는 이유는,
	// 이 숫자들이 팀이 원문을 판단할 때 필요한 정보이고 conditionSummary에 문맥이 남기 때문임(과잉 배제 회피).
	// "이내"·"한도"는 넣지 않음 — "최대 20만원 이내"처럼 개인 지급 상한에 압도적으로 많이 붙어, 넣으면 SINGLE이
	// 붕괴해 추천 품질이 떨어짐(과잉 배제).
	private static final String[] AMOUNT_THRESHOLD_FORWARD_MARKERS = { "이하", "미만", "이상", "초과" };

	// AMOUNT_THRESHOLD_FORWARD_MARKERS 검사 범위임. 실측 최대 간격은 "600원이상"(SD0000010824, 0자)부터
	// "48만 원을 초과할"(654000000030, 2자)까지라 6자면 충분함. 더 넓히면 다음 문장의 조건 표현이 앞 금액에
	// 잘못 붙음.
	private static final int AMOUNT_THRESHOLD_FORWARD_WINDOW = 6;

	// 금액 오분류 수정 임무 유형B — 조건 마커("당" 계열)가 AMOUNT_CONDITION_CONTEXT_WINDOW(10자) 밖에
	// 있거나 금액 뒤에 오는 경우를 놓치는 문제의 보강 마커임. 스냅샷 전체(n=1097)에서 "[가-힣]{1,3}당" 패턴을
	// 전수로 뽑아(임무 지시 2장 "전수로 뽑아서 규칙을 만들어라") 사람 수·가구·건수 등 실제 "~당"(per-X) 의미인
	// 것만 남기고, "해당"·"상당"(~어치)·"담당"·"혈당"·"경로당"(노인정)·"한식당"·"근저당"·"납골당"·"강당"
	// 같은 동음이의 함정과 "혼인당사자"·"배우자수당"·"입학당해"처럼 다른 단어의 부분 문자열로 걸리는 경우는
	// 제외함. bare "당"(AMOUNT_CONDITION_SIMPLE_MARKERS)과 별개로 더 넓은 창에서 이 화이트리스트만
	// 검사하는 이유는, bare "당"을 넓히면 "해당"·"상당" 같은 무관 단어까지 오탐이 커지기 때문임.
	private static final String[] PER_UNIT_CONDITION_MARKERS = { "인당", "명당", "가구당", "세대당", "세대원당", "농가당", "업소당", "아동당",
			"가정당", "부부당", "건당", "회당", "팀당", "학기당", "점포당", "분기당", "톤당", "포당", "포대당", "개소당", "대당", "사업장당", "출생아당", "개당",
			"평당", "자격증당", "쌍당", "가마당", "진료당", "사고당", "리터당", "기업당", "마리당", "시간당", "안당", "공당", "매당" };

	// PER_UNIT_CONDITION_MARKERS 뒤쪽(순방향) 검사 범위임. "200천원(1인당)"(422000000133, 2자)·
	// "10만원 이내/ 아동당"(405000000628, 5자) 실측 기준 여유를 두어 10자로 잡음.
	private static final int PER_UNIT_MARKER_FORWARD_WINDOW = 10;

	// PER_UNIT_CONDITION_MARKERS 앞쪽(역방향) 검사 범위임. "업소당 조리환경 개선 비용 최대
	// 30만원"(328000000120, 15자)·"1인당 안경(렌즈 등) 구입비 최대 10만원"(650000001140, 17자) 실측
	// 최대 간격이 17자라 20자로 잡음. AMOUNT_CONDITION_CONTEXT_WINDOW(10자)보다 넓지만 화이트리스트
	// 자체가 좁혀져 있어 무관 단어 충돌 위험은 낮음(전수 재검사로 확인함).
	private static final int PER_UNIT_MARKER_BACKWARD_WINDOW = 20;

	// 금액 오분류 수정 임무 유형C — 단위가 마지막 항목에만 붙는 다단 차등 나열임(예 "초 30, 중 40, 고
	// 50만원", 439000000873). 완전한 항목별 추출은 하지 않고(상한 — ponytail: 스냅샷 n=1097 전수
	// 스캔으로 이 형태가 1건뿐임을 확인해 항목별 금액 역산 로직은 만들지 않음) CONDITIONAL로만 분류하고
	// conditionSummary에 원문을 남겨 팀이 엑셀에서 직접 판단하게 함(임무 지시 3장). "한글라벨+숫자+콤마"가
	// 1회 이상 반복되다 마지막에 단위+원이 붙는 형태만 매칭해, "100,000원"류 3자리 콤마 구분 금액과는
	// 구조적으로 겹치지 않음(전수 재검사로 오탐 0건 확인함).
	private static final Pattern TIERED_OMITTED_UNIT_ENUM = Pattern
		.compile("(?:[가-힣]\\s*\\d+\\s*,\\s*)+[가-힣]\\s*\\d+\\s*(?:억|천만|백만|만|천)?\\s*원");

	// amountUnit 판정용 근접 키워드임(임무 지시 2장 "원/월/회/인/가구 중 관찰되는 것"). 순서가 우선순위임 — 가구
	// 단위가 인·회·월보다 먼저 오면 그쪽으로 판정함(스냅샷 관찰 사례 기준, ponytail: 레코드 1개당 단위 1개로
	// 단순화).
	private static final String[] AMOUNT_UNIT_HOUSEHOLD_KEYWORDS = { "가구" };

	private static final String[] AMOUNT_UNIT_PERSON_KEYWORDS = { "인당", "명당" };

	private static final String[] AMOUNT_UNIT_COUNT_KEYWORDS = { "회당", "매회" };

	// "월"은 "5월"처럼 날짜 표기의 일부로도 흔히 나타나므로, 바로 앞에 숫자가 없을 때만(즉 "매월"·"월
	// 최대"처럼 독립된 "월"일 때만) 단위로 인정함.
	private static final Pattern AMOUNT_UNIT_MONTH_MARKER = Pattern.compile("(?<!\\d)월");

	private static final String DEFAULT_AMOUNT_UNIT = "원";

	// 소득 조건 원문 언급 정규식임("중위소득 50%"처럼 씀). Gov24JaFieldParserTest의 감사 로직(테스트 전용
	// private 구현)과 별개로 프로덕션 파서에 이식함(임무 지시 4장 — "그걸 필드로 승격하는 것이다").
	private static final Pattern MEDIAN_INCOME_PERCENT = Pattern.compile("중위소득\\s*(\\d+)\\s*%");

	// 소관기관명 첫 토큰이 이 목록에 있으면 시도로 봄(임무 지시 3장 — 지역은 소관기관명에서만 유추 가능함,
	// 오케스트레이터 실측 시도+시군구 58.8%/645건, 시도만 12.8%/140건과 정확히 일치하도록 스냅샷 n=1097로
	// 검증한 목록). 2026-07-01 행정구역 개편 반영 명칭을 씀(전남광주통합특별시가 광주광역시·전라남도를
	// 대체) — 대구·경북 통합은 조사 리포트 3장 G5 기준 미반영 확정이라 두 시도를 그대로 유지함.
	// 사용자구분(serviceList) 어휘 4종을 개인 축과 사업자 축으로 가름. 전수 10,974건 실측 결과 이 4종과 "||" 조합뿐임.
	// "가구"를 개인으로 넣은 근거: 근로·자녀장려금(가구)처럼 세대 단위로 신청하지만 받는 주체는 개인임.
	private static final Set<String> PERSONAL_USER_TYPES = Set.of("개인", "가구");

	// "소상공인"은 개인사업자를 포함하지만 사업자등록이 요건이라 개인 축이 아님. 제품 범위가 개인 대상 지원금이라 배제 대상임.
	private static final Set<String> BUSINESS_USER_TYPES = Set.of("소상공인", "법인/시설/단체");

	private static final Set<String> SIDO_NAMES = Set.of("서울특별시", "부산광역시", "대구광역시", "인천광역시", "대전광역시", "울산광역시",
			"세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도", "전남광주통합특별시", "경상북도", "경상남도", "제주특별자치도");

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * serviceList 또는 serviceDetail 응답 JSON을 파싱함(두 엔드포인트가 같은 래퍼 구조를 씀).
	 * @param json 원문 JSON 문자열
	 * @return 서비스 항목 목록
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐(리포트 대상인 파싱 실패와는 다른 입력 오류임)
	 */
	public List<Gov24ServiceItemDto> parseServiceItems(String json) throws IOException {
		Gov24ServiceListResponseDto response = parseServicePage(json);
		return response.data() == null ? List.of() : response.data();
	}

	/**
	 * serviceList 또는 serviceDetail의 페이징 메타와 항목을 함께 파싱함.
	 * @param json 원문 JSON 문자열
	 * @return 페이징 응답
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐
	 */
	public Gov24ServiceListResponseDto parseServicePage(String json) throws IOException {
		return objectMapper.readValue(json, Gov24ServiceListResponseDto.class);
	}

	/**
	 * supportConditions 응답 JSON을 파싱함.
	 * @param json 원문 JSON 문자열
	 * @return 지원조건 항목 목록
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐
	 */
	public List<Gov24SupportConditionDto> parseSupportConditions(String json) throws IOException {
		Gov24SupportConditionsResponseDto response = parseSupportConditionsPage(json);
		return response.data() == null ? List.of() : response.data();
	}

	/**
	 * supportConditions의 페이징 메타와 항목을 함께 파싱함.
	 * @param json 원문 JSON 문자열
	 * @return 페이징 응답
	 * @throws IOException JSON 구조가 깨져 역직렬화에 실패하면 던짐
	 */
	public Gov24SupportConditionsResponseDto parseSupportConditionsPage(String json) throws IOException {
		return objectMapper.readValue(json, Gov24SupportConditionsResponseDto.class);
	}

	/**
	 * 서비스 항목 1건과 지원조건 맵을 서비스ID로 합쳐 최종 파싱 결과를 만듦. conditionsById에 없는 서비스ID는 ageMin·ageMax가
	 * null, incomeSignal·householdSignal이 UNKNOWN으로 남음(지원조건 응답이 아직 안 온 경우와 동일하게 취급함).
	 * @param item serviceList 또는 serviceDetail 항목
	 * @param conditionsById 서비스ID를 키로 하는 supportConditions 맵
	 * @return 최종 파싱 결과
	 */
	public ParsedSubsidyResult toParsedSubsidy(Gov24ServiceItemDto item,
			Map<String, Gov24SupportConditionDto> conditionsById) {
		return toParsedSubsidy(item, conditionsById, Map.of(), Map.of());
	}

	/**
	 * 위와 같되 <b>serviceList에서 얻은 사용자구분을 함께 받음</b>. 사용자구분은 serviceDetail에 없으므로, detail 아이템만
	 * 넘기면 대상 판정이 UNKNOWN으로 떨어짐 — 그래서 두 오퍼레이션을 서비스ID로 join한 맵을 별도로 받음.
	 * @param item serviceList 또는 serviceDetail 항목
	 * @param conditionsById 서비스ID를 키로 하는 supportConditions 맵
	 * @param userTypeById 서비스ID를 키로 하는 사용자구분 원문 맵(serviceList 출처). 비어 있으면 판정은 UNKNOWN임
	 * @return 최종 파싱 결과
	 */
	public ParsedSubsidyResult toParsedSubsidy(Gov24ServiceItemDto item,
			Map<String, Gov24SupportConditionDto> conditionsById, Map<String, String> userTypeById) {
		return toParsedSubsidy(item, conditionsById, userTypeById, Map.of());
	}

	/**
	 * 위와 같되 serviceList의 사용자구분과 서비스분야 원문을 함께 결합함. 두 필드는 serviceDetail에 없으므로 서비스ID join이
	 * 빠지면 각각 UNKNOWN과 null로 남음.
	 * @param item serviceList 또는 serviceDetail 항목
	 * @param conditionsById 서비스ID를 키로 하는 supportConditions 맵
	 * @param userTypeById 서비스ID를 키로 하는 사용자구분 원문 맵(serviceList 출처)
	 * @param categoryRawTextById 서비스ID를 키로 하는 서비스분야 원문 맵(serviceList 출처)
	 * @return 최종 파싱 결과
	 */
	public ParsedSubsidyResult toParsedSubsidy(Gov24ServiceItemDto item,
			Map<String, Gov24SupportConditionDto> conditionsById, Map<String, String> userTypeById,
			Map<String, String> categoryRawTextById) {
		// 아이템 자체가 serviceList에서 왔으면 필드가 이미 채워져 있고, serviceDetail에서 왔으면 join 맵에서 가져옴
		String userTypeText = item.userTypeText() != null ? item.userTypeText() : userTypeById.get(item.serviceId());
		String categoryRawText = item.categoryRawText() != null ? item.categoryRawText()
				: categoryRawTextById.get(item.serviceId());
		TargetAudience targetAudience = classifyTargetAudience(userTypeText);
		return toParsedSubsidy(item, conditionsById, targetAudience, blankToNull(categoryRawText));
	}

	/**
	 * 사용자구분 원문을 {@link TargetAudience}로 판정함.
	 *
	 * <p>
	 * 원문은 {@code "개인"}, {@code "소상공인"}처럼 단일 값이거나 {@code "개인||소상공인"}처럼 {@code ||}로 이어 붙은
	 * 복수 값임(전수 10,974건 실측. 단일 어휘 4종과 그 조합 10종뿐임).
	 *
	 * <p>
	 * <b>개인 신호와 사업자 신호가 함께 켜지면 {@link TargetAudience#MIXED}임.</b> 이걸 BUSINESS로 뭉개면
	 * {@code 전략작물직불}(개인||법인/시설/단체)처럼 개인 농업인이 실제로 받는 지원금이 함께 죽음. MIXED는 추천에는 넣고 예상 총액
	 * 합산에서만 뺌.
	 *
	 * <p>
	 * 알려진 4개 어휘 밖의 값이 나타나면 <b>BUSINESS로 추측하지 않고</b> UNKNOWN으로 떨어뜨림. 정체 모를 레코드를 사업자로 둔갑시키는
	 * 것보다 사람이 보고 분류하는 편이 안전함.
	 * @param userTypeText 사용자구분 원문(null 가능)
	 * @return 대상 판정
	 */
	public static TargetAudience classifyTargetAudience(String userTypeText) {
		if (userTypeText == null || userTypeText.isBlank()) {
			return TargetAudience.UNKNOWN;
		}
		boolean personal = false;
		boolean business = false;
		boolean unknownVocabulary = false;
		for (String token : userTypeText.split("\\|\\|")) {
			String value = token.trim();
			if (PERSONAL_USER_TYPES.contains(value)) {
				personal = true;
			}
			else if (BUSINESS_USER_TYPES.contains(value)) {
				business = true;
			}
			else if (!value.isEmpty()) {
				unknownVocabulary = true;
			}
		}
		if (unknownVocabulary && !personal && !business) {
			return TargetAudience.UNKNOWN;
		}
		if (personal && business) {
			return TargetAudience.MIXED;
		}
		if (business) {
			return TargetAudience.BUSINESS;
		}
		return personal ? TargetAudience.PERSONAL : TargetAudience.UNKNOWN;
	}

	private ParsedSubsidyResult toParsedSubsidy(Gov24ServiceItemDto item,
			Map<String, Gov24SupportConditionDto> conditionsById, TargetAudience targetAudience,
			String categoryRawText) {
		Gov24SupportConditionDto condition = conditionsById.get(item.serviceId());
		Integer ageMin = condition == null ? null : condition.ageMin();
		Integer ageMax = condition == null ? null : condition.ageMax();
		EligibilitySignal incomeSignal = condition == null ? EligibilitySignal.UNKNOWN : condition.incomeSignal();
		EligibilitySignal householdSignal = condition == null ? EligibilitySignal.UNKNOWN : condition.householdSignal();
		PaymentType paymentType = mapPaymentType(item.paymentTypeText());
		DeadlineParseResult deadline = parseDeadline(item.applicationDeadlineText());
		String eligibilityText = buildEligibilityText(item.eligibilitySummaryText(), item.selectionCriteriaText());
		String externalUrl = blankToNull(item.externalUrl());
		LocalDateTime dataUpdatedAt = parseDataUpdatedAt(item.dataUpdatedAtText());
		Gov24ApplicationMethodFlags applicationMethod = parseApplicationMethod(item.applicationMethodText());
		String requiredDocumentsText = normalizeRequiredDocuments(item.requiredDocumentsText());
		ParsedDeadline parsedDeadline = classifyDeadlineKind(item.applicationDeadlineText());
		ParsedAmount amount = parseAmount(item.description());
		ParsedRegion region = parseRegion(item.agency());
		IncomeSignalSource incomeSignalSource = computeIncomeSignalSource(eligibilityText, condition);
		IncomeConsistencyStatus incomeConsistencyStatus = computeIncomeConsistencyStatus(eligibilityText, condition);
		String incomeTextEvidence = extractIncomeTextEvidence(eligibilityText);
		return new ParsedSubsidyResult(item.serviceId(), item.serviceName(), item.agency(), item.description(),
				eligibilityText, categoryRawText, ageMin, ageMax, incomeSignal, householdSignal, paymentType,
				item.paymentTypeText(), externalUrl, dataUpdatedAt, applicationMethod, requiredDocumentsText, deadline,
				parsedDeadline, amount, region, incomeSignalSource, incomeConsistencyStatus, incomeTextEvidence,
				targetAudience,
				// supportConditions가 없으면 직업군 판정 근거 자체가 없음. 없는 제한을 지어내지 않고 통과시킴
				condition == null ? OccupationRestriction.NONE : condition.occupationRestriction());
	}

	/**
	 * 지원유형 원문을 PaymentType으로 매핑함. 매핑표({@link #PAYMENT_TYPE_MAP})에 있는 값은 그대로 쓰고, "||" 콤보
	 * 값은 유형별 금액을 나눌 수 없어 항상 UNKNOWN으로 처리함. 매핑표에도 없고 콤보도 아닌 신규 값은 UNKNOWN으로 떨어뜨리되 경고 로그를
	 * 남겨 다음 스냅샷 갱신 때 매핑표를 보강할 수 있게 함.
	 * @param rawText 지원유형 원문(null 가능)
	 * @return 매핑된 PaymentType
	 */
	public PaymentType mapPaymentType(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return PaymentType.UNKNOWN;
		}
		if (rawText.contains(COMBO_DELIMITER)) {
			// 콤보(여러 유형을 한 필드에 나열)는 지원내용 원문이 유형별 금액을 안 나눠 어느 유형이 실제
			// 현금 몫인지 알 수 없으므로 전부 UNKNOWN임. 스냅샷(n=1097) 실측 30종 콤보 전부 이 규칙으로
			// 걸러지므로 개별 나열 대신 구조 규칙으로 처리함(로그 생략 — 이미 알려진 패턴).
			return PaymentType.UNKNOWN;
		}
		PaymentType mapped = PAYMENT_TYPE_MAP.get(rawText);
		if (mapped == null) {
			log.warn("gov24 지원유형 매핑표에 없는 신규 값 감지, UNKNOWN으로 처리함(원문: \"{}\")", rawText);
			return PaymentType.UNKNOWN;
		}
		return mapped;
	}

	/**
	 * 신청방법 원문을 키워드로 분류해 플래그로 반환함(임무 지시 3장 — enum 단일값 금지, 온라인과 방문을 동시에 지원하는 지원금이 실제로 있음).
	 * 스냅샷(n=1097) 실측 미분류율은 12.40%임.
	 * @param rawText 신청방법 원문(null 가능)
	 * @return 키워드 분류 플래그
	 */
	public Gov24ApplicationMethodFlags parseApplicationMethod(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return new Gov24ApplicationMethodFlags(false, false, false, false, false, false, true);
		}
		boolean online = containsAny(rawText, ONLINE_KEYWORDS);
		boolean visit = containsAny(rawText, VISIT_KEYWORDS);
		boolean mail = containsAny(rawText, MAIL_KEYWORDS);
		boolean fax = containsAny(rawText, FAX_KEYWORDS);
		boolean phone = containsAny(rawText, PHONE_KEYWORDS);
		boolean autoProvided = containsAny(rawText, AUTO_PROVIDED_KEYWORDS);
		boolean unclassified = !(online || visit || mail || fax || phone || autoProvided);
		return new Gov24ApplicationMethodFlags(online, visit, mail, fax, phone, autoProvided, unclassified);
	}

	private static boolean containsAny(String text, String[] keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	// 지원대상을 본문으로 쓰고, 선정기준이 있으면(채움률 9.75%) 뒤에 이어붙임. 지원대상이 비어 있는 극단적인
	// 경우(스냅샷 실측 0건, 방어적 처리)는 선정기준만이라도 씀.
	private static String buildEligibilityText(String eligibilitySummaryText, String selectionCriteriaText) {
		boolean hasSummary = eligibilitySummaryText != null && !eligibilitySummaryText.isBlank();
		boolean hasCriteria = selectionCriteriaText != null && !selectionCriteriaText.isBlank();
		if (hasSummary && hasCriteria) {
			return eligibilitySummaryText + "\n\n[선정기준] " + selectionCriteriaText;
		}
		if (hasSummary) {
			return eligibilitySummaryText;
		}
		return hasCriteria ? selectionCriteriaText : null;
	}

	// 구비서류 원문을 정규화함. "해당없음"(스냅샷 실측 41.66%)은 화면에 낼 내용이 없다는 뜻이라 null로 둠.
	private static String normalizeRequiredDocuments(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return null;
		}
		return NO_DOCUMENTS_REQUIRED.equals(rawText.trim()) ? null : rawText;
	}

	private static String blankToNull(String rawText) {
		return (rawText == null || rawText.isBlank()) ? null : rawText;
	}

	/**
	 * 수정일시 원문을 LocalDateTime으로 파싱함. serviceDetail 실측 형식("YYYY-MM-DD")을 먼저 시도하고, 실패하면 로컬
	 * 픽스처에서 관찰된 "YYYYMMDDHHmmss" 14자리 형식을 시도함. 둘 다 실패하면 null(예외를 던지지 않음 — 이 필드의 파싱 실패가 전체
	 * 파싱을 막으면 안 됨). 날짜만 있는 형식은 자정(00:00)으로 채움.
	 * @param rawText 수정일시 원문(null 가능)
	 * @return 파싱된 LocalDateTime, 실패하면 null
	 */
	public LocalDateTime parseDataUpdatedAt(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(rawText, DATA_UPDATED_AT_DATE_ONLY).atStartOfDay();
		}
		catch (DateTimeException dateOnlyEx) {
			try {
				return LocalDateTime.parse(rawText, DATA_UPDATED_AT_DATE_TIME);
			}
			catch (DateTimeException dateTimeEx) {
				return null;
			}
		}
	}

	/**
	 * 신청기한 자유텍스트 1건을 LocalDate로 파싱 시도함. 이 메서드가 이 PoC의 핵심 검증 대상임(PLAN.md 3장 W4 절). 알려진 날짜
	 * 패턴이 없으면 예외를 던지지 않고 실패 사유를 붙여 반환함.
	 * @param rawText 신청기한 원문(null 또는 빈 문자열 가능)
	 * @return 파싱 결과(성공하면 deadline 포함, 실패하면 failureReason 포함)
	 */
	public DeadlineParseResult parseDeadline(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.UNRECOGNIZED_FORMAT);
		}
		if (rawText.contains("상시신청") || rawText.contains("상시 접수")) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.ALWAYS_OPEN);
		}
		if (rawText.contains("예산 소진")) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.BUDGET_EXHAUSTION);
		}
		if (rawText.contains("규정에 따름") || rawText.contains("규정에 의함")) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.EXTERNAL_REGULATION_REFERENCE);
		}
		DeadlineParseResult absolute = tryParseAbsoluteDate(rawText);
		if (absolute != null) {
			return absolute;
		}
		DeadlineParseResult range = tryParseDateRange(rawText);
		if (range != null) {
			return range;
		}
		return DeadlineParseResult.failure(rawText, DeadlineFailureReason.UNRECOGNIZED_FORMAT);
	}

	/**
	 * "2025년 8월 30일까지" 형식을 시도함. 매칭이 없으면 null을 반환해 다음 형식 시도로 넘김. 날짜 값이 유효하지 않으면(예 2월 30일)
	 * 예외를 던지지 않고 실패 결과로 감쌈.
	 */
	private DeadlineParseResult tryParseAbsoluteDate(String rawText) {
		Matcher matcher = ABSOLUTE_DATE.matcher(rawText);
		if (!matcher.find()) {
			return null;
		}
		try {
			int year = Integer.parseInt(matcher.group(1));
			int month = Integer.parseInt(matcher.group(2));
			int day = Integer.parseInt(matcher.group(3));
			return DeadlineParseResult.success(rawText, LocalDate.of(year, month, day));
		}
		catch (DateTimeException ex) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.UNRECOGNIZED_FORMAT);
		}
	}

	/**
	 * "5.1.~5.31." 형식을 시도함. 매칭이 없으면 null을 반환해 다음 형식 시도로 넘김. 텍스트에 여러 구간이 있어도(예 정기신청과 반기신청이
	 * 함께 있는 경우) 첫 번째 구간의 종료일만 취함.
	 */
	private DeadlineParseResult tryParseDateRange(String rawText) {
		Matcher matcher = DATE_RANGE.matcher(rawText);
		if (!matcher.find()) {
			return null;
		}
		try {
			int month = Integer.parseInt(matcher.group(3));
			int day = Integer.parseInt(matcher.group(4));
			int year = Year.now().getValue();
			return DeadlineParseResult.success(rawText, LocalDate.of(year, month, day));
		}
		catch (DateTimeException ex) {
			return DeadlineParseResult.failure(rawText, DeadlineFailureReason.UNRECOGNIZED_FORMAT);
		}
	}

	/**
	 * 신청기한 자유텍스트를 {@link DeadlineKind} 7분류로 판정함(후속 임무 1장). 기존
	 * {@link #parseDeadline(String)}의 성공/실패 이분법과 독립적으로 동작함(기존 메서드·테스트는 그대로 둠). 우선순위는 다음과
	 * 같음(스냅샷 n=1097 실측 근거는 각 순서의 코드 주석 참조).
	 * <ol>
	 * <li>실제 연도가 포함된 날짜 범위(DATE_RANGE) — 계산 가능한 실제 날짜가 있으면 키워드보다 우선함. 스냅샷 실측
	 * 51건(4.65%)</li>
	 * <li>"YYYY년 M월 D일까지" 단일 날짜(FIXED_DATE) — 스냅샷 실측 0건(신청기한 필드는 전부 범위형이거나 키워드형이었음. 다른
	 * 소스를 위해 분류 능력은 유지함)</li>
	 * <li>예산 소진(UNTIL_BUDGET_EXHAUSTED) — 11건(1.00%)</li>
	 * <li>상시접수(ALWAYS_OPEN) — 673건(61.35%)</li>
	 * <li>정기·주기(PERIODIC) — 79건(7.20%)</li>
	 * <li>별도 공고 참조(ANNOUNCEMENT_BASED) — 37건(3.37%)</li>
	 * <li>위 어디에도 안 걸림(UNKNOWN) — 246건(22.42%). 기존 UNRECOGNIZED_FORMAT(421건, 38.38%)에서
	 * 175건을 PERIODIC·ANNOUNCEMENT_BASED·DATE_RANGE·UNTIL_BUDGET_EXHAUSTED로 재분류해 건진 것임 —
	 * 이번 재분류의 실익(임무 지시 1장)</li>
	 * </ol>
	 * @param rawText 신청기한 원문(null 또는 빈 문자열 가능)
	 * @return 7분류 판정 결과
	 */
	public ParsedDeadline classifyDeadlineKind(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, rawText);
		}
		ParsedDeadline range = tryClassifyDateRange(rawText);
		if (range != null) {
			return range;
		}
		ParsedDeadline fixed = tryClassifyFixedDate(rawText);
		if (fixed != null) {
			return fixed;
		}
		if (containsAny(rawText, DEADLINE_KIND_BUDGET_KEYWORDS)) {
			return new ParsedDeadline(DeadlineKind.UNTIL_BUDGET_EXHAUSTED, null, null, rawText);
		}
		if (containsAny(rawText, DEADLINE_KIND_ALWAYS_OPEN_KEYWORDS)) {
			return new ParsedDeadline(DeadlineKind.ALWAYS_OPEN, null, null, rawText);
		}
		if (containsAny(rawText, DEADLINE_KIND_PERIODIC_KEYWORDS)) {
			return new ParsedDeadline(DeadlineKind.PERIODIC, null, null, rawText);
		}
		if (containsAny(rawText, DEADLINE_KIND_ANNOUNCEMENT_KEYWORDS)) {
			return new ParsedDeadline(DeadlineKind.ANNOUNCEMENT_BASED, null, null, rawText);
		}
		return new ParsedDeadline(DeadlineKind.UNKNOWN, null, null, rawText);
	}

	// "YYYY.MM.DD~YYYY.MM.DD" 계열(둘째 날짜 연도 생략 가능)을 시도함. 날짜 값이 유효하지 않으면(예 2월 30일)
	// null을 반환해 다음 판정 단계로 넘김(지어내지 않음).
	private ParsedDeadline tryClassifyDateRange(String rawText) {
		Matcher matcher = DEADLINE_DATE_RANGE_WITH_YEAR.matcher(rawText);
		if (!matcher.find()) {
			return null;
		}
		try {
			int year1 = Integer.parseInt(matcher.group(1));
			int month1 = Integer.parseInt(matcher.group(2));
			int day1 = Integer.parseInt(matcher.group(3));
			String year2Text = matcher.group(4);
			int year2 = year2Text == null ? year1 : Integer.parseInt(year2Text);
			int month2 = Integer.parseInt(matcher.group(5));
			int day2 = Integer.parseInt(matcher.group(6));
			LocalDate start = LocalDate.of(year1, month1, day1);
			LocalDate end = LocalDate.of(year2, month2, day2);
			return new ParsedDeadline(DeadlineKind.DATE_RANGE, start, end, rawText);
		}
		catch (DateTimeException ex) {
			return null;
		}
	}

	// "YYYY년 M월 D일까지" 단일 날짜를 시도함(기존 ABSOLUTE_DATE 정규식 재사용).
	private ParsedDeadline tryClassifyFixedDate(String rawText) {
		Matcher matcher = ABSOLUTE_DATE.matcher(rawText);
		if (!matcher.find()) {
			return null;
		}
		try {
			int year = Integer.parseInt(matcher.group(1));
			int month = Integer.parseInt(matcher.group(2));
			int day = Integer.parseInt(matcher.group(3));
			return new ParsedDeadline(DeadlineKind.FIXED_DATE, null, LocalDate.of(year, month, day), rawText);
		}
		catch (DateTimeException ex) {
			return null;
		}
	}

	/**
	 * 지원내용 원문에서 금액 표현을 뽑아 {@link AmountKind} 4분류로 판정함(후속 임무 2장, 2026-07-12 오분류 수정 임무로
	 * 유형A~E 보강, 같은 날 적대 검증으로 유형G(이용자 자부담금) 보강).
	 * <p>
	 * 설계 원칙 — 두 방향의 오류 중 <b>과소 배제(사업예산을 개인 지급액으로 오인)</b> 쪽을 더 무겁게 봄. 이유는 비대칭임: SINGLE 하나에
	 * 사업예산 8억원(430000000135)이 섞이면 예상총액이 실제(수십만원 단위)의 수백 배로 부풀지만, 반대로 한 건을 과잉 배제하면 그 건만
	 * "산정불가"(화면 231913 배지)로 정직하게 빠질 뿐 다른 값을 오염시키지 않음. 다만 과잉 배제도 추천 품질을 깎으므로,
	 * 배제(EXCLUDED)는 <b>실측으로 확인한 좁은 마커</b>에만 걸고 애매한 것(문턱 표현·조건 표현)은 배제가 아니라 CONDITIONAL로
	 * 내려 금액을 보존함. 즉 3단계임: 확실한 비지급액은 배제, 조건부 지급액은 CONDITIONAL(단순 합산 금지 신호), 명확한 단일 지급액만
	 * SINGLE(예상총액 자동 채움 대상).
	 * <p>
	 * 스냅샷(n=1097) 실측 분포는 전체 NONE 653건(59.53%)·SINGLE 124건(11.30%)·MULTIPLE
	 * 89건(8.11%)·CONDITIONAL 231건(21.06%)이고, 현금성(paymentType 원문 "현금" 433건) 기준으로는 NONE
	 * 161건(37.18%)·SINGLE 82건(18.94%)·MULTIPLE 45건(10.39%)·CONDITIONAL 145건(33.49%)임 —
	 * 현금성으로 좁혀도 금액이 아예 없는 경우(NONE)가 여전히 최빈이라, 예상총액 자동 채움의 실제 커버리지는 CASH 안에서도 SINGLE
	 * 18.94%만큼만 가장 안전함(MULTIPLE·CONDITIONAL은 단순 합산이 위험함).
	 * <p>
	 * 배제 사유는 <b>돈의 방향</b>으로 셋을 가름. 셋을 한 상태로 뭉치지 않는 이유는 팀이 엑셀에서 "왜 산정불가인가"를 바로 읽어야 하기
	 * 때문임(조용히 버리지 않음).
	 * <ul>
	 * <li><b>유형A 사업예산</b> — 정부가 <b>쓰는</b>
	 * 돈({@link AmountParseStatus#EXCLUDED_BUDGET_CONTEXT},
	 * {@link AmountParseStatus#PARSED_WITH_BUDGET_EXCLUSION})</li>
	 * <li><b>유형G 자부담금</b> — 이용자가 <b>내는</b>
	 * 돈({@link AmountParseStatus#EXCLUDED_SELF_PAY_CONTEXT},
	 * {@link AmountParseStatus#PARSED_WITH_SELF_PAY_EXCLUSION})</li>
	 * <li><b>유형H 대출·보증 한도</b> — <b>아무도 주지 않는</b> 돈(빌린 뒤 갚아야 할 채무 상한.
	 * {@link AmountParseStatus#EXCLUDED_LOAN_CONTEXT},
	 * {@link AmountParseStatus#PARSED_WITH_LOAN_EXCLUSION})</li>
	 * </ul>
	 * <p>
	 * <b>유형H가 특히 까다로운 이유 — 두 방향의 오류가 둘 다 실재함.</b> "대출"이라는 낱말이 있다고 배제하면 <b>진짜 현금 지원이
	 * 날아감</b>("대출이자 지원(최대 100만원)"의 100만원은 실제 수령액임). 반대로 낱말을 무시하면 채무 상한이 예상총액에 들어감 ("대출한도
	 * : 최대 1억원"). 그래서 낱말이 아니라 <b>금액과 낱말의 관계</b>를 봄 — 대출 어휘가 한도 어휘와 <b>붙어 명사구를 이루고</b>, 그
	 * 마커와 금액 <b>사이에 수혜 어휘나 퍼센트가 없을 때만</b> 배제함({@link #isLoanLimitAmount} 참조).
	 * <p>
	 * 배제로는 부족하고 <b>강등</b>이 맞는 경우도 셋 둠(금액을 지우지 않고 CONDITIONAL로만 내려 자동 합산에서 뺌). 유형I는 창업 기업의
	 * 사업화 자금(실제로 지급되지만 <b>개인 생활 지원금이 아니라 기업 사업비</b>), 유형J는 1억원 이상 고액(마커가 놓친 형태를 붙잡는 최후
	 * 그물), 그리고 단위 배분액("1인당 사업비"·"개소당 사업비")임({@link #isPerUnitBudgetAllocation}).
	 * <p>
	 * <b>단위 배분액은 배제가 아니라 강등이어야 함(2026-07-12 적대 검증 최우선 지시 — 진짜 개인 지급액을 배제하지 말 것).</b> 예산
	 * 어휘가 붙었다는 이유로 "1인당 사업비 12백만원"(20250901005400211556)·"개소당 사업비
	 * 30백만원"(20250109005400210105)까지 배제하면 <b>실제로 수혜자가 받는 금액</b>이 화면에서 통째로 사라짐(수정 전 두 건이
	 * 산정불가였음). 예산 어휘 <b>바로 앞자리</b>에 단위 수식어가 붙은 경우만 되살리고 CONDITIONAL로 내려, 금액은 보존하되 예상총액 자동
	 * 채움에는 넣지 않음.
	 * <p>
	 * <b>이 파서가 판정하지 못하는 축이 하나 남아 있음 — 수혜 단위(개인 대 기업).</b> SINGLE + CASH로 살아남은 금액 중에도 기업이
	 * 받는 돈이 있음(예 "지원금(평균 55백만원)" B55101400003, "해외진출자금 평균 3천만원" 20260416005400112760).
	 * 이들은 금액 표현만 보면 개인 지급액과 구별되지 않아 <b>금액 마커로 풀 수 없고</b>(무리하게 풀면 빈집재생 3천만원·농창업 1천만원 같은 진짜
	 * 개인 지급액을 함께 죽임), 지원대상 필드를 공통 타깃으로 승격해 수혜 단위를 봐야 함. 그때까지 예상총액 자동 합산을 출시하지 않는 것이 이번
	 * 회차의 결정이고, 그 게이트는 {@code EstimatedTotalGateTest}가 빌드에서 강제함.
	 * @param description 지원내용 원문(null 가능)
	 * @return 금액 파싱 결과
	 */
	public ParsedAmount parseAmount(String description) {
		return parseAmount(description, true);
	}

	/**
	 * 기업마당({@code BizinfoAmountParser}) 위임용임 — 대출·보증 한도(유형H)를 <b>배제하지 않고 후보로 남김</b>.
	 * <p>
	 * 개인 대상 소스와 계약을 다르게 두는 근거는 <b>수혜 단위와 합산 경로</b>임. 기업마당은 지급유형이 항상 {@code UNKNOWN}이라 개인
	 * 예상총액 합산 경로에 구조적으로 못 들어가고( {@code BizinfoSubsidyNormalizer} 참조), 기업 대상 화면에서는 융자한도
	 * 자체가 필요한 정보임. 그래서 그 소스는 금액을 남기고 호출부가 레코드 단위로 CONDITIONAL 강등만 함(BizinfoAmountParser의
	 * C2 규칙과 {@code BizinfoParserTest.parseAmount_loanLimit_isConditionalNotSingle}이 이
	 * 계약을 고정함). 반면 개인 대상 소스(보조금24·온통청년)는 지급유형이 CASH로 올라와 예상총액에 그대로 합산되므로, 채무 상한을 후보로 남기면
	 * 안 되어 배제함.
	 * @param description 지원내용 원문(null 가능)
	 * @return 금액 파싱 결과(대출 한도는 후보에 남음)
	 */
	public ParsedAmount parseAmountKeepingLoanLimits(String description) {
		return parseAmount(description, false);
	}

	private ParsedAmount parseAmount(String description, boolean excludeLoanLimits) {
		if (description == null || description.isBlank()) {
			return new ParsedAmount(AmountKind.NONE, List.of(), null, null, null, null, AmountParseStatus.NOT_FOUND);
		}
		Matcher tieredMatcher = TIERED_OMITTED_UNIT_ENUM.matcher(description);
		boolean hasTieredOmittedUnitEnum = tieredMatcher.find();
		Matcher matcher = AMOUNT_TOKEN.matcher(description);
		List<Long> candidates = new ArrayList<>();
		Set<Long> excludedBudgetValues = new HashSet<>();
		boolean anySelfPayExcluded = false;
		String selfPayExcerpt = null;
		boolean anyLoanExcluded = false;
		String loanExcerpt = null;
		boolean anyDemoted = false;
		String demotionExcerpt = null;
		boolean anyConditional = false;
		String conditionSummary = null;
		String unit = null;
		while (matcher.find()) {
			int start = matcher.start();
			int end = matcher.end();
			long value = Long.parseLong(matcher.group(1).replace(",", "")) * amountMultiplierFor(matcher.group(2));
			// 유형A 과잉 배제 차단 — "1인당 사업비 12백만원"처럼 단위 수식어가 예산 어휘에 바로 붙은 금액은 사업
			// 총예산이 아니라 수혜자 1단위 배분액이라 배제하지 않음(값 중복 배제도 건너뜀). 아래 강등 조건에서
			// CONDITIONAL로 내려 예상총액 자동 채움에서만 뺌.
			boolean unitAllocation = isPerUnitBudgetAllocation(description, start);
			if (!unitAllocation && (isBudgetContextAmount(description, start, end, value)
					|| excludedBudgetValues.contains(value))) {
				// 유형A — 사업 전체 예산·총사업비·대회 총상금·총규모 총액은 개인 지급액 후보에서 제외함.
				// 조용히 버리지 않고 excludedBudgetValues에 남겨 상태(parseStatus)로 드러냄. 같은 값의
				// 재언급도 같은 예산으로 보고 함께 배제함(154300000061 "1년차 48백만원 ..., 2년차 48백만원").
				if (!anyDemoted && isProgramScaleAmount(description, start, value)) {
					// "편성규모" 계열 총규모를 배제한 레코드는 남은 금액도 예산 항목일 공산이 커서 강등함.
					// 20251114005400211890("편성규모 : 총300백만원(행사운영비 210백만원, 민간경상사업보조
					// 90백만원)")에서 총액 2개를 빼면 남는 90백만원이 SINGLE로 **승격**되는데, 그것 역시 예산
					// 항목이지 지급액이 아님. 배제가 없던 SINGLE을 만드는 것을 막는 장치임.
					anyDemoted = true;
					demotionExcerpt = buildConditionSummary(description, start, end);
				}
				excludedBudgetValues.add(value);
				continue;
			}
			if (excludeLoanLimits && isLoanLimitAmount(description, start, end)) {
				// 유형H — 대출·보증 한도는 받는 돈이 아니라 빌릴 수 있는 상한(갚아야 할 채무)이라 후보에서 제외함.
				// 자부담과 마찬가지로 **값 중복 배제는 하지 않음** — 같은 값의 진짜 지급액이 나란히 적히는 경우가
				// 정상이기 때문임(20260318005400212197 "대출잔액이 2억원인 경우, 2%가 300만원을 초과하므로
				// 300만원 지원" — 배제 대상은 2억원뿐이고 300만원은 살아야 함).
				anyLoanExcluded = true;
				if (loanExcerpt == null) {
					loanExcerpt = buildConditionSummary(description, start, end);
				}
				continue;
			}
			if (isSelfPayAmount(description, start)) {
				// 유형G — 이용자가 내는 자부담금은 지급액이 아니므로 후보에서 제외함. **값 중복 배제
				// (excludedBudgetValues)에는 넣지 않음** — 예산 재언급과 달리 자부담은 같은 값의 진짜 보조금과
				// 나란히 적히는 게 정상이기 때문임. 483000000113 "지원단가 : 20,000천원 /개소(보조
				// 10,000천원, 자부담 10,000천원)"에서 자부담 10,000천원을 값으로 배제하면 뒤따르는 **진짜**
				// 보조 10,000천원과 주택수리비 지원단가 10,000천원까지 죽는 과잉 배제가 남(전수 실측).
				anySelfPayExcluded = true;
				if (selfPayExcerpt == null) {
					selfPayExcerpt = buildConditionSummary(description, start, end);
				}
				continue;
			}
			candidates.add(value);
			if (unit == null) {
				unit = detectAmountUnit(description, start);
			}
			// 유형I·J와 단위 배분액 — 살아남았지만 개인 지급액으로 단순 합산하면 안 되는 금액을 CONDITIONAL로 강등함
			// (배제 아님 — 금액은 보존). 기업 단위 사업화 자금이거나, 개인 지급액 자릿수를 벗어난 고액이거나,
			// 사업비를 수혜자 1단위로 나눈 배분액임.
			if (!anyDemoted && (unitAllocation || isBusinessFundAmount(description, start, end)
					|| value >= PROGRAM_SCALE_THRESHOLD)) {
				anyDemoted = true;
				demotionExcerpt = buildConditionSummary(description, start, end);
			}
			if (!anyConditional && (hasAmountConditionMarkerNearby(description, start)
					|| hasPerUnitConditionMarkerNearby(description, start, end)
					|| hasThresholdMarkerAfter(description, end))) {
				anyConditional = true;
				conditionSummary = buildConditionSummary(description, start, end);
			}
		}
		boolean anyExcludedAsBudget = !excludedBudgetValues.isEmpty();
		if (candidates.isEmpty()) {
			// 배제 사유가 겹치면 예산·대출·자부담 순으로 씀(스냅샷 실측 겹침 0건 — 규칙상 정해두는 우선순위임).
			// 예산 오염이 자릿수가 커서 팀이 먼저 확인해야 할 대상이고, 대출 한도가 그 다음으로 큼.
			AmountParseStatus status;
			if (anyExcludedAsBudget) {
				status = AmountParseStatus.EXCLUDED_BUDGET_CONTEXT;
			}
			else if (anyLoanExcluded) {
				status = AmountParseStatus.EXCLUDED_LOAN_CONTEXT;
			}
			else if (anySelfPayExcluded) {
				status = AmountParseStatus.EXCLUDED_SELF_PAY_CONTEXT;
			}
			else {
				status = AmountParseStatus.NOT_FOUND;
			}
			return new ParsedAmount(AmountKind.NONE, List.of(), null, null, null, null, status);
		}
		// 자부담이 있으면 남은 금액을 SINGLE(예상총액 자동 채움 대상)로 두지 않고 CONDITIONAL로 강등함. 자부담
		// 구조에서는 남은 금액이 순 지급액이 아니라 **총 기준단가**인 경우가 많기 때문임(650000001099 "22만원
		// 기준 자부담 2만원" — 남는 22만원은 검진 총비용이지 수령액 20만원이 아님. 483000000113의 지원단가
		// 20,000천원도 보조 10,000천원 더하기 자부담 10,000천원의 합계임). 배제가 조건 마커를 함께 걷어내
		// CONDITIONAL이 SINGLE로 **승격**되는 부작용(650000001099의 "기준" 마커가 자부담 금액 쪽에 붙어 있었음)도
		// 이 규칙으로 함께 막힘. 금액 자체는 보존하므로 과잉 배제가 아님(추천에서 떨어지지 않음).
		// 유형H도 자부담과 같은 이유로 남은 금액을 강등함 — 배제가 조건 마커를 함께 걷어내 남은 금액이 SINGLE로
		// 승격되면 없던 예상총액 항목이 새로 생김(451000000242 "대출이자 지원(대출한도 5천만 원) ... 연 최대
		// 1.5백만 원 범위 내" — 5천만원을 빼면 뒤 금액이 MULTIPLE에서 SINGLE로 올라섬).
		boolean conditional = anyConditional || hasTieredOmittedUnitEnum || anySelfPayExcluded || anyLoanExcluded
				|| anyDemoted;
		AmountKind kind;
		if (conditional) {
			kind = AmountKind.CONDITIONAL;
		}
		else if (candidates.size() >= 2) {
			kind = AmountKind.MULTIPLE;
		}
		else {
			kind = AmountKind.SINGLE;
		}
		long min = Collections.min(candidates);
		long max = Collections.max(candidates);
		String resolvedUnit = unit == null ? DEFAULT_AMOUNT_UNIT : unit;
		String summary = kind == AmountKind.CONDITIONAL ? resolveConditionSummary(description, tieredMatcher,
				hasTieredOmittedUnitEnum, conditionSummary, selfPayExcerpt, loanExcerpt, demotionExcerpt) : null;
		AmountParseStatus status;
		if (anyExcludedAsBudget) {
			status = AmountParseStatus.PARSED_WITH_BUDGET_EXCLUSION;
		}
		else if (anyLoanExcluded) {
			status = AmountParseStatus.PARSED_WITH_LOAN_EXCLUSION;
		}
		else if (anySelfPayExcluded) {
			status = AmountParseStatus.PARSED_WITH_SELF_PAY_EXCLUSION;
		}
		else {
			status = AmountParseStatus.PARSED;
		}
		return new ParsedAmount(kind, List.copyOf(candidates), min, max, resolvedUnit, summary, status);
	}

	// CONDITIONAL 판정 사유가 여럿일 수 있으므로 발췌 근거를 우선순위로 고름. 개별 후보 근처 조건 마커(유형B·E)가 가장
	// 구체적이라 먼저 쓰고, 그게 없으면 다단 차등 나열(유형C) 구간, 자부담(유형G)·대출 한도(유형H) 배제 구간,
	// 사업화·고액 강등(유형I·J) 구간 순으로 씀. kind가 CONDITIONAL이면 이 다섯 중 하나는 반드시 있음.
	private static String resolveConditionSummary(String description, Matcher tieredMatcher,
			boolean hasTieredOmittedUnitEnum, String conditionSummary, String selfPayExcerpt, String loanExcerpt,
			String demotionExcerpt) {
		if (conditionSummary != null) {
			return conditionSummary;
		}
		if (hasTieredOmittedUnitEnum) {
			return buildConditionSummary(description, tieredMatcher.start(), tieredMatcher.end());
		}
		if (selfPayExcerpt != null) {
			return selfPayExcerpt;
		}
		return loanExcerpt != null ? loanExcerpt : demotionExcerpt;
	}

	private static long amountMultiplierFor(String unitChar) {
		if (unitChar == null) {
			return 1L;
		}
		return switch (unitChar) {
			case "천" -> 1_000L;
			case "만" -> 10_000L;
			case "백만" -> 1_000_000L;
			case "천만" -> 10_000_000L;
			case "억" -> 100_000_000L;
			default -> 1L;
		};
	}

	// 금액 숫자 바로 앞 AMOUNT_CONDITION_CONTEXT_WINDOW자 안에 조건 표현이 있는지 봄. "시"는 별도 정규식으로
	// 오탐(시간·시설 등)을 거름(AMOUNT_CONDITION_SI_MARKER Javadoc 참조).
	private static boolean hasAmountConditionMarkerNearby(String text, int matchStart) {
		int windowStart = Math.max(0, matchStart - AMOUNT_CONDITION_CONTEXT_WINDOW);
		String window = text.substring(windowStart, matchStart);
		if (containsAny(window, AMOUNT_CONDITION_SIMPLE_MARKERS)) {
			return true;
		}
		Matcher siMatcher = AMOUNT_CONDITION_SI_MARKER.matcher(text);
		return siMatcher.find(windowStart) && siMatcher.start() < matchStart;
	}

	// 조건부로 판정된 금액 앞뒤 문맥을 200자 이내로 발췌함(임무 지시 2장 conditionSummary 스펙).
	private static String buildConditionSummary(String text, int matchStart, int matchEnd) {
		int excerptStart = Math.max(0, matchStart - 40);
		int excerptEnd = Math.min(text.length(), matchEnd + 40);
		String excerpt = text.substring(excerptStart, excerptEnd).replace("\r\n", " ").replace("\n", " ").trim();
		return excerpt.length() > 200 ? excerpt.substring(0, 200) : excerpt;
	}

	// 이 금액이 개인 지급액이 아니라 사업 총예산·총사업비·총상금·총규모 총액인지 판정함(오분류 수정 임무 유형A).
	// 세 갈래로 봄.
	// (1) 금액 앞 BUDGET_CONTEXT_WINDOW자 안의 예산 어휘. 공백을 지우고 대조해 "예 산 액"·"사 업 비" 같은
	// 자간 띄우기 표기까지 잡음. 어휘 마커는 앞쪽만 봄 — "20만원 지원 ※ 예산 조기 소진에 따른 마감
	// 가능"(321000000110)·"1인당 수술 5백만원 이내 ... (예산범위 내 지원)"(626000000112)처럼 금액 뒤에
	// 오는 "예산"은 마감·재원 안내이지 그 금액이 사업예산이라는 뜻이 아님(전수 재검사로 확인함).
	// (2) 금액 앞의 "총 N명·N가구·N세대·N개소" 총규모 표기(O00081200001).
	// (3) 금액 뒤의 "/콤마 있는 수량" 나눗셈 표기(430000000135).
	private static boolean isBudgetContextAmount(String text, int matchStart, int matchEnd, long value) {
		int windowStart = Math.max(0, matchStart - BUDGET_CONTEXT_WINDOW);
		String window = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		if (containsAny(window, BUDGET_CONTEXT_MARKERS)) {
			return true;
		}
		int scaleWindowStart = Math.max(0, matchStart - PER_UNIT_MARKER_BACKWARD_WINDOW);
		if (TOTAL_SCALE_HEADCOUNT.matcher(text.substring(scaleWindowStart, matchStart)).find()) {
			return true;
		}
		// (4) "지원규모·사업규모·편성규모" 더하기 1억원 이상 — 사업 편성 예산임(기업마당 B3 규칙 이식).
		if (isProgramScaleAmount(text, matchStart, value)) {
			return true;
		}
		return BULK_DIVISOR.matcher(text.substring(matchEnd)).lookingAt();
	}

	/**
	 * 이 금액이 사업 총예산이 아니라 <b>수혜자 1단위에 배분되는 지급액</b>인지 봄(유형A 과잉 배제 차단 장치, 2026-07-12 적대 검증의
	 * 최우선 지시 "진짜 개인 지급액을 배제하지 말 것"). 예산 어휘 <b>바로 앞자리</b>에 단위 수식어가 붙어 하나의 명사구를 이룰 때만("1인당
	 * 사업비"·"개소당 사업비") 참임 — 배제 규칙과 정확히 반대 방향의 판정이라 창을 넓히지 않고 붙어 있는지만 봄.
	 * <p>
	 * 참이면 배제(EXCLUDED)를 건너뛰고 대신 CONDITIONAL로 <b>강등</b>함. 단위 배분액은 실제로 지급되지만 수혜 단위가
	 * "1인"·"1개소"라 개인 예상총액에 그대로 합산할 수 없기 때문임(유형I 사업화 자금과 같은 처방).
	 * <p>
	 * 전수 스캔(4종 6,840건)에서 이 융합 형태가 걸리는 금액은 2개뿐이고 둘 다 진짜 단위 배분액임 —
	 * 20250901005400211556(경북청년 예비창업가 육성 "지원내용 : 1인당 사업비 12백만원"),
	 * 20250109005400210105(영세창업농 초기영농비 "개소당 사업비 30백만원"). 두 건 다 수정 전에는 금액이 통째로 사라져 산정불가였음.
	 * 반대로 진짜 사업예산에는 이 융합이 하나도 없음 — 155000000018("예산 158억 원 내외, 건당 평균 8천만 원")의 "건당"은
	 * <b>뒤따르는 금액</b>에 붙은 마커라 앞의 158억원은 그대로 배제되고, 20260527005400113224("1인당 年 3회 지원, 단 예산
	 * 242억 원")는 수식어와 예산 어휘 사이에 다른 문구가 끼어 융합이 아님. 두 집합이 구조적으로 갈리는 지점임.
	 */
	private static boolean isPerUnitBudgetAllocation(String text, int matchStart) {
		int windowStart = Math.max(0, matchStart - BUDGET_CONTEXT_WINDOW);
		String window = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		for (String budgetMarker : BUDGET_CONTEXT_MARKERS) {
			int markerStart = window.lastIndexOf(budgetMarker);
			if (markerStart < 0) {
				continue;
			}
			String beforeMarker = window.substring(0, markerStart);
			for (String unitMarker : PER_UNIT_CONDITION_MARKERS) {
				if (beforeMarker.endsWith(unitMarker)) {
					return true;
				}
			}
		}
		return false;
	}

	// "규모"라는 낱말만으로는 개인·기업 단위 상한에도 쓰이므로(기업마당 실측 "지원규모 : 최대 45,000천원"은 진짜 기업
	// 상한임) 반드시 1억원 경계와 **함께** 봄. 두 조건을 다 만족하는 금액은 전수에서 사업 편성 예산 4건뿐임.
	private static boolean isProgramScaleAmount(String text, int matchStart, long value) {
		if (value < PROGRAM_SCALE_THRESHOLD) {
			return false;
		}
		int windowStart = Math.max(0, matchStart - PROGRAM_SCALE_BACKWARD_WINDOW);
		String window = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		return window.contains(PROGRAM_SCALE_MARKER);
	}

	/**
	 * 이 금액이 지급액이 아니라 대출·융자·보증 <b>한도</b>인지 판정함(오분류 수정 임무 유형H). 두 갈래로 봄.
	 * <ol>
	 * <li><b>역방향 융합 마커</b> — 금액 앞 {@value #LOAN_LIMIT_BACKWARD_WINDOW}자(공백 제거)에 대출 어휘와 한도
	 * 어휘가 <b>붙어 있는</b> 명사구(대출한도·융자한도·보증한도·대부한도·대출잔액·보증금액)가 있고, 그 마커와 금액 <b>사이</b>에 수혜 어휘나
	 * 퍼센트가 없을 때. 사이를 보는 것이 이 규칙의 전부임 — "대출잔액의 <b>이자 지원</b>(최대 150만 원)"과 "대출잔액의 <b>2%</b>,
	 * 가구당 최대 300만원"의 금액은 대출에서 파생된 <b>진짜 지급액</b>이라 살려야 하고, "대출한도 : 최대 1억원"의 금액만 죽여야
	 * 함.</li>
	 * <li><b>순방향 마커</b> — 금액 바로 뒤에 대출 어휘가 붙는 형태("최대 5억원 융자지원해주고"). 대출 어휘 뒤에 이자·금리·이차·보증료가
	 * 오면 그건 <b>대출이자 지원</b>이라 배제하지 않음(부정 선읽기).</li>
	 * </ol>
	 * 낱말이 아니라 <b>금액과 낱말의 관계</b>를 보는 것이라, "대출"이 들어간 레코드를 통째로 배제하는 방식과 다름(그렇게 하면 대출이자
	 * 지원금·보증료 지원금 같은 진짜 현금 지원이 전부 날아감).
	 */
	private static boolean isLoanLimitAmount(String text, int matchStart, int matchEnd) {
		int windowStart = Math.max(0, matchStart - LOAN_LIMIT_BACKWARD_WINDOW);
		String window = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		Matcher markerMatcher = LOAN_LIMIT_MARKER.matcher(window);
		int markerEnd = -1;
		while (markerMatcher.find()) {
			// 창 안에 마커가 여럿이면 금액에 가장 가까운 것을 기준으로 gap을 봄.
			markerEnd = markerMatcher.end();
		}
		if (markerEnd >= 0 && !containsAny(window.substring(markerEnd), LOAN_LIMIT_GAP_BLOCKERS)) {
			return true;
		}
		int forwardEnd = Math.min(text.length(), matchEnd + LOAN_FORWARD_WINDOW);
		return LOAN_FORWARD_MARKER.matcher(text.substring(matchEnd, forwardEnd)).find();
	}

	// 이 금액이 개인 생활 지원금이 아니라 창업 기업의 사업화 자금인지 봄(오분류 수정 임무 유형I). 배제가 아니라 강등
	// 근거임 — 돈은 실제로 지급되지만 수혜 단위가 기업이고 자부담 매칭·사업비 정산이 붙어 개인 예상총액에 합산할 수 없음.
	private static boolean isBusinessFundAmount(String text, int matchStart, int matchEnd) {
		int windowStart = Math.max(0, matchStart - BUSINESS_FUND_BACKWARD_WINDOW);
		String backwardWindow = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		if (containsAny(backwardWindow, BUSINESS_FUND_MARKERS)) {
			return true;
		}
		int forwardEnd = Math.min(text.length(), matchEnd + BUSINESS_FUND_FORWARD_WINDOW);
		String forwardWindow = WHITESPACE.matcher(text.substring(matchEnd, forwardEnd)).replaceAll("");
		return containsAny(forwardWindow, BUSINESS_FUND_MARKERS);
	}

	// 이 금액이 지급액이 아니라 이용자가 내는 자부담금인지 판정함(오분류 수정 임무 유형G). 금액 앞
	// SELF_PAY_CONTEXT_WINDOW자만 봄 — 뒤쪽까지 보면 "4만원 지원, 자부담 5천원"(392000000164)에서 **진짜
	// 지급액** 4만원이 뒤의 "자부담"에 걸려 배제됨(과잉 배제). 예산 마커와 마찬가지로 공백을 지우고 대조함.
	private static boolean isSelfPayAmount(String text, int matchStart) {
		int windowStart = Math.max(0, matchStart - SELF_PAY_CONTEXT_WINDOW);
		String window = WHITESPACE.matcher(text.substring(windowStart, matchStart)).replaceAll("");
		return containsAny(window, SELF_PAY_CONTEXT_MARKERS);
	}

	// 금액 바로 뒤 AMOUNT_THRESHOLD_FORWARD_WINDOW자 안에 문턱 표현("이하"·"미만"·"이상"·"초과")이 있는지
	// 봄(오분류 수정 임무 유형E). 자격 기준선(건강보험료 N원 이하, 승소가액 N원 이상 제외 등)을 개인 지급액
	// SINGLE로 오인하는 것을 막음.
	private static boolean hasThresholdMarkerAfter(String text, int matchEnd) {
		int windowEnd = Math.min(text.length(), matchEnd + AMOUNT_THRESHOLD_FORWARD_WINDOW);
		return containsAny(text.substring(matchEnd, windowEnd), AMOUNT_THRESHOLD_FORWARD_MARKERS);
	}

	// 금액 숫자 앞뒤로 PER_UNIT_CONDITION_MARKERS 화이트리스트 단어가 있는지 봄(오분류 수정 임무 유형B).
	// AMOUNT_CONDITION_CONTEXT_WINDOW(10자) 안의 bare "당"·"별" 등과 별개로, 더 멀리 있거나(예 "농가당 1대
	// 지원(최대 240만원 보조)") 금액 뒤에 오는(예 "200천원(1인당)") "당" 계열 조건 표현을 보강함.
	private static boolean hasPerUnitConditionMarkerNearby(String text, int matchStart, int matchEnd) {
		int backwardStart = Math.max(0, matchStart - PER_UNIT_MARKER_BACKWARD_WINDOW);
		String backwardWindow = text.substring(backwardStart, matchStart);
		if (containsAny(backwardWindow, PER_UNIT_CONDITION_MARKERS)) {
			return true;
		}
		int forwardEnd = Math.min(text.length(), matchEnd + PER_UNIT_MARKER_FORWARD_WINDOW);
		String forwardWindow = text.substring(matchEnd, forwardEnd);
		return containsAny(forwardWindow, PER_UNIT_CONDITION_MARKERS);
	}

	// 금액 숫자 앞 문맥에서 단위(가구·인·회·월)를 관찰함. 아무 단서도 없으면 호출부에서 기본값 "원"으로 채움.
	private static String detectAmountUnit(String text, int matchStart) {
		int windowStart = Math.max(0, matchStart - AMOUNT_CONDITION_CONTEXT_WINDOW);
		String window = text.substring(windowStart, matchStart);
		if (containsAny(window, AMOUNT_UNIT_HOUSEHOLD_KEYWORDS)) {
			return "가구";
		}
		if (containsAny(window, AMOUNT_UNIT_PERSON_KEYWORDS)) {
			return "인";
		}
		if (containsAny(window, AMOUNT_UNIT_COUNT_KEYWORDS)) {
			return "회";
		}
		if (AMOUNT_UNIT_MONTH_MARKER.matcher(window).find()) {
			return "월";
		}
		// 오분류 수정 임무 유형B 보강(amountUnit 오추출 점검, 임무 지시 4장) — 좁은 창(10자)에서 "인당"·"명당"을
		// 못 찾으면 PER_UNIT_MARKER_BACKWARD_WINDOW(20자)까지 넓혀 다시 봄. "1인당 안경(렌즈 등) 구입비
		// 최대 10만원"(650000001140, 17자)처럼 hasPerUnitConditionMarkerNearby가 넓은 창으로
		// CONDITIONAL로
		// 분류하는 레코드가 amountUnit만 기본값 "원"으로 남는 불일치를 막기 위함(두 판정의 근거 창을 맞춤).
		int wideWindowStart = Math.max(0, matchStart - PER_UNIT_MARKER_BACKWARD_WINDOW);
		String wideWindow = text.substring(wideWindowStart, matchStart);
		if (containsAny(wideWindow, AMOUNT_UNIT_PERSON_KEYWORDS)) {
			return "인";
		}
		return null;
	}

	/**
	 * 소관기관명에서 지역을 유추함(후속 임무 3장). 첫 토큰이 {@link #SIDO_NAMES}에 있으면 시도로 보고, 남은 토큰이 있으면 시군구까지
	 * 판정함. 오케스트레이터 실측(시도+시군구 58.8%/645건, 시도만 12.8%/140건, 중앙부처 등 28.4%/312건)과 스냅샷 n=1097
	 * 기준으로 정확히 일치함(SIGUNGU 645건, SIDO 140건, NATIONAL 312건). 자치법규 필드 교차검증(시도 또는 시군구명이 자치법규
	 * 원문에 포함되는지) 일치율은 94.32%(465/493건)임.
	 * @param agencyName 소관기관명 원문(null 가능)
	 * @return 지역 유추 결과
	 */
	public ParsedRegion parseRegion(String agencyName) {
		if (agencyName == null || agencyName.isBlank()) {
			return new ParsedRegion(null, null, RegionLevel.NATIONAL, RegionScopeBasis.NOT_APPLICABLE,
					RegionConfidence.LOW);
		}
		String[] parts = agencyName.split(" ", 2);
		String first = parts[0];
		if (SIDO_NAMES.contains(first)) {
			if (parts.length > 1 && !parts[1].isBlank()) {
				return new ParsedRegion(first, parts[1].trim(), RegionLevel.SIGUNGU,
						RegionScopeBasis.INFERRED_FROM_AGENCY_NAME, RegionConfidence.MEDIUM);
			}
			return new ParsedRegion(first, null, RegionLevel.SIDO, RegionScopeBasis.INFERRED_FROM_AGENCY_NAME,
					RegionConfidence.LOW);
		}
		return new ParsedRegion(null, null, RegionLevel.NATIONAL, RegionScopeBasis.NOT_APPLICABLE,
				RegionConfidence.LOW);
	}

	/**
	 * 소득 조건 신호의 출처를 판정함(후속 임무 4장). JA 플래그(제한없음·제한형)와 원문 "중위소득 N%" 언급 유무를 함께 봄. 스냅샷 실측
	 * JA_FLAGS 1,033건·BOTH 63건·TEXT 1건(B55307700005 소상공인 무료법률구조 — JA 플래그는 전부 null인데 원문은
	 * "중위소득 125% 이하"를 언급함, SNAPSHOT_META.md 기록 사례).
	 * @param eligibilityText 지원대상+선정기준 결합 텍스트(null 가능)
	 * @param condition supportConditions 매칭 결과(null 가능 — 매칭 없으면 JA 신호 없음으로 봄)
	 * @return 소득 신호 출처
	 */
	public IncomeSignalSource computeIncomeSignalSource(String eligibilityText, Gov24SupportConditionDto condition) {
		boolean hasJaSignal = condition != null && condition.incomeSignal() != EligibilitySignal.UNKNOWN;
		boolean hasTextEvidence = maxMedianIncomePercent(eligibilityText) != null;
		if (hasJaSignal && hasTextEvidence) {
			return IncomeSignalSource.BOTH;
		}
		if (hasJaSignal) {
			return IncomeSignalSource.JA_FLAGS;
		}
		if (hasTextEvidence) {
			return IncomeSignalSource.TEXT;
		}
		// 둘 다 없어도 incomeSignal 필드 자체는 항상 JA_FLAGS 구조(UNKNOWN 포함)에서 파생되므로 기본값으로 둠
		return IncomeSignalSource.JA_FLAGS;
	}

	/**
	 * JA 소득 플래그와 원문("중위소득 N%" 언급)의 일치 여부를 판정함(후속 임무 4장 — 기존 감사 테스트를 필드로 승격). 원문 언급이 없으면
	 * 대조 자체가 불가능해 NO_TEXT_EVIDENCE임. 스냅샷 실측 CONSISTENT 50건·CONFLICT 14건·NO_TEXT_EVIDENCE
	 * 1,033건(합 64건 = 기존 Gov24JaFieldParserTest 감사 결과와 정합).
	 * @param eligibilityText 지원대상+선정기준 결합 텍스트(null 가능)
	 * @param condition supportConditions 매칭 결과(null 가능)
	 * @return 일치 여부
	 */
	public IncomeConsistencyStatus computeIncomeConsistencyStatus(String eligibilityText,
			Gov24SupportConditionDto condition) {
		Integer maxPercent = maxMedianIncomePercent(eligibilityText);
		if (maxPercent == null) {
			return IncomeConsistencyStatus.NO_TEXT_EVIDENCE;
		}
		List<String> expected = expectedIncomeFieldsFor(maxPercent);
		List<String> actual = actualIncomeYFields(condition);
		return actual.containsAll(expected) ? IncomeConsistencyStatus.CONSISTENT : IncomeConsistencyStatus.CONFLICT;
	}

	/**
	 * 원문에서 "중위소득 N%" 언급 근거 문구를 뽑음(앞뒤 20자 발췌). 언급이 여러 개면 가장 먼저 나온 것을 씀.
	 * @param eligibilityText 지원대상+선정기준 결합 텍스트(null 가능)
	 * @return 근거 문구(언급 없으면 null)
	 */
	public String extractIncomeTextEvidence(String eligibilityText) {
		if (eligibilityText == null) {
			return null;
		}
		Matcher matcher = MEDIAN_INCOME_PERCENT.matcher(eligibilityText);
		if (!matcher.find()) {
			return null;
		}
		int start = Math.max(0, matcher.start() - 20);
		int end = Math.min(eligibilityText.length(), matcher.end() + 20);
		return eligibilityText.substring(start, end).replace("\r\n", " ").replace("\n", " ").trim();
	}

	// 원문에서 "중위소득 N%" 언급 중 가장 큰 N을 찾음(구간이 여러 번 언급되면 상한 기준으로 봄). 언급이 없으면 null.
	private static Integer maxMedianIncomePercent(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = MEDIAN_INCOME_PERCENT.matcher(text);
		Integer max = null;
		while (matcher.find()) {
			int value = Integer.parseInt(matcher.group(1));
			if (max == null || value > max) {
				max = value;
			}
		}
		return max;
	}

	// 원문의 중위소득 N% 상한을 swagger 구간 정의에 매핑해, 그 상한을 커버하려면 최소한 Y여야 하는 JA 구간 목록을 반환함
	// (Gov24JaFieldParserTest.expectedIncomeFieldsFor와 동일 로직 — 테스트 전용 private 구현과 별개로
	// 프로덕션에
	// 이식함).
	private static List<String> expectedIncomeFieldsFor(int maxPercent) {
		if (maxPercent <= 50) {
			return List.of("JA0201");
		}
		if (maxPercent <= 75) {
			return List.of("JA0201", "JA0202");
		}
		if (maxPercent <= 100) {
			return List.of("JA0201", "JA0202", "JA0203");
		}
		if (maxPercent <= 200) {
			return List.of("JA0201", "JA0202", "JA0203", "JA0204");
		}
		return List.of("JA0201", "JA0202", "JA0203", "JA0204", "JA0205");
	}

	private static List<String> actualIncomeYFields(Gov24SupportConditionDto condition) {
		if (condition == null) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		if ("Y".equals(condition.income0To50())) {
			result.add("JA0201");
		}
		if ("Y".equals(condition.income51To75())) {
			result.add("JA0202");
		}
		if ("Y".equals(condition.income76To100())) {
			result.add("JA0203");
		}
		if ("Y".equals(condition.income101To200())) {
			result.add("JA0204");
		}
		if ("Y".equals(condition.incomeOver200())) {
			result.add("JA0205");
		}
		return result;
	}

}
