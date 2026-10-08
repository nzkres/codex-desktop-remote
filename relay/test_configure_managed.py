import json
from pathlib import Path
import sqlite3
import stat
import sys
import tempfile
import unittest

from configure_managed import ConfigureError, configure


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux protected home fixture")
class ConfigureManagedTests(unittest.TestCase):
    def test_new_identity_schema_and_repeat_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / "relay-home"
            prompts = []

            def password(prompt):
                prompts.append(prompt)
                return "synthetic-password-123"

            result = configure(home, "test-owner", 28181, 28191, 28192, 28200, 28299,
                               password_provider=password)
            self.assertTrue(result["configured"])
            self.assertEqual(len(prompts), 2)
            self.assertEqual(stat.S_IMODE(home.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE((home / "data").stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE((home / "control.token").stat().st_mode), 0o600)
            self.assertEqual(json.loads((home / "config.json").read_text())["apiPort"], 28191)
            self.assertNotIn("synthetic-password", (home / "config.json").read_text())
            self.assertFalse((home / "configure.lock").exists())
            for name in ("auth", "registry"):
                path = home / "data" / f"{name}.sqlite3"
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
                with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
                    self.assertEqual(db.execute("PRAGMA user_version").fetchone()[0], 1)
            with sqlite3.connect(home / "data" / "auth.sqlite3") as db:
                self.assertEqual(db.execute("SELECT username FROM account").fetchone()[0], "test-owner")
                self.assertEqual(db.execute("SELECT COUNT(*) FROM sessions").fetchone()[0], 0)
            original = (home / "control.token").read_bytes()
            with self.assertRaisesRegex(ConfigureError, "already_configured"):
                configure(home, "other", 28181, 28191, 28192, 28200, 28299,
                          password_provider=lambda _: self.fail("must refuse before prompting"))
            self.assertEqual((home / "control.token").read_bytes(), original)

    def test_existing_database_never_opened_or_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / "relay-home"
            data = home / "data"
            data.mkdir(parents=True, mode=0o700)
            existing = data / "auth.sqlite3"
            existing.write_bytes(b"synthetic-existing-identity")
            with self.assertRaisesRegex(ConfigureError, "identity_already_exists"):
                configure(home, "owner", 28181, 28191, 28192, 28200, 28299,
                          password_provider=lambda _: self.fail("must refuse before prompting"))
            self.assertEqual(existing.read_bytes(), b"synthetic-existing-identity")


if __name__ == "__main__":
    unittest.main()
