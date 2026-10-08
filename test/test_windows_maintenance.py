"""Isolated alpha.5 -> alpha.6 maintenance contracts; no real GUI or identity."""

import base64
from contextlib import contextmanager
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import time
import types
import unittest
from unittest.mock import patch
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from updates import windows_maintenance as maintenance
from updates.engine import Transaction
from updates.security import COMPATIBILITY, UpdateError, unpack
from updates.storage import atomic, digest, ensure_launchable, read


SOURCE = "https://updates.example.test/desktop-remote-updates/test/windows/latest.json"
OLD = "0.1.0-alpha.5"
NEW = "0.1.0-alpha.6"


def tree(root, version, payload=b"synthetic source"):
    files = {"app.txt": payload, "windows/ota_entry.py": b"synthetic entry"}
    manifest = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                "platform": "windows-x64", "version": version, "dataSchema": 1,
                "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    root.mkdir(parents=True)
    for name, data in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    (root / "package.json").write_text(json.dumps(manifest), encoding="utf-8")
    return manifest


class FakeStore:
    def __init__(self):
        self.state = {"state": "connected", "identity": "synthetic only"}
        self.leases = []

    @contextmanager
    def lock(self, name="identity.lock"):
        self.leases.append(name)
        yield

    def read(self):
        return dict(self.state)


class MaintenanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.public = cls.key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="maintenance-synthetic-")
        self.addCleanup(self.temporary.cleanup)
        # Windows CI may supply RUNNER~1 in TEMP. Production review first calls
        # physical_home(), so the synthetic expected command must use that
        # same canonical path rather than a short-name alias.
        self.base = Path(self.temporary.name).resolve(strict=True)
        self.home = self.base / "home"
        for name in ("versions", "staging", "state", "data/identity", "data/task/receipts"):
            (self.home / name).mkdir(parents=True, exist_ok=True)
        self.old_root = self.home / "versions/old"
        tree(self.old_root, OLD)
        self.old = {"path": "versions/old", "version": OLD,
                    "manifestSha256": hashlib.sha256((self.old_root / "package.json").read_bytes()).hexdigest()}
        atomic(self.home / "current.json", self.old)
        atomic(self.home / "install.json", {"schema": 1, "component": "windows",
                                                 "platform": "windows-x64", "compatibility": COMPATIBILITY["windows"]})
        atomic(self.home / "trust.json", {"schema": 1, "product": "codex-desktop-remote",
                                               "channel": "test", "keys": {"synthetic": self.public},
                                               "sources": {"windows": SOURCE}})
        atomic(self.home / "state/ledger.json", {"sequence": 5, "binding": "1" * 64,
                                                     "versions": {OLD: "2" * 64}})
        (self.home / "Start.pyw").write_bytes(b"synthetic launcher")
        (self.home / "data/identity/identity.dpapi").write_bytes(b"synthetic opaque identity")
        (self.home / "data/fixture.local.json").write_text("synthetic fixture", encoding="utf-8")
        (self.home / "data/task/receipts" / ("a" * 64 + ".json")).write_text(
            '{"schema":1,"state":"accepted","turnId":"synthetic"}', encoding="utf-8")
        self.store = FakeStore()
        self.controller = types.SimpleNamespace(store=self.store,
            config={"dataDir": str(self.home / "data/task")},
            config_file=self.home / "data/fixture.local.json")
        self.archive = self.base / "candidate.zip"
        self.metadata = self.base / "latest.json"
        self.sign_candidate()

    @contextmanager
    def fake_controller(self):
        with patch.object(maintenance, "controller_for", return_value=self.controller):
            yield

    def sign_candidate(self, version=NEW, sequence=6):
        candidate = self.base / "candidate-source"
        if candidate.exists():
            shutil.rmtree(candidate)
        tree(candidate, version, b"candidate source")
        with zipfile.ZipFile(self.archive, "w") as bundle:
            for path in sorted(candidate.rglob("*")):
                if path.is_file():
                    bundle.write(path, path.relative_to(candidate).as_posix())
        now = int(time.time())
        payload = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                   "platform": "windows-x64", "channel": "test", "version": version,
                   "sequence": sequence, "issuedAt": now - 1, "expiresAt": now + 3600,
                   "url": SOURCE.rsplit("/", 1)[0] + f"/windows-{version}.zip",
                   "bytes": self.archive.stat().st_size,
                   "sha256": hashlib.sha256(self.archive.read_bytes()).hexdigest(),
                   "notes": "synthetic", "compatibility": [COMPATIBILITY["windows"]],
                   "dataSchema": 1, "rollbackCompatible": True}
        raw = json.dumps(payload, separators=(",", ":")).encode()
        envelope = {"schema": 1, "keyId": "synthetic",
                    "payload": base64.b64encode(raw).decode(),
                    "signature": base64.b64encode(self.key.sign(raw, padding.PKCS1v15(), hashes.SHA256())).decode()}
        atomic(self.metadata, envelope)
        return envelope, payload

    def review_value(self):
        with self.fake_controller():
            envelope, release, _, _ = maintenance.bundle(self.home, self.metadata, self.archive)
            before = maintenance.binding(self.home, allow_running=True)
        now = int(time.time())
        return {"schema": 1, "kind": "alpha5-reviewer-alias", "home": str(self.home),
                "owner": {"pid": 1, "created": 2, "path": "synthetic", "sha256": "3" * 64},
                "createdAt": now, "expiresAt": now + maintenance.REVIEW_SECONDS,
                "binding": before, "envelope": digest(envelope), "package": release["sha256"]}

    def test_bundle_requires_exact_old_and_new_versions_test_channel_and_signed_media(self):
        maintenance.bundle(self.home, self.metadata, self.archive)
        self.sign_candidate("0.1.0-alpha.7")
        with self.assertRaisesRegex(UpdateError, "maintenance_candidate_version_mismatch"):
            maintenance.bundle(self.home, self.metadata, self.archive)
        self.sign_candidate()
        (self.archive).write_bytes(self.archive.read_bytes() + b"changed")
        with self.assertRaisesRegex(UpdateError, "maintenance_package_changed"):
            maintenance.bundle(self.home, self.metadata, self.archive)
        self.sign_candidate()
        envelope = read(self.metadata)
        envelope["signature"] = base64.b64encode(b"invalid").decode()
        atomic(self.metadata, envelope)
        with self.assertRaisesRegex(UpdateError, "invalid_release_signature"):
            maintenance.bundle(self.home, self.metadata, self.archive)
        self.sign_candidate()
        ledger = read(self.home / "state/ledger.json")
        ledger["sequence"] = 7
        atomic(self.home / "state/ledger.json", ledger)
        with self.assertRaisesRegex(UpdateError, "release_replay_rejected"):
            maintenance.bundle(self.home, self.metadata, self.archive)
        ledger["sequence"] = 5
        atomic(self.home / "state/ledger.json", ledger)
        trust = read(self.home / "trust.json")
        trust["channel"] = "stable"
        atomic(self.home / "trust.json", trust)
        with self.assertRaisesRegex(UpdateError, "maintenance_test_channel_required"):
            maintenance.bundle(self.home, self.metadata, self.archive)
        trust["channel"] = "test"
        atomic(self.home / "trust.json", trust)
        current = dict(self.old, version="0.1.0-alpha.4")
        atomic(self.home / "current.json", current)
        with self.assertRaises(UpdateError):
            maintenance.bundle(self.home, self.metadata, self.archive)

    def test_review_hash_expiry_and_binding_change_are_rejected(self):
        review = self.review_value()
        expected = digest(review)
        maintenance.validate_review(review, expected, now=review["createdAt"])
        with self.assertRaisesRegex(UpdateError, "maintenance_review_changed"):
            maintenance.validate_review({**review, "package": "0" * 64}, expected)
        for now in (review["createdAt"] - 1, review["expiresAt"]):
            with self.assertRaisesRegex(UpdateError, "maintenance_review_expired"):
                maintenance.validate_review(review, expected, now=now)
        with self.fake_controller():
            self.store.state["identity"] = "changed"
            with self.assertRaisesRegex(UpdateError, "maintenance_state_changed"):
                maintenance.stage_local(self.home, self.metadata, self.archive, review)
        self.assertEqual(read(self.home / "current.json"), self.old)
        self.assertFalse((self.home / "state/staged.json").exists())

    def test_runtime_needs_review_false_is_not_accepted_as_cold_state(self):
        self.store.state["runtimeNeedsReview"] = False
        with self.fake_controller():
            with self.assertRaisesRegex(UpdateError, "identity_or_runtime_requires_review"):
                maintenance.binding(self.home, allow_running=True)
            with self.assertRaisesRegex(UpdateError, "identity_or_runtime_requires_review"):
                maintenance.binding(self.home)

    def test_stage_local_preserves_pointer_and_never_overwrites_existing_slot(self):
        review = self.review_value()
        with self.fake_controller():
            pointer, expected = maintenance.stage_local(self.home, self.metadata, self.archive, review)
        self.assertEqual(read(self.home / "current.json"), self.old)
        self.assertEqual(read(self.home / "state/staged.json")["candidate"], pointer)
        self.assertNotEqual(expected["ledger"], review["binding"]["ledger"])
        self.assertTrue((self.home / pointer["path"]).is_dir())

    def test_existing_damaged_candidate_slot_is_not_overwritten(self):
        review = self.review_value()
        envelope, release, _, _ = maintenance.bundle(self.home, self.metadata, self.archive)
        destination = self.home / "versions" / (NEW + "-" + release["sha256"][:16])
        destination.mkdir()
        marker = destination / "sentinel.txt"
        marker.write_text("do not overwrite", encoding="utf-8")
        with self.fake_controller():
            with self.assertRaises(UpdateError):
                maintenance.stage_local(self.home, self.metadata, self.archive, review)
        self.assertEqual(marker.read_text(encoding="utf-8"), "do not overwrite")
        self.assertEqual(read(self.home / "current.json"), self.old)

    def staged_driver(self):
        review = self.review_value()
        with self.fake_controller():
            pointer, expected = maintenance.stage_local(self.home, self.metadata, self.archive, review)
        update_driver = sys.modules[maintenance.WindowsDriver.__module__]
        with patch.object(update_driver, "controller_for", return_value=self.controller):
            driver = maintenance.AliasRecoveryDriver(self.home, self.old_root, pointer, expected)
        self.store.leases.clear()
        return pointer, driver

    def transaction_hooks(self, driver, *, fail_verify=False, fail_cleanup=False,
                          fail_activate=False):
        events = []

        @contextmanager
        def application_lock(path):
            events.append("application_lock_enter")
            try:
                yield
            finally:
                events.append("application_lock_exit")

        def probe(root, home):
            self.assertEqual(root, driver.candidate_root)
            self.assertEqual(home, self.home)
            events.append("candidate_alias_probe")

        def start(self_driver, root):
            events.append("candidate_start")
            self.assertEqual(root, driver.candidate_root)

        def verify(root):
            events.append("candidate_verify")
            if fail_verify:
                raise UpdateError("candidate_health_unverified")

        def cleanup():
            events.append("cleanup_target")
            if fail_cleanup:
                raise UpdateError("candidate_tree_exit_unconfirmed")

        def activate():
            events.append("activate")
            if fail_activate:
                raise UpdateError("activation_ack_unknown")

        from contextlib import ExitStack
        stack = ExitStack()
        stack.enter_context(self.fake_controller())
        stack.enter_context(patch.object(maintenance, "lock", application_lock))
        stack.enter_context(patch.object(maintenance, "alias_probe", probe))
        stack.enter_context(patch.object(maintenance.WindowsDriver, "start", start))
        stack.enter_context(patch.object(driver, "verify", verify))
        stack.enter_context(patch.object(driver, "cleanup_target", cleanup))
        stack.enter_context(patch.object(driver, "activate", activate))
        return events, stack

    def test_driver_preflight_uses_locks_and_only_candidate_alias_probe(self):
        _, driver = self.staged_driver()
        events, hooks = self.transaction_hooks(driver)
        with hooks:
            driver.preflight()
            self.assertEqual(events[:2], ["application_lock_enter", "candidate_alias_probe"])
            self.assertIn("runtime.lock", self.store.leases)
            driver.release("checked")
        self.assertEqual(events[-1], "application_lock_exit")
        self.assertEqual(read(self.home / "current.json"), self.old)

    def test_failed_candidate_cleans_tree_and_restores_pointer_without_old_start(self):
        pointer, driver = self.staged_driver()
        events, hooks = self.transaction_hooks(driver, fail_verify=True)
        with hooks:
            with self.assertRaisesRegex(UpdateError, "update_needs_review"):
                Transaction(self.home, driver).apply()
        self.assertLess(events.index("candidate_start"), events.index("cleanup_target"))
        self.assertNotIn("activate", events)
        self.assertEqual(events.count("candidate_start"), 1)
        self.assertEqual(read(self.home / "current.json"), self.old)
        self.assertEqual(read(self.home / "state/transaction.json")["phase"], "needs_review")
        self.assertEqual(read(self.home / "state/staged.json")["candidate"], pointer)

    def test_unproven_candidate_cleanup_keeps_candidate_pointer_and_needs_review(self):
        pointer, driver = self.staged_driver()
        events, hooks = self.transaction_hooks(driver, fail_verify=True, fail_cleanup=True)
        with hooks:
            with self.assertRaisesRegex(UpdateError, "update_needs_review"):
                Transaction(self.home, driver).apply()
        self.assertIn("cleanup_target", events)
        self.assertEqual(events.count("candidate_start"), 1)
        self.assertEqual(read(self.home / "current.json"), pointer)
        self.assertEqual(read(self.home / "state/transaction.json")["phase"], "needs_review")

    def test_ambiguous_activation_never_cleans_or_restarts_old(self):
        pointer, driver = self.staged_driver()
        events, hooks = self.transaction_hooks(driver, fail_activate=True)
        with hooks:
            with self.assertRaisesRegex(UpdateError, "update_needs_review"):
                Transaction(self.home, driver).apply()
        self.assertIn("activate", events)
        self.assertNotIn("cleanup_target", events)
        self.assertEqual(events.count("candidate_start"), 1)
        self.assertEqual(read(self.home / "current.json"), pointer)
        self.assertEqual(read(self.home / "state/transaction.json")["phase"], "needs_review")

    def test_review_is_offline_and_apply_rejects_changed_binding_or_owner(self):
        identity = {"pid": 2468, "created": 999, "path": str(Path(sys.executable).resolve()),
                    "sha256": "3" * 64}

        class Pin:
            def __init__(self, expected, current=False):
                if expected == {"pid": 2468} and current:
                    self.identity = identity
                elif expected == identity and not current:
                    raise UpdateError("old_process_owner_changed")
                else:
                    raise AssertionError("unexpected pin request")

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

        output = self.base / "review.json"
        fake_os = types.SimpleNamespace(name="nt", fsync=os.fsync, open=os.open)
        with self.fake_controller(), patch.object(maintenance, "os", fake_os), patch.object(
                maintenance, "PinnedProcess", Pin), patch.object(
                maintenance, "verify_gui", return_value=None), patch.object(
                maintenance, "alias_probe", return_value=None):
            result = maintenance.make_review(self.home, self.metadata, self.archive, 2468, output)
            self.assertEqual(result["reviewHash"], digest(read(output)))
            self.assertEqual(read(self.home / "current.json"), self.old)
            self.assertFalse((self.home / "state/staged.json").exists())
            self.store.state["identity"] = "changed"
            with self.assertRaisesRegex(UpdateError, "maintenance_state_changed"):
                maintenance.apply_review(output, result["reviewHash"], self.metadata, self.archive)
            self.store.state["identity"] = "synthetic only"
            with self.assertRaisesRegex(UpdateError, "old_process_owner_changed"):
                maintenance.apply_review(output, result["reviewHash"], self.metadata, self.archive)
        self.assertEqual(read(self.home / "current.json"), self.old)
        self.assertFalse((self.home / "state/staged.json").exists())

    def test_gui_cim_user_session_and_exact_command_line_are_required(self):
        self.assertEqual(self.home, self.home.resolve(strict=True))
        self.assertEqual(self.old_root, self.old_root.resolve(strict=True))
        identity = {"pid": 2468, "path": str(Path(sys.executable).resolve())}
        pin = types.SimpleNamespace(identity=identity, handle=object(),
            api=types.SimpleNamespace(WaitForSingleObject=lambda *_: 258))
        correct = [str(Path(sys.executable).resolve()), "-B", "-I",
                   str(self.old_root / "windows/ota_entry.py"), "--home", str(self.home)]
        info = {"command": "synthetic CIM command", "sameUser": True, "sameSession": True}

        def call(value, args):
            result = types.SimpleNamespace(returncode=0, stdout=json.dumps(value).encode())
            with patch.object(maintenance.subprocess, "run", return_value=result), patch.object(
                    maintenance.subprocess, "CREATE_NO_WINDOW", 0, create=True), patch.object(
                    maintenance, "command_args", return_value=args):
                maintenance.verify_gui(pin, self.home, self.old_root)

        call(info, correct)
        for changed, args in (({**info, "sameUser": False}, correct),
                              ({**info, "sameSession": False}, correct),
                              (info, [*correct[:-1], str(self.base / "other-home")]),
                              (info, [*correct[:3], str(self.base / "other-entry.py"), *correct[4:]])):
            with self.subTest(changed=changed, args=args):
                with self.assertRaisesRegex(UpdateError, "gui_command_unverified"):
                    call(changed, args)

    def fenced_driver(self, prior=None):
        pointer, driver = self.staged_driver()
        if prior is not None:
            atomic(self.home / "state/transaction.json", prior)
        review_hash = "4" * 64
        with self.fake_controller():
            marker = maintenance.fence(self.home, review_hash, driver.expected)
        return pointer, driver, review_hash, marker

    def test_fence_blocks_cold_launch_and_embeds_previous_terminal_journal(self):
        previous = {"schema": 1, "phase": "committed", "success": True,
                    "candidate": {"version": OLD}}
        _, _, review_hash, marker = self.fenced_driver(previous)
        self.assertEqual(marker["phase"], "needs_review")
        self.assertEqual(marker["maintenanceReviewHash"], review_hash)
        self.assertEqual(marker["error"], "maintenance_waiting_normal_gui_exit")
        self.assertEqual(marker["previousJournal"], previous)
        self.assertEqual(read(self.home / "state/transaction.json"), marker)
        histories = list((self.home / "state").glob("history-*.json"))
        self.assertEqual(len(histories), 1)
        self.assertEqual(read(histories[0]), previous)
        with self.assertRaisesRegex(UpdateError, "interrupted_update_requires_review"):
            ensure_launchable(self.home)

    def test_success_mirrors_committed_to_both_journals(self):
        pointer, driver, review_hash, _ = self.fenced_driver()
        events, hooks = self.transaction_hooks(driver)
        with hooks:
            result = maintenance.MaintenanceTransaction(self.home, driver, review_hash).apply()
        self.assertEqual(result["phase"], "committed")
        self.assertEqual(read(self.home / "current.json"), pointer)
        primary = read(self.home / "state/transaction.json")
        secondary = read(self.home / "state/maintenance-transaction.json")
        self.assertEqual(primary, secondary)
        self.assertEqual(primary["phase"], "committed")
        self.assertEqual(primary["maintenanceReviewHash"], review_hash)
        self.assertEqual(events.count("candidate_start"), 1)
        ensure_launchable(self.home)

    def test_failed_candidate_mirrors_needs_review_without_old_restart(self):
        _, driver, review_hash, _ = self.fenced_driver()
        events, hooks = self.transaction_hooks(driver, fail_verify=True)
        with hooks:
            with self.assertRaisesRegex(UpdateError, "update_needs_review"):
                maintenance.MaintenanceTransaction(self.home, driver, review_hash).apply()
        self.assertEqual(events.count("candidate_start"), 1)
        self.assertIn("cleanup_target", events)
        self.assertEqual(read(self.home / "current.json"), self.old)
        self.assertEqual(read(self.home / "state/transaction.json")["phase"], "needs_review")
        self.assertEqual(read(self.home / "state/maintenance-transaction.json")["phase"], "needs_review")
        with self.assertRaisesRegex(UpdateError, "interrupted_update_requires_review"):
            ensure_launchable(self.home)

    def test_preflight_or_interruption_preserves_primary_fence(self):
        _, driver, review_hash, marker = self.fenced_driver()
        events, hooks = self.transaction_hooks(driver)
        with hooks, patch.object(maintenance, "alias_probe",
                                 side_effect=UpdateError("active_or_unverified_desktop_task")):
            tx = maintenance.MaintenanceTransaction(self.home, driver, review_hash)
            with self.assertRaisesRegex(UpdateError, "active_or_unverified_desktop_task"):
                tx.apply()
            with self.assertRaisesRegex(UpdateError, "maintenance_interruption_requires_review"):
                tx.recover()
        self.assertEqual(read(self.home / "state/transaction.json"), marker)
        self.assertFalse((self.home / "state/maintenance-transaction.json").exists())
        with self.assertRaisesRegex(UpdateError, "interrupted_update_requires_review"):
            ensure_launchable(self.home)

    def test_expired_review_does_not_clear_existing_primary_fence(self):
        review = self.review_value()
        _, _, _, marker = self.fenced_driver()
        with self.assertRaisesRegex(UpdateError, "maintenance_review_expired"):
            maintenance.validate_review(review, digest(review), now=review["expiresAt"])
        self.assertEqual(read(self.home / "state/transaction.json"), marker)
        with self.assertRaisesRegex(UpdateError, "interrupted_update_requires_review"):
            ensure_launchable(self.home)

    def test_mirror_write_failure_keeps_primary_fence_and_secondary_evidence(self):
        _, driver, review_hash, marker = self.fenced_driver()
        tx = maintenance.MaintenanceTransaction(self.home, driver, review_hash)
        tx.value = {"schema": 1, "success": False}
        original_atomic = maintenance.atomic

        def fail_primary(path, value):
            if Path(path) == self.home / "state/transaction.json":
                raise OSError("synthetic mirror failure")
            return original_atomic(path, value)

        with patch.object(maintenance, "atomic", side_effect=fail_primary):
            with self.assertRaisesRegex(OSError, "synthetic mirror failure"):
                tx.record("prepared")
        self.assertEqual(read(self.home / "state/transaction.json"), marker)
        self.assertEqual(read(self.home / "state/maintenance-transaction.json")["phase"], "prepared")
        with self.assertRaisesRegex(UpdateError, "interrupted_update_requires_review"):
            ensure_launchable(self.home)


if __name__ == "__main__":
    unittest.main()
