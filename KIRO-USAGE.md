# Kiro 활용 기록

`python3 scripts/kiro_log.py add`로만 추가한다. 실패한 시도도 기록한다.

> 기록 시각 안내: `2026-11-14T02:44:32Z`, `2026-11-14T07:28:40Z`처럼 같은 시각이 여러 행에 붙은 것은 앞선 작업들을 그 시각에 한꺼번에 기록한 것이다. 이 경우 행 순서가 실제 작업 순서다. 당시 저장소 `tlswltjq/kirothon`은 운영자료 확인 후 템플릿 기반 제출 저장소 `tlswltjq/kirothon-team-011`로 옮겼다.

| UTC | 단계 | 기능 | 참조 | 결과 |
|---|---|---|---|---|
| 2026-11-14T02:44:32Z | 2. 명세 | spec | kiro.sh spec run1 (kirothon-spec, claude-opus-5.5/high) | 중단: 텍스트 노출 전제 → 사용자가 렌더링 마스킹으로 변경. product/requirements/design 초안 생성 |
| 2026-11-14T02:44:32Z | 2. 명세 | spec | kiro.sh spec run2 (claude-opus-5.5/high) | 중단: 평가 방식(핀 첨삭+총평/총점) 변경을 합쳐 재실행 |
| 2026-11-14T02:44:32Z | 2. 명세 | spec | kiro.sh spec run3 (claude-opus-5.5/high), .kiro/specs/resume-review/ | 성공: 요구사항 14개(인수 조건 97), 작업 15개, 열린 질문 4개 |
| 2026-11-14T02:44:32Z | 2. 명세 | steering | .kiro/steering/product.md (spec 에이전트 작성), tech/kiro-workflow/agents/java | 성공: always 4개 + fileMatch 1개 |
| 2026-11-14T02:44:32Z | 2. 명세 | hook | .kiro/evidence/hook-events.log | 성공: spec 에이전트 파일 쓰기 16건 기록 |
| 2026-11-14T02:50:33Z | 2. 명세 | review | kiro.sh review (kirothon-review, gpt-5.6-terra/high), spec 교차 리뷰 | 지적 5건(높음 2, 중간 3). 반영 3: 평가 제출 201 본문 없음, E2E 시나리오 구체화, 샘플 PDF 리소스화. 복구 코드 설계 보완 2건은 확장 작업 12 착수 조건으로 기록. 기각 2: SVG·diff MVP 유지 요구(사용자가 핵심 범위 재조정) |
| 2026-11-14T02:51:03Z | 3. 구현 | spawn_run | 작업 1 impl (claude-sonnet-5.5/medium) | 성공: ./gradlew test bootJar 종료 코드 0, 테스트 11개 통과, 기동 후 health UP |
| 2026-11-14T02:58:47Z | 3. 구현 | spawn_run | 작업 2·3·4 병렬 (sonnet-5.5/medium ×3, git worktree 3개) | 성공: 각 2~4분, 병합 후 전체 테스트 90개 통과 |
| 2026-11-14T03:07:23Z | 3. 구현 | spawn_run | 작업 5+6·9·10·11(일부) 병렬 4개 (sonnet-5.5/medium, worktree 4개) | 작업 9·10 완료·병합(정적 파일 11개, CSP 위반 없음). 5+6·11 진행 중 |
| 2026-11-14T03:09:49Z | 5. 운영 | cron | Kiro Crew cron 일회성 알림 5개 (df1eff59, 563d2906, 9f7116e8, a54d66d7, 1c6b6bf6) | 등록 성공: 14:30·15:45·17:00·17:20·17:45 KST 대시보드 알림 |
| 2026-11-14T03:11:57Z | 3. 구현 | spawn_run | 작업 5+6, 11(샘플 PDF) (sonnet-5.5/medium, kiro backend) | 성공: 병합 후 테스트 115개 통과, 샘플 PDF 4개 한글 렌더링·마스킹 눈 확인 |
| 2026-11-14T03:14:54Z | 6. 제출 | artifact | https://github.com/tlswltjq/kirothon (kirothon-2026), README.md, docs/KIRO.md | 공개 저장소 게시. 커밋 이메일 noreply로 정리 |
| 2026-11-14T03:17:21Z | 4. 검증 | spawn_run | 작업 13 Docker 배포 (sonnet-5.5/medium, kiro backend) | 성공: compose up, smoke 통과, 이미지 467MB(fonts-noto-cjk). 업로드 확인은 curl -F가 보안 정책(반출 패턴)에 차단 → E2E로 대체 |
| 2026-11-14T03:17:21Z | 4. 검증 | review | kiro.sh review 코드 교차 리뷰 1 (gpt-5.6-terra/high) | 높음 5건: 게시-초안 교체 경쟁, 원본 만료 미강제, 페이지 크기 무제한 메모리, 타임아웃 후 스레드 누적(추정), 평가 API 없음(구현 중). 1~4 수정 예정 |
| 2026-11-14T03:21:55Z | 3. 구현 | spawn_run | 작업 7+8 평가 API (sonnet-5.5/medium, kiro backend) | 성공: 병합 후 테스트 135개 통과(동시 중복 제출·좌표 경계·접근 경계 포함) |
| 2026-11-14T03:37:06Z | 4. 검증 | workflow_run | wf_000002 review-fix-loop (.kiro/workflows/review-fix-loop.py), 3라운드 13분 40초 | 수정 6건(게시 경쟁 잠금, 원본 만료 강제, 페이지 크기 상한, 공용 PDF 실행기 503, 버전 업로드 직렬화, 평가 대상 확인 순서), 테스트 141개 통과. 남은 중간 2건: 6.6 명세 정정, 동일 주인 동시 요청은 한계로 기록. PDFBox 인터럽트 미보장은 추정·보류 |
| 2026-11-14T03:47:58Z | 4. 검증 | spawn_run | 작업 11 샘플 시드·E2E·녹화 스크립트 (sonnet-5.5/medium, kiro backend, 22분 48초) | 성공: 병합 후 테스트 148개, Playwright E2E 1개 통과, 시험 녹화 1분 41초. 스크린샷 직접 확인으로 화면 결함 2건(평가 화면 'null' 표시, 내 이력서 메뉴 불일치) 발견·수정 후 E2E 재통과 |
| 2026-11-14T04:07:06Z | 4. 검증 | browser | Playwright 컨테이너 headless Chromium으로 실제 이력서 3개 업로드, 마스킹 미리보기 캡처 (demo-private, 커밋 안 함) | 결함 2건 발견·수정: 괄호 국가번호 전화 형식 미검출, 이름 후보에 가린 이메일 노출. 테스트 150개 통과 |
| 2026-11-14T04:17:24Z | 6. 제출 | artifact | PRESENTATION.pdf (presentation/slides.html → Playwright Chromium PDF, 9쪽) | 생성·페이지 이미지 직접 확인. 실제 이력서 데모 리허설 92.9초 MP4 생성, 프레임 확인으로 이름·연락처·사진 노출 없음 |
| 2026-11-14T05:44:22Z | 4. 검증 | review | gpt-5.6-terra high (5b94e2b3f1460c64), 화면 개편 diff | 지적 10건(높음2·중간5·낮음3), 그중 1건 기각: E2E 스크롤 지적은 E2E 통과로 기각, 나머지 8건 impl에 위임 |
| 2026-11-14T06:25:00Z | 3. 구현 | spawn_run | 팀 6역할: 아트디렉터(opus)·UX라이터·접근성감사(gpt)·데모프로듀서 -> 구현 W1~W6 -> QA | 문서 4개 -> 구현 6개 병렬 합본, 테스트 150 통과, E2E·데모 통과(스크립트 선택자 수정 2건) |
| 2026-11-14T07:28:40Z | 2. 명세 | workflow_run | wf_000003 kirothon-ux-design-loop (설계 claude-opus-5.5 <-> 평가 gpt-5.6-terra, kiro backend), .kiro/specs/resume-review/ux-flow.md | 성공: 3라운드, 평균 7.0->5.1->4.57(평가자가 설계가 아닌 현재 구현을 채점). 결과 ux-flow.md |
| 2026-11-14T07:28:40Z | 3. 구현 | spawn_run | UX T1~T5 병렬 5개(worktree) + T6 E2E 1개 (kiro backend) | 성공: E2E 통과, 테스트 150개 |
| 2026-11-14T07:28:40Z | 3. 구현 | spawn_run | 디자인 D1~D4 병렬 4개 + spawn_steer로 실행 중 'Mobile-first' 지시를 4개에 주입 | 성공: 375/1280px 가로 넘침 0 |
| 2026-11-14T07:28:40Z | 4. 검증 | spawn_run | 리뷰 지적 수정 1개 (acb20e1171cb0a8f) | 성공: 8건 반영 |
| 2026-11-14T07:28:40Z | 2. 명세 | spawn_run | 기획·검토 팀 4명: 아트디렉터 claude-opus-5.5(1b97dad2f860c6ed), UX 라이터 sonnet(a1570f84ce2b7c91), 접근성 감사 gpt-5.6-terra(f4257f1c8e1f0377), 데모 프로듀서 gpt-5.6-terra(66be201cb27cc0e0) | 성공: 문서 4개 .kiro/specs/resume-review/team/ |
| 2026-11-14T07:28:40Z | 4. 검증 | spawn_run | QA 1개 (704c5f22d57809cd) | 성공: E2E·데모 스크립트 선택자 2건 수정 후 통과, 모바일 게시 바 201px 측정 |
| 2026-11-14T07:28:40Z | 4. 검증 | review | gpt-5.6-terra high (13890111c3b49c92), 팀 반영분 교차 리뷰 | 지적 9건 중 8건 반영, 1건(visible=true 선택자)은 실제 테스트 통과로 기각 |
| 2026-11-14T07:28:40Z | 3. 구현 | spawn_run | 병렬 3개: F1(a50d3e5aae44e88f), G1(e6d075635653c658), G2(877314c1ff5f2d11) | 성공: F1 모바일 게시 바 201->96px, G1 키보드 핀 좌표 검증·알림 중복 제거, G2 데모 이름 가림·콜드오픈 구간 |
| 2026-11-14T07:28:40Z | 4. 검증 | browser | Playwright 실제 이력서 최종 녹화 79초 MP4, YouTube youtu.be/NpGPTHtYJJQ | 프레임 점검으로 영문 이름·가릴 단어 칩 실명 노출 2건 발견 -> 수정 후 재녹화 성공 |
| 2026-11-14T07:28:40Z | 4. 검증 | browser | 운영자료 Notion 재확인 시도 | 실패: 스냅샷에 제목만, iframe 때문에 스크린샷 차단 |
| 2026-11-14T07:28:40Z | 3. 구현 | spawn_run | 화면 마무리 1개 (871608db6b4359f4) | 성공: my.css 2건 수정 |
| 2026-11-14T07:28:40Z | 6. 제출 | artifact | PRESENTATION.pdf 재생성, 슬라이드별 PNG presentation/slides/ 9장, 발표 대본 presentation/script.md | 성공: 생성 완료 |
