# BUILD REPORT

개인 참가(team-011 취업하자). 기획부터 검증까지 Kiro(kiro-cli 3.1.0 + Kiro Crew)로 진행했다. 모든 Kiro 기능 사용은 시각·참조·결과와 함께 [`KIRO-USAGE.md`](KIRO-USAGE.md)에 기록했고(실패·중단 포함), 파일 쓰기 hook 기록은 [`.kiro/evidence/hook-events.log`](.kiro/evidence/hook-events.log)에 있다.

**이번 해커톤은 핸드폰으로 진행했다.** 노트북 화면이 고장 나서 평소 외부 모니터를 연결해 쓰는데, 오늘 외부 모니터를 챙기지 못했다. 그래서 원격 서버에서 돌아가는 Kiro Crew 대시보드에 핸드폰으로 접속해 기획·spec·구현·리뷰·녹화까지 모든 작업을 지시하고 확인했다. 직접 코드를 치는 대신, 작은 화면에서는 결정과 검토만 하고 실행은 Kiro에 맡기는 방식이 필요했다:
- **한 줄 지시 → 병렬 서브에이전트:** "병렬로 시작해" 같은 짧은 지시로 worktree별 서브에이전트(최대 6개 동시)를 띄우고, 완료 이벤트로 결과만 받아 확인했다.
- **선택지 버튼으로 결정:** 쟁점마다 추천안과 선택지를 받아 버튼 하나로 결정했다(서비스 이름, 마스킹 방식, 평가 방식, 발표 구성 등).
- **실행 중 방향 수정:** 작업 도중 "Mobile-first로", "Kiro 크레딧으로" 같은 지시를 보내면 실행 중인 에이전트에 바로 반영(`spawn_steer`)됐다.
- **화면 확인은 스크린샷으로:** 에이전트가 Playwright로 찍은 375px·1280px 스크린샷과 녹화 프레임을 대화창에서 보고 판단했다. 앱 자체도 핸드폰에서 열어 직접 써 봤다. 앱은 계속 `127.0.0.1`에만 바인딩했고, 공개 포트를 열지 않은 채 Tailscale 사설망(내 인증된 기기만 접속 가능)을 거쳐 접속했다.
- **마감 관리:** 마감에서 거꾸로 잡은 알림(Kiro Crew cron)이 핸드폰으로 왔다.

## Kiro 활용 내역

