# 🚀 정비서 백엔드 (Spring Boot + JPA + MySQL)

정비서 Back-end 레포지토리입니다. **정비서**는 개인 맞춤 **정부지원금 추천 서비스**로, 교내 해커톤 팀 **아기삼자**의 출품작입니다. 주제는 **"AI가 정부지원금 격차를 없앤다면?"**입니다.

> 정비서는 **정보 격차가 지원금 격차를 만든다**는 문제를 다룹니다. **AI는 공고문을 읽고, 규칙 엔진은 자격과 금액을 결정적으로 계산합니다.**

사용자의 온보딩 정보와 공공 API(정부24, 온통청년) 데이터를 결합해 받을 수 있는 지원금을 추천하고, 예상 총액과 마감 일정을 보여주는 REST API 서버입니다. 설계 원칙의 정본은 `docs/architecture/AI-추천-판정원칙.md`, 구현 불변식은 `AGENTS.md`에 있습니다.

> **현재 상태: 스캐폴드입니다.** `src/`에는 `JeongbiseoApplication.java`와 설정 yml만 있습니다. 도메인 코드는 외부 테스트베드에서 검증한 뒤 팀 리뷰를 거쳐 이 저장소로 이식합니다. **`jeongbiseo-backend`가 제품 코드와 테스트의 유일한 정본입니다.**

## 📍 API 엔드포인트

**모든 엔드포인트는 `/api/v1` 접두사를 가집니다.** 전체 경로·operationId·에러코드 계약은 **서버 기동 후 Swagger UI**와 **`docs/architecture/API명세서.md`**에서 관리합니다. README에는 목록을 중복하지 않으므로 **API가 바뀌어도 README를 고칠 필요가 없습니다.**

> Swagger UI: `http://localhost:8080/swagger-ui/index.html` (springdoc `/v3/api-docs`가 코드에서 자동 생성)

`/api/v1/subsidies/**`는 같은 경로 공간에서 인증 요구가 갈립니다. `SecurityConfig`에 blanket `permitAll`을 걸지 말고 **HttpMethod와 정확한 경로 단위로** 분기하세요.

## 🛠️ 기술 스택

- **Language**: Java 17 (toolchain 고정 — Java 21 API 사용 금지. `List.getFirst()` 등은 컴파일 실패)
- **Framework**: Spring Boot 4.0.3, Spring MVC
- **Build**: Gradle (Groovy DSL, wrapper 9.3.1)
- **Persistence**: Spring Data JPA + MySQL 8
- **Security**: Spring Security + JWT (jjwt 0.13), Kakao / Google OAuth
- **API Docs**: springdoc-openapi 3.0.2 (Swagger UI)
- **Test**: JUnit 5 + Testcontainers (실제 MySQL)
- **Quality**: spring-javaformat, Checkstyle, SpotBugs, JaCoCo (라인·브랜치 70%)

> Redis와 WebFlux는 의도적으로 제외했습니다. 외부 API 호출은 `spring-web`의 `RestClient`를 씁니다.

## 🔐 인증

- 카카오·구글 **소셜 로그인 전용**입니다. 소셜 첫 로그인 시 자동 가입됩니다.
- 액세스 토큰 30분, 리프레시 토큰 14일이며 리프레시 토큰은 MySQL에서 회전합니다.

## 🏃 빠른 시작

**사전 요구사항: JDK 17, Docker.** Docker는 로컬 MySQL과 Testcontainers 통합 테스트 모두에 필요합니다.

```bash
# 1. JDK 확인 (17이어야 함)
./gradlew -version

# 2. 로컬 MySQL 기동
docker compose up -d

# 3. 환경변수 준비 - .env.example을 복사해 .env(로컬 전용)로 채움
cp .env.example .env

# 4. 서버 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 5. 확인
#    http://localhost:8080/actuator/health
#    http://localhost:8080/swagger-ui/index.html
```

**Spring Boot는 `.env`를 자동으로 읽지 않습니다.** `.env`는 git에 올리지 않으며(로컬 전용), 주입 방법은 셋 중 하나입니다.

