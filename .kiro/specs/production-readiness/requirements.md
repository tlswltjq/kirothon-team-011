# 요구사항: 배포 가능 수준 고도화 (production-readiness)

배경: MVP(`.kiro/specs/resume-review/`)는 localhost 데모용이다(H2 파일 DB, `ddl-auto=update`, 수제 필터, 호스트 네트워크 컨테이너, CI 없음). 이 spec은 기능을 바꾸지 않고 운영 배포에 필요한 프레임워크·인프라를 적용한다(2026-11-25 사용자 지시 "프레임워크 등을 적용해서 배포 가능한 수준으로 고도화", "최대한 병렬").
범위 밖: 실제 회원 인증, 공개 배포 실행, 클라우드 리소스 생성, 기능 변경. 서버 바인딩 규칙(호스트에서는 127.0.0.1만)은 유지한다.

## 1. 영속성: 스키마 마이그레이션과 운영 DB
- 1.1 WHEN 앱이 기동하면 THE SYSTEM SHALL Flyway 마이그레이션(`db/migration`)으로 스키마를 만들고, Hibernate는 `ddl-auto=validate`로 엔터티와 스키마 일치만 검사한다.
- 1.2 WHEN `prod` 프로필이면 THE SYSTEM SHALL PostgreSQL을 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD` 환경 변수로 연결하고, 값이 없으면 기동에 실패한다.
- 1.3 WHEN 기본·테스트 프로필이면 THE SYSTEM SHALL H2를 계속 쓰되 같은 Flyway 마이그레이션을 적용한다.
- 1.4 THE SYSTEM SHALL 실제 PostgreSQL(Testcontainers)에서 마이그레이션·validate·핵심 저장소 동작을 검증하는 테스트를 가진다.

## 2. 보안: Spring Security
- 2.1 THE SYSTEM SHALL 익명 토큰 인증을 Spring Security `SecurityFilterChain`의 인증 필터로 처리하고, 기존 API 동작(401 `UNAUTHENTICATED` JSON, 인증 예외 경로)을 바꾸지 않는다.
- 2.2 THE SYSTEM SHALL CSP·nosniff·frame-ancestors 등 보안 헤더를 Spring Security 헤더 설정으로 낸다(값은 기존과 같거나 더 엄격).
- 2.3 WHEN 같은 클라이언트 IP가 `POST /api/users` 또는 업로드 API를 설정된 한도보다 자주 호출하면 THE SYSTEM SHALL 429 `RATE_LIMITED` JSON과 `Retry-After`로 거부한다.
- 2.4 THE SYSTEM SHALL 세션을 만들지 않고(STATELESS), CSRF는 헤더 토큰 API이므로 끄되 이유를 코드 주석으로 남긴다.

## 3. 운영 관측
- 3.1 THE SYSTEM SHALL liveness·readiness 프로브(`/actuator/health/liveness`, `/actuator/health/readiness`)를 제공한다.
- 3.2 WHEN `prod` 프로필이면 THE SYSTEM SHALL actuator를 별도 관리 포트(`MANAGEMENT_PORT`, 기본 18081)에 두고 Prometheus 지표(`/actuator/prometheus`)를 노출한다.
- 3.3 WHEN `prod` 프로필이면 THE SYSTEM SHALL 구조화(JSON) 로그를 내고, 모든 요청에 `X-Request-Id`(없으면 생성)를 응답 헤더와 로그 MDC에 넣는다. 본문·토큰은 로그에 남기지 않는다.
- 3.4 THE SYSTEM SHALL graceful shutdown을 켠다.

## 4. API 문서
- 4.1 THE SYSTEM SHALL OpenAPI 3 문서(`/v3/api-docs`)와 Swagger UI를 제공하고, 익명 토큰 헤더 보안 스킴을 문서화한다.
- 4.2 WHEN `prod` 프로필이면 THE SYSTEM SHALL API 문서를 기본으로 끈다(`API_DOCS_ENABLED=true`로만 켬).

## 5. 컨테이너와 CI/CD
- 5.1 THE SYSTEM SHALL 멀티 스테이지 Dockerfile(컨테이너 안 Gradle 빌드, 계층화 jar, 비루트, HEALTHCHECK)을 가진다.
- 5.2 THE SYSTEM SHALL `compose.prod.yaml`로 PostgreSQL + 앱을 띄우며, 호스트 공개는 `127.0.0.1`에만 하고 비밀은 `.env`(커밋 금지)로 받는다.
- 5.3 THE SYSTEM SHALL GitHub Actions CI(test·bootJar·Docker 이미지 빌드)와 Dependabot 설정을 가진다.
- 5.4 THE SYSTEM SHALL `DEPLOY.md`에 운영 배포 절차, 환경 변수 표, 백업·롤백 방법을 적는다.
