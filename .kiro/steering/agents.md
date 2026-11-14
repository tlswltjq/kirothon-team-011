---
inclusion: always
name: agents
description: 역할별 서브에이전트(모델·추론 깊이)와 교차 리뷰 규칙
---
# 역할별 서브에이전트
구현과 리뷰는 다른 모델 계열이 맡는다(Claude 구현 → GPT 리뷰). 비싼 모델은 명세·리뷰에만 쓴다.

| 역할 | 하는 일 | 모델 | 추론 | 쓰기 범위 |
|---|---|---|---|---|
| spec | product.md, requirements/design/tasks | claude-opus-5.5 | high | `.kiro/` 등 문서만 (src·tests·scripts·e2e 거부) |
| impl | tasks.md 작업 구현 + HTTP 테스트 | claude-sonnet-5.5 | medium | 전체 |
| test | 인수 조건별 테스트 작성·실행 | claude-sonnet-5.5 | low | `src/main`·spec·steering 거부 |
| check | 빌드·테스트·smoke 실행 후 결과만 보고 | claude-haiku-5.5 | low | 쓰기 도구 없음 |
| review | 교차 리뷰: 정확성·보안·요구사항 누락 | gpt-5.6-terra | high | 셸·쓰기 도구 없음 |

어려운 버그나 설계 판단은 이번 실행만 추론을 올린다: `KIRO_EFFORT=xhigh scripts/kiro.sh impl "..."`.

## 실행 경로 A: kiro-cli (터미널, 프로젝트 에이전트)
- `scripts/kiro.sh <역할> "지시"`. 에이전트 정의는 `.kiro/agents/kirothon*.json`, 추론 깊이는 스크립트에서 정한다.
- review는 실행 전에 `.kiro/review/CHANGES.md`(git status·diff·새 파일 목록)를 자동으로 만든다.

## 실행 경로 B: Kiro Crew 대시보드 (`spawn_run`)
같은 역할을 아래 인자로 띄운다. 모든 작업에 `cwd="/home/ubuntu/workspaces/kirothon"`을 넣어 steering이 로드되게 한다.
모든 `spawn_run`에 `backend="kiro"`를 명시해 Kiro 계정 크레딧으로 실행한다(2026-11-14 사용자 지시). ACP의 claude·codex 백엔드는 쓰지 않는다.

| 역할 | `spawn_run` 인자 |
|---|---|
| spec | `model="claude-opus-5.5", reasoning_effort="high"` |
| impl | `model="claude-sonnet-5.5", reasoning_effort="medium"` |
| test | `model="claude-sonnet-5.5", reasoning_effort="low"` |
| check | `model="claude-haiku-5.5", reasoning_effort="low", include_memory=false` |
| review | `model="gpt-5.6-terra", reasoning_effort="high", include_memory=false, include_lessons=false` + 지시에 "파일 수정 금지" |
| review(대안) | 사용 안 함(2026-11-14): codex 백엔드 대신 Kiro 백엔드에서 다른 모델(`model="claude-opus-5.5"` 등)로 두 번째 리뷰를 띄운다 |

- 파일 경계가 다른 impl/test는 한 번의 `spawn_run(tasks=[...])`로 병렬 실행. 같은 파일은 동시에 수정하지 않는다.
- 같은 diff를 두 모델에 맡기려면 `tasks=[{task, model:"gpt-5.6-terra"}, {task, model:"claude-opus-5.5"}]`.

## 작업 흐름
1. spec 작성 → 사람 검토.
2. tasks 하나씩 impl → check(빌드·테스트).
3. test가 인수 조건 테스트 보강 → check.
4. review가 diff를 교차 리뷰 → 지적은 impl이 수정 → check. 반영/기각 이유를 남긴다.
5. 단계마다 `python3 scripts/kiro_log.py add --feature <spawn_run|review|...>`로 기록한다. 리뷰는 모델 이름을 참조에 쓴다.
