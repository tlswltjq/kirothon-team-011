#!/usr/bin/env python3
"""Kiro 활용 증거 기록: 기능 사용 기록(add), 진행 현황(status), kiro-cli hook 이벤트(hook)."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
USAGE = ROOT / "KIRO-USAGE.md"
HOOK_LOG = ROOT / ".kiro/evidence/hook-events.log"
SPECS = ROOT / ".kiro/specs"
HEADER = ("# Kiro 활용 기록\n\n"
          "`python3 scripts/kiro_log.py add`로만 추가한다. 실패한 시도도 기록한다.\n\n"
          "| UTC | 단계 | 기능 | 참조 | 결과 |\n|---|---|---|---|---|\n")
# 심사 증거로 보여 줄 핵심 기능. status가 아직 기록 없는 항목을 알려 준다.
CORE = ("workflow_run", "spec", "steering", "hook", "task_run", "spawn_run",
        "review", "browser", "monitor", "artifact")
ROW = re.compile(r"^\|\s*\d{4}-\d\d-\d\dT[^|]*\|[^|]*\|\s*([^|]+?)\s*\|")


def utc():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def cell(text):
    return " ".join(str(text).split()).replace("|", "\\|")


def add(stage, feature, ref, result):
    if not USAGE.exists():
        USAGE.write_text(HEADER, encoding="utf-8")
    row = "| {} | {} | {} | {} | {} |\n".format(
        utc(), cell(stage), cell(feature), cell(ref), cell(result))
    with USAGE.open("a", encoding="utf-8") as f:
        f.write(row)
    print("기록: " + row.strip())
    return 0


def feature_counts():
    counts = {}
    if USAGE.exists():
        for line in USAGE.read_text(encoding="utf-8").splitlines():
            m = ROW.match(line)
            if m:
                counts[m.group(1)] = counts.get(m.group(1), 0) + 1
    return counts


def spec_progress():
    result = []
    for tasks in sorted(SPECS.glob("*/tasks.md")):
        text = tasks.read_text(encoding="utf-8")
        done = len(re.findall(r"^\s*- \[[xX]\]", text, re.M))
        todo = len(re.findall(r"^\s*- \[ \]", text, re.M))
        result.append((tasks.parent.name, done, done + todo))
    return result


def status():
    specs = spec_progress()
    if specs:
        for name, done, total in specs:
            print("SPEC {}: {}/{} 작업 완료".format(name, done, total))
    else:
        print("SPEC: 아직 없음 (.kiro/templates/spec 복사 후 작성)")
    counts = feature_counts()
    print("KIRO 기록: " + (", ".join("{}={}".format(k, v) for k, v in sorted(counts.items())) or "없음"))
    missing = [f for f in CORE if f not in counts]
    print("미기록 핵심 기능: " + (", ".join(missing) or "없음"))
    return 0


def paths_from(data):
    found = []
    if isinstance(data, dict):
        for key, value in data.items():
            if key in ("path", "file_path", "paths") and value:
                found.extend(value if isinstance(value, list) else [value])
            elif isinstance(value, (dict, list)):
                found.extend(paths_from(value))
    elif isinstance(data, list):
        for item in data:
            found.extend(paths_from(item))
    return [str(p) for p in found if isinstance(p, str)]


def hook(stream=None):
    """kiro-cli postToolUse hook. 내용은 기록하지 않고 도구 이름과 프로젝트 상대 경로만 남긴다."""
    try:
        raw = (stream or sys.stdin).read(1 << 20)
        event = json.loads(raw) if raw.strip() else {}
    except (ValueError, OSError):
        event = {}
    if not isinstance(event, dict):
        event = {}
    tool = cell(event.get("tool_name") or event.get("toolName") or "unknown")
    # kiro-cli는 권한 규칙에 막힌 호출도 postToolUse로 보낸다. 응답 문자열로 구분한다.
    response = event.get("tool_response")
    if isinstance(response, str) and response.startswith("Blocked"):
        tool += " BLOCKED"
    rel = []
    for p in paths_from(event.get("tool_input") or event.get("toolInput") or {}):
        try:
            rel.append(str((ROOT / p).resolve().relative_to(ROOT)))
        except ValueError:
            rel.append("<project-outside>")
    try:
        HOOK_LOG.parent.mkdir(parents=True, exist_ok=True)
        with HOOK_LOG.open("a", encoding="utf-8") as f:
            f.write("{} {} {}\n".format(utc(), tool, ",".join(rel) or "-"))
    except OSError:
        pass  # hook 실패가 에이전트 작업을 막지 않게 한다.
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="action", required=True)
    a = sub.add_parser("add")
    for name in ("stage", "feature", "ref", "result"):
        a.add_argument("--" + name, required=True)
    sub.add_parser("status")
    sub.add_parser("hook")
    args = parser.parse_args(argv)
    if args.action == "add":
        return add(args.stage, args.feature, args.ref, args.result)
    if args.action == "status":
        return status()
    return hook()


if __name__ == "__main__":
    raise SystemExit(main())
