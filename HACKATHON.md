# Kirothon 2026 실행 계획

## 심사 기준과 대응
| 항목 | 1차 | 결선 | 무엇으로 보여 주나 |
|---|---|---|---|
| Kiro 활용 | 35 | 20 | `KIRO-USAGE.md` 기능별 실행 기록, spec·steering·hook 파일 |
| 작동 | 30 | 15 | 녹화 데모(실제 조작), 테스트·smoke 결과 |
| 문제 정의 | 20 | 25 | `.kiro/steering/product.md`, requirements 소개 |
| 사용자 가치 | 15 | 25 | 전/후 비교, 데모 첫 30초 |
| 발표 | - | 15 | 문제 → 해결 → 데모 → Kiro 활용 순서 |

## 도구
| 용도 | 도구 | 비고 |
|---|---|---|
| 에이전트 | Kiro Crew 대시보드(백엔드 `kas`) `spawn_run`, `scripts/kiro.sh <역할> "지시"`(`--trust-all-tools`) | 역할 spec·impl·test·check·review, 모델·추론 깊이·쓰기 범위는 `.kiro/steering/agents.md` |
| 교차 리뷰 | review 역할 = gpt-5.6-terra(구현은 Claude), 대안 `backend="codex"` | 셸·쓰기 도구 없음, `.kiro/review/CHANGES.md` 입력 |
| 규칙 | `.kiro/steering/` tech·kiro-workflow·java(fileMatch), 당일 product | |
| 명세 | `.kiro/templates/spec/` → `.kiro/specs/<기능>/` | EARS, 요구사항 추적 |
| 자동화 | kiro-cli hooks: 시작 시 `kiro_log.py status`, 파일 쓰기 후 `kiro_log.py hook` | `.kiro/evidence/hook-events.log` |
| 오케스트레이션 | `workflow_run`, `task_run`, `spawn_run`, 독립 검증 서브에이전트 | |
| 빌드·테스트 | Gradle Wrapper `test bootJar`, `scripts/tools.py`, Playwright 1.65.0 | |
| 배포 | `.kiro/templates/deploy/` Docker 로컬 배포 | 공개 배포는 미정 |
| 증거 | `scripts/kiro_log.py`, `KIRO-USAGE.md`, 위젯 발표 대시보드 | |

## 시간 배분
10% 주제·MVP 선택과 spec, 55% 구현, 20% 검증, 15% 제출·데모·발표.

## 당일 절차
1. `python3 scripts/tools.py doctor` 통과 확인.
2. 주제 공개 → `workflow_run`으로 3안 비교 → 선택 → `product.md` 작성 → 기록.
3. spec 템플릿 복사 → requirements → design → tasks 순으로 작성·검토 → 기록.
4. tasks를 `task_run` 또는 `spawn_run`으로 구현. 작업마다 `./gradlew test bootJar`.
5. 독립 검증 서브에이전트로 인수 조건과 실패 경로 확인. 수정은 구현 쪽에서.
6. 배포 템플릿으로 로컬 컨테이너 배포, smoke.
7. 데모 녹화, `kiro_log.py status`로 미기록 기능 점검, 발표 자료.

## 진행
- 2026-11-14: 연습 프로젝트 제거, 저장소 재초기화, Kiro 중심 도구 재편.