- **IntelliJ**: Run/Debug Configurations → Environment variables에 붙여넣기
- **bash**: `set -a; source .env; set +a` 후 `bootRun`
- **PowerShell**: `Get-Content .env | ForEach-Object { if ($_ -match '^(\w+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`

로컬 프로필은 `ddl-auto: update`라 **엔티티만 만들면 테이블이 자동 생성**됩니다(마이그레이션 도구 없음). 운영 프로필에는 쓰지 않습니다.

### 개별 테스트 실행

`--tests` 패턴으로 클래스·메소드·패키지 단위로 골라 실행합니다.

```bash
./gradlew test --tests "com.jeongbiseo.domain.auth.AuthServiceTest"          # 클래스 하나
./gradlew test --tests "com.jeongbiseo.domain.auth.AuthServiceTest.카카오_로그인_성공"  # 메소드 하나
./gradlew test --tests "com.jeongbiseo.domain.auth.*"                        # 패키지 전체
./gradlew test --tests "com.jeongbiseo.domain.auth.AuthServiceTest" --info   # 상세 로그
```

> IntelliJ에서는 테스트 클래스·메소드 옆의 ▶ 버튼으로도 개별 실행할 수 있습니다. 코드 변경 없이 강제 재실행하려면 `--rerun-tasks`를 붙입니다.

### 막히면

| 증상 | 원인 |
| :--- | :--- |
| 컴파일 실패 (`getFirst()` 등) | JDK 21 문법을 썼습니다. **Java 17 문법**으로 쓰세요 |
| 테스트가 Docker 오류로 실패 | Docker Desktop이 꺼져 있습니다(Testcontainers 필요) |
| DB 연결·권한 오류 | `docker compose up -d` 미실행, 또는 `.env`가 주입되지 않았습니다 |

## 🔒 검증 루프 (커밋 전 필수)

커밋 전 아래 5단계를 순서대로 실행합니다. 한 단계라도 실패하면 수정 후 **그 단계부터** 재실행하며, 전체 통과 전에는 커밋·push하지 않습니다.

```bash
./gradlew checkFormat
./gradlew checkstyleMain checkstyleTest spotbugsMain
./gradlew test
./gradlew jacocoTestCoverageVerification   # 라인·브랜치 70% 게이트
./gradlew build bootJar
```

포맷 위반은 `./gradlew format`으로 자동 수정합니다. **`format`은 검증 명령과 같은 호출에 섞지 않습니다**(Gradle 9 암묵 의존성 검증 실패). 별도로 돌린 뒤 `checkFormat`부터 재검증하세요.

> GitHub Actions CI가 `main`·`dev` push와 PR마다 `commitlint → checkFormat → Checkstyle → SpotBugs → test → JaCoCo(70%) → build bootJar` 순으로 동일 게이트를 재검증합니다. `docs/**`·`**.md` 변경은 CI를 트리거하지 않습니다. JaCoCo 게이트는 낮추지 않고 테스트를 추가해 넘깁니다.

## 💻 개발 환경 설정 (필수!)

1. **IntelliJ IDEA**를 권장합니다.
2. **들여쓰기는 탭**입니다. spring-javaformat 플러그인을 설치하면 저장 시 자동 포맷이 적용되어 손으로 맞출 필요가 없습니다.
3. 줄바꿈은 **LF**, 인코딩은 **UTF-8**입니다.

## 📜 프로젝트 규약 (Conventions)

- **Git 협업 전략** — base 브랜치는 `dev`입니다.

```
main                # 배포 브랜치
dev                 # 개발 메인 브랜치 (base)
feature/{N}-{name}  # 기능 개발
fix/{name}          # 버그 수정
hotfix/{name}       # 긴급 수정
refactor/{name}     # 리팩토링
docs/{name}         # 문서 작업
```

