# Kiro 활용 보고서: 이게 다 너 잘되라고 하는 소리야

Kirothon 2026 개인 참가. 기획부터 구현·검증까지 Kiro(kiro-cli, Kiro Crew)로 진행한 방식과 실제 증거를 정리한다.
기능별 실행 기록 원본은 [`KIRO-USAGE.md`](../KIRO-USAGE.md)(시각·참조·결과), 파일 쓰기 hook 기록은 [`.kiro/evidence/hook-events.log`](../.kiro/evidence/hook-events.log)다.

## 한눈에 보기
| Kiro 기능 | 어떻게 썼나 | 증거 |
|---|---|---|
| Spec (requirements·design·tasks) | Opus 5.5(high) spec 에이전트가 EARS 요구사항 14개(인수 조건 97개), 설계, 작업 16개 작성. 사용자 결정이 바뀔 때마다 spec을 먼저 고친 뒤 구현 | `.kiro/specs/resume-review/` |
| Steering | 항상 적용 4개(tech, product, kiro-workflow, agents) + `*.java`에만 적용되는 fileMatch 1개 | `.kiro/steering/` |
| 역할별 커스텀 에이전트 | spec·impl·test·check·review 5개 에이전트에 모델·추론 깊이·쓰기 범위를 다르게 지정 | `.kiro/agents/*.json`, `scripts/kiro.sh` |
| Hooks | `agentSpawn`에서 spec 진행률·미기록 기능 표시, `postToolUse(write)`에서 파일 쓰기와 권한 차단(BLOCKED)을 로그로 기록 | `.kiro/evidence/hook-events.log` |
| 병렬 서브에이전트 | Kiro Crew `spawn_run`으로 파일 경계가 다른 작업을 git worktree에 하나씩 배정해 동시 구현 후 병합 | 아래 "구현 웨이브" |
| 교차 리뷰 | 구현은 Claude, 리뷰는 GPT 5.6 Terra(읽기 전용: 셸·쓰기 도구 없음). spec과 코드 모두 리뷰 | 아래 "교차 리뷰" |
| 스케줄 알림 | Kiro Crew cron 일회성 알림 5개로 컷 판단·구현 마감·기능 동결·녹화·마감 직전 알림 | `HACKATHON.md` 일정 |
| 메모리·교훈 | 사용자 지시("항상 한국어", "서브에이전트는 Kiro 백엔드로")를 Kiro Crew 교훈으로 저장 | Kiro Crew lessons |

## 1. Spec 주도 개발
주제 공개 후 아이디어 논의 → `scripts/kiro.sh spec`(kirothon-spec, claude-opus-5.5, effort high)으로 명세 작성.
사용자 결정이 바뀔 때마다 구현이 아니라 spec을 먼저 바꿨다.

| 실행 | 사용자 결정 | 결과 |
|---|---|---|
| spec 1 | 순수 무작위 노출, AI 평가 없음, PDF만 | 텍스트만 노출하는 설계 → 중단 |
| spec 2 | "디자인도 평가받아야 한다" → 페이지 이미지 렌더링 + 검은 박스 마스킹 | 중단(평가 방식도 바뀜) |
| spec 3 | "이력서 위에 클릭해 첨삭 + 총평·총점" | 요구사항 14개, 인수 조건 97개, 작업 15개, 열린 질문 4개 |
| 이후 | 열린 질문 확정, MVP를 "마스킹 + 익명 평가"로 좁힘, 교차 리뷰 반영 | 작업 16개(핵심 11, 배포·녹화 2, 확장 3) |

요구사항은 EARS(`WHEN … THE SYSTEM SHALL …`), 작업은 `_요구사항: x.y_`로 추적한다. 병렬 구현 전에 API JSON 계약과 공유 타입(`PdfTypes.java`)을 설계에 고정해 에이전트끼리 형식이 어긋나지 않게 했다.

## 2. 역할별 에이전트와 모델 선택
같은 작업이라도 성격에 따라 모델과 추론 깊이를 나눴다. 비싼 모델은 명세와 리뷰에만 썼다.

| 역할 | 모델 / 추론 | 쓰기 범위(에이전트 권한 규칙) |
|---|---|---|
| spec | claude-opus-5.5 / high | 문서만. `src/**`, `tests/**`, `scripts/**`, `e2e/**` 쓰기 거부 |
| impl | claude-sonnet-5.5 / medium | 전체 |
| test | claude-sonnet-5.5 / low | `src/main/**`, spec, steering 쓰기 거부 |
| check | claude-haiku-5.5 / low | 쓰기 도구 없음(빌드·테스트 실행과 보고만) |
| review | gpt-5.6-terra / high | 셸·쓰기 도구 없음. diff는 `kiro.sh`가 `.kiro/review/CHANGES.md`로 준비 |

