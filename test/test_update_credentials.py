"""Synthetic private OTA credentials; never read a real identity or login."""

from contextlib import nullcontext
import json
import os
from pathlib import Path
import stat
import tempfile
import time
import unittest
from unittest.mock import patch

from updates import credentials
from updates.security import UpdateError
from updates.storage import atomic, read


TOKEN = "a" * 64
TOKEN_NEXT = "c" * 64
TERMINAL = "b" * 32
WINDOWS_SOURCE = "https://relay.example.test/desktop-remote-updates/test/windows/latest.json"
RELAY_SOURCE = "https://relay.example.test/desktop-remote-updates/test/relay/latest.json"


class CredentialAssertions(unittest.TestCase):
    def rejects(self, code, callback):
        with self.assertRaises(UpdateError) as error:
            callback()
        self.assertEqual(error.exception.code, code)


class WindowsCredentialsTests(CredentialAssertions):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ota-windows-credentials-")
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / "home"
        self.home.mkdir()
        identity = self.home / "data" / "identity"
        identity.mkdir(parents=True)
        (identity / "identity.dpapi").write_bytes(b"synthetic fixture bytes, never decrypted")
        self.state = {"schema": 1, "state": "connected", "relay": "https://relay.example.test",
                      "deviceToken": TOKEN, "terminalId": TERMINAL}

    def authorize(self, state, source=WINDOWS_SOURCE):
        calls = []

        class FakeStore:
            def __init__(_self, path):
                calls.append(Path(path))

            def lock(_self):
                return nullcontext()

            def read(_self):
                return state

        with patch.object(credentials, "_source", return_value=("windows", source)), patch(
                "windows.vendor.connector_core.CredentialStore", FakeStore):
            result = credentials.for_installation(self.home)
        self.assertEqual(calls, [self.home / "data" / "identity"])
        return result

    def test_connected_identity_reuses_device_capability_without_exposing_token(self):
        authorization = self.authorize(self.state)
        headers = authorization.headers_for(
            "https://relay.example.test/desktop-remote-updates/test/windows/windows-0.1.0.zip")
        self.assertEqual(headers["Authorization"], "Bearer " + TOKEN)
        self.assertEqual(headers["X-Desktop-Remote-Principal"], "device")
        self.assertEqual(headers["X-Desktop-Remote-Device"], TERMINAL)
        self.assertNotIn(TOKEN, repr(authorization))

    def test_missing_paused_review_and_cross_source_are_rejected(self):
        self.rejects("update_device_not_paired", lambda: self.authorize(None))
        self.rejects("update_device_not_paired", lambda: self.authorize({**self.state, "state": "paused"}))
        self.rejects("identity_or_runtime_requires_review", lambda: self.authorize(
            {**self.state, "runtimeNeedsReview": True}))
        self.rejects("untrusted_update_url", lambda: self.authorize(
            self.state, source=WINDOWS_SOURCE.replace("relay.example.test", "other.example.test")))

    def test_unavailable_store_has_fixed_error(self):
        class BrokenStore:
            def __init__(self, _path):
                raise OSError("synthetic private exception")

        with patch.object(credentials, "_source", return_value=("windows", WINDOWS_SOURCE)), patch(
                "windows.vendor.connector_core.CredentialStore", BrokenStore):
            self.rejects("update_device_identity_unavailable", lambda: credentials.for_installation(self.home))

    def test_absent_identity_is_not_created_by_update_check(self):
        identity = self.home / 'data' / 'identity'
        (identity / 'identity.dpapi').unlink()
        identity.rmdir()
        with patch.object(credentials, '_source', return_value=('windows', WINDOWS_SOURCE)), patch(
                'windows.vendor.connector_core.CredentialStore') as store:
            self.rejects('update_device_not_paired', lambda: credentials.for_installation(self.home))
            store.assert_not_called()
        self.assertFalse(identity.exists())