| 단계 | 사용한 Kiro 기능 | 무엇을 했는지 | 결과물 위치 |
|---|---|---|---|
| 준비 | Steering (always 4 + fileMatch 1) | 기술 규칙(localhost 바인딩, 비밀 파일 금지, 검증 명령), Kiro 우선 워크플로, 역할별 에이전트 표, `*.java`에만 적용되는 코딩 규칙을 작성 | `.kiro/steering/tech.md`, `kiro-workflow.md`, `agents.md`, `java.md` |
| 준비 | 커스텀 에이전트 5개(역할별 모델·추론 깊이·쓰기 권한) | spec(claude-opus-5.5/high, 문서만 쓰기), impl(claude-sonnet-5.5/medium), test(sonnet/low, `src/main` 쓰기 거부), check(claude-haiku-5.5/low, 쓰기 도구 없음), review(gpt-5.6-terra/high, 셸·쓰기 도구 없음). `--trust-all-tools`로 실행해도 권한 규칙이 쓰기를 막는 것을 실제로 확인 | `.kiro/agents/*.json`, `scripts/kiro.sh` |
| 준비 | Hooks (kiro-cli 에이전트 hook) | `agentSpawn`: spec 진행률과 아직 안 쓴 Kiro 기능을 세션 시작 때 표시. `postToolUse(write)`: 파일 쓰기를 로그로 남기고, 권한에 막힌 쓰기는 BLOCKED로 표시하도록 구현. hook 로그는 kiro-cli 경로(spec 단계)에서만 쌓였고, 이후 Kiro Crew 서브에이전트 경로에서는 kiro-cli hook이 실행되지 않는다 | `.kiro/agents/kirothon*.json`의 `hooks`, `scripts/kiro_log.py`, `.kiro/evidence/hook-events.log` |
| 기획 | Spec (requirements) | spec 에이전트가 EARS 형식(`WHEN … THE SYSTEM SHALL …`) 요구사항 14개, 인수 조건 97개를 작성. 사용자 결정이 바뀔 때마다 spec을 3회 다시 실행 | `.kiro/specs/resume-review/requirements.md`, `.kiro/steering/product.md` |
| 기획 | 교차 리뷰(다른 모델) | 구현 전에 gpt-5.6-terra가 spec 일관성 리뷰. 지적 5건 중 3건 반영(평가 제출 응답 본문 제거, E2E 시나리오 구체화, 샘플 PDF 리소스화) | `KIRO-USAGE.md` 2026-11-14T02:50 |
| 설계 | Spec (design) | API 19개 설계(그중 복구 코드 API는 확장 작업으로 미구현), 데이터 모델 9개 테이블, 접근 경계, 오류 표, 테스트 전략. 병렬 구현 전에 요청·응답 JSON 계약과 공유 타입을 설계에 고정해 에이전트 사이 형식 불일치를 막음 | `.kiro/specs/resume-review/design.md`, `src/main/java/dev/kirothon/resume/pdf/PdfTypes.java` |
| 설계 | Spec (tasks) | 작업 16개를 요구사항 번호(`_요구사항: x.y_`)로 추적. MVP 핵심(마스킹 + 익명 평가) / 배포·녹화 / 확장으로 우선순위 재조정 | `.kiro/specs/resume-review/tasks.md` |
| 구현 | Kiro Crew 병렬 서브에이전트(`spawn_run`, Kiro 백엔드) + git worktree | 파일 경계가 다른 작업을 worktree 하나씩에 배정해 동시 구현 후 병합. 웨이브 1: 골격 / 웨이브 2: PDF 추출·마스킹 영역·렌더링 3개 동시 / 웨이브 3: 업로드·게시 API, 화면 2개, 샘플 PDF 4개 동시 / 웨이브 4: 평가 API, Docker 배포, 코드 리뷰 3개 동시. 병합할 때마다 전체 테스트 재실행 | `KIRO-USAGE.md` 구현 행, 커밋 이력 |
| 검증 | 교차 코드 리뷰(gpt-5.6-terra, 읽기 전용) | 병합된 백엔드를 리뷰해 높음 5건 발견(게시와 초안 교체 경쟁, 원본 PDF 만료 미강제, 페이지 크기 무제한 메모리, 타임아웃 후 스레드 누적, 평가 API 미구현) | `KIRO-USAGE.md` 검증 행 |
| 검증 | Kiro Crew Dynamic Workflow(리뷰 → 수정 → 테스트 반복) | 라운드마다 수정(sonnet, 유일한 쓰기) → 테스트(haiku, 읽기) → 교차 리뷰(gpt, 읽기). 3라운드 13분 40초 동안 6건 수정, 테스트 135 → 141개, 모든 라운드 빌드 성공 | `.kiro/workflows/review-fix-loop.py`, Workflows 실행 `wf_000002` |
| 검증 | check 에이전트, 렌더링 눈 확인 | 빌드·테스트 결과 보고, 샘플 PDF 렌더링 PNG를 직접 열어 한글 렌더링과 마스킹 위치 확인 | `src/test/java/dev/kirothon/resume/demo/DemoPdfTest.java` |
| 기타 | Kiro Crew cron 알림 | 마감(18:00)에서 거꾸로 잡은 일정에 맞춰 컷 판단·구현 마감·기능 동결·녹화·마감 직전 알림 5개 등록 | `HACKATHON.md` 실전 일정 |
| UX 설계 | Kiro Crew Dynamic Workflow(설계 ↔ 평가 반복, `wf_000003`) | 설계자(claude-opus-5.5)가 랜딩 두 버튼·평가 연속 흐름·평가받기 3단계를 설계하고, 평가자(gpt-5.6-terra)가 7개 기준(첫 30초 행동, 평가 반복 속도, 개인정보 신뢰 등)으로 채점·지적해 3라운드 반복 | `.kiro/workflows/ux-design-loop.py`, `.kiro/specs/resume-review/ux-flow.md` |
| UX 구현 | `spawn_run` 병렬 5개 + worktree, 이어서 E2E 담당 1개 | 공통 JS·랜딩·평가하기·평가받기·내 이력서를 파일 소유권으로 나눠 동시 구현. 공통 함수 이름(`ensureAnon`, `loadPageImage`, `showBanner`)을 지시서에 계약으로 먼저 고정해 서로 기다리지 않음 | `src/main/resources/static/` |
| 디자인 | `spawn_run` 병렬 4개 + 실행 중 지시 변경(`spawn_steer`) | 보라·검정·흰색 색상 토큰을 spec(ux-flow 9절)에 먼저 고정하고 화면별 4개 에이전트가 `var()`로만 참조. 진행 중 사용자의 "Mobile-first" 지시를 4개 에이전트에 즉시 주입(375px 기본, `min-width` 쿼리만, 터치 44px) | `ux-flow.md` 9절, `css/*.css` |
| 팀 분업 | 역할별 서브에이전트 팀: 기획·검토 4명 → 구현 6명 → QA 1명 → 교차 리뷰 | 아트디렉터(opus), UX 라이터(sonnet), 접근성 감사(gpt, WCAG 대비비 실제 계산), 데모 프로듀서(gpt)가 문서 4개를 쓰고, 이를 근거로 구현 6명이 파일 소유권대로 병렬 반영. E2E가 의존하는 문구 15개는 UX 라이터가 "변경 금지"로 표시해 구현이 테스트를 깨지 않게 함 | `.kiro/specs/resume-review/team/` |
| 검증 | 화면 교차 리뷰 2회(gpt-5.6-terra, 읽기 전용) + 수정 에이전트 | 1회차 9건(409 뒤 미리보기 미갱신, 탭 두 개에서 익명 계정 중복 생성 등) 중 8건 반영. 2회차 9건(키보드 핀 좌표 검증, 오류 알림 중복 낭독, 실제 이력서 녹화 시 이름 노출 경로 등) 중 8건 반영. 기각은 실제 테스트 통과로 근거를 남김 | `KIRO-USAGE.md` |
| 검증 | QA 에이전트 + 직접 확인 | 375px·1280px 5개 화면 가로 넘침 0, 모바일 게시 바 201px → 96px, 키보드만으로 핀 추가 확인. 데모 영상은 4초 간격 프레임을 직접 보며 실명 노출을 검사 | `e2e/resume-flow.spec.cjs`, `e2e/demo.spec.cjs` |
| 기타 | Kiro Crew 메모리·교훈 | "서브에이전트는 Kiro 백엔드로", "항상 한국어로 답할 것" 같은 사용자 지시를 교훈으로 저장해 이후 세션·서브에이전트에 적용 | Kiro Crew lessons |

