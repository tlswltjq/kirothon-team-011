#!/usr/bin/env python3
"""Local readiness checks; no installs, secret loading, or tool output dumps."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
WRAPPER_FILES = ("gradlew", "gradle/wrapper/gradle-wrapper.jar",
                 "gradle/wrapper/gradle-wrapper.properties")


def command(args, timeout=30):
    try:
        result = subprocess.run(args, cwd=str(ROOT), timeout=timeout,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        # Never print arbitrary tool output: it may include local configuration.
        return result.returncode == 0, result.stdout
    except (OSError, subprocess.TimeoutExpired):
        return False, ""


def wrapper_ready():
    missing = [name for name in WRAPPER_FILES if not (ROOT / name).is_file()]
    if not any((ROOT / name).is_file() for name in ("build.gradle", "build.gradle.kts")):
        missing.append("build.gradle[.kts]")
    print("WRAPPER: " + ("missing " + ", ".join(missing) if missing else "complete"))
    return not missing


def doctor(strict=False):
    required = True
    for tool in ("java", "javac", "git", "curl"):
        flag = "-version" if tool in ("java", "javac") else "--version"
        ok, output = command([tool, flag])
        if ok and tool in ("java", "javac"):
            match = re.search(r'(?:version\s+"|javac\s+)(\d+)', output)
            ok = bool(match and int(match.group(1)) >= 21)
        print("{}: {}".format("OK" if ok else "FAIL", tool))
        required = ok and required
    # 로그인 정보(이메일)는 출력하지 않고 성공 여부만 본다.
    for name, cmd in (("kiro-cli", ["kiro-cli", "--version"]),
                      ("kiro-cli login", ["kiro-cli", "whoami"]),
                      ("kiro agent", ["kiro-cli", "agent", "validate", "--path",
                                      ".kiro/agents/kirothon.json"])):
        ok, _ = command(cmd)
        print("{}: {}".format("OK" if ok else "FAIL", name))
        required = ok and required
    optional = True
    for cmd in (["docker", "version"], ["docker", "compose", "version"],
                ["node", "--version"], ["npm", "--version"]):
        ok, _ = command(cmd)
        print("{}: {}".format("OK" if ok else "WARN", " ".join(cmd)))
        optional = ok and optional
    base = required and (optional or not strict)
    print("BASE: " + ("ready" if base else "not ready"))
    app = wrapper_ready()
    print("APP: " + ("files present; build still required" if app else "not generated/incomplete"))
    return 0 if base and app else 1


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def smoke(port, management_port=None):
    # prod 프로필은 actuator를 관리 포트(MANAGEMENT_PORT, 기본 18081)로 분리한다.
    url = "http://127.0.0.1:{}/actuator/health".format(management_port or port)
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        with opener.open(url, timeout=5) as response:
            raw = response.read(65537)
            if response.status != 200 or len(raw) > 65536:
                raise ValueError("invalid response")
            data = json.loads(raw)
            ok = isinstance(data, dict) and data.get("status") == "UP"
    except (OSError, ValueError, UnicodeError):
        ok = False
    print("{}: localhost health".format("OK" if ok else "FAIL"))
    return 0 if ok else 1


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["doctor", "check", "smoke"])
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--management-port", type=int, default=None,
                        help="smoke: actuator port when separated (prod: 18081); default --port")
    parser.add_argument("--strict", action="store_true",
                        help="doctor: optional tool failures also fail readiness")
    args = parser.parse_args(argv)
    if not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    if args.management_port is not None and not 1 <= args.management_port <= 65535:
        parser.error("--management-port must be between 1 and 65535")
    if args.action == "doctor":
        return doctor(args.strict)
    if args.action == "check":
        if not wrapper_ready():
            return 1
        ok, _ = command(["bash", "./gradlew", "--no-daemon", "test", "bootJar"], timeout=900)
        print("{}: test bootJar (run Wrapper directly for detailed logs)".format("OK" if ok else "FAIL"))
        return 0 if ok else 1
    return smoke(args.port, args.management_port)


if __name__ == "__main__":
    raise SystemExit(main())
