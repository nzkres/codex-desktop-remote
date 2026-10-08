"""Synthetic publisher-vault tests; no project signing key is read or written."""

import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest

from cryptography.hazmat.primitives import serialization

from updates import publish, signing
from updates.security import COMPATIBILITY, TARGETS, verify


KEY_ID = "synthetic-vault-1"
SOURCE = "https://updates.example.test/desktop-remote-updates/test/windows/latest.json"
URL = SOURCE.rsplit("/", 1)[0] + "/windows-0.1.0-alpha.1.zip"


class SigningVaultTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="ota-vault-synthetic-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / "repo"
        self.root.mkdir()
        self.home = self.base / "publisher-vault"

    def test_non_windows_explicitly_rejects_vault_creation_and_load(self):
        if os.name == "nt":
            self.skipTest("non-Windows contract")
        with self.assertRaisesRegex(Exception, "windows_signing_required"):
            signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        with self.assertRaisesRegex(Exception, "windows_signing_required"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        self.assertFalse(self.home.exists())

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_roundtrip_has_only_encrypted_private_material_and_never_overwrites(self):
        public = signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        self.assertEqual(set(public), {"schema", "product", "keyId", "publicKeyPem", "publicKeySha256"})
        self.assertEqual(public["schema"], 1)
        self.assertEqual(public["product"], publish.PRODUCT)
        self.assertEqual(public["keyId"], KEY_ID)
        public_key = serialization.load_pem_public_key(public["publicKeyPem"].encode())
        der = public_key.public_bytes(serialization.Encoding.DER,
                                      serialization.PublicFormat.SubjectPublicKeyInfo)
        self.assertEqual(public["publicKeySha256"], hashlib.sha256(der).hexdigest())
        self.assertEqual(json.loads((self.home / "public.json").read_text(encoding="utf-8")), public)
        self.assertTrue((self.home / "publisher.dpapi").is_file())
        self.assertEqual({p.name for p in self.home.iterdir()}, {"publisher.dpapi", "public.json"})
        self.assertNotIn(b"BEGIN PRIVATE KEY", (self.home / "publisher.dpapi").read_bytes())
        key = signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        actual_public = key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        self.assertEqual(actual_public, public["publicKeyPem"])
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            self.assertEqual(signing.main(["inspect", "--directory", str(self.home),
                                           "--key-id", KEY_ID]), 0)
        self.assertEqual(json.loads(stdout.getvalue()), public)
        self.assertNotIn(str(self.home), stdout.getvalue())
        self.assertNotIn("PRIVATE KEY", stdout.getvalue())
        protected_before = (self.home / "publisher.dpapi").read_bytes()
        with self.assertRaisesRegex(Exception, "new_signing_directory_required"):
            signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        self.assertEqual((self.home / "publisher.dpapi").read_bytes(), protected_before)

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_wrong_key_id_and_modified_public_or_encrypted_record_are_rejected(self):
        signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id="other-key", root=self.root)
        public_path = self.home / "public.json"
        original_public = public_path.read_bytes()
        public = json.loads(original_public)
        public["publicKeySha256"] = "0" * 64
        public_path.write_text(json.dumps(public), encoding="utf-8")
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        public_path.write_bytes(original_public)
        protected = self.home / "publisher.dpapi"
        protected.write_bytes(b"corrupted synthetic ciphertext")
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_relative_repo_internal_and_linked_paths_are_rejected(self):
        with self.assertRaisesRegex(Exception, "external_signing_directory_required"):
            signing.create_vault(Path("relative-vault"), key_id=KEY_ID, root=self.root)
        with self.assertRaisesRegex(Exception, "external_signing_directory_required"):
            signing.create_vault(self.root / "publisher-vault", key_id=KEY_ID, root=self.root)
        self.assertFalse((self.root / "publisher-vault").exists())
        signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        with self.assertRaisesRegex(Exception, "external_signing_directory_required"):
            signing.load_key(Path("relative-vault"), key_id=KEY_ID, root=self.root)
        other = self.base / "hardlink.dpapi"
        os.link(self.home / "publisher.dpapi", other)
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        other.unlink()
        os.link(self.home / "public.json", other)
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        other.unlink()
        linked_parent = self.base / "linked-parent"
        try:
            linked_parent.symlink_to(self.root, target_is_directory=True)
        except OSError:
            pass  # Symlink creation can require a Windows privilege; hardlink case is still tested.
        else:
            with self.assertRaisesRegex(Exception, "unsafe_local_path"):
                signing.create_vault(linked_parent / "new-vault", key_id=KEY_ID, root=self.root)

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_oversized_record_and_cli_failure_are_redacted(self):
        signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        (self.home / "publisher.dpapi").write_bytes(b"X" * (5 * 1024 * 1024))
        with self.assertRaisesRegex(Exception, "invalid_signing_vault"):
            signing.load_key(self.home, key_id=KEY_ID, root=self.root)
        secret_marker = "synthetic-secret-marker"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            status = publish.main([
                "sign", "windows", "--artifact", str(self.base / (secret_marker + ".zip")),
                "--output", str(self.base / "latest.json"),
                "--signing-home", str(self.home), "--key-id", KEY_ID,
                "--source", SOURCE, "--url", URL, "--sequence", "1",
            ])
        self.assertEqual(status, 1)
        self.assertIn("publish_failed", stderr.getvalue())
        self.assertNotIn(str(self.home), stderr.getvalue())
        self.assertNotIn(secret_marker, stderr.getvalue())
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            status = signing.main(["inspect", "--directory", str(self.home), "--key-id", KEY_ID])
        self.assertEqual(status, 1)
        self.assertEqual(stderr.getvalue().strip(), "signing_vault_failed")

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_publisher_signs_with_vault_and_rejects_two_key_sources(self):
        public = signing.create_vault(self.home, key_id=KEY_ID, root=self.root)
        for name in set(publish.UPDATE_REQUIRED) | set(publish.WINDOWS_REQUIRED):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"synthetic source\n")
        catalog = {"schema": 1, "component": "windows", "releases": [{
            "version": "0.1.0-alpha.1", "summary": "Synthetic",
            "changes": [{"title": "Fixture", "experience": "Local", "verify": "Read"}],
            "limitations": [],
        }]}
        (self.root / "windows" / "releases.json").write_text(json.dumps(catalog), encoding="utf-8")
        archive = self.base / "windows.zip"
        publish.pack("windows", archive, root=self.root)
        metadata = self.base / "latest.json"
        envelope = publish.sign("windows", archive, metadata,
                                signing_home=self.home, key_id=KEY_ID,
                                source=SOURCE, url=URL, sequence=1, root=self.root)
        trust = {"schema": 1, "product": publish.PRODUCT, "channel": "test",
                 "keys": {KEY_ID: public["publicKeyPem"]}, "sources": {"windows": SOURCE}}
        payload, _ = verify(envelope, trust, "windows", TARGETS["windows"],
                            COMPATIBILITY["windows"], None)
        self.assertEqual(payload["version"], "0.1.0-alpha.1")
        self.assertEqual(json.loads(metadata.read_text(encoding="utf-8")), envelope)
        with self.assertRaisesRegex(Exception, "one_publisher_key_source_required"):
            publish.sign("windows", archive, self.base / "second.json",
                         private_key=self.base / "unused.pem", signing_home=self.home,
                         key_id=KEY_ID, source=SOURCE, url=URL, sequence=2, root=self.root)
        self.assertFalse((self.base / "second.json").exists())


if __name__ == "__main__":
    unittest.main()
