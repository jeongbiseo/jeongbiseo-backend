> 출처: campus-hackathon-2026/.agents/rules/api-versioning.md — 정본 갱신 시 동기화함(최근 동기화 2026-07-20)

# API 버전화 규칙 (정비서, campus-hackathon-2026)

> 참고 레포 LikeLion14th-BE-fork와 멋사 관례를 따라 모든 엔드포인트에 `/api/v1` 접두사를 둠. 경로 계약 정본은 `docs/architecture/API명세서.md`의 "한눈에 보기" 표이며, 본 문서는 그 계약을 Spring 컨트롤러에 매핑하는 규칙임. stamp-rally-checkin의 `/api/v1` 관행을 Spring Boot로 각색함.

## 1. 원칙

- 모든 REST 엔드포인트는 `/api/v1/` 접두사를 가짐. 버전 없는 `/api/...` 노출 금지.
- 경로 계약(경로, 메소드, operationId, 인증)의 정본은 `docs/architecture/API명세서.md` 한눈에 보기 표임. 본 문서와 명세서가 충돌하면 명세서가 우선함.
- auth 도메인도 같은 원칙을 따름. 명세서 v1.11(2026-07-19)에서 auth 절이 방식 B로 정리 완료됨 — 1번이 `POST /api/v1/auth/{provider}`(login), 2번은 결번, 5번이 `POST /api/v1/auth/reissue`(reissue)임. 명세서에 방식 A 상세절은 남아 있지 않음.
- 버전 접두사는 클래스 레벨 `@RequestMapping`에 박음(아래 2절). 컨트롤러, 통합테스트, `SecurityConfig` 경로 패턴 모두 `/api/v1`을 포함함.

## 2. 컨트롤러 경로 매핑

각 컨트롤러 클래스에 `@RequestMapping("/api/v1/...")`을 두고, 메소드 매핑(`@GetMapping` 등)은 상대 경로만 씀. 엔드포인트는 아래 8개 컨트롤러로 나뉨(operationId 정본은 API명세서). 명세서 한눈에 보기 표의 번호는 20까지 가지만 **결번이 2개**라 실제 계약은 18개임. 2번(socialCallback)은 방식 B 전환(명세서 v1.11)으로, 3번(checkNickname)은 실명 수집 확정(명세서 v1.4)으로 각각 폐기됨. 번호를 당기지 않고 결번으로 남기는 이유는 기능ID·RTM·프론트 문서의 기존 참조가 깨지기 때문임.

2026-07-19에 **getMe(`GET /api/v1/members/me`, 명세서 21번)를 신설**함(v1.12). 프론트가 앱 시작·새로고침 직후 로그인 상태와 회원 정보를 복구할 경로가 없었기 때문임 — getMyOnboarding은 온보딩 프로필 레코드 존재 여부로 판정해 온보딩 전 회원에게 ONB404_1을 던지고, reissue 응답은 `accessToken` 하나뿐임. getMe는 `Member.onboardingCompleted` 플래그를 그대로 실어 온보딩 전 회원도 200으로 반환함. 따라서 아래 개수 서술은 계약 19개로 갱신됨(구현 수는 다음 문단 참조).

엔드포인트 개수는 **계약 19개, 구현 18개**임(2026-07-20 배포본 `/v3/api-docs` 실측). 미구현은 `getSubsidyCategories` 1개뿐이며 카테고리 매핑 기준(DEC-15) 팀 확정 대기로 막혀 있음. addFavorite·removeFavorite는 2026-07-20에 구현·배포됨.

> 2026-07-19까지 이 절은 "계약 18개, 구현 15개"로 적혀 있었고 바로 위 문단의 19/16과 서로 모순이었음. 방식 A 폐기로 auth가 4개에서 3개로 줄어든 것을 계약 총수에서 한 번 더 뺀 중복 차감이 원인임. **결번 2개(2번 socialCallback, 3번 checkNickname)를 뺀 뒤의 값이 19이므로 거기서 또 빼지 말 것.**

| 컨트롤러 | 클래스 레벨 매핑 | 담당 operationId |
|---|---|---|
| AuthController | `/api/v1/auth` | login, reissue, logOut |
| MemberController | `/api/v1/members` | getMe, getMyOnboarding, updateMyOnboarding, deleteMember |
| OnboardingController | `/api/v1/onboarding` | submitOnboarding, setReceivedSubsidies |
| RegionController | `/api/v1/regions` | getRegions |
| SubsidyController | `/api/v1/subsidies` | getSubsidyCategories, searchSubsidies, getSubsidyDetail, addFavorite, removeFavorite |
| RecommendationController | `/api/v1/recommendations` | getRecommendations |
| CalendarController | `/api/v1/calendar` | getDeadlineCalendar |
| EstimatedAmountController | `/api/v1/estimated-total` | getEstimatedTotal, getEstimatedBreakdown |

