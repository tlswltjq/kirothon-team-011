# 배포 문서

이 문서는 두 가지 배포를 다룬다.

1. **운영 배포**: `compose.prod.yaml`로 PostgreSQL + 앱(`prod` 프로필)을 한 서버에 띄운다. 앞단 리버스 프록시가 TLS를 끝내고 `127.0.0.1:18080`으로 넘긴다.
2. **로컬 데모**: `compose.yaml`로 H2 파일 DB를 쓰는 데모(기본 프로필 + `demo`)를 띄운다. 공개 배포가 아니다.

명세: `.kiro/specs/production-readiness/` (요구사항 5.1~5.4).

## 1. 운영 배포

### 1.1 구성도

```text
인터넷
  │ HTTPS :443
  ▼
리버스 프록시 (nginx·Caddy 등, TLS 종료, 요청 한도, X-Forwarded-For 설정)
  │ HTTP 127.0.0.1:18080          ← 호스트 loopback에만 공개
  ▼
┌──────────────── docker compose (compose.prod.yaml) ────────────────┐
│ app  (resume-review, 비루트 10001, read_only, cap_drop ALL)        │
│   :18080  서비스 API·정적 화면                                     │
│   :18081  관리 포트(actuator health·prometheus) → 127.0.0.1:18081 │
│   /data   볼륨 appdata (DATA_DIR)                                  │
│      │ jdbc:postgresql://db:5432/${POSTGRES_DB} (내부 네트워크)    │
│      ▼                                                             │
│ db   (postgres:16, 호스트 포트 공개 없음)                          │
│   볼륨 pgdata                                                       │
└─────────────────────────────────────────────────────────────────────┘
모니터링(Prometheus 등) ──▶ 127.0.0.1:18081/actuator/prometheus  (내부 전용)
```

- 앱 컨테이너 안에서는 `SERVER_ADDRESS=0.0.0.0`으로 바인딩하고, 호스트 공개는 `127.0.0.1:18080`, `127.0.0.1:18081`만 한다. 외부 포트를 직접 열거나 방화벽을 바꾸지 않는다.
- 관리 포트(18081)는 리버스 프록시로 노출하지 않는다. 같은 서버의 모니터링 에이전트만 접근한다.
- DB는 compose 내부 네트워크에서만 접근한다.

### 1.2 환경 변수

`.env.example`을 복사해 `.env`를 만든다(`.env`는 `.gitignore`에 있어 커밋되지 않는다).

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (compose.prod.yaml에서 `prod`) | 프로필. 기본(로컬·데모, H2), `test`, `demo`, `prod`(PostgreSQL) |
| `SERVER_ADDRESS` | `127.0.0.1` (이미지 안 `0.0.0.0`) | 바인딩 주소. 호스트에서 직접 실행할 때는 `127.0.0.1`만 쓴다 |
| `SERVER_PORT` | `18080` | 서비스 포트 |
| `MANAGEMENT_PORT` | `18081` (prod) | actuator 관리 포트. health(liveness·readiness)·prometheus |
| `DB_URL` | (compose가 `jdbc:postgresql://db:5432/${POSTGRES_DB}`로 만듦) | prod 필수. 없으면 기동 실패 |
| `DB_USERNAME` | (compose가 `${POSTGRES_USER}`로 채움) | prod 필수 |
| `DB_PASSWORD` | (compose가 `${POSTGRES_PASSWORD}`로 채움) | prod 필수 |
| `DATA_DIR` | `./data` (이미지 안 `/data`) | 파일 데이터 경로(기본 프로필은 H2 파일 DB도 여기) |
| `API_DOCS_ENABLED` | prod `false` | `true`일 때만 `/v3/api-docs`, Swagger UI 제공 |
| `RATE_LIMIT_USERS_PER_MIN` | `10` | IP별 분당 `POST /api/users` 한도 |
| `RATE_LIMIT_UPLOADS_PER_MIN` | `20` | IP별 분당 업로드 API 한도 |
| `POSTGRES_DB` | 없음(필수) | compose 전용. DB 이름 |
| `POSTGRES_USER` | 없음(필수) | compose 전용. DB 사용자 |
| `POSTGRES_PASSWORD` | 없음(필수) | compose 전용. DB 비밀번호. 길고 무작위인 값 |
| `APP_IMAGE` | `resume-review:prod` | compose 전용. 앱 이미지 태그(롤백에 사용) |
| `HEALTHCHECK_URL` | `http://127.0.0.1:18081/actuator/health/readiness` | 이미지 HEALTHCHECK 대상. 기본·demo 프로필은 `http://127.0.0.1:18080/actuator/health` |

`POSTGRES_*`가 비어 있으면 `docker compose`가 설정 단계에서 바로 실패한다(`${VAR:?}`).

### 1.3 배포 절차