> `feature/{N}-{name}`에서 `{N}`은 **GitHub 이슈 번호**, `{name}`은 기능 이름입니다.
> - 이슈 단위로 브랜치를 파서 어떤 작업인지 이슈와 바로 연결됩니다.
> - 이슈가 있는 기능 개발: 이슈 12번 소셜 로그인 → `feature/12-social-login`
> - 이슈가 없는 단순 수정: 버튼 정렬 버그 → `fix/button-align` (번호 생략)

- **작업 순서:**
  1. 이슈를 만들고 `dev`에서 작업 브랜치를 분기합니다.
  2. 작업 후 `dev`로 PR을 생성합니다.
  3. 리뷰 1명 이상을 받고 `dev`에 병합합니다. `main` 직접 commit 금지.

- **커밋 메시지 컨벤션** — gitmoji 접두사 + Conventional Commits 형식입니다. 이모지가 type 바로 앞에 공백 없이 붙습니다. description은 소문자 시작, 마침표 없이 작성합니다. CI의 commitlint가 검사합니다. 1 task = 1 commit.

```
{이모지}{type}({scope}): {description}

예: ✨feat(auth): 카카오 소셜 로그인 구현

✨feat     새로운 기능 추가
🐛fix      버그 수정
📝docs     문서 수정
✅test     테스트 추가·수정
♻️refactor 리팩토링
🔧chore    빌드·설정 등 (로직 변경 없음)
💄style    코드 스타일 (포맷팅 등)
⚡perf     성능 개선
👷ci       CI 설정
⏪revert   되돌리기
```

- **디렉토리 구조** — 멋사(LikeLion) 도메인 주도 패키지 구조를 따릅니다. 팀 공통 컨벤션이며, 현재는 스캐폴드 단계라 아래 트리는 목표 구조입니다.

**전체 구조 (한눈에)**

```
src/main/java/com/jeongbiseo/
├── domain/          # 도메인별 기능 (auth, member, onboarding, subsidy, recommendation, calendar, estimated)
├── global/          # 공통 (apiPayload 응답봉투, config, security)
└── JeongbiseoApplication.java
```

**도메인 하나 세부 (subsidy 예시)**

```
src/main/java/com/jeongbiseo/
├── domain/
│   └── subsidy/                  # 도메인 하나. 다른 도메인도 같은 구조를 따름
│       ├── controller/           # REST 컨트롤러 (/api/v1)
│       │   └── docs/             # Swagger 문서 인터페이스 분리
│       ├── converter/            # entity ↔ dto 매핑
│       ├── dto/
│       │   ├── request/          # 요청 DTO
│       │   └── response/         # 응답 DTO
│       ├── entity/               # JPA 엔티티
│       ├── enums/                # 도메인 enum
│       ├── exception/            # 도메인별 예외
│       ├── repository/           # JPA 저장소
│       └── service/
│           ├── command/          # 생성·수정·삭제 (CQRS)
│           └── query/            # 조회 (CQRS)
├── global/
│   ├── apiPayload/               # 공통 응답봉투 (CustomResponse)
│   │   ├── code/                 # 성공·에러 코드
│   │   └── exception/
│   │       └── handler/          # 전역 예외 핸들러
│   ├── common/                   # BaseEntity 등 공통 엔티티
│   ├── config/                   # 스프링 설정
│   └── security/                 # 인증·인가 (jwt, filter, handler, userdetails)
└── JeongbiseoApplication.java
```

> `auth` 도메인은 카카오·구글 제공자 분기를 위해 `strategy/`를 추가로 둡니다. `service/`는 도메인에 따라 `command`만 두거나 `command`·`query`를 모두 둡니다.

- **네이밍 컨벤션**
  - 클래스는 PascalCase, 메소드·변수는 camelCase, 상수는 SCREAMING_SNAKE_CASE를 사용합니다.
  - 클래스·메소드·필드·변수·파라미터 네이밍은 **Checkstyle이 자동 강제**합니다(위반 시 빌드 실패). 테스트 메소드명은 한글·언더스코어 서술형을 허용합니다(강제 예외).
  - 상수(SCREAMING_SNAKE_CASE)는 **자동 강제 아님(규약)** — `static final` 헬퍼 객체와 구분이 어려워 코드 리뷰로 지킵니다.
