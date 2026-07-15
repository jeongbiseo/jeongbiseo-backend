> 출처: campus-hackathon-2026/.agents/rules/api-versioning.md — 정본 갱신 시 동기화함

# API 버전화 규칙 (정비서, campus-hackathon-2026)

> 참고 레포 LikeLion14th-BE-fork와 멋사 관례를 따라 모든 엔드포인트에 `/api/v1` 접두사를 둠. 경로 계약 정본은 `docs/architecture/API명세서.md`의 "한눈에 보기" 표이며, 본 문서는 그 계약을 Spring 컨트롤러에 매핑하는 규칙임. stamp-rally-checkin의 `/api/v1` 관행을 Spring Boot로 각색함.

## 1. 원칙

- 모든 REST 엔드포인트는 `/api/v1/` 접두사를 가짐. 버전 없는 `/api/...` 노출 금지.
- 경로 계약(경로, 메소드, operationId, 인증)의 정본은 `docs/architecture/API명세서.md` 한눈에 보기 표임. 본 문서와 명세서가 충돌하면 명세서가 우선함.
- 버전 접두사는 클래스 레벨 `@RequestMapping`에 박음(아래 2절). 컨트롤러, 통합테스트, `SecurityConfig` 경로 패턴 모두 `/api/v1`을 포함함.

## 2. 컨트롤러 경로 매핑

각 컨트롤러 클래스에 `@RequestMapping("/api/v1/...")`을 두고, 메소드 매핑(`@GetMapping` 등)은 상대 경로만 씀. 엔드포인트 19개는 아래 8개 컨트롤러로 나뉨(operationId 정본은 API명세서). 명세서 한눈에 보기 표의 번호는 20까지 가지만 **3번은 결번**임 — 실명 수집 확정(명세서 v1.4)으로 checkNickname이 제거되고 이후 번호를 보존하려 결번 처리함.

| 컨트롤러 | 클래스 레벨 매핑 | 담당 operationId |
|---|---|---|
| AuthController | `/api/v1/auth` | socialAuthorize, socialCallback, logOut, refreshToken |
| MemberController | `/api/v1/members` | getMyOnboarding, updateMyOnboarding, deleteMember |
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

    // GET /api/v1/auth/{provider}  (operationId: socialAuthorize)
    @GetMapping("/{provider}")
    public void socialAuthorize(@PathVariable String provider, HttpServletResponse response) { ... }

    // GET /api/v1/auth/{provider}/callback  (operationId: socialCallback)
    // IdP는 브라우저를 프론트 콜백 페이지로 되돌리고, 프론트가 code·state를 이 API로 전달함
    @GetMapping("/{provider}/callback")
    public CustomResponse<SocialCallbackResDTO> socialCallback(@PathVariable String provider, @RequestParam String code, @RequestParam String state) { ... }
}

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    // GET /api/v1/members/me/onboarding  (operationId: getMyOnboarding)
    @GetMapping("/me/onboarding")
    public CustomResponse<MyOnboardingResDTO> getMyOnboarding(...) { ... }
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
- 인증 불필요 엔드포인트: socialAuthorize, socialCallback, getRegions, getSubsidyCategories, refreshToken(리프레시 토큰 자체가 자격 증명이라 Authorization 헤더 불필요). 그 외는 Bearer 토큰 필요이며 getSubsidyDetail만 선택 인증임.
- socialCallback 흐름(명세서 v1.5 확정, 프론트 전달본과 동일): IdP에 등록하는 `redirect_uri`는 **백엔드가 아니라 프론트 콜백 페이지**임. IdP가 브라우저를 프론트로 되돌리면 프론트가 쿼리의 `code`·`state`를 그대로 socialCallback에 전달하고, 백엔드는 서명 state를 무상태 검증한 뒤 JWT를 JSON으로 반환함. 따라서 socialCallback은 프론트에서 호출되므로 인증 불필요이되 CORS 허용 대상임.

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
