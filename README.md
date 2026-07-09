# jeongbiseo-backend

## 개요

정비서 — 정부지원금 추천 백엔드. 교내 해커톤(팀 아기삼자) 출품작.

## 스택

Java 17, Spring Boot 4.0.3, Spring Data JPA, MySQL, Spring Security 더하기 JWT(jjwt), springdoc.

## 실행 방법

JDK 17 필요. 로컬 프로필은 환경변수 3개(DB_URL, DB_USERNAME, DB_PASSWORD)를 읽음.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger UI는 서버 기동 후 http://localhost:8080/swagger-ui/index.html 에서 확인.

## 검증

커밋 전 아래 순서로 전부 통과해야 함. 상세 규칙은 .agents/rules/verification-loop.md 참조.

```bash
./gradlew checkFormat
./gradlew checkstyleMain checkstyleTest spotbugsMain
./gradlew test
./gradlew jacocoTestCoverageVerification
./gradlew build bootJar
```

포맷 위반은 `./gradlew format`으로 자동 수정. 통합 테스트는 로컬 Docker(Testcontainers MySQL)가 필요함.

## AGENTS.md 포인터

AI 에이전트 작업 규칙은 AGENTS.md 참조.
