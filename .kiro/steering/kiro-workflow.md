---
inclusion: always
name: kiro-workflow
description: 단계별로 사용할 Kiro/Kiro Crew 기능과 증거 기록 규칙
---
# Kiro 우선 워크플로
심사에서 Kiro 활용이 35점이다. 각 단계는 아래 기능으로 수행하고 증거를 남긴다.

| 단계 | 기능 | 산출물 |
|---|---|---|
| 1. 주제 해석 | `workflow_run`으로 아이디어 3안 병렬 비교·추천 | 실행 ID, `.kiro/steering/product.md` |
| 2. 명세 | Kiro Spec: `.kiro/templates/spec/` 형식으로 requirements(EARS) → design → tasks | `.kiro/specs/<기능>/` |
| 3. 구현 | `task_run`으로 tasks.md 실행, 파일 경계가 분명하면 `spawn_run` 병렬 | Task Runner·Subagents 기록 |
| 4. 검증 | 독립 검증 서브에이전트(구현 문맥 없음), browser MCP 또는 Playwright | 테스트 로그, 스크린샷 |
| 5. 운영 | `monitor_start`로 마감 알림(예산 제한), `session_ledger_record`로 진행 상태 | 모니터·원장 |
| 6. 제출 | `artifact`/위젯으로 발표 대시보드, 데모 녹화 | `KIRO-USAGE.md`, 영상 |

## 증거 기록
- Kiro 기능을 실제로 쓸 때마다 `python3 scripts/kiro_log.py add --stage <단계> --feature <기능> --ref <실행ID·세션·파일> --result <결과>`를 실행한다.
- 실패한 시도도 결과에 `실패: <이유>`로 기록한다. 쓰지 않은 기능을 쓴 것처럼 적지 않는다.
- `python3 scripts/kiro_log.py status`로 spec 진행률과 기능별 기록 수를 확인한다.

## Spec 규칙
- 요구사항은 EARS 형식: `WHEN <조건> THE SYSTEM SHALL <동작>`. 번호는 `1.1`처럼 붙인다.
- tasks.md 각 항목은 `_요구사항: 1.1, 1.2_`로 추적하고, 끝나면 `- [x]`로 바꾼다.
- 범위가 바뀌면 코드보다 spec을 먼저 고친다.
