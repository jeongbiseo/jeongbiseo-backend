> 출처: campus-hackathon-2026/.agents/rules/commit-conventions.md — 정본 갱신 시 동기화함

# 커밋 규칙 — campus-hackathon-2026

## 커밋·push 전 로컬 검증

`verification-loop.md`의 검증 루프를 통과한 뒤에만 커밋함. 한 단계라도 실패하면 수정 후 재실행.

## gitmoji 접두사 + Conventional Commits

```
{이모지}{type}({scope}): {description}
```

이모지가 type **바로 앞에 공백 없이** 붙음.

| 이모지 | type | 용도 |
|--------|------|------|
| ✨ | feat | 새 기능 |
| 🐛 | fix | 버그 수정 |
| 📝 | docs | 문서 |
| ✅ | test | 테스트 |
| ♻️ | refactor | 구조 개선 |
| 🔧 | chore | 빌드·설정·패키지 |
| 💄 | style | 포맷·스타일 |
| ⚡ | perf | 성능 |
| 👷 | ci | CI 설정 |
| ⏪ | revert | 되돌리기 |

예: `✨feat(auth): 카카오 로그인 추가`, `🐛fix(score): 합산 누락 수정`, `📝docs(rtm): 추적표 갱신`.

- description은 소문자 시작·마침표 없이.
- scope는 lowercase kebab-case. 단일 MVP라 scope 생략도 허용(예: `✨feat: ...`).
- 1 task = 1 commit. 의도가 하나면 squash 또는 최대 2커밋. 파일 단위로 잘게 쪼개지 않음.
- body는 1~2 문장이면 충분. 모방 출처·보고서체·명사화 과잉 금지.

> 본 레포는 Gitmoji를 명시 컨벤션으로 채택함 — 커밋 타입 이모지 허용(평문 우선 일반 규칙의 명시 예외).

## Co-authored-by

모든 커밋 메시지 마지막에 고정 작성:

```
Co-authored-by: KWONSEOK02 <gwonseok02@gmail.com>
```

- 이메일 `gwonseok02@gmail.com` 고정(`noreply` 금지).
- 이름은 GitHub username `KWONSEOK02`.
- Claude `Co-Authored-By` 라인 추가 금지 — `KWONSEOK02` 단독.
