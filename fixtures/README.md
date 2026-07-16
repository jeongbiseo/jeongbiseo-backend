# fixtures

적재 파이프라인 회귀 테스트가 쓰는 외부 소스 스냅샷임. 반대 방향 회귀 테스트(Gov24TargetAudience·Gov24OccupationRestriction·YouthcenterBusinessPolicyList)가 배제 규칙을 세게 잡아도 개인 지원금이 죽지 않는지를 실데이터로 고정함.

- 위치는 프로젝트 루트 고정임. 테스트가 `Path.of("fixtures", ...)` CWD 상대 경로로 읽고, gradle test workingDir 기본이 프로젝트 루트라 로컬과 CI 둘 다 이 위치를 씀. 옮기지 말 것.
- 스냅샷은 회귀 기준선이라 손편집 금지임. 갱신이 필요하면 원천 재수집으로 교체함.
