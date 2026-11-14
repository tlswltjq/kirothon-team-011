#!/usr/bin/env bash
# 데모 녹화 WebM을 MP4(H.264, yuv420p, faststart)로 변환한다. 같은 폴더에 segments.env(녹화가 남김)가 있으면
# 콜드 오픈으로 편집한다: [랜딩 문제 문구] + [v2 결과] + [업로드~평가자~v2 결과 본편]. EDIT=0이면 편집 없이 변환만 한다. 사용: e2e/to-mp4.sh [입력.webm] [출력.mp4]
# 호스트에 ffmpeg를 설치하지 않고, digest로 고정한 멀티 아키텍처(amd64/arm64) 컨테이너 이미지를 쓴다.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="linuxserver/ffmpeg@sha256:822d04f0b2d0c4847430f5cc90f20ea48be866bb502ece63fdeb729400bf5361"
IN="${1:-artifacts/demo/demo.webm}"
OUT="${2:-artifacts/demo/demo.mp4}"

[ -f "$IN" ] || { echo "입력 파일이 없습니다: $IN" >&2; exit 2; }
mkdir -p "$(dirname "$OUT")"

SEG="$(dirname "$IN")/segments.env"
FF=(timeout 600 docker run --rm --network none -u "$(id -u):$(id -g)" -v "$PWD":/work -w /work --entrypoint ffmpeg "$IMAGE" -y -loglevel error)
if [ "${EDIT:-1}" != "0" ] && [ -f "$SEG" ]; then
  # shellcheck disable=SC1090
  . "$SEG"
  : "${PROBLEM_END:?}" "${RESULT_START:?}" "${RESULT_END:?}" "${BODY_START:?}"
  FILTER="[0:v]trim=0:${PROBLEM_END},setpts=PTS-STARTPTS[a];[0:v]trim=${RESULT_START}:${RESULT_END},setpts=PTS-STARTPTS[b];[0:v]trim=${BODY_START}:${RESULT_END},setpts=PTS-STARTPTS[c];[a][b][c]concat=n=3:v=1:a=0[v]"
  "${FF[@]}" -i "$IN" -filter_complex "$FILTER" -map "[v]" \
    -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -movflags +faststart -an "$OUT"
else
  "${FF[@]}" -i "$IN" -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -movflags +faststart -an "$OUT"
fi
ls -l "$OUT"