## 개발 과정에서 바뀐 점

| 처음 계획 | 바뀐 결정 | 이유 |
|---|---|---|
| 이력서에서 텍스트를 추출해 마스킹한 **텍스트만** 평가자에게 보여 줌 | 페이지를 **PNG로 렌더링하고 검은 박스**를 칠한 이미지를 보여 줌 | 이력서 디자인(가시성·시인성)도 평가 대상이어야 한다는 사용자 판단. PDF 위에 박스만 덮으면 텍스트가 남아 복사로 새므로 이미지로 렌더링 |
| 항목별 1~5점 평가표 | 이미지 위 **클릭 첨삭 핀** + 총평·총점 | 위치를 짚는 구체적 피드백이 더 쓸모 있다는 사용자 판단 |
| 문장 diff·복구 코드까지 MVP(점수 추이 차트는 이후 구현됨) | MVP는 **마스킹 + 익명 평가**에 집중, 나머지는 확장 작업 | 7시간(11:00~18:00) 일정 안에서 핵심 흐름 완주를 우선 |
| 서브에이전트 백엔드 혼용(Codex 검증 1회) | 모든 서브에이전트를 **Kiro 백엔드**로 실행하고, 다른 모델이 필요하면 Kiro의 model 인자로 선택 | 행사 Kiro 크레딧 사용 원칙(사용자 지시) |
| 공개 배포 | 로컬 Docker 배포 + 녹화 데모 | 배포는 필수가 아니고, 시간은 핵심 기능에 쓰기로 결정 |

각 변경은 구현보다 spec을 먼저 고친 뒤 반영했다(spec 3회 반복, 열린 질문 4개 확정 기록은 `requirements.md` 끝).

## 어려웠던 점과 해결 방법

| 어려움 | 해결 |
|---|---|
| 병렬 에이전트가 같은 저장소에서 동시에 빌드하면 반쯤 쓴 파일 때문에 서로의 컴파일이 깨짐 | 작업마다 **git worktree**와 브랜치를 따로 주고, 공유 타입(`PdfTypes.java`)과 JSON 계약을 먼저 고정한 뒤 병렬 실행. 병합 후 전체 테스트로 확인 |
| 리뷰 에이전트를 "읽기 전용"으로 지시했는데 셸로 파일을 만들 수 있었음 | 에이전트 설정에서 셸·쓰기 도구를 제거하고, diff는 `scripts/kiro.sh`가 `.kiro/review/CHANGES.md`로 준비하게 바꿈. 재시험으로 쓰기 실패 확인 |
| 서버에 한글 글꼴이 PDF에 포함되지 않으면 □로 렌더링될 위험 | 샘플 PDF는 Noto CJK 서브셋을 포함해 생성하고, Docker 이미지에 `fonts-noto-cjk` 설치. 렌더링 PNG를 직접 열어 확인 |
| 교차 리뷰가 찾은 동시성·자원 결함(게시 경쟁, 초대형 페이지 메모리 고갈 등) | 리뷰 → 수정 → 테스트 워크플로로 3라운드 반복해 6건 수정. 재현 못 한 추정 1건(PDFBox 내부 인터럽트)은 페이지 크기·스레드·대기열 상한으로 영향 범위를 제한하고 한계로 기록 |
| 업로드 확인용 `curl -F file=@…`가 Kiro 보안 정책(데이터 반출 패턴)에 차단됨 | 우회하지 않고 업로드 검증을 Java HTTP 테스트와 브라우저(Playwright) E2E로 옮김 |
| 가상 샘플 이력서로는 마스킹이 완벽해 보였지만, 팀원의 실제 이력서(릴리즈 PDF 2개 + 추가 1개)로 시험하자 국가번호를 괄호로 감싼 전화번호 형식이 가려지지 않았고, 이름 후보 안내에 이미 가린 이메일이 다시 드러남 | 실제 이력서는 공개 저장소에 커밋하지 않고(`.gitignore`의 `demo-private/`) 브라우저 업로드로만 확인. 정규식과 이름 후보 계산을 고치고 회귀 테스트 추가(테스트 150개) |
| 텍스트 미리보기 → 이미지 마스킹 → 핀 첨삭으로 요구가 두 번 바뀜 | 실행 중인 spec 작업을 중단하고 새 결정을 합친 지시로 재실행해 낭비를 줄임 |
