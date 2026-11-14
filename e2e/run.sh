#!/usr/bin/env bash
# E2E 또는 데모 녹화 실행. 사용: e2e/run.sh [e2e|demo]
# 앱: build/libs/app.jar(./gradlew bootJar)을 127.0.0.1의 테스트 전용 포트와 임시 DATA_DIR로 띄우고 끝나면 종료한다.
# 브라우저: 공식 Playwright 이미지를 --network host로 띄워 그 안에서 npx playwright test를 실행한다.
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-e2e}"
PORT="${E2E_PORT:-18093}"
IMAGE="mcr.microsoft.com/playwright:v1.65.0-noble"
JAR="build/libs/app.jar"

[ -f "$JAR" ] || { echo "먼저 ./gradlew bootJar 를 실행하세요." >&2; exit 2; }
[ -d node_modules/@playwright/test ] || { echo "먼저 npm ci 를 실행하세요." >&2; exit 2; }

DATA="$(mktemp -d "${TMPDIR:-/tmp}/e2e-data.XXXXXX")"
timeout 900 env SERVER_ADDRESS=127.0.0.1 SERVER_PORT="$PORT" DATA_DIR="$DATA" SPRING_PROFILES_ACTIVE=demo \
  java -jar "$JAR" >"$DATA/app.log" 2>&1 &
APP_PID=$!
cleanup() {
  kill "$APP_PID" 2>/dev/null || true
  wait "$APP_PID" 2>/dev/null || true
  rm -rf "$DATA"
}
trap cleanup EXIT

ready=0
for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:$PORT/actuator/health" >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
if [ "$ready" != 1 ]; then echo "앱이 기동하지 않았습니다." >&2; tail -30 "$DATA/app.log" >&2; exit 3; fi

ARGS=(-c playwright.config.cjs)
ENVS=(-e "E2E_BASE_URL=http://127.0.0.1:$PORT")
if [ "$MODE" = demo ]; then
  ARGS=(-c e2e/demo.config.cjs)
  ENVS+=(-e RECORD_DEMO=1)
fi

timeout 600 docker run --rm --network host --ipc=host --init -u "$(id -u):$(id -g)" -e HOME=/tmp "${ENVS[@]}" \
  -v "$PWD":/work -w /work "$IMAGE" npx playwright test "${ARGS[@]}"