매핑 예시:

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // POST /api/v1/auth/{provider}  (operationId: login)
    // 프론트 콜백 페이지가 code·codeVerifier·redirectUri를 바디로 전달함(셋 다 @NotBlank).
    // 200 result는 accessToken·isNewMember·onboardingCompleted이고, 리프레시 토큰은 Set-Cookie로 나감.
    @PostMapping("/{provider}")
    public CustomResponse<SocialCallbackResponse> login(@PathVariable("provider") String provider,
            @Valid @RequestBody SocialLoginRequest request, HttpServletResponse response) { ... }

    // POST /api/v1/auth/reissue  (operationId: reissue)
    // 바디 없음. 리프레시 토큰은 쿠키로만 받음. 200 result는 accessToken이고 새 리프레시 쿠키로 회전함.
    @PostMapping("/reissue")
    public CustomResponse<ReissueResponse> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken, HttpServletResponse response) { ... }

    // POST /api/v1/auth/logout  (operationId: logOut)
    // 200 result는 문자열이고 리프레시 쿠키를 Max-Age 0으로 삭제함.
    @PostMapping("/logout")
    public CustomResponse<String> logOut(HttpServletResponse response) { ... }
}

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    // GET /api/v1/members/me/onboarding  (operationId: getMyOnboarding)
    // 회원 식별은 파라미터가 아니라 FixedMemberResolver 주입으로 함(배포 N 기준).
    @GetMapping("/me/onboarding")
    public CustomResponse<OnboardingProfileResponse> getMyOnboarding() { ... }
}
```

- 인증 방식 재결정(2026-07-09, 소셜 전용)으로 signUp과 D1 결정(회원가입은 회원 자원 생성이라 `POST /api/v1/members`에 둠)은 폐기됨. 소셜 첫 로그인 시 자동 가입이라 별도 회원가입 엔드포인트 자체가 없음.
- 실명 수집 확정(2026-07-13, 명세서 v1.4)으로 닉네임 도메인이 소멸함. checkNickname 엔드포인트와 `GET /api/v1/members/nickname/check` 경로는 없음. `Member`는 `name`을 가지며 UNIQUE 제약이 없음(동명이인 허용).
- "내 ~" 자원은 `/me` 세그먼트를 씀(getMyOnboarding, updateMyOnboarding, deleteMember). `/me` 패턴은 members·auth 도메인에만 허용함. 다른 도메인(subsidies, recommendations, calendar 등)은 `/me`를 쓰지 않음. 알림 설정 조회·변경(getNotificationSetting, updateNotificationSetting)은 2026-07-09 알림 제거 결정으로 소멸함.

기각한 방식: `WebMvcConfigurer`의 `configurePathMatch(... addPathPrefix("/api/v1", ...))`로 접두사를 일괄 주입하는 방식은 쓰지 않음. 이유는 세 가지임. 첫째, 소스에서 `grep`으로 실제 경로가 안 보여 점검(5절)이 무력화됨. 둘째, 참고 레포 관례가 클래스 레벨 명시 매핑임. 셋째, 컨트롤러별 예외(버전 혼재 등)를 두기 어려움.

## 3. 메소드와 인증 분기 주의

같은 경로 공간에서 메소드나 세부 경로별로 인증 요구가 갈리므로, `SecurityConfig`는 경로 패턴 하나로 뭉뚱그리지 말고 HttpMethod와 정확한 경로 단위로 분기함.

- `/api/v1/subsidies` 공간은 인증이 혼재함:
  - `GET /api/v1/subsidies/categories`(getSubsidyCategories): 인증 불필요.
  - `GET /api/v1/subsidies`(searchSubsidies): 인증 필요.
  - `GET /api/v1/subsidies/{subsidyId}`(getSubsidyDetail): 선택 인증(로그인 시 isFavorite 반영, 비로그인 시 false). 시큐리티는 permitAll로 두고 서비스가 토큰이 있으면 읽음.
  - `POST`·`DELETE /api/v1/subsidies/{subsidyId}/favorite`(addFavorite, removeFavorite): 둘 다 인증 필요.
  - 따라서 `/api/v1/subsidies/**`에 blanket permitAll을 걸면 안 됨. `requestMatchers(HttpMethod.GET, "/api/v1/subsidies/categories").permitAll()`처럼 메소드와 경로를 좁혀 지정함.
- **auth 도메인 3종의 인증 요구**(계약 기준):
  - `POST /api/v1/auth/{provider}`(login): 인증 불필요. 아직 토큰이 없는 상태에서 부르는 발급 엔드포인트임. 에러는 두 갈래임 — `{provider}`가 KAKAO·GOOGLE 밖이면 400 `VALID400_0`(`ValidationErrorCode.INVALID_QUERY_PARAMETER`)이고, code 누락이나 IdP 토큰 교환 실패, 자동가입 레이스 패배 후 재조회 실패는 사유를 노출하지 않고 401 `AUTH401_1`로 통합함. 바디 필드 3개(code, codeVerifier, redirectUri)는 모두 `@NotBlank`라 누락 시 Bean Validation 400으로 먼저 걸림.
  - `POST /api/v1/auth/reissue`(reissue): Authorization 헤더 불필요. 리프레시 토큰 쿠키 자체가 자격 증명임. 쿠키가 없거나 무효·만료·재사용이면 401 `AUTH401_2`를 반환함.
  - `POST /api/v1/auth/logout`(logOut): 인증 필요(Bearer). 회원 식별은 전 도메인 단일 패턴인 `FixedMemberResolver`로 주입함.
- 그 밖에 인증 불필요인 것은 getRegions, getSubsidyCategories임. 나머지는 Bearer 토큰 필요이며 getSubsidyDetail만 선택 인증임. **getMe(`GET /api/v1/members/me`)도 Bearer 필요**이고, 온보딩 전 회원도 200으로 반환함(`Member.onboardingCompleted` 플래그를 그대로 실음).
- **현재 `SecurityConfig`는 `anyRequest().permitAll()` 전면 허용임(배포 N)**. 위 인증 요구는 아직 시큐리티로 강제되지 않는 계약상의 값이며, JWT 필터 enforcement와 경로별 좁힘은 배포 N+1 별건임. 그전까지는 고정 회원 주입으로 개발함. 따라서 소스만 보고 "인증이 없다"고 계약을 고쳐 쓰지 말 것 — 반대로 enforcement를 붙일 때 본 절이 그 목표 상태임.
- 토큰 계약: 액세스 토큰은 HS256 JWT 30분이며 `Authorization: Bearer`로 보냄. 리프레시 토큰은 JWT가 아니라 **불투명 랜덤 256비트**이고, 서버는 SHA-256 해시만 회원당 1행으로 저장해 원자적으로 회전함. 쿠키 속성은 HttpOnly, Secure, `Path=/`, `Max-Age` 14일, `SameSite=${COOKIE_SAME_SITE:None}`임.
- 소셜 로그인 흐름(방식 B): 인가 URL 생성과 PKCE `code_verifier`·`state` 소유는 **프론트**임. 백엔드에는 인가 URL을 만들어 주는 엔드포인트가 없음. IdP에 등록하는 `redirect_uri`도 **백엔드가 아니라 프론트 콜백 페이지**이며, 백엔드 env로 두지 않음. IdP가 브라우저를 프론트 콜백으로 되돌리면 프론트가 `code`와 자신이 보관한 `codeVerifier`, 인가 때 쓴 `redirectUri`를 `POST /api/v1/auth/{provider}` 바디로 보내고, 백엔드가 IdP와 토큰을 교환해 액세스 토큰은 JSON으로, 리프레시 토큰은 쿠키로 반환함. PKCE는 유지함. login은 프론트에서 호출되므로 CORS 허용 대상임.
- **[2026-07-20 실측 확정 — 대기 아님]** `CORS_ALLOWED_ORIGINS`와 `COOKIE_SAME_SITE`는 운영 `.env`에 이미 들어가 있음. 값은 `CORS_ALLOWED_ORIGINS=https://jeongbiseo-frontend.vercel.app,http://localhost:5173`, `COOKIE_SAME_SITE=None`(크로스 오리진이라 None이 맞음). 폰 시연을 위해 프론트를 `app.cartlab.store`로 옮기면 그때 `Strict`로 바꾸고 두 값을 함께 갱신함. `CorsConfig`는 프로퍼티가 비면 아무 origin도 허용하지 않으므로(allowCredentials가 켜져 있어 와일드카드 불가), 값이 비었을 때 브라우저 호출이 차단되는 것은 정상 동작임.

## 4. 버전 증가 기준

- v1 유지(하위호환 변경): 응답 필드 추가, 신규 엔드포인트 추가, 내부 구현이나 에러 메시지 문구 변경.
- v2 신설(계약 파괴 변경): 응답 필드 제거나 의미 변경, 요청 형식 변경, 에러코드 계약 변경. 이때 `controller.v2` 패키지에 새 컨트롤러를 만들어 `/api/v2/...`로 공존시키고, v1은 당분간 유지함.
- 해커톤 데모 범위에서는 v1만 사용함. v2는 계약 파괴 변경이 실제로 필요할 때만 신설.

## 5. 점검 명령

```bash
# 버전 없는 잔여 경로 탐지(0건이어야 함). 클래스·메서드 레벨 절대경로 매핑 대상
grep -rn '"/api/[^v]' src/main/java src/test/java

# 런타임 스펙 대조(모든 경로가 /api/v1로 시작하는지). 서버 기동 후 실행
curl -s http://localhost:8080/v3/api-docs | jq -r '.paths | keys[]' | grep -v '^/api/v1'
```

두 명령 모두 출력이 없어야 정상임(위반 0건). 메서드 레벨 매핑은 상대 경로라 `/api/`를 포함하지 않으므로 첫 grep에 걸리지 않음.

## 6. springdoc 정합

`/v3/api-docs` 출력을 API명세서와 대조하는 게이트(경로, 메소드, operationId, 에러코드)는 커밋 전 검증 루프에 포함함. 상세 절차는 `.agents/rules/verification-loop.md`의 "API 계약 정합 게이트" 절 참조.
