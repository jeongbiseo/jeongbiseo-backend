package com.jeongbiseo.domain.region;

import java.util.List;

/**
 * 거주지 시/도와 시군구 목록의 고정 참조 데이터임(getRegions 응답과 온보딩 제출 시 regionCode 파생에 함께 씀).
 *
 * <p>
 * 행정안전부 「행정기관(행정동) 및 관할구역(법정동) 변경내역(2026.7.20. 시행)」의
 * {@code jscode20260720.zip/KIKcd_B.20260720}을 2026-07-23 내려받아, 유효 법정동코드 중 시군구 단위의 앞 5자리를
 * 반영함. 주민 거주지 선택 대상이 아닌 출장 코드 4건은 제외하고, 하위 시군구가 없는 세종특별자치시는 온통청년 {@code zipCd}와 같은 자체 코드
 * 36110을 둠.
 *
 * @see <a href=
 * "https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do?bbsId=BBSMSTR_000000000052&nttId=127979">행정안전부
 * 법정동코드 자료</a>
 */
// ponytail: 데모 일정과 변경 빈도를 고려해 DB·시더·런타임 파일 로더 대신 공식 기준일의 불변 목록을 코드에 고정함.
public final class RegionCatalog {

	private static final List<Sido> SIDOS = List.of(
			new Sido("서울특별시",
					List.of(new Sigungu("11110", "종로구"), new Sigungu("11140", "중구"), new Sigungu("11170", "용산구"),
							new Sigungu("11200", "성동구"), new Sigungu("11215", "광진구"), new Sigungu("11230", "동대문구"),
							new Sigungu("11260", "중랑구"), new Sigungu("11290", "성북구"), new Sigungu("11305", "강북구"),
							new Sigungu("11320", "도봉구"), new Sigungu("11350", "노원구"), new Sigungu("11380", "은평구"),
							new Sigungu("11410", "서대문구"), new Sigungu("11440", "마포구"), new Sigungu("11470", "양천구"),
							new Sigungu("11500", "강서구"), new Sigungu("11530", "구로구"), new Sigungu("11545", "금천구"),
							new Sigungu("11560", "영등포구"), new Sigungu("11590", "동작구"), new Sigungu("11620", "관악구"),
							new Sigungu("11650", "서초구"), new Sigungu("11680", "강남구"), new Sigungu("11710", "송파구"),
							new Sigungu("11740", "강동구"))),
			new Sido("전남광주통합특별시",
					List.of(new Sigungu("12110", "목포시"), new Sigungu("12130", "여수시"), new Sigungu("12150", "순천시"),
							new Sigungu("12170", "나주시"), new Sigungu("12190", "광양시"), new Sigungu("12210", "동구"),
							new Sigungu("12240", "서구"), new Sigungu("12270", "남구"), new Sigungu("12300", "북구"),
							new Sigungu("12330", "광산구"), new Sigungu("12710", "담양군"), new Sigungu("12720", "곡성군"),
							new Sigungu("12730", "구례군"), new Sigungu("12740", "고흥군"), new Sigungu("12750", "보성군"),
							new Sigungu("12760", "화순군"), new Sigungu("12770", "장흥군"), new Sigungu("12780", "강진군"),
							new Sigungu("12790", "해남군"), new Sigungu("12800", "영암군"), new Sigungu("12810", "무안군"),
							new Sigungu("12820", "함평군"), new Sigungu("12830", "영광군"), new Sigungu("12840", "장성군"),
							new Sigungu("12850", "완도군"), new Sigungu("12860", "진도군"), new Sigungu("12870", "신안군"))),
			new Sido("부산광역시",
					List.of(new Sigungu("26110", "중구"), new Sigungu("26140", "서구"), new Sigungu("26170", "동구"),
							new Sigungu("26200", "영도구"), new Sigungu("26230", "부산진구"), new Sigungu("26260", "동래구"),
							new Sigungu("26290", "남구"), new Sigungu("26320", "북구"), new Sigungu("26350", "해운대구"),
							new Sigungu("26380", "사하구"), new Sigungu("26410", "금정구"), new Sigungu("26440", "강서구"),
							new Sigungu("26470", "연제구"), new Sigungu("26500", "수영구"), new Sigungu("26530", "사상구"),
							new Sigungu("26710", "기장군"))),
			new Sido("대구광역시",
					List.of(new Sigungu("27110", "중구"), new Sigungu("27140", "동구"), new Sigungu("27170", "서구"),
							new Sigungu("27200", "남구"), new Sigungu("27230", "북구"), new Sigungu("27260", "수성구"),
							new Sigungu("27290", "달서구"), new Sigungu("27710", "달성군"), new Sigungu("27720", "군위군"))),
			new Sido("인천광역시",
					List.of(new Sigungu("28125", "제물포구"), new Sigungu("28155", "영종구"), new Sigungu("28177", "미추홀구"),
							new Sigungu("28185", "연수구"), new Sigungu("28200", "남동구"), new Sigungu("28237", "부평구"),
							new Sigungu("28245", "계양구"), new Sigungu("28275", "서해구"), new Sigungu("28290", "검단구"),
							new Sigungu("28710", "강화군"), new Sigungu("28720", "옹진군"))),
			new Sido("대전광역시",
					List.of(new Sigungu("30110", "동구"), new Sigungu("30140", "중구"), new Sigungu("30170", "서구"),
							new Sigungu("30200", "유성구"), new Sigungu("30230", "대덕구"))),
			new Sido("울산광역시",
					List.of(new Sigungu("31110", "중구"), new Sigungu("31140", "남구"), new Sigungu("31170", "동구"),
							new Sigungu("31200", "북구"), new Sigungu("31710", "울주군"))),
			new Sido("세종특별자치시", List.of(new Sigungu("36110", "세종특별자치시"))),
			new Sido("경기도", List.of(new Sigungu("41110", "수원시"), new Sigungu("41111", "수원시 장안구"),
					new Sigungu("41113", "수원시 권선구"), new Sigungu("41115", "수원시 팔달구"), new Sigungu("41117", "수원시 영통구"),
					new Sigungu("41130", "성남시"), new Sigungu("41131", "성남시 수정구"), new Sigungu("41133", "성남시 중원구"),
					new Sigungu("41135", "성남시 분당구"), new Sigungu("41150", "의정부시"), new Sigungu("41170", "안양시"),
					new Sigungu("41171", "안양시 만안구"), new Sigungu("41173", "안양시 동안구"), new Sigungu("41190", "부천시"),
					new Sigungu("41192", "부천시 원미구"), new Sigungu("41194", "부천시 소사구"), new Sigungu("41196", "부천시 오정구"),
					new Sigungu("41210", "광명시"), new Sigungu("41220", "평택시"), new Sigungu("41250", "동두천시"),
					new Sigungu("41270", "안산시"), new Sigungu("41271", "안산시 상록구"), new Sigungu("41273", "안산시 단원구"),
					new Sigungu("41280", "고양시"), new Sigungu("41281", "고양시 덕양구"), new Sigungu("41285", "고양시 일산동구"),
					new Sigungu("41287", "고양시 일산서구"), new Sigungu("41290", "과천시"), new Sigungu("41310", "구리시"),
					new Sigungu("41360", "남양주시"), new Sigungu("41370", "오산시"), new Sigungu("41390", "시흥시"),
					new Sigungu("41410", "군포시"), new Sigungu("41430", "의왕시"), new Sigungu("41450", "하남시"),
					new Sigungu("41460", "용인시"), new Sigungu("41461", "용인시 처인구"), new Sigungu("41463", "용인시 기흥구"),
					new Sigungu("41465", "용인시 수지구"), new Sigungu("41480", "파주시"), new Sigungu("41500", "이천시"),
					new Sigungu("41550", "안성시"), new Sigungu("41570", "김포시"), new Sigungu("41590", "화성시"),
					new Sigungu("41591", "화성시 만세구"), new Sigungu("41593", "화성시 효행구"), new Sigungu("41595", "화성시 병점구"),
					new Sigungu("41597", "화성시 동탄구"), new Sigungu("41610", "광주시"), new Sigungu("41630", "양주시"),
					new Sigungu("41650", "포천시"), new Sigungu("41670", "여주시"), new Sigungu("41800", "연천군"),
					new Sigungu("41820", "가평군"), new Sigungu("41830", "양평군"))),
			new Sido("충청북도",
					List.of(new Sigungu("43110", "청주시"), new Sigungu("43111", "청주시 상당구"),
							new Sigungu("43112", "청주시 서원구"), new Sigungu("43113", "청주시 흥덕구"),
							new Sigungu("43114", "청주시 청원구"), new Sigungu("43130", "충주시"), new Sigungu("43150", "제천시"),
							new Sigungu("43720", "보은군"), new Sigungu("43730", "옥천군"), new Sigungu("43740", "영동군"),
							new Sigungu("43745", "증평군"), new Sigungu("43750", "진천군"), new Sigungu("43760", "괴산군"),
							new Sigungu("43770", "음성군"), new Sigungu("43800", "단양군"))),
			new Sido("충청남도",
					List.of(new Sigungu("44130", "천안시"), new Sigungu("44131", "천안시 동남구"),
							new Sigungu("44133", "천안시 서북구"), new Sigungu("44150", "공주시"), new Sigungu("44180", "보령시"),
							new Sigungu("44200", "아산시"), new Sigungu("44210", "서산시"), new Sigungu("44230", "논산시"),
							new Sigungu("44250", "계룡시"), new Sigungu("44270", "당진시"), new Sigungu("44710", "금산군"),
							new Sigungu("44760", "부여군"), new Sigungu("44770", "서천군"), new Sigungu("44790", "청양군"),
							new Sigungu("44800", "홍성군"), new Sigungu("44810", "예산군"), new Sigungu("44825", "태안군"))),
			new Sido("경상북도",
					List.of(new Sigungu("47110", "포항시"), new Sigungu("47111", "포항시 남구"), new Sigungu("47113", "포항시 북구"),
							new Sigungu("47130", "경주시"), new Sigungu("47150", "김천시"), new Sigungu("47170", "안동시"),
							new Sigungu("47190", "구미시"), new Sigungu("47210", "영주시"), new Sigungu("47230", "영천시"),
							new Sigungu("47250", "상주시"), new Sigungu("47280", "문경시"), new Sigungu("47290", "경산시"),
							new Sigungu("47730", "의성군"), new Sigungu("47750", "청송군"), new Sigungu("47760", "영양군"),
							new Sigungu("47770", "영덕군"), new Sigungu("47820", "청도군"), new Sigungu("47830", "고령군"),
							new Sigungu("47840", "성주군"), new Sigungu("47850", "칠곡군"), new Sigungu("47900", "예천군"),
							new Sigungu("47920", "봉화군"), new Sigungu("47930", "울진군"), new Sigungu("47940", "울릉군"))),
			new Sido("경상남도",
					List.of(new Sigungu("48120", "창원시"), new Sigungu("48121", "창원시 의창구"),
							new Sigungu("48123", "창원시 성산구"), new Sigungu("48125", "창원시 마산합포구"),
							new Sigungu("48127", "창원시 마산회원구"), new Sigungu("48129", "창원시 진해구"),
							new Sigungu("48170", "진주시"), new Sigungu("48220", "통영시"), new Sigungu("48240", "사천시"),
							new Sigungu("48250", "김해시"), new Sigungu("48270", "밀양시"), new Sigungu("48310", "거제시"),
							new Sigungu("48330", "양산시"), new Sigungu("48720", "의령군"), new Sigungu("48730", "함안군"),
							new Sigungu("48740", "창녕군"), new Sigungu("48820", "고성군"), new Sigungu("48840", "남해군"),
							new Sigungu("48850", "하동군"), new Sigungu("48860", "산청군"), new Sigungu("48870", "함양군"),
							new Sigungu("48880", "거창군"), new Sigungu("48890", "합천군"))),
			new Sido("제주특별자치도", List.of(new Sigungu("50110", "제주시"), new Sigungu("50130", "서귀포시"))),
			new Sido("강원특별자치도",
					List.of(new Sigungu("51110", "춘천시"), new Sigungu("51130", "원주시"), new Sigungu("51150", "강릉시"),
							new Sigungu("51170", "동해시"), new Sigungu("51190", "태백시"), new Sigungu("51210", "속초시"),
							new Sigungu("51230", "삼척시"), new Sigungu("51720", "홍천군"), new Sigungu("51730", "횡성군"),
							new Sigungu("51750", "영월군"), new Sigungu("51760", "평창군"), new Sigungu("51770", "정선군"),
							new Sigungu("51780", "철원군"), new Sigungu("51790", "화천군"), new Sigungu("51800", "양구군"),
							new Sigungu("51810", "인제군"), new Sigungu("51820", "고성군"), new Sigungu("51830", "양양군"))),
			new Sido("전북특별자치도",
					List.of(new Sigungu("52110", "전주시"), new Sigungu("52111", "전주시 완산구"),
							new Sigungu("52113", "전주시 덕진구"), new Sigungu("52130", "군산시"), new Sigungu("52140", "익산시"),
							new Sigungu("52180", "정읍시"), new Sigungu("52190", "남원시"), new Sigungu("52210", "김제시"),
							new Sigungu("52710", "완주군"), new Sigungu("52720", "진안군"), new Sigungu("52730", "무주군"),
							new Sigungu("52740", "장수군"), new Sigungu("52750", "임실군"), new Sigungu("52770", "순창군"),
							new Sigungu("52790", "고창군"), new Sigungu("52800", "부안군"))));

