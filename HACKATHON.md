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
| 배포 | `.kiro/templates/deploy/` Docker 로컬 배포 | 목표는 로컬 데모. 공개 URL은 필수가 아닌 완성도 가점이라 MVP 완료 후 여유가 있을 때만 검토(후보: Cloudflare Tunnel) |
| 증거 | `scripts/kiro_log.py`, `KIRO-USAGE.md`, 위젯 발표 대시보드 | |

## 시간 배분
10% 주제·MVP 선택과 spec, 55% 구현, 20% 검증, 15% 제출·데모·발표.

## 실전 일정 (2026-11-14, 작업 마감 KST 18:00 = 09:00 UTC, 사용자 정정)
| 시점 | UTC | KST | 할 일 |
|---|---|---|---|
| spec 확정 | 03:08 | 12:08 | 완료 |
| MVP 구현 마감 | 06:45 | 15:45 | 핵심 흐름(마스킹 + 익명 평가) 완주 |
| 검증·수정 마감 | 08:00 | 17:00 | 교차 리뷰 반영, E2E, 로컬 Docker 배포 확인. 이후 기능 추가 금지 |
| 녹화 시작 | 08:20 | 17:20 | 데모 녹화(재녹화 여유) |
| 작업 마감 | 09:00 | 18:00 | 영상, KIRO-USAGE.md, 발표 자료 |

- 컷 기준: 05:30 UTC(14:30 KST)까지 업로드→마스킹 미리보기→게시→평가가 안 되면 내 이력서 화면을 버전 목록과 평가 목록만으로 줄인다.
- 확장 작업(복구 코드 → 추이·필터·diff → 수동 박스)은 검증·수정 마감 전 여유가 있을 때만.
- 마감 알림(Kiro Crew cron, 일회성, KST): 14:30 컷 판단 `df1eff59`, 15:45 MVP 마감 `563d2906`, 17:00 기능 동결 `9f7116e8`, 17:20 녹화 시작 `a54d66d7`, 17:45 마감 15분 전 `1c6b6bf6`.
- 발표: 3분은 발표 전체(문제 약 30초, 데모 영상 약 1분 40초, Kiro 활용 약 50초). 데모 영상은 자막 없음, 발표자가 직접 설명(2026-11-14 사용자 결정).
- 녹화: Playwright 컨테이너 headless Chromium 자동 녹화(WebM) 후 MP4 변환, 한 페이지에서 토큰 전환으로 주인↔평가자, 클릭 지점 표시(2026-11-14 사용자 결정).
- 품질 루프: Kiro Crew workflow `.kiro/workflows/review-fix-loop.py`(리뷰→수정→테스트 반복)로 교차 리뷰를 반영한다.
- 저장소: GitHub 공개 저장소에 README·steering·spec·Kiro 활용 문서(docs/KIRO.md)까지 모두 올린다.

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
- 2026-11-14: 주제 "내 일상에 바로 적용할 수 있는 서비스" → 익명 이력서 평가와 버전별 평가 이력. MVP 결정: 순수 무작위 노출, AI 평가 없음, PDF만 입력.
- 2026-11-14: 마스킹 결정: 텍스트 노출 폐기, 렌더링 PNG에 검은 박스(PDFBox). 원본 PDF 미보관, 스캔본 거부, 수동 박스는 2순위.
- 2026-11-14: 평가 결정: 항목 점수표 폐기. 페이지 위 클릭 첨삭 핀(정규화 좌표) + 총평(20자 이상) + 총점(1~5 정수).
- 2026-11-14: 복구 코드 MVP 포함(첫 방문 시 1회 표시, 다른 브라우저에서 입력해 이어 쓰기). 총점 1~5 확정.
- 2026-11-14: 이미지 마스킹: MVP는 모든 이미지를 구분·경고 없이 똑같이 가림. 평가자 라벨 버전별, 핀 50개·총평 1000자. 열린 질문 모두 확정.
- 2026-11-14: MVP 핵심 = 이력서 마스킹 + 익명 평가(+ 주인이 받은 핀·총평 확인). 복구 코드·점수 추이·필터·diff·수동 박스는 확장(작업 12→16→15 순).
- 2026-11-14: 서비스 이름 확정: "이게 다 너 잘되라고 하는 소리야".
- 2026-11-14: 배포 결정: 로컬 Docker 데모와 녹화 영상이 목표. 공개 배포는 제출 필수 아님(가점), 후순위.
