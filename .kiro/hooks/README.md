# Hooks

이 프로젝트는 Kiro IDE 대신 kiro-cli(3.1.0)와 Kiro Crew로 개발했다. 그래서 hook은 `.kiro/hooks/*.kiro.hook`(IDE 형식)이 아니라 **kiro-cli 커스텀 에이전트 설정의 `hooks`**에 정의돼 있다.

| hook | 트리거 | 정의 위치 | 하는 일 |
|---|---|---|---|
| agentSpawn | 에이전트 세션 시작 | `.kiro/agents/kirothon.json`, `kirothon-spec.json`, `kirothon-test.json`, `kirothon-check.json` | `python3 -B scripts/kiro_log.py status`: spec 진행률(완료/전체 작업)과 아직 기록이 없는 핵심 Kiro 기능을 세션 시작 때 보여 준다 |
| postToolUse (matcher `write`) | 파일 쓰기 도구 사용 직후 | `.kiro/agents/kirothon.json`, `kirothon-spec.json`, `kirothon-test.json` | `python3 -B scripts/kiro_log.py hook`: 도구 이름과 프로젝트 상대 경로를 기록한다. 권한 규칙에 막힌 쓰기는 `BLOCKED`로 표시한다. 파일 내용은 기록하지 않는다 |

- 실행 기록: [`.kiro/evidence/hook-events.log`](../evidence/hook-events.log) (spec 에이전트의 문서 작성 등)
- hook 스크립트와 테스트: `scripts/kiro_log.py`, `tests/test_kiro_log.py`
- 실행: `scripts/kiro.sh <spec|impl|test|check|review> "지시"`
