# 구현 계획: 배포 가능 수준 고도화

1~5는 파일 경계가 달라 한 번의 `spawn_run(tasks=[...])`로 병렬 실행한다(각자 worktree). 6 이후는 병합 뒤 순서대로.

- [x] 1. A persistence: Flyway V1 스키마, `ddl-auto=validate`, prod PostgreSQL, Testcontainers 테스트
  - _요구사항: 1.1, 1.2, 1.3, 1.4_
- [x] 2. B security: Spring Security 필터 체인, 보안 헤더, IP별 rate limit(429), STATELESS
  - _요구사항: 2.1, 2.2, 2.3, 2.4_
- [x] 3. C ops: liveness/readiness, prod 관리 포트 + Prometheus, 구조화 로그, X-Request-Id, graceful shutdown
  - _요구사항: 3.1, 3.2, 3.3, 3.4_
- [x] 4. D container-ci: 멀티 스테이지 Dockerfile, `compose.prod.yaml`, GitHub Actions CI, Dependabot, DEPLOY.md
  - _요구사항: 5.1, 5.2, 5.3, 5.4_
- [x] 5. E api-docs: springdoc OpenAPI + Swagger UI, 토큰 보안 스킴, prod 기본 끔
  - _요구사항: 4.1, 4.2_
- [x] 6. 통합: 브랜치 병합, 충돌 해결, `./gradlew test bootJar`
- [x] 7. 검증(독립): `compose.prod.yaml` 기동 → smoke → readiness·prometheus·429·헤더 확인
- [x] 8. 교차 리뷰(작성과 다른 모델) → 지적 반영 → 재검증
