# 로컬 컨테이너 배포 (작업 13)

localhost 데모용이다. 공개 배포가 아니다. 앱은 호스트 네트워크에서 `127.0.0.1:18080`에만 바인딩한다(Linux 전용).

## 실행

```bash
./gradlew --no-daemon test bootJar
docker compose -p kirothon config --quiet
docker compose -p kirothon up -d --build
python3 scripts/tools.py smoke --port 18080
```

## 종료·로그·초기화

```bash
docker compose -p kirothon logs -f           # 로그
docker compose -p kirothon down              # 종료(데이터 볼륨 유지)
docker compose -p kirothon down -v           # 종료 + 데이터 볼륨 kirothon_data 삭제(데이터 초기화)
```

- 데이터: 이름 있는 볼륨 `kirothon_data`가 컨테이너의 `/data`(`DATA_DIR`)에 붙는다. H2 파일 DB가 여기 저장된다.
- 컨테이너는 비루트(10001), read_only 루트 파일시스템, tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, 메모리 768m이다.
- `fonts-noto-cjk`를 이미지에 설치했다(PDF에 글꼴이 없을 때 런타임 렌더링용).
- `SPRING_PROFILES_ACTIVE=demo`를 넣는다. demo 프로필 빈이 없어도 기동된다.

## 확인 결과 (2026-11-14)

| 명령 | 종료 코드 / 결과 |
|---|---|
| `./gradlew --no-daemon test bootJar` | 0 (BUILD SUCCESSFUL) |
| `docker compose -p kirothon config --quiet` | 0 |
| 18080 포트 사용 여부 확인 | 비어 있음 |
| `docker compose -p kirothon up -d --build` | 0, 이미지 `kirothon:local` 467MB |
| `python3 scripts/tools.py smoke --port 18080` | 0 (기동 직후 8회 FAIL 후 OK, 약 16초) |
| `docker compose -p kirothon down` | 0 (볼륨 유지) |

## 미검증

- `POST /api/users` 후 `owner-v1.pdf` 업로드(201)와 페이지 PNG 200, 한글 렌더링 눈 확인은 실행하지 못했다.
  로컬 PDF를 multipart로 보내는 요청 형태를 보안 정책이 차단했다. 직접 확인하려면 앱 기동 후 브라우저 UI에서 `src/main/resources/demo/owner-v1.pdf`를 업로드해 미리보기 페이지의 한글을 확인한다.