```bash
# 1회: 비밀 파일 준비
cp .env.example .env && chmod 600 .env
# .env에서 POSTGRES_PASSWORD 등을 실제 값으로 바꾼다.

# 설정 검사
docker compose -f compose.prod.yaml --env-file .env config --quiet

# 빌드 + 기동 (이미지 안에서 Gradle로 bootJar를 만든다)
docker compose -f compose.prod.yaml --env-file .env up -d --build

# 상태 확인: app이 healthy가 될 때까지 기다린다(readiness)
docker compose -f compose.prod.yaml --env-file .env ps
curl -fsS http://127.0.0.1:18081/actuator/health/readiness
python3 scripts/tools.py smoke --port 18080

# 로그(JSON 구조화 로그)
docker compose -f compose.prod.yaml --env-file .env logs -f app
```

- 배포 전 버전 태그를 남겨 두면 롤백이 쉽다: `.env`의 `APP_IMAGE=resume-review:<git-sha>`로 정하고 `up -d --build`.
- 이미지는 멀티 스테이지로 만든다: build 스테이지(`eclipse-temurin:21-jdk-noble`)에서 `./gradlew bootJar -x test` 후 `java -Djarmode=tools -jar app.jar extract --layers --launcher`로 계층 추출, runtime 스테이지(`eclipse-temurin:21-jre-noble`, digest 고정)에 계층별 복사. 테스트는 CI(`./gradlew test bootJar`)에서 돈다.

### 1.4 DB 마이그레이션

- 앱이 기동할 때 Flyway가 `db/migration`의 SQL을 자동 적용한다. 별도 명령이 없다. Hibernate는 `ddl-auto=validate`로 스키마 일치만 검사한다.
- `db`가 `pg_isready`로 healthy가 된 뒤에 app이 뜬다(`depends_on: service_healthy`).
- 마이그레이션이 실패하면 app이 기동하지 않고 readiness가 올라오지 않는다. `logs app`에서 원인을 확인한다.
- 기존 H2 데이터와 호환되지 않는다: Flyway 도입 전(`ddl-auto=update`) 만든 H2 파일 DB(`./data`, 데모 볼륨 `kirothon_data`)에는 Flyway 이력 테이블이 없어 기동이 "non-empty schema" 오류로 멈춘다. 데모 데이터는 버리고 새로 시작한다: 로컬은 새 `DATA_DIR`을 쓰거나 `./data`를 비우고, 데모 컨테이너는 `docker compose -p kirothon down -v`로 볼륨을 지운다(데이터 삭제, 되돌릴 수 없음).

### 1.5 백업과 복구

```bash
# 백업 (custom 형식, 압축)
docker compose -f compose.prod.yaml --env-file .env exec -T db \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backup-$(date +%Y%m%d-%H%M%S).dump

# 복구 (앱을 먼저 멈춘다)
docker compose -f compose.prod.yaml --env-file .env stop app
docker compose -f compose.prod.yaml --env-file .env exec -T db \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < backup-YYYYMMDD-HHMMSS.dump
docker compose -f compose.prod.yaml --env-file .env start app
```

- 배포(특히 마이그레이션이 추가된 배포) 직전에 반드시 백업한다.
- 백업 파일에는 주인 전용 추출 텍스트와 평가가 들어 있다. 권한 600으로 두고 서버 밖으로 옮길 때 암호화한다.
- 초안 원본 PDF는 DB의 초안 테이블에 최대 24시간만 남는다. 백업 시점에 따라 덤프에 포함될 수 있으므로 백업 파일 보존 기간을 짧게 둔다.

### 1.6 롤백

```bash
# 이전 이미지 태그로 되돌린다
APP_IMAGE=resume-review:<이전-sha> docker compose -f compose.prod.yaml --env-file .env up -d --no-build app
```

- Flyway는 전진 전용이다. 새 버전이 마이그레이션을 적용한 뒤 이전 이미지로 돌아가면, 이전 코드가 새 스키마에서 `ddl-auto=validate`에 실패하거나 오동작할 수 있다.
  - 마이그레이션은 이전 버전과 호환되게(컬럼 추가는 nullable·기본값, 삭제·이름 변경은 다음 릴리스로 나눔) 작성한다.
  - 호환되지 않는 마이그레이션 뒤에 롤백해야 하면 1.5의 배포 전 백업을 복구한 뒤 이전 이미지로 띄운다(백업 이후 데이터는 잃는다).
- `flyway_schema_history` 행을 손으로 지우지 않는다.

### 1.7 운영 주의