권한 규칙은 `--trust-all-tools`로 실행해도 유지되는 것을 실제로 확인했다: test 에이전트에게 `src/main`에 파일을 만들라고 시키면 `Blocked by a permission rule`로 거부되고, hook 로그에 `BLOCKED`로 남는다. 처음 만든 review 에이전트가 셸로 파일을 만들 수 있다는 것을 발견해 셸을 제거했다.

## 3. 구현 웨이브 (Kiro Crew `spawn_run`, 모두 Kiro 백엔드)
작업마다 git worktree와 브랜치를 따로 주고, 끝나면 main에 병합한 뒤 전체 테스트를 다시 돌렸다.

| 웨이브 | 병렬 작업 | 결과 |
|---|---|---|
| 1 | 작업 1 골격·익명 토큰 | 테스트 11개, Boot 4.1.2 / Gradle 8.14.3(SHA-256 고정) / PDFBox 3.0.7 |
| 2 | 작업 2 PDF 추출, 3 마스킹 영역, 4 렌더링 (3개 동시) | 각 2~4분, 병합 후 테스트 90개 |
| 3 | 작업 5+6 업로드·게시 API, 9 업로드·평가 화면, 10 내 이력서 화면, 11 샘플 PDF (4개 동시) | 병합 후 테스트 115개, 샘플 PDF 한글 렌더링·마스킹 확인 |
| 4 | 작업 7+8 평가 API, 13 Docker 배포, 코드 교차 리뷰 (3개 동시) | 진행 기록은 `KIRO-USAGE.md` |

## 4. 교차 리뷰
| 대상 | 리뷰어 | 지적 | 처리 |
|---|---|---|---|
| spec | gpt-5.6-terra / high | 5건(높음 2, 중간 3) | 반영 3(평가 제출 응답 본문 제거, E2E 시나리오 구체화, 샘플 PDF 리소스화), 확장 작업 조건으로 기록 2, 범위 재조정으로 기각 1 |
| 백엔드 코드 1차 | gpt-5.6-terra / high (`kiro.sh review`) | 높음 5건 | 4건을 워크플로 1라운드 입력으로, 1건(평가 API 없음)은 구현 중이라 해당 없음 |
| 리뷰→수정→테스트 워크플로 `wf_000002` | 수정 sonnet-5.5 / 테스트 haiku-5.5 / 리뷰 gpt-5.6-terra, 3라운드 | 1라운드 4건 수정, 2라운드 2건 수정, 3라운드 추정 1건 보류 | 테스트 135→141개, 모든 라운드 빌드 성공. 남은 중간 2건: 1건은 명세 쪽을 구현에 맞춰 고침(후보 없음 204), 1건은 같은 주인의 동시 요청이 안전하게 거부되는 경우라 한계로 기록 |

### 리뷰→수정→테스트 워크플로
`.kiro/workflows/review-fix-loop.py`(Kiro Crew dynamic workflow). 라운드마다 수정(유일한 쓰기) → 테스트(읽기) → 교차 리뷰(읽기, 다른 모델 계열)를 돌리고, 빌드 성공 + 리뷰 clean이면 끝난다. 아니면 리뷰 지적과 실패 테스트가 다음 라운드 입력이 된다. 최대 3라운드.

## 5. 운영: 시간 관리와 기록
- 마감(KST 18:00)에서 거꾸로 일정을 잡고 Kiro Crew cron 알림 5개를 등록했다.
- 모든 Kiro 기능 사용은 `python3 scripts/kiro_log.py add`로 `KIRO-USAGE.md`에 남기고, 실패·중단한 시도도 기록했다.
- `python3 scripts/kiro_log.py status`는 spec 진행률과 아직 쓰지 않은 핵심 기능을 보여 준다(kiro-cli 세션 시작 hook에서도 실행).

## 재현 방법
```sh
python3 scripts/tools.py doctor                 # JDK·kiro-cli 로그인·에이전트 설정 검증
scripts/kiro.sh spec "명세 작업 지시"              # 역할별 에이전트 실행(impl|spec|test|check|review)
scripts/kiro.sh review "리뷰 지시"                 # 읽기 전용 교차 리뷰
python3 scripts/kiro_log.py status              # spec 진행률과 Kiro 기능 기록 현황
```
