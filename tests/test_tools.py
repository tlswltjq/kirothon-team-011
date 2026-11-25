import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location("tools", Path(__file__).parents[1] / "scripts/tools.py")
tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tools)


class ToolsTests(unittest.TestCase):
    def setUp(self):
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def test_missing_command(self):
        with patch.object(tools.subprocess, "run", side_effect=FileNotFoundError):
            self.assertEqual(tools.command(["missing"]), (False, ""))

    def test_command_timeout(self):
        with patch.object(tools.subprocess, "run",
                          side_effect=subprocess.TimeoutExpired("tool", 30)):
            self.assertEqual(tools.command(["tool"]), (False, ""))

    def test_command_uses_project_cwd(self):
        with patch.object(tools.subprocess, "run", return_value=Mock(returncode=0, stdout="secret")) as run:
            self.assertEqual(tools.command(["tool"]), (True, "secret"))
            self.assertEqual(run.call_args.kwargs["cwd"], str(tools.ROOT))

    def test_partial_wrapper_is_rejected(self):
        with patch.object(tools.Path, "is_file", lambda p: p.name == "gradlew"):
            self.assertFalse(tools.wrapper_ready())

    def test_complete_wrapper(self):
        with patch.object(tools.Path, "is_file", return_value=True):
            self.assertTrue(tools.wrapper_ready())

    def doctor_command(self, args, **kwargs):
        if args[0] in ("java", "javac"):
            return True, 'openjdk version "21.0.12"' if args[0] == "java" else "javac 21.0.12"
        return args[0] not in ("docker", "node", "npm"), ""

    def test_optional_failure_is_warning(self):
        with patch.object(tools, "command", side_effect=self.doctor_command), patch.object(tools, "wrapper_ready", return_value=True):
            self.assertEqual(tools.doctor(), 0)
            self.assertEqual(tools.doctor(strict=True), 1)

    def test_kiro_login_failure_is_required(self):
        def no_login(args, **kwargs):
            if args[:2] == ["kiro-cli", "whoami"]:
                return False, ""
            return self.doctor_command(args)
        with patch.object(tools, "command", side_effect=no_login), patch.object(tools, "wrapper_ready", return_value=True):
            self.assertEqual(tools.doctor(), 1)

    def test_old_java_rejected(self):
        with patch.object(tools, "command", return_value=(True, 'openjdk version "17.0.1"')), patch.object(tools, "wrapper_ready", return_value=True):
            self.assertEqual(tools.doctor(), 1)

    def test_check_missing_wrapper_does_not_execute(self):
        with patch.object(tools, "wrapper_ready", return_value=False), patch.object(tools, "command") as cmd:
            self.assertEqual(tools.main(["check"]), 1)
            cmd.assert_not_called()

    def test_check_build_failure(self):
        with patch.object(tools, "wrapper_ready", return_value=True), patch.object(tools, "command", return_value=(False, "")):
            self.assertEqual(tools.main(["check"]), 1)

    def test_invalid_port(self):
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as exc:
                tools.main(["smoke", "--port", "65536"])
            self.assertEqual(exc.exception.code, 2)

    def test_health_payloads(self):
        for body, expected in [(b'{"status":"UP"}', 0), (b'{"status":"DOWN"}', 1),
                               (b"null", 1), (b"[]", 1), (b"broken", 1),
                               (b"{}", 1), (b"x" * 65537, 1), (b"\xff", 1)]:
            with self.subTest(body=body[:30]):
                response = Mock(status=200)
                response.read.return_value = body
                opener = Mock()
                opener.open.return_value.__enter__ = Mock(return_value=response)
                opener.open.return_value.__exit__ = Mock(return_value=False)
                with patch.object(tools.urllib.request, "build_opener", return_value=opener):
                    self.assertEqual(tools.smoke(18080), expected)

    def test_health_connection_failure(self):
        opener = Mock()
        opener.open.side_effect = OSError()
        with patch.object(tools.urllib.request, "build_opener", return_value=opener):
            self.assertEqual(tools.smoke(18080), 1)

    def test_redirect_not_followed(self):
        self.assertIsNone(tools.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.com"))


if __name__ == "__main__":
    unittest.main()