@unittest.skipUnless(os.name == "posix" and credentials.sys_linux(), "Linux owner-only login")
class LinuxCredentialsTests(CredentialAssertions):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ota-linux-credentials-")
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / "home"
        self.home.mkdir(mode=0o700)
        (self.home / "state").mkdir(mode=0o700)
        os.chmod(self.home, 0o700)
        os.chmod(self.home / "state", 0o700)
        (self.home / "trust.json").write_text(json.dumps({
            "schema": 1, "product": "codex-desktop-remote", "channel": "test",
            "keys": {"synthetic": "not-used-by-these-tests"}, "sources": {"relay": RELAY_SOURCE},
        }), encoding="utf-8")
        self.installed = patch.object(credentials, "installed", return_value=({"component": "relay"}, {}, self.home))
        self.installed.start()
        self.addCleanup(self.installed.stop)
        self.record = self.home / "state" / credentials.LOGIN_FILE

    def _login(self):
        notices = []
        def fake_post(relay, operation, body, **_kwargs):
            self.assertEqual(relay, "https://relay.example.test")
            self.assertEqual(operation, "login")
            self.assertEqual(body, {"username": "synthetic", "password": "synthetic-password"})
            return {"token": TOKEN, "expiresAt": int(time.time()) + 3600}
        with patch.object(credentials, "_post", side_effect=fake_post):
            result = credentials.login(self.home, username_provider=lambda _: "synthetic",
                                       password_provider=lambda _: "synthetic-password",
                                       notice=notices.append)
        self.assertEqual(notices, ["Relay update login server: https://relay.example.test"])
        self.assertEqual(result["authenticated"], True)
        return read(self.record)

    def test_login_persists_only_expiring_token_and_logout_removes_after_revoke(self):
        value = self._login()
        self.assertEqual(set(value), {"schema", "relay", "token", "expiresAt"})
        self.assertEqual(value["token"], TOKEN)
        self.assertNotIn("synthetic-password", self.record.read_text(encoding="utf-8"))
        self.assertEqual(stat.S_IMODE(self.home.stat().st_mode), 0o700)
        self.assertEqual(stat.S_IMODE((self.home / "state").stat().st_mode), 0o700)
        self.assertEqual(stat.S_IMODE(self.record.stat().st_mode), 0o600)
        authorization = credentials.for_installation(self.home)
        self.assertEqual(authorization.headers_for(
            "https://relay.example.test/desktop-remote-updates/test/relay/relay-0.1.0.zip")[
                "Authorization"], "Bearer " + TOKEN)
        with patch.object(credentials, "_post", return_value={}) as post:
            self.assertEqual(credentials.logout(self.home), {"signedOut": True})
        self.assertEqual(post.call_args.kwargs["token"], TOKEN)
        self.assertFalse(self.record.exists())

    def test_missing_expired_hardlink_and_loose_permissions_rejected(self):
        self.rejects("update_authentication_required", lambda: credentials.for_installation(self.home))
        atomic(self.record, {"schema": 1, "relay": "https://relay.example.test",
                             "token": TOKEN, "expiresAt": int(time.time()) - 1})
        self.rejects("update_authentication_required", lambda: credentials.for_installation(self.home))
        atomic(self.record, {"schema": 1, "relay": "https://relay.example.test",
                             "token": TOKEN, "expiresAt": int(time.time()) + 3600})
        link = self.home / "state" / "same-inode.json"
        os.link(self.record, link)
        self.rejects("update_login_permissions_invalid", lambda: credentials.for_installation(self.home))
        link.unlink()
        os.chmod(self.record, 0o644)
        self.rejects("update_login_permissions_invalid", lambda: credentials.for_installation(self.home))
        os.chmod(self.record, 0o600)
        os.chmod(self.home / "state", 0o755)
        self.rejects("update_login_permissions_invalid", lambda: credentials.login(
            self.home, username_provider=lambda _: "synthetic",
            password_provider=lambda _: "synthetic-password", notice=lambda _: None))

    def test_existing_login_and_failed_logout_preserve_record(self):
        original = self._login()
        self.rejects("update_login_exists_logout_first", lambda: credentials.login(
            self.home, username_provider=lambda _: "synthetic",
            password_provider=lambda _: "synthetic-password", notice=lambda _: None))
        with patch.object(credentials, "_post", side_effect=UpdateError("update_access_denied")):
            self.rejects("update_access_denied", lambda: credentials.logout(self.home))
        self.assertEqual(read(self.record), original)

    def test_logout_detects_changed_record_and_preserves_new_token(self):
        original = self._login()
        def changed(_relay, operation, _body, **_kwargs):
            self.assertEqual(operation, "logout")
            atomic(self.record, {**original, "token": TOKEN_NEXT})
            return {}
        with patch.object(credentials, "_post", side_effect=changed):
            self.rejects("update_login_changed", lambda: credentials.logout(self.home))
        self.assertEqual(read(self.record)["token"], TOKEN_NEXT)

    def test_logout_preserves_same_content_replaced_file(self):
        original = self._login()
        def replaced(*_args, **_kwargs):
            atomic(self.record, original)
            return {}
        with patch.object(credentials, '_post', side_effect=replaced):
            self.rejects('update_login_changed', lambda: credentials.logout(self.home))
        self.assertEqual(read(self.record), original)


if __name__ == "__main__":
    unittest.main()
