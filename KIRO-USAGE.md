# Kiro 활용 기록

`python3 scripts/kiro_log.py add`로만 추가한다. 실패한 시도도 기록한다.

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
| 2026-11-14T03:09:49Z | 5. 운영 | monitor | Kiro Crew cron 일회성 알림 5개 (df1eff59, 563d2906, 9f7116e8, a54d66d7, 1c6b6bf6) | 등록 성공: 14:30·15:45·17:00·17:20·17:45 KST 대시보드 알림 |
| 2026-11-14T03:11:57Z | 3. 구현 | spawn_run | 작업 5+6, 11(샘플 PDF) (sonnet-5.5/medium, kiro backend) | 성공: 병합 후 테스트 115개 통과, 샘플 PDF 4개 한글 렌더링·마스킹 눈 확인 |
| 2026-11-14T03:14:54Z | 6. 제출 | artifact | https://github.com/tlswltjq/kirothon (kirothon-2026), README.md, docs/KIRO.md | 공개 저장소 게시. 커밋 이메일 noreply로 정리 |
| 2026-11-14T03:17:21Z | 4. 검증 | spawn_run | 작업 13 Docker 배포 (sonnet-5.5/medium, kiro backend) | 성공: compose up, smoke 통과, 이미지 467MB(fonts-noto-cjk). 업로드 확인은 curl -F가 보안 정책(반출 패턴)에 차단 → E2E로 대체 |
| 2026-11-14T03:17:21Z | 4. 검증 | review | kiro.sh review 코드 교차 리뷰 1 (gpt-5.6-terra/high) | 높음 5건: 게시-초안 교체 경쟁, 원본 만료 미강제, 페이지 크기 무제한 메모리, 타임아웃 후 스레드 누적(추정), 평가 API 없음(구현 중). 1~4 수정 예정 |
| 2026-11-14T03:21:55Z | 3. 구현 | spawn_run | 작업 7+8 평가 API (sonnet-5.5/medium, kiro backend) | 성공: 병합 후 테스트 135개 통과(동시 중복 제출·좌표 경계·접근 경계 포함) |
