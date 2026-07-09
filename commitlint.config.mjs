// gitmoji 접두사 커밋 컨벤션 대응 — `✨feat(scope): desc` 형태(이모지가 type 바로 앞, 공백 없음)
// wagoid/commitlint-github-action v6는 .js 설정을 지원하지 않으므로 .mjs 확장자 필수
export default {
  parserPreset: {
    parserOpts: {
      headerPattern: /^(?:\p{Extended_Pictographic}️?)(\w+)(?:\(([\w\-.]+)\))?: (.+)$/u,
      headerCorrespondence: ['type', 'scope', 'subject'],
    },
  },
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'test', 'refactor', 'chore', 'style', 'perf', 'ci', 'revert'],
    ],
    'header-max-length': [2, 'always', 100],
  },
};
