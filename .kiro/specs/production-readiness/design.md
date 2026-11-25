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
- 환경 변수: `SERVER_ADDRESS`(기본 127.0.0.1; 컨테이너 안에서는 0.0.0.0, 호스트 공개는 127.0.0.1만), `SERVER_PORT`(18080), `MANAGEMENT_PORT`(prod 18081), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DATA_DIR`, `API_DOCS_ENABLED`(prod 기본 false), `RATE_LIMIT_USERS_PER_MIN`(기본 10), `RATE_LIMIT_UPLOADS_PER_MIN`(기본 20), `MANAGEMENT_ADDRESS`(prod 기본 127.0.0.1; 컨테이너 안 0.0.0.0), `TRUSTED_PROXIES`(prod 기본 `127\.0\.0\.1|0:0:0:0:0:0:0:1`, compose `172\.30\.0\.1`), `APP_SUBNET`(compose 기본 172.30.0.0/24), `APP_GATEWAY`(172.30.0.1, `TRUSTED_PROXIES`와 맞춤), `DB_POOL_SIZE`(기본 10), `HEALTHCHECK_URL`(이미지 HEALTHCHECK 대상).
- smoke: 기본 프로필 `--port 18080`, prod `--port 18080 --management-port 18081`.
- 헬스체크 URL: prod는 `http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/readiness`, 기본 프로필은 `http://127.0.0.1:18080/actuator/health`.
- Spring Security가 들어오면 `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, 정적 자원, `POST /api/users`, `GET /api/demo`는 인증 없이 허용한다(E 작업 경로는 B가 미리 허용 목록에 넣는다).
- 오류 JSON 형식은 기존 `ApiError`(`{code,message,fields}`)를 그대로 쓴다.
- 기존 테스트(`./gradlew test`)는 전부 통과해야 한다. 기능·화면은 바꾸지 않는다.

## 결정
- 마이그레이션: Flyway(Spring Boot 기본 지원, SQL 파일로 리뷰 쉬움). Liquibase보다 단순.
- Rate limit: 외부 의존성 없이 IP별 고정 창(분 단위) 카운터를 `ConcurrentHashMap`으로 구현하거나 Bucket4j. 단일 인스턴스 기준이며 다중 인스턴스는 리버스 프록시 한도로 보완한다고 DEPLOY.md에 적는다. 카운트는 키별 `ConcurrentHashMap.compute` 안에서 창 선택과 함께 원자적으로 늘리고, 창은 앞으로만 바꾼다(R2·R4). 클라이언트 IP: prod는 `server.forward-headers-strategy=native`로 `X-Forwarded-For`를 받되 `server.tomcat.remoteip.internal-proxies=${TRUSTED_PROXIES}`에 맞는 직전 홉에서 온 것만 믿는다(Tomcat 기본은 사설 IP 전부라 위조 가능, R3). compose는 고정 서브넷 게이트웨이(docker userland-proxy가 접속하는 주소)만 믿으며, 배포 경로가 다르면 실측 후 바꾼다(R5).
- 로그: Spring Boot 내장 구조화 로그(`logging.structured.format.console=ecs`).
- 기존 H2 데이터(교차 리뷰 R1, 2026-11-25 사용자 확정 A): Flyway 도입 전 `ddl-auto=update`로 만든 H2 파일 DB는 이전하지 않는다. 해커톤 데모 데이터뿐이라 버리고 새로 시작하며, 절차는 DEPLOY.md 1.4에 둔다. 이전 도구는 만들지 않는다.
- 이미지: 빌드 스테이지 `eclipse-temurin:21-jdk`, 실행 스테이지 기존 JRE digest 유지, `fonts-noto-cjk` 유지.
