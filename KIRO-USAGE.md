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
