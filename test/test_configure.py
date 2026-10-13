"""Synthetic boundary checks for the local first-run configurator."""

from contextlib import redirect_stdout
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "configure.py"
SPEC = importlib.util.spec_from_file_location("configure_under_test", SCRIPT)
configure = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(configure)
THREAD_ID = uuid.UUID("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
FAKE_SECRET = "fixture-secret-must-not-appear"


class ConfigureTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()  # Windows CI may use an 8.3 TEMP alias.
        self.project = self.root / "public-project"
        self.desktop = self.root / "public-desktop"
        self.project.mkdir()
        self.desktop.mkdir()
        self.node = self.root / "public-node.exe"
        self.frpc = self.root / "public-frpc.exe"
        self.node.write_bytes(b"synthetic node file")
        self.frpc.write_bytes(b"synthetic frpc file")
        self.output = self.root / "fixture.local.json"

    def invoke(self, runner, *, project=None, node=None, selected=True):
        argv = [str(SCRIPT), "--thread-id", str(THREAD_ID),
                "--project", str(project or self.project),
                "--desktop-root", str(self.desktop),
                "--node", str(node or self.node), "--frpc", str(self.frpc)]
        if not selected:
            argv = [argv[0]] + argv[5:]
        printed = io.StringIO()
        with patch.object(configure, "ROOT", self.root), patch.object(sys, "argv", argv), \
                patch.object(configure.subprocess, "run", side_effect=runner) as probe, \
                redirect_stdout(printed):
            try:
                configure.main()
                error = None
            except Exception as exc:
                error = exc
        return error, printed.getvalue(), probe

    def test_existing_config_is_untouched_and_probe_not_started(self):
        original = b'{"keep":"existing identity"}\n'
        self.output.write_bytes(original)
        error, printed, probe = self.invoke(lambda *args, **kwargs: self.fail("probe ran"))
        self.assertEqual(str(error), "configuration_already_exists")
        self.assertEqual(self.output.read_bytes(), original)
        self.assertEqual(printed, "")
        probe.assert_not_called()

    def test_success_writes_public_config_then_probes_without_leaking_output(self):
        def fake_probe(command, **kwargs):
            self.assertTrue(self.output.is_file())
            self.assertEqual(command, [str(self.node), str(self.root / "scripts" / "probe.mjs"), str(self.output)])
            self.assertEqual(kwargs["cwd"], self.root)
            return SimpleNamespace(returncode=0, stdout=FAKE_SECRET.encode(), stderr=FAKE_SECRET.encode())

        error, printed, probe = self.invoke(fake_probe)
        self.assertIsNone(error)
        probe.assert_called_once()
        cfg = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertEqual(cfg["schema"], 1)
        self.assertEqual(cfg["threadId"], str(THREAD_ID))
        self.assertEqual(cfg["projectPath"], str(self.project.resolve()))
        self.assertEqual(cfg["desktopRoot"], str(self.desktop.resolve()))
        self.assertEqual(cfg["dataDir"], str(self.root / "data" / f"fixture-{THREAD_ID}"))
        self.assertEqual(cfg["permissionsHash"], "")
        self.assertTrue(0 < cfg["port"] < 65536)
        for name, path in (("node", self.node), ("frpc", self.frpc)):
            self.assertEqual(cfg[name], {"path": str(path.resolve()),
                                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        self.assertNotIn(FAKE_SECRET, printed + self.output.read_text(encoding="utf-8"))

    def test_probe_failure_retains_config_and_reports_fixed_error(self):
        def failed_probe(*args, **kwargs):
            self.assertTrue(self.output.is_file())
            return SimpleNamespace(returncode=17, stdout=FAKE_SECRET.encode(), stderr=FAKE_SECRET.encode())

        error, printed, probe = self.invoke(failed_probe)
        probe.assert_called_once()
        self.assertIsInstance(error, ValueError)
        self.assertEqual(str(error), "desktop_fixture_unverified_config_retained")
        self.assertTrue(self.output.is_file())
        self.assertNotIn(FAKE_SECRET, printed + str(error) + self.output.read_text(encoding="utf-8"))

    def test_invalid_input_does_not_create_config_or_probe(self):
        error, _, probe = self.invoke(lambda *args, **kwargs: self.fail("probe ran"), project=self.node)
        self.assertEqual(str(error), "directory_required")
        self.assertFalse(self.output.exists())
        probe.assert_not_called()

        error, _, probe = self.invoke(lambda *args, **kwargs: self.fail("probe ran"), node=self.root / "missing.exe")
        self.assertIsInstance(error, FileNotFoundError)
        self.assertFalse(self.output.exists())
        probe.assert_not_called()

    def test_new_install_defaults_to_all_local_without_thread_id_or_permissions_hash(self):
        with patch.object(configure, 'read_catalog', return_value={'schema':1,'tasks':[]}):
            error, printed, probe = self.invoke(lambda *a, **k: SimpleNamespace(returncode=0), selected=False)
        self.assertIsNone(error)
        cfg=json.loads(self.output.read_text(encoding='utf-8'))
        self.assertEqual(cfg['taskScope'], 'all-local')
        self.assertNotIn('threadId', cfg)
        self.assertNotIn('permissionsHash', cfg)
        self.assertIn('--input-type=module', probe.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
