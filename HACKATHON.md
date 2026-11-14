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

## 실전 일정 (운영자료 기준: 18:00 KST 커밋 마감, 18:00 이후 커밋은 심사 미반영)
| 시점 | KST | 할 일 | 알림 |
|---|---|---|---|
| 컷 판단 | 14:30 | 핵심 흐름이 안 되면 내 이력서 화면 축소 | `df1eff59` |
| MVP 구현 마감 | 15:45 | 핵심 흐름 완주 | `563d2906` |
| 기능 동결 | 16:45 | 버그 수정·데모 준비만 | `a244c232` |
| 녹화 | 17:00 | Playwright 자동 녹화 → MP4 | `4913167a` |
| 영상 업로드(사용자) | 17:25 | YouTube 일부 공개 또는 구글 드라이브 링크 | `6ad80c13` |
| 링크·PDF 커밋 | 17:40 | README Demo 줄, PRESENTATION.pdf | |
| 최종 push·제출 | 17:50 | 시크릿 창 공개 확인, 제출 폼(사용자) | `7fd0117e` |

- 발표: 운영자료 기준 결선 발표 5분 + Q&A 3분(녹화 데모 포함). 데모 영상은 자막 없음, 발표자가 직접 설명.
- 녹화: Playwright 컨테이너 headless Chromium 자동 녹화(WebM) 후 MP4 변환, 한 페이지에서 토큰 전환으로 주인↔평가자, 클릭 지점 표시(2026-11-14 사용자 결정).
- 품질 루프: Kiro Crew workflow `.kiro/workflows/review-fix-loop.py`(리뷰→수정→테스트 반복)로 교차 리뷰를 반영한다.
- 저장소: 운영 템플릿 kirothon-starter로 만든 https://github.com/tlswltjq/kirothon-team-011 (Public, 기본 브랜치 kirothon-2026). 필수 파일 README.md(템플릿 섹션), BUILD_REPORT.md, .kiro/, PRESENTATION.pdf. 영상은 저장소에 올리지 않고 README Demo 줄에 링크.

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
- 2026-11-14 12:50 KST: 운영자료 확인 → 팀 저장소 kirothon-team-011(템플릿) 생성·이력 병합, README·BUILD_REPORT 템플릿 형식으로 작성, 일정·알림 18:00 커밋 마감 기준으로 조정. 팀 team-011 취업하자, 신지섭.
- 2026-11-14: 배포 결정: 로컬 Docker 데모와 녹화 영상이 목표. 공개 배포는 제출 필수 아님(가점), 후순위.
