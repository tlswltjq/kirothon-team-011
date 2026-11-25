# 설계: 배포 가능 수준 고도화

## 병렬 작업 경계 (파일 소유권)
작업마다 별도 git worktree(`.worktrees/<작업>`, 브랜치 `prod/<작업>`)에서 한다. 공용 파일은 아래처럼 섹션만 추가하고, 병합 충돌은 오케스트레이터가 해결한다.

| 작업 | 소유 파일 | 공용 파일에서 손대는 부분 |
|---|---|---|
| A persistence | `src/main/resources/db/**`, `application-prod.properties`의 datasource·jpa 줄, 엔터티(스키마 맞춤만), Testcontainers 테스트 | `build.gradle` 의존성, `application.properties`의 datasource·jpa 줄 |
| B security | `security/**`, `web/**`, 보안·rate limit 테스트 | `build.gradle` 의존성(spring-boot-starter-security) |
| C ops | `web/RequestIdFilter`는 B와 겹치므로 `ops/**` 패키지에 둔다, 로그 설정, ops 테스트 | `build.gradle`(micrometer-registry-prometheus), `application.properties`·`application-prod.properties`의 management·logging·shutdown 줄 |
| D container-ci | `Dockerfile`, `compose.prod.yaml`, `.github/**`, `DEPLOY.md`, `.env.example` | 없음 |
| E api-docs | `docs/openapi` 설정 클래스(`apidocs/**` 패키지), 컨트롤러의 OpenAPI 어노테이션 | `build.gradle`(springdoc), `application*.properties`의 springdoc 줄 |

## 공통 계약 (모든 작업이 따른다)
- 프로필: 기본(로컬·데모, H2), `test`(테스트), `demo`(기존), `prod`(운영, PostgreSQL).
- 환경 변수: `SERVER_ADDRESS`(기본 127.0.0.1; 컨테이너 안에서는 0.0.0.0, 호스트 공개는 127.0.0.1만), `SERVER_PORT`(18080), `MANAGEMENT_PORT`(prod 18081), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DATA_DIR`, `API_DOCS_ENABLED`(prod 기본 false), `RATE_LIMIT_USERS_PER_MIN`(기본 10), `RATE_LIMIT_UPLOADS_PER_MIN`(기본 20).
- 헬스체크 URL: prod는 `http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/readiness`, 기본 프로필은 `http://127.0.0.1:18080/actuator/health`.
- Spring Security가 들어오면 `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, 정적 자원, `POST /api/users`, `GET /api/demo`는 인증 없이 허용한다(E 작업 경로는 B가 미리 허용 목록에 넣는다).
- 오류 JSON 형식은 기존 `ApiError`(`{code,message,fields}`)를 그대로 쓴다.
- 기존 테스트(`./gradlew test`)는 전부 통과해야 한다. 기능·화면은 바꾸지 않는다.

## 결정
- 마이그레이션: Flyway(Spring Boot 기본 지원, SQL 파일로 리뷰 쉬움). Liquibase보다 단순.
- Rate limit: 외부 의존성 없이 IP별 고정 창(분 단위) 카운터를 `ConcurrentHashMap`으로 구현하거나 Bucket4j. 단일 인스턴스 기준이며 다중 인스턴스는 리버스 프록시 한도로 보완한다고 DEPLOY.md에 적는다. 클라이언트 IP는 `server.forward-headers-strategy=native` 설정 시 프록시 헤더를 신뢰한다(prod만).
- 로그: Spring Boot 내장 구조화 로그(`logging.structured.format.console=ecs`).
- 이미지: 빌드 스테이지 `eclipse-temurin:21-jdk`, 실행 스테이지 기존 JRE digest 유지, `fonts-noto-cjk` 유지.
