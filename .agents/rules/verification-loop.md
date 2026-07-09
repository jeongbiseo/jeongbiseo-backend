> 출처: campus-hackathon-2026/.agents/rules/verification-loop.md — 정본 갱신 시 동기화함

# 검증 루프 규칙 — campus-hackathon-2026

> 2026-07-09 스택 확정(Java 17, Spring Boot 4.0.3, Gradle 9.3.1)으로 실제 명령 박제함. CI(.github/workflows/ci.yml)와 동일 순서이며 로컬에서 먼저 통과시켜야 함.

## 파이프라인(커밋 전, fail-fast)

| 단계 | 명령 | 목적 | 실패 시 |
|------|------|------|---------|
| 1 포맷 | `./gradlew checkFormat` | spring-javaformat 위반 탐지 | `./gradlew format` 자동 수정 후 재실행 (format과 build는 같은 호출에 섞지 말 것 — Gradle 9 암묵 의존성 검증 실패) |
| 2 정적 분석 | `./gradlew checkstyleMain checkstyleTest spotbugsMain` | 스타일·버그 패턴 조기 발견 | 수정 후 재실행. 억제는 checkstyle/suppressions.xml, spotbugs/spotbugs-exclude.xml에 사유와 함께 |
| 3 테스트 | `./gradlew test` | 회귀 방지 (Testcontainers MySQL 통합 포함, 로컬 Docker 필요) | 실패 케이스 수정 후 재실행 |
| 4 커버리지 | `./gradlew jacocoTestCoverageVerification` | 라인·브랜치 70% 게이트(추후 80 상향) | 테스트 보강 후 재실행 |
| 5 빌드 | `./gradlew build bootJar` | 산출물 확인 (check 전체 포함) | 컴파일 오류 수정 후 재실행 |

JDK는 17 고정(로컬 `C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot`). 커밋 메시지는 commitlint.config.mjs 규칙(gitmoji 접두사)을 CI가 검사함.

## API 계약 정합 게이트 (Swagger, 코드 착수 후)

- 코드 착수 후 매 검증 루프(커밋 전)에서 springdoc `/v3/api-docs` 출력을 `docs/architecture/API명세서.md`와 대조함. 대조 항목은 경로, HTTP 메소드, operationId, 에러코드임.
- 감사는 `springdoc-swagger-auditor` 스킬로 수행함.
- 불일치 발견 시 정본은 코드가 아니라 API명세서임. 명세서에 맞춰 코드(어노테이션)를 고침.
- 스택 미확정 단계에서는 미적용. Spring Boot 더하기 springdoc 도입 후 활성화함.

## 수동 점검(권장)

- 데모 핵심 흐름을 직접 실행해 확인함(해커톤 발표 시연 경로 우선).

## 자기 검증 규칙

- 전체 루프 통과 전까지 "완료"·"통과" 선언 금지. 검증 안 한 것을 통과로 적지 말 것.
- 동일 단계 3회 연속 실패 시 사람에게 에스컬레이션. 실패 위장(케이스 삭제·주석 처리) 금지.