- **Rate limit은 단일 인스턴스 기준이다.** IP별 카운터가 앱 메모리에 있으므로, 앱을 여러 개 띄우면 한도가 인스턴스 수만큼 늘어난다. 다중 인스턴스에서는 리버스 프록시(nginx `limit_req` 등)에 한도를 둔다. 프록시는 `X-Forwarded-For`를 덮어써서(클라이언트가 보낸 값을 그대로 넘기지 않게) 설정한다. prod는 `server.forward-headers-strategy=native`로 이 헤더를 받되, `TRUSTED_PROXIES`(정규식, 기본 `127\.0\.0\.1|0:0:0:0:0:0:0:1`; `compose.prod.yaml`은 고정 서브넷 게이트웨이 `172\.30\.0\.1`)에 맞는 직전 홉에서 온 경우만 믿는다. 다른 주소에서 온 헤더는 무시하고 접속 주소로 센다.
- **신뢰 프록시 주소는 실제 접속 경로로 확인한다.** 게이트웨이 주소를 믿는 기본값은 Docker userland-proxy(기본 켜짐)가 `127.0.0.1` 공개 포트 접속을 게이트웨이 주소에서 넘겨주는 경로를 전제로 한다(2026-11-25 실측). `userland-proxy: false`이거나 프록시를 컨테이너로 같은 네트워크에 두면 앱이 보는 직전 홉이 달라져 XFF가 무시되고 모든 사용자가 한 IP의 한도를 나눠 쓴다. 배포 뒤 프록시를 거친 요청 몇 개를 보내 IP별로 따로 세는지(예: 한도를 2로 낮추고 XFF만 바꿔 3회 → 모두 201) 확인하고, 아니면 앱 로그의 접속 주소에 맞춰 `TRUSTED_PROXIES`를 고친다.
- 고정 서브넷(`APP_SUBNET`, 기본 `172.30.0.0/24`)이 호스트의 다른 네트워크와 겹치면 `up`이 실패한다. 바꿀 때는 `APP_GATEWAY`와 `TRUSTED_PROXIES`(점은 `\.`로)를 같은 게이트웨이로 맞춘다.
- **원본 PDF 24시간 정리**: 초안 원본 PDF는 미리보기 재생성용으로 DB 초안 테이블에 최대 24시간 보관되고, 게시 시점에 지워지며 남은 것은 `DraftSourceCleaner`가 10분(`app.draft-source.cleanup-millis`)마다 만료분을 지운다. 이 동작은 운영(PostgreSQL)에서도 그대로다. 다중 인스턴스면 각 인스턴스가 같은 정리 작업을 돌리지만 만료 행 삭제라 결과는 같다.
- 종료는 graceful shutdown(요청 마무리 후 종료)이다. compose `stop_grace_period`는 40초다.
- 컨테이너는 비루트(10001), read_only 루트 파일시스템, tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, 메모리 768m이다.
- CI(`.github/workflows/ci.yml`)는 push·PR마다 `./gradlew test bootJar`와 Docker 이미지 빌드(push 없음)를 한다. Dependabot이 Gradle·Actions·Docker 베이스 이미지를 주간 점검한다.

## 2. 로컬 데모 (작업 13)

localhost 데모용이다. 공개 배포가 아니다. 앱은 호스트 네트워크에서 `127.0.0.1:18080`에만 바인딩한다(Linux 전용). 기본 프로필(H2) + `demo`.

### 2.1 실행

```bash
./gradlew --no-daemon test bootJar            # 선택: 테스트. 이미지는 컨테이너 안에서 따로 빌드한다
docker compose -p kirothon config --quiet
docker compose -p kirothon up -d --build
python3 scripts/tools.py smoke --port 18080
```

### 2.2 종료·로그·초기화

```bash
docker compose -p kirothon logs -f           # 로그
docker compose -p kirothon down              # 종료(데이터 볼륨 유지)
docker compose -p kirothon down -v           # 종료 + 데이터 볼륨 kirothon_data 삭제(데이터 초기화)
```

- 데이터: 이름 있는 볼륨 `kirothon_data`가 컨테이너의 `/data`(`DATA_DIR`)에 붙는다. H2 파일 DB가 여기 저장된다.
- 컨테이너는 비루트(10001), read_only 루트 파일시스템, tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, 메모리 768m이다.
- `fonts-noto-cjk`를 이미지에 설치했다(PDF에 글꼴이 없을 때 런타임 렌더링용).
- `SPRING_PROFILES_ACTIVE=demo`를 넣는다. demo 프로필 빈이 없어도 기동된다.
- HEALTHCHECK는 `HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health`로 덮어쓴다(기본 프로필은 관리 포트를 따로 두지 않는다).

### 2.3 확인 결과 (2026-11-14, 단일 스테이지 이미지 당시)

| 명령 | 종료 코드 / 결과 |
|---|---|
| `./gradlew --no-daemon test bootJar` | 0 (BUILD SUCCESSFUL) |
| `docker compose -p kirothon config --quiet` | 0 |
| 18080 포트 사용 여부 확인 | 비어 있음 |
| `docker compose -p kirothon up -d --build` | 0, 이미지 `kirothon:local` 467MB |
| `python3 scripts/tools.py smoke --port 18080` | 0 (기동 직후 8회 FAIL 후 OK, 약 16초) |
| `docker compose -p kirothon down` | 0 (볼륨 유지) |

### 2.4 미검증

- `POST /api/users` 후 `owner-v1.pdf` 업로드(201)와 페이지 PNG 200, 한글 렌더링 눈 확인은 실행하지 못했다.
  로컬 PDF를 multipart로 보내는 요청 형태를 보안 정책이 차단했다. 직접 확인하려면 앱 기동 후 브라우저 UI에서 `src/main/resources/demo/owner-v1.pdf`를 업로드해 미리보기 페이지의 한글을 확인한다.
