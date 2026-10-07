import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from release_notes import format_history, load_catalog


class ReleaseNotesTests(unittest.TestCase):
    def test_local_record_has_current_version_and_readable_history(self):
        catalog = load_catalog()
        self.assertEqual(catalog["component"], "relay")
        self.assertEqual(catalog["version"], catalog["releases"][0]["version"])
        history = format_history(catalog)
        for release in catalog["releases"]:
            self.assertIn(release["version"], history)
            for change in release["changes"]:
                self.assertIn(change["experience"], history)
                self.assertIn(change["verify"], history)

    def test_cli_notes_do_not_require_or_create_a_database(self):
        script = Path(__file__).with_name("control_server.py")
        catalog = load_catalog()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            environment = dict(os.environ, CODEX_RELAY_AUTH_DB=str(root / "auth.db"),
                               PYTHONIOENCODING="utf-8", PYTHONDONTWRITEBYTECODE="1")
            for flag in ("--version", "--release-notes"):
                result = subprocess.run([sys.executable, "-B", str(script), flag], cwd=root,
                                        env=environment, capture_output=True, text=True,
                                        encoding="utf-8", timeout=10)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stderr, "")
                self.assertIn(catalog["version"], result.stdout)
                if flag == "--release-notes":
                    self.assertIn("体验：", result.stdout)
                    self.assertIn("验证：", result.stdout)
                self.assertEqual(list(root.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
