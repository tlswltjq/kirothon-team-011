import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("kiro_log", Path(__file__).parents[1] / "scripts/kiro_log.py")
kl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kl)


class KiroLogTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for name, value in (("ROOT", root), ("USAGE", root / "KIRO-USAGE.md"),
                            ("HOOK_LOG", root / ".kiro/evidence/hook-events.log"),
                            ("SPECS", root / ".kiro/specs")):
            old = getattr(kl, name)
            setattr(kl, name, value)
            self.addCleanup(setattr, kl, name, old)
        self.root = root
        out = contextlib.redirect_stdout(io.StringIO())
        self.out = out.__enter__()
        self.addCleanup(out.__exit__, None, None, None)

    def test_add_escapes_pipes_and_counts(self):
        kl.add("1. 주제", "workflow_run", "wf_a|b", "성공\n3안")
        kl.add("3. 구현", "workflow_run", "wf_c", "실패: 시간초과")
        text = kl.USAGE.read_text(encoding="utf-8")
        self.assertIn("wf_a\\|b", text)
        self.assertIn("성공 3안", text)
        self.assertEqual(kl.feature_counts(), {"workflow_run": 2})

    def test_status_reports_spec_progress_and_missing(self):
        d = kl.SPECS / "demo"
        d.mkdir(parents=True)
        (d / "tasks.md").write_text("- [x] 1. a\n- [ ] 2. b\n  - [X] 2.1 c\n", encoding="utf-8")
        kl.add("2. 명세", "spec", ".kiro/specs/demo", "작성")
        kl.status()
        printed = self.out.getvalue()
        self.assertIn("SPEC demo: 2/3", printed)
        self.assertIn("미기록 핵심 기능: workflow_run, steering", printed)

    def test_hook_logs_relative_paths_only(self):
        event = '{"tool_name":"fs_write","tool_input":{"path":"src/A.java","file_text":"secret"}}'
        self.assertEqual(kl.hook(io.StringIO(event)), 0)
        line = kl.HOOK_LOG.read_text(encoding="utf-8")
        self.assertIn("fs_write src/A.java", line)
        self.assertNotIn("secret", line)

    def test_hook_marks_blocked_write(self):
        event = ('{"tool_name":"fs_write","tool_input":{"path":"src/main/A.java"},'
                 '"tool_response":"Blocked by a permission rule (deny fs_write matching \\"src/main/**\\")"}')
        kl.hook(io.StringIO(event))
        self.assertIn("fs_write BLOCKED src/main/A.java", kl.HOOK_LOG.read_text(encoding="utf-8"))

    def test_hook_marks_outside_path_and_survives_bad_input(self):
        kl.hook(io.StringIO('{"tool_name":"fs_write","tool_input":{"path":"/etc/passwd"}}'))
        kl.hook(io.StringIO("not json"))
        lines = kl.HOOK_LOG.read_text(encoding="utf-8").splitlines()
        self.assertIn("<project-outside>", lines[0])
        self.assertTrue(lines[1].endswith("unknown -"))


if __name__ == "__main__":
    unittest.main()
