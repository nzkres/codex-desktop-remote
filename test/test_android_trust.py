import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa, ec

from scripts.check_android_trust import TrustCheckError, check_apk_trust, main, validate_trust


class AndroidTrustTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.pkcs1 = key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.PKCS1).decode("ascii")
        cls.pem = key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode("ascii")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.trust = self.root / "trust.json"
        self.apk = self.root / "candidate.apk"
        self.document = {"schema": 1, "product": "codex-desktop-remote", "channel": "test",
                         "keys": {"publisher-test-1": self.pem},
                         "sources": {"android": "https://example.invalid:9443/desktop-remote-updates/test/android/latest.json"}}

    def save(self):
        self.trust.write_text(json.dumps(self.document), encoding="utf-8")

    def apk_with(self, *contents):
        with zipfile.ZipFile(self.apk, "w") as archive:
            for content in contents:
                archive.writestr("assets/update-trust.json", content)

    def test_accepts_exact_test_trust_and_matching_asset(self):
        self.save()
        self.assertEqual(validate_trust(self.trust), self.document)
        self.apk_with(self.trust.read_bytes())
        check_apk_trust(self.apk, self.trust)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(main(["--trust", str(self.trust), "--apk", str(self.apk)]), 0)
        self.assertEqual(out.getvalue(), "Android OTA trust verified.\n")

    def test_rejects_bad_key_and_shape(self):
        weak = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        ec_key = ec.generate_private_key(ec.SECP256R1())
        for change in (
            lambda d: d["keys"].update({"publisher-test-1": weak.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()}),
            lambda d: d["keys"].update({"publisher-test-1": ec_key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()}),
            lambda d: d["keys"].update({"bad key": self.pem}),
            lambda d: d["keys"].update({"another": self.pem}),
            lambda d: d.update({"schema": True}),
            lambda d: d.update({"channel": "stable"}),
            lambda d: d["sources"].update({"windows": d["sources"]["android"]}),
        ):
            self.document = {"schema": 1, "product": "codex-desktop-remote", "channel": "test",
                             "keys": {"publisher-test-1": self.pem},
                             "sources": {"android": "https://example.invalid/desktop-remote-updates/test/android/latest.json"}}
            change(self.document)
            self.save()
            with self.assertRaises(TrustCheckError):
                validate_trust(self.trust)

    def test_requires_single_canonical_spki_pem(self):
        for pem in (self.pkcs1, self.pem + self.pem, self.pem + "garbage"):
            self.document["keys"] = {"publisher-test-1": pem}
            self.save()
            with self.subTest(kind=pem[:30]), self.assertRaises(TrustCheckError):
                validate_trust(self.trust)
        self.document["keys"] = {"publisher-test-1": " \n" + self.pem.replace("\n", "\r\n") + "\t"}
        self.save()
        validate_trust(self.trust)

    def test_rejects_wrong_source(self):
        for source in (
            "http://example.invalid/desktop-remote-updates/test/android/latest.json",
            "https://example.invalid/desktop-remote-updates/stable/android/latest.json",
            "https://example.invalid/desktop-remote-updates/test/windows/latest.json",
            "https://user@example.invalid/desktop-remote-updates/test/android/latest.json",
            "https://example.invalid/desktop-remote-updates/test/android/latest.json?",
            "https://example.invalid/desktop-remote-updates/test/android/latest.json#",
            "https://example.invalid/desktop-remote-updates/test/android/%6catest.json",
            "https://example.invalid/desktop-remote-updates/test/android/../android/latest.json",
        ):
            self.document["sources"]["android"] = source
            self.save()
            with self.subTest(source=source), self.assertRaises(TrustCheckError):
                validate_trust(self.trust)

    def test_rejects_missing_duplicate_or_changed_apk_asset(self):
        self.save()
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("assets/other", b"x")
        with self.assertRaises(TrustCheckError):
            check_apk_trust(self.apk, self.trust)
        self.apk_with(self.trust.read_bytes() + b" ")
        with self.assertRaises(TrustCheckError):
            check_apk_trust(self.apk, self.trust)
        with contextlib.redirect_stderr(io.StringIO()):
            self.apk_with(self.trust.read_bytes(), self.trust.read_bytes())
        with self.assertRaises(TrustCheckError):
            check_apk_trust(self.apk, self.trust)

    def test_cli_failure_redacts_input(self):
        self.document["keys"] = {"publisher-test-1": "PRIVATE-SENTINEL"}
        self.save()
        output = io.StringIO()
        with contextlib.redirect_stderr(output):
            self.assertEqual(main(["--trust", str(self.trust)]), 1)
        self.assertEqual(output.getvalue(), "Android OTA trust verification failed.\n")


if __name__ == "__main__":
    unittest.main()
