> 출처: campus-hackathon-2026/.agents/rules/code-style.md — 정본 갱신 시 동기화함

# 코드 스타일 규칙 — campus-hackathon-2026

> 스택은 Java 17 더하기 Spring Boot 4 더하기 Gradle로 확정됨.

## 일반

- **들여쓰기는 탭임**(spring-javaformat이 강제함). LF, UTF-8.

  > 2026-07-13 정정: 이 문서가 "2칸(탭 금지)"이라고 적고 있었으나 **실제 코드와 formatter는 탭**이다. 규칙대로 2칸을 쓰면 `./gradlew format`이 탭으로 되돌리고 `checkFormat`이 계속 어긋난다. **정본은 spring-javaformat이며 사람이 손으로 맞추지 않는다** — 포맷은 `./gradlew format`에 위임한다.

- Java 17 계약. 로컬 JDK가 21이어도 Java 21 API(`List.getFirst()`, `SequencedCollection` 등)를 쓰지 않고 `get(0)`처럼 17 문법으로 작성함.

## 네이밍

- 클래스/인터페이스/Enum/레코드: PascalCase (`AuthService`, `CustomResponse`).
- 메서드/변수/필드: camelCase.
- 상수: SCREAMING_SNAKE_CASE.
- **Java 파일명은 클래스명과 일치함**(`AuthService.java`). 개명 대상이 아님.
- 패키지·폴더: 소문자 단어 연결(`recommendation`, `apiPayload`는 기존 계약 유지). 하이픈·언더스코어를 넣지 않음(Java 패키지 규칙).
- 프레임워크·도구가 이름을 고정하는 파일은 그 이름을 그대로 씀: `application-local.yml`, `build.gradle`, `settings.gradle`, `commitlint.config.mjs`, `AGENTS.md`, `README.md`.

## 주석 — 한국어 명사형 종결

코드 내 모든 주석(인라인 `//`, 블록 `/* */`, JSDoc 본문)은 한국어 명사형으로 종결함.

허용 종결: `~함`, `~완료`, `~처리`, `~반환`, `~생성`, `~사용`, `~임`.

```
// 올바름:  목록을 반환함
// 잘못됨:  목록을 반환합니다 / 목록을 반환하는 함수
```

적용 제외: 사용자 응답·마크다운 산문·UI 표시 문자열은 주석이 아니므로 평서형 허용.

## ponytail 원칙(오버엔지니어링 금지)

- 추측성 추상화 금지: 단일 구현 인터페이스·단일 제품 팩토리·안 바뀌는 값의 config 금지.
- 표준 라이브러리·플랫폼 기능 우선(`<input type="date">`을 datepicker 라이브러리보다, CSS를 JS보다).
- 의도된 단순화는 `// ponytail:` 주석으로 표시함.
- 해커톤은 시간 제약이 큼 — "나중을 위한" 스캐폴드 금지. 데모에 필요한 최소 코드만.

## 입력 검증·접근성

- 사용자 입력은 사용 전에 검증함(신뢰 경계의 검증은 생략 금지).
- 접근성 기본: 폼 입력에 label, 이미지에 alt, 인터랙션 요소는 키보드 접근 가능.
