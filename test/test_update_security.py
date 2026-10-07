"""Synthetic OTA trust and inert package boundaries; no real feed or publisher key."""

import base64
import hashlib
import io
import json
import ssl
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from updates import security


class SecurityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.pem = cls.private.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()

    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = "https://updates.example.test/desktop-remote-updates/test/windows/latest.json"
        self.trust = {"schema": 1, "product": "codex-desktop-remote", "channel": "test",
                      "keys": {"synthetic": self.pem}, "sources": {"windows": self.source}}
        self.payload = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                        "platform": "windows-x64", "channel": "test", "version": "0.1.0-alpha.3",
                        "sequence": 3, "issuedAt": 1000, "expiresAt": 2000,
                        "url": "https://updates.example.test/desktop-remote-updates/test/windows/build.zip",
                        "bytes": 123, "sha256": "a" * 64, "notes": "synthetic", "compatibility": ["desktop-26.930.7945.0"],
                        "dataSchema": 1, "rollbackCompatible": True}

    def signed(self, payload=None, raw=None):
        raw = raw if raw is not None else json.dumps(payload or self.payload, separators=(",", ":")).encode()
        signature = self.private.sign(raw, padding.PKCS1v15(), hashes.SHA256())
        return {"schema": 1, "keyId": "synthetic", "payload": base64.b64encode(raw).decode(),
                "signature": base64.b64encode(signature).decode()}

    def verify(self, envelope=None, **changes):
        args = dict(envelope=envelope or self.signed(), trust=self.trust, component="windows",
                    platform="windows-x64", compatibility="desktop-26.930.7945.0",
                    installed_version="0.1.0-alpha.2", now=1500)
        args.update(changes)
        return security.verify(**args)

    def rejects(self, code, envelope=None, **changes):
        with self.assertRaises(security.UpdateError) as result:
            self.verify(envelope, **changes)
        self.assertEqual(result.exception.code, code)

    def test_raw_bytes_are_signed_and_monotonic_ledger_returned(self):
        raw = json.dumps(self.payload, ensure_ascii=False, indent=1).encode()
        value, ledger = self.verify(self.signed(raw=raw))
        self.assertEqual(value, self.payload)
        self.assertEqual(ledger["binding"], hashlib.sha256(raw).hexdigest())
        self.assertEqual(ledger["versions"][value["version"]], value["sha256"])
        self.rejects("invalid_release_signature", {**self.signed(raw=raw), "payload": self.signed()["payload"]})

    def test_signature_target_schema_time_and_duplicate_keys(self):
        self.rejects("invalid_release_signature", {**self.signed(), "signature": base64.b64encode(b"wrong").decode()})
        self.rejects("invalid_release_envelope", {**self.signed(), "unknown": 1})
        self.rejects("invalid_json", self.signed(raw=b'{"schema":1,"schema":1}'))
        self.rejects("wrong_release_target", component="relay", platform="linux-x64",
                     compatibility="desktop-remote-v1", trust={**self.trust, "sources": {"relay": self.source}})
        self.rejects("expired_release", now=2000)
        self.rejects("update_unconfigured", trust={**self.trust, "keys": {}})
        self.rejects("invalid_release_metadata", self.signed({**self.payload, "unknown": 1}))
        self.rejects("wrong_release_target", self.signed({**self.payload, "platform": "linux-x64"}))

    def test_replay_same_sequence_same_version_and_url_boundaries(self):
        _, ledger = self.verify()
        self.rejects("same_sequence_replacement_rejected", self.signed({**self.payload, "notes": "changed"}), ledger=ledger)
        self.rejects("same_version_replacement_rejected", self.signed({**self.payload, "sequence": 4, "sha256": "b" * 64}), ledger=ledger)
        self.rejects("no_update_available", installed_version="0.1.0-alpha.3", ledger=ledger)
        self.rejects("release_replay_rejected", self.signed({**self.payload, "sequence": 2}), ledger=ledger)
        self.rejects("release_replay_rejected", installed_version="0.1.0-alpha.4")
        for url in ("http://updates.example.test/x", "https://evil.example.test/desktop-remote-updates/test/windows/a.zip",
                    "https://updates.example.test/desktop-remote-updates/test/relay/a.zip",
                    "https://updates.example.test/desktop-remote-updates/test/windows/%2e%2e/relay/a.zip",
                    "https://user@updates.example.test/desktop-remote-updates/test/windows/a.zip"):
            self.rejects("untrusted_update_url", self.signed({**self.payload, "url": url}))

    def test_android_exact_fields_and_stable_prerelease(self):
        android = {**self.payload, "component": "android", "platform": "android",
                   "compatibility": ["desktop-remote-v1"], "versionCode": 3,
                   "packageId": "dev.codex.desktopremote.lite", "minSdk": 26,
                   "url": "https://updates.example.test/android/build.apk"}
        trust = {**self.trust, "sources": {"android": "https://updates.example.test/android/latest.json"}}
        self.verify(self.signed(android), component="android", platform="android",
                    compatibility="desktop-remote-v1", trust=trust)
        self.rejects("wrong_release_target", self.signed({**android, "packageId": "old.app"}),
                     component="android", platform="android", compatibility="desktop-remote-v1", trust=trust)
        stable = {**self.payload, "channel": "stable"}
        self.rejects("wrong_release_target", self.signed(stable), trust={**self.trust, "channel": "stable"})

    def test_download_checks_exact_bytes_hash_and_fsync_without_network(self):
        body = b"synthetic artifact"
        payload = {"url": self.payload["url"], "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()}

        class Response:
            status = 200
            headers = {"Content-Length": str(len(body))}
            def __enter__(self): return self
            def __exit__(self, *args): return None
            def read(self, size):
                data, body_holder[0] = body_holder[0][:size], body_holder[0][size:]
                return data

        body_holder = [body]
        opener = SimpleNamespace(open=lambda *args, **kwargs: Response())
        with patch.object(security, "_opener", return_value=opener):
            target = self.root / "package.zip"
            self.assertEqual(security.download(payload, target), target.absolute())
            self.assertEqual(target.read_bytes(), body)
            with self.assertRaisesRegex(security.UpdateError, "new_download_path_required"):
                security.download(payload, target)
            body_holder[0] = body
            with self.assertRaisesRegex(security.UpdateError, "download_integrity_failed"):
                security.download({**payload, "sha256": "b" * 64}, self.root / "wrong.zip")

    def test_fetch_is_bounded_strict_json_and_never_accepts_insecure_context(self):
        class Response(io.BytesIO):
            status = 200

        with patch.object(security, "_opener", return_value=SimpleNamespace(
                open=lambda *args, **kwargs: Response(json.dumps(self.signed()).encode()))):
            self.assertEqual(security.fetch(self.source)["keyId"], "synthetic")
        with patch.object(security, "_opener", return_value=SimpleNamespace(
                open=lambda *args, **kwargs: Response(b'{"schema":1,"schema":1}'))):
            with self.assertRaisesRegex(security.UpdateError, "invalid_json"):
                security.fetch(self.source)
        insecure = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        insecure.check_hostname = False
        insecure.verify_mode = ssl.CERT_NONE
        with self.assertRaisesRegex(security.UpdateError, "invalid_tls_context"):
            security.fetch(self.source, insecure)

    def archive(self, entries, manifest_files=None):
        manifest = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                    "platform": "windows-x64", "version": "0.1.0-alpha.3", "dataSchema": 1,
                    "files": manifest_files or {"app.txt": hashlib.sha256(b"safe").hexdigest()}}
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as bundle:
            bundle.writestr("package.json", json.dumps(manifest))
            for name, data in entries:
                bundle.writestr(name, data)
        path = self.root / "archive.zip"
        path.write_bytes(raw.getvalue())
        return path, {"component": "windows", "platform": "windows-x64", "version": "0.1.0-alpha.3",
                      "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}

    def test_unpack_and_verify_tree_complete_roundtrip(self):
        archive, payload = self.archive([("app.txt", b"safe")])
        stage = security.unpack(archive, self.root / "stage", payload)
        self.assertEqual(security.verify_tree(stage, "windows", "0.1.0-alpha.3")["files"],
                         {"app.txt": hashlib.sha256(b"safe").hexdigest()})
        (stage / "app.txt").write_bytes(b"changed")
        with self.assertRaisesRegex(security.UpdateError, "package_file_changed"):
            security.verify_tree(stage)

    def test_unpack_rejects_extra_traversal_case_duplicate_and_symlink(self):
        for entries, code in (
            ([("app.txt", b"safe"), ("extra.txt", b"x")], "package_file_set_changed"),
            ([("../app.txt", b"safe")], "unsafe_archive_path"),
            ([("CON.txt", b"safe")], "unsafe_archive_path"),
            ([("app.txt", b"safe"), ("APP.TXT", b"safe")], "unsafe_archive_entry"),
            ([("app.txt", b"safe"), ("app.txt/child", b"x")], "unsafe_archive_entry"),
            ([("data/identity.json", b"safe")], "forbidden_package_content"),
        ):
            with self.subTest(entries=entries):
                archive, payload = self.archive(entries)
                with self.assertRaises(security.UpdateError) as result:
                    security.unpack(archive, self.root / "stage", payload)
                self.assertEqual(result.exception.code, code)

        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as bundle:
            bundle.writestr("package.json", json.dumps({"schema": 1, "product": "codex-desktop-remote",
                "component": "windows", "platform": "windows-x64", "version": "0.1.0-alpha.3",
                "dataSchema": 1, "files": {"link": "a" * 64}}))
            link = zipfile.ZipInfo("link")
            link.create_system = 3
            link.external_attr = 0o120777 << 16
            bundle.writestr(link, "app.txt")
        archive = self.root / "link.zip"
        archive.write_bytes(raw.getvalue())
        payload = {"component": "windows", "platform": "windows-x64", "version": "0.1.0-alpha.3",
                   "bytes": archive.stat().st_size, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}
        with self.assertRaisesRegex(security.UpdateError, "unsafe_archive_entry"):
            security.unpack(archive, self.root / "stage", payload)


if __name__ == "__main__":
    unittest.main()
