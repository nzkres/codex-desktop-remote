"""Synthetic Android signing and GitHub provisioning boundaries."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import types
import unittest
from unittest import mock

from updates import android_publisher, signing
from updates.security import UpdateError


KEY_ID = "synthetic-android-1"
ORIGIN = "https://updates.example.test"


class AndroidPublisherTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="android-publisher-synthetic-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.repo = self.base / "repo"
        self.repo.mkdir()
        self.home = self.base / "publisher-vault"

    def make_vault(self):
        return signing.create_vault(self.home, key_id=KEY_ID, root=self.repo)

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_create_binds_trust_to_vault_key_and_encrypts_pkcs12(self):
        publisher_public = self.make_vault()
        public = android_publisher.create_android_signer(self.home, key_id=KEY_ID, origin=ORIGIN)
        self.assertEqual(public["keyId"], KEY_ID)
        self.assertEqual(public["origin"], ORIGIN)
        self.assertEqual(public["keyAlias"], android_publisher.ALIAS)
        self.assertEqual(json.loads((self.home / "android-public.json").read_text()), public)
        trust = json.loads((self.home / "android-trust.json").read_text())
        self.assertEqual(trust["keys"], {KEY_ID: publisher_public["publicKeyPem"]})
        self.assertEqual(trust["sources"], {
            "android": ORIGIN + "/desktop-remote-updates/test/android/latest.json"})
        self.assertEqual(trust["channel"], "test")
        values = android_publisher._github_values(self.home, key_id=KEY_ID, origin=ORIGIN)
        self.assertEqual(set(values), set(android_publisher.NAMES))
        self.assertEqual(json.loads(values["ANDROID_UPDATE_TRUST_JSON"]), trust)
        self.assertEqual(values["ANDROID_SIGNING_KEY_ALIAS"], android_publisher.ALIAS)
        self.assertEqual(values["ANDROID_SIGNING_STORE_PASSWORD"], values["ANDROID_SIGNING_KEY_PASSWORD"])
        files = {p.name for p in self.home.iterdir()}
        self.assertEqual(files, {"publisher.dpapi", "public.json", "android-signing.dpapi",
                                 "android-public.json", "android-trust.json"})
        for path in self.home.iterdir():
            data = path.read_bytes()
            self.assertNotIn(b"BEGIN PRIVATE KEY", data)
            self.assertNotIn(values["ANDROID_SIGNING_KEYSTORE_BASE64"].encode(), data)
            self.assertNotIn(values["ANDROID_SIGNING_STORE_PASSWORD"].encode(), data)

    @unittest.skipUnless(os.name == "nt", "Windows CurrentUser DPAPI required")
    def test_create_rejects_duplicate_and_corruption_without_repair(self):
        self.make_vault()
        android_publisher.create_android_signer(self.home, key_id=KEY_ID, origin=ORIGIN)
        before = {p.name: p.read_bytes() for p in self.home.iterdir()}
        with self.assertRaisesRegex(UpdateError, "android_signer_already_exists"):
            android_publisher.create_android_signer(self.home, key_id=KEY_ID, origin=ORIGIN)
        self.assertEqual({p.name: p.read_bytes() for p in self.home.iterdir()}, before)
        with self.assertRaisesRegex(UpdateError, "invalid_android_signer"):
            android_publisher._github_values(self.home, key_id=KEY_ID, origin="https://other.example.test")
        for filename, replacement in (
            ("android-public.json", b"{}"),
            ("android-trust.json", b"{}"),
            ("android-signing.dpapi", b"corrupt synthetic ciphertext"),
        ):
            with self.subTest(filename=filename):
                path = self.home / filename
                path.write_bytes(replacement)
                with self.assertRaisesRegex(UpdateError, "invalid_android_signer"):
                    android_publisher._github_values(self.home, key_id=KEY_ID, origin=ORIGIN)
                path.write_bytes(before[filename])

    def fake_values(self):
        return {name: "synthetic-secret-for-" + name for name in android_publisher.NAMES}

    def run_provision(self, docker_like_run, values=None):
        with mock.patch.object(android_publisher, "_github_values", return_value=values or self.fake_values()), \
             mock.patch.object(android_publisher.subprocess, "run", side_effect=docker_like_run):
            return android_publisher.provision_github(self.home, key_id=KEY_ID, origin=ORIGIN)

    def test_provision_uses_fixed_repo_and_sends_six_values_only_via_stdin(self):
        self.home.mkdir()
        calls = []
        values = self.fake_values()

        def fake_run(argv, **kwargs):
            calls.append((argv, kwargs))
            if argv[1:3] == ["secret", "list"]:
                return types.SimpleNamespace(stdout="[]")
            return types.SimpleNamespace(stdout="")

        result = self.run_provision(fake_run, values)
        self.assertEqual(result, {"configured": True, "repository": android_publisher.REPOSITORY,
                                  "secretCount": 6})
        self.assertEqual(android_publisher.REPOSITORY, "nzkres/codex-desktop-remote")
        self.assertEqual(calls[0][0], ["gh", "secret", "list", "--repo",
                                       "nzkres/codex-desktop-remote", "--json", "name"])
        self.assertEqual(len(calls), 7)
        self.assertEqual([call[0][4] for call in calls[1:]], ["--repo"] * 6)
        self.assertEqual([call[0][3] for call in calls[1:]], list(android_publisher.NAMES))
        for argv, kwargs in calls[1:]:
            self.assertEqual(argv, ["gh", "secret", "set", argv[3], "--repo",
                                    "nzkres/codex-desktop-remote"])
            self.assertEqual(kwargs["input"], values[argv[3]])
            self.assertTrue(kwargs["check"])
            self.assertTrue(kwargs["capture_output"])
            self.assertTrue(kwargs["text"])
            self.assertFalse(any(secret in " ".join(argv) for secret in values.values()))
        journal = json.loads((self.home / "github-provision.json").read_text())
        self.assertEqual(journal["phase"], "configured")
        self.assertEqual(journal["completedNames"], list(android_publisher.NAMES))
        count = len(calls)
        with self.assertRaisesRegex(UpdateError, "signing_provision_record_exists"):
            self.run_provision(fake_run, values)
        self.assertEqual(len(calls), count)

    def test_existing_secret_fails_preflight_without_writing_journal(self):
        self.home.mkdir()
        calls = []

        def fake_run(argv, **kwargs):
            calls.append(argv)
            return types.SimpleNamespace(stdout=json.dumps([
                {"name": android_publisher.NAMES[0]}]))

        with self.assertRaisesRegex(UpdateError, "signing_provision_preflight_failed"):
            self.run_provision(fake_run)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][1:3], ["secret", "list"])
        self.assertFalse((self.home / "github-provision.json").exists())

    def test_partial_failure_records_needs_review_and_never_retries(self):
        self.home.mkdir()
        calls = []
        marker = "synthetic-sensitive-stderr"

        def fake_run(argv, **kwargs):
            calls.append(argv)
            if argv[1:3] == ["secret", "list"]:
                return types.SimpleNamespace(stdout="[]")
            if argv[3] == android_publisher.NAMES[1]:
                raise subprocess.CalledProcessError(1, argv, stderr=marker)
            return types.SimpleNamespace(stdout="")

        with self.assertRaisesRegex(UpdateError, "signing_provision_requires_review") as failure:
            self.run_provision(fake_run)
        self.assertNotIn(marker, str(failure.exception))
        journal = json.loads((self.home / "github-provision.json").read_text())
        self.assertEqual(journal["phase"], "needs_review")
        self.assertEqual(journal["completedNames"], [android_publisher.NAMES[0]])
        self.assertEqual(journal["activeName"], android_publisher.NAMES[1])
        self.assertNotIn(marker, json.dumps(journal))
        count = len(calls)
        with self.assertRaisesRegex(UpdateError, "signing_provision_record_exists"):
            self.run_provision(fake_run)
        self.assertEqual(len(calls), count)


if __name__ == "__main__":
    unittest.main()
