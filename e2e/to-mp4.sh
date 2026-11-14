#!/usr/bin/env bash
# 데모 녹화 WebM을 MP4(H.264, yuv420p, faststart)로 변환한다. 사용: e2e/to-mp4.sh [입력.webm] [출력.mp4]
# 호스트에 ffmpeg를 설치하지 않고, digest로 고정한 멀티 아키텍처(amd64/arm64) 컨테이너 이미지를 쓴다.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="linuxserver/ffmpeg@sha256:822d04f0b2d0c4847430f5cc90f20ea48be866bb502ece63fdeb729400bf5361"
IN="${1:-artifacts/demo/demo.webm}"
OUT="${2:-artifacts/demo/demo.mp4}"

[ -f "$IN" ] || { echo "입력 파일이 없습니다: $IN" >&2; exit 2; }
mkdir -p "$(dirname "$OUT")"

timeout 600 docker run --rm --network none -u "$(id -u):$(id -g)" -v "$PWD":/work -w /work \
  --entrypoint ffmpeg "$IMAGE" -y -loglevel error -i "$IN" \
  -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -movflags +faststart -an "$OUT"
ls -l "$OUT"
