# AGENTS.md — jeongbiseo-backend

campus-hackathon-2026 상위 AGENTS.md 거버넌스를 따름. 본 문서는 레포 로컬 보강만 담음.

## 1. 프로젝트 개요

정비서(정부지원금 추천) 백엔드. 베이스 패키지 `com.jeongbiseo`.

## 2. API 경로

모든 엔드포인트는 `/api/v1` 접두사를 가짐. 정본은 `.agents/rules/api-versioning.md`.

## 3. 커밋 컨벤션

gitmoji 접두사 더하기 Conventional Commits(`.agents/rules/commit-conventions.md` 정본). 모든 커밋 메시지 마지막에 아래를 고정 작성함.

```
Co-authored-by: KWONSEOK02 <gwonseok02@gmail.com>
```

Claude `Co-Authored-By` 라인 추가 금지 — `KWONSEOK02` 단독.

## 4. 검증 루프

커밋 전 검증 절차는 `.agents/rules/verification-loop.md` 참조.

## 계획 산출물 위치

**`.plans/`는 이 저장소 안이 아니라 상위 작업공간 `campus-hackathon-2026/`에 있음**(저장소 루트 기준 `../.plans/`). 그 상위 폴더는 git 저장소가 아니라서 `.plans/`·`docs/`·상위 `AGENTS.md`는 어느 저장소에도 커밋되지 않음. 저장소 안에서 `.plans/`를 찾으면 없는 것이 정상임.

세션 핸드오프 정본은 `../.plans/HANDOFF.md` 고정명 1개, 대체 시 이전 본은 `../.plans/_archive/HANDOFF-YYYYMMDD.md`. 작업 폴더는 `../.plans/{NN}-{기능명}` 관례를 따름.
