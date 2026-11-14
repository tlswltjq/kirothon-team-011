#!/usr/bin/env bash
# 역할별 kirothon 에이전트로 kiro-cli chat 실행. 모든 도구를 승인 없이 실행한다(--trust-all-tools).
# 사용: scripts/kiro.sh [역할] ["작업 지시"]
#   역할: impl(기본) | spec | test | check | review
#   지시가 있으면 한 번 실행 후 종료(--no-interactive), 없으면 대화형.
#   KIRO_MODEL / KIRO_EFFORT 환경변수로 이번 실행만 모델·추론 깊이를 바꿀 수 있다.
set -euo pipefail
cd "$(dirname "$0")/.."

role="impl"
case "${1:-}" in
  impl|spec|test|check|review) role="$1"; shift ;;
esac

# 역할 → 에이전트(모델은 .kiro/agents/*.json), 추론 깊이
case "$role" in
  spec)   agent=kirothon-spec;   effort=high ;;    # claude-opus-5.5
  impl)   agent=kirothon;        effort=medium ;;  # claude-sonnet-5.5
  test)   agent=kirothon-test;   effort=low ;;     # claude-sonnet-5.5
  check)  agent=kirothon-check;  effort=low ;;     # claude-haiku-5.5
  review) agent=kirothon-review; effort=high ;;    # gpt-5.6-terra (구현과 다른 모델)
esac

# 리뷰 담당은 셸이 없으므로 변경 내용을 파일로 준비한다(.gitignore 대상).
if [ "$role" = review ]; then
  mkdir -p .kiro/review
  {
    echo "# 리뷰 대상 변경 ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
    echo '## git status'; echo '```'; git status --short; echo '```'
    if git rev-parse --verify -q HEAD >/dev/null; then
      echo '## git diff HEAD'; echo '```diff'; git diff HEAD; echo '```'
    fi
    echo '## 추적되지 않은 새 파일(직접 읽을 것)'
    git ls-files --others --exclude-standard | sed 's/^/- /'
  } > .kiro/review/CHANGES.md
fi

args=(chat --agent "$agent" --effort "${KIRO_EFFORT:-$effort}" --trust-workspace --trust-all-tools)
if [ -n "${KIRO_MODEL:-}" ]; then
  args+=(--model "$KIRO_MODEL")
fi
if [ "$#" -gt 0 ]; then
  args+=(--no-interactive "$@")
fi
exec kiro-cli "${args[@]}"
