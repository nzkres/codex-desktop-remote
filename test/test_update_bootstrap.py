"""Fresh-home bootstrap and slot verification with synthetic trust only."""

import json
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from updates import bootstrap, launch, publish
from updates.security import UpdateError
from updates.storage import read


@unittest.skipUnless(os.name == "nt", "Windows fresh-home bootstrap test")
class WindowsBootstrapTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=3072)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ota-bootstrap-test-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.home = self.base / "new-installation"
        self.archive = self.base / "synthetic-windows.zip"
        self.metadata = self.base / "latest.json"
        self.trust_path = self.base / "trust.json"
        self.private_key = self.base / "synthetic-publisher.pem"
        self.private_key.write_bytes(self.key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption()))
        publish.pack("windows", self.archive)
        source = "https://updates.example.test/desktop-remote-updates/test/windows/latest.json"
        version = read(publish.ROOT / "windows" / "releases.json")["releases"][0]["version"]
        publish.sign("windows", self.archive, self.metadata,
                     private_key=self.private_key, key_id="synthetic-bootstrap",
                     source=source,
                     url=source.rsplit("/", 1)[0] + f"/windows-{version}.zip",
                     sequence=7)
        public = self.key.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        self.trust_path.write_text(json.dumps({
            "schema": 1, "product": publish.PRODUCT, "channel": "test",
            "keys": {"synthetic-bootstrap": public}, "sources": {"windows": source},
        }), encoding="utf-8")

    def install(self):
        # ACL/DPAPI integration is separate; no real identity or service is read.
        fake_core = types.SimpleNamespace(private_directory=lambda home: None)
        with patch.dict("sys.modules", {"connector_core": fake_core}):
            return bootstrap.install(self.home, self.trust_path, self.metadata,
                                     self.archive, "windows")

    def test_signed_package_seeds_new_home_and_launcher_verifies_slot(self):
        slot = self.install()
        self.assertEqual(launch.verified_slot(self.home), (slot, "windows"))
        self.assertEqual((self.home / "Start.pyw").read_bytes(),
                         (slot / "updates" / "launch.py").read_bytes())
        self.assertEqual(read(self.home / "state" / "ledger.json")["sequence"], 7)
        self.assertEqual(read(self.home / "current.json")["version"],
                         read(publish.ROOT / "windows" / "releases.json")["releases"][0]["version"])
        self.assertEqual(read(self.home / "install.json")["component"], "windows")
        self.assertEqual(read(self.home / "trust.json"), read(self.trust_path))
        self.assertTrue((self.home / "data").is_dir())
        self.assertFalse((self.home / "data" / "fixture.local.json").exists())

    def test_existing_home_is_preserved(self):
        self.home.mkdir()
        marker = self.home / "old-user-data.txt"
        marker.write_text("do not touch", encoding="utf-8")
        with self.assertRaisesRegex(UpdateError, "new_installation_home_required"):
            self.install()
        self.assertEqual(marker.read_text(encoding="utf-8"), "do not touch")
        self.assertFalse((self.home / "current.json").exists())

    def test_corrupt_archive_cannot_create_home(self):
        with self.archive.open("ab") as stream:
            stream.write(b"corrupt after signing")
        with self.assertRaises(UpdateError):
            self.install()
        self.assertFalse(self.home.exists())

    def test_tampered_slot_launcher_is_rejected_before_spawn(self):
        slot = self.install()
        entry = slot / "updates" / "launch.py"
        entry.write_bytes(entry.read_bytes() + b"\n# tampered")
        with self.assertRaisesRegex(ValueError, "installed_version_changed"):
            launch.verified_slot(self.home)

    def test_unfinished_transaction_blocks_launcher(self):
        self.install()
        (self.home / "state" / "transaction.json").write_text(
            '{"phase":"prepared"}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unfinished_update_requires_review"):
            launch.verified_slot(self.home)


if __name__ == "__main__":
    unittest.main()