	private RegionCatalog() {
	}

	/**
	 * 등록된 시 또는 도 목록을 반환함.
	 * @return 시 또는 도 이름 목록
	 */
	public static List<String> sidoList() {
		return SIDOS.stream().map(Sido::name).toList();
	}

	/**
	 * 지정한 시 또는 도의 시군구 목록을 반환함. 등록되지 않은 시 또는 도면 빈 목록을 반환함.
	 * @param sido 시 또는 도 이름
	 * @return 시군구 목록
	 */
	public static List<Sigungu> sigunguListOf(String sido) {
		return SIDOS.stream()
			.filter(item -> item.name().equals(sido))
			.findFirst()
			.map(Sido::sigunguList)
			.orElse(List.of());
	}

	/**
	 * 시/도와 시군구 이름으로 매칭용 지역코드를 조회함. 등록되지 않은 조합이면 null을 반환함. 온보딩 제출 시 sido·sigungu를
	 * region_code로 변환하는 데 씀(데이터모델 온보딩 절).
	 * @param sido 시 또는 도 이름
	 * @param sigungu 시군구 이름
	 * @return 지역코드(없으면 null)
	 */
	public static String regionCodeOf(String sido, String sigungu) {
		return sigunguListOf(sido).stream()
			.filter(item -> item.name().equals(sigungu))
			.map(Sigungu::code)
			.findFirst()
			.orElse(null);
	}

	/**
	 * 시군구 코드와 이름임(값 객체).
	 *
	 * @param code 지역코드
	 * @param name 시군구 이름
	 */
	public record Sigungu(String code, String name) {

	}

	private record Sido(String name, List<Sigungu> sigunguList) {

	}

}
