"""Synthetic signed-package transactions with real temp slots and a fake lifecycle driver."""

import base64
import copy
import hashlib
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import time
import unittest
from unittest.mock import patch
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from updates import engine
from updates.security import UpdateError
from updates.storage import atomic, read


COMPAT = "desktop-26.930.7945.0"


def package(root, version, content):
    root.mkdir(parents=True)
    (root / "app.txt").write_bytes(content)
    manifest = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                "platform": "windows-x64", "version": version, "dataSchema": 1,
                "files": {"app.txt": hashlib.sha256(content).hexdigest()}}
    (root / "package.json").write_text(json.dumps(manifest), encoding="utf-8")
    return manifest


class Driver:
    def __init__(self):
        self.events = []
        self.fail = set()
        self.baseline = {"identity": "same", "receipts": "same", "task": "same"}
        self.stopped = False
        self.active = False

    def step(self, name):
        self.events.append(name)
        if name in self.fail:
            raise UpdateError(name + "_failed")

    def preflight(self): self.step("preflight")
    def snapshot(self):
        self.events.append("snapshot")
        return copy.deepcopy(self.baseline)
    def stop_old(self):
        self.step("stop_old")
        self.stopped = True
    def start(self, root): self.step("start_" + Path(root).name)
    def verify(self, root): self.step("verify_" + Path(root).name)
    def cleanup_target(self): self.step("cleanup_target")
    def activate(self):
        self.step("activate")
        self.active = True
    def release(self, outcome): self.events.append("release_" + outcome)


class EngineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.pem = cls.private.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()

    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        for name in ("versions", "staging", "state"):
            (self.home / name).mkdir()
        self.old = self.home / "versions" / "old"
        package(self.old, "0.1.0-alpha.2", b"old")
        self.pointer = {"path": "versions/old", "version": "0.1.0-alpha.2",
                        "manifestSha256": hashlib.sha256((self.old / "package.json").read_bytes()).hexdigest()}
        atomic(self.home / "current.json", self.pointer)
        atomic(self.home / "install.json", {"schema": 1, "component": "windows", "platform": "windows-x64",
                                                   "compatibility": COMPAT})
        source = "https://updates.example.test/ota/test/windows/latest.json"
        atomic(self.home / "trust.json", {"schema": 1, "product": "codex-desktop-remote", "channel": "test",
                                                 "keys": {"fixture": self.pem}, "sources": {"windows": source}})
        payload_tree = self.home / "fixture-package"
        package(payload_tree, "0.1.0-alpha.3", b"candidate")
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as bundle:
            for path in payload_tree.iterdir():
                bundle.write(path, path.name)
        self.archive = archive.getvalue()
        now = int(time.time())
        payload = {"schema": 1, "product": "codex-desktop-remote", "component": "windows",
                   "platform": "windows-x64", "channel": "test", "version": "0.1.0-alpha.3",
                   "sequence": 3, "issuedAt": now - 10, "expiresAt": now + 3600,
                   "url": "https://updates.example.test/ota/test/windows/build.zip",
                   "bytes": len(self.archive), "sha256": hashlib.sha256(self.archive).hexdigest(),
                   "notes": "synthetic", "compatibility": [COMPAT], "dataSchema": 1,
                   "rollbackCompatible": True}
        raw = json.dumps(payload, separators=(",", ":")).encode()
        signature = self.private.sign(raw, padding.PKCS1v15(), hashes.SHA256())
        self.envelope = {"schema": 1, "keyId": "fixture", "payload": base64.b64encode(raw).decode(),
                         "signature": base64.b64encode(signature).decode()}
        self.driver = Driver()

    def prepared(self):
        with patch.object(engine, "fetch", return_value=self.envelope), patch.object(
                engine, "download", side_effect=lambda payload, path, **kwargs: Path(path).write_bytes(self.archive)):
            self.assertEqual(engine.check(self.home)["version"], "0.1.0-alpha.3")
            self.assertEqual(engine.stage(self.home)["sha256"], hashlib.sha256(self.archive).hexdigest())
        return engine.Transaction(self.home, self.driver)

    def test_success_uses_real_signed_stage_and_all_driver_phases(self):
        tx = self.prepared()
        result = tx.apply()
        self.assertEqual(result["phase"], "committed")
        self.assertEqual(read(self.home / "current.json"), result["candidate"])
        self.assertEqual(read(self.home / "state" / "transaction.json")["phase"], "committed")
        self.assertLess(self.driver.events.index("stop_old"), self.driver.events.index("activate"))
        self.assertIn("snapshot", self.driver.events)
        self.assertEqual(self.driver.events[-1], "release_committed")

    def test_active_preflight_refuses_without_stopping_or_journaling(self):
        tx = self.prepared()
        self.driver.fail.add("preflight")
        with self.assertRaisesRegex(UpdateError, "preflight_failed"):
            tx.apply()
        self.assertNotIn("stop_old", self.driver.events)
        self.assertFalse(tx.journal.exists())
        self.assertEqual(read(self.home / "current.json"), self.pointer)

    def test_install_revalidates_slot_and_signed_archive_before_preflight(self):
        tx = self.prepared()
        candidate = read(self.home / "state" / "staged.json")["candidate"]
        (self.home / candidate["path"] / "app.txt").write_bytes(b"changed after staging")
        with self.assertRaisesRegex(UpdateError, "package_file_changed"):
            tx.apply()
        self.assertEqual(self.driver.events, [])
        self.assertFalse(tx.journal.exists())


    def test_install_rejects_archive_swap_before_preflight(self):
        tx = self.prepared()
        next((self.home / "staging").glob("*/package.zip")).write_bytes(b"not the signed archive")
        with self.assertRaisesRegex(UpdateError, "staged_package_missing"):
            tx.apply()
        self.assertEqual(self.driver.events, [])
        self.assertFalse(tx.journal.exists())

    def test_pointer_cas_conflict_keeps_foreign_state_and_does_not_restart_old(self):
        tx = self.prepared()
        original_stop = self.driver.stop_old
        def conflict():
            original_stop()
            atomic(self.home / "current.json", {"path": "versions/foreign", "version": "foreign"})
        self.driver.stop_old = conflict
        with self.assertRaisesRegex(UpdateError, "update_needs_review"):
            tx.apply()
        self.assertEqual(read(tx.pointer)["version"], "foreign")
        self.assertEqual(read(tx.journal)["phase"], "needs_review")
        self.assertIn("cleanup_target", self.driver.events)
        self.assertNotIn("start_old", self.driver.events)

    def test_candidate_failure_rolls_back_only_after_cleanup(self):
        tx = self.prepared()
        candidate_name = read(self.home / "state" / "staged.json")["candidate"]["path"].split("/")[-1]
        self.driver.fail.add("verify_" + candidate_name)
        with self.assertRaisesRegex(UpdateError, "update_rolled_back"):
            tx.apply()
        self.assertEqual(read(tx.pointer), self.pointer)
        self.assertEqual(read(tx.journal)["phase"], "rolled_back")
        self.assertLess(self.driver.events.index("cleanup_target"), self.driver.events.index("start_old"))

    def test_unproven_cleanup_never_starts_old(self):
        tx = self.prepared()
        candidate_name = read(self.home / "state" / "staged.json")["candidate"]["path"].split("/")[-1]
        self.driver.fail.update({"verify_" + candidate_name, "cleanup_target"})
        with self.assertRaisesRegex(UpdateError, "update_needs_review"):
            tx.apply()
        self.assertEqual(read(tx.journal)["phase"], "needs_review")
        self.assertNotIn("start_old", self.driver.events)

    def test_rollback_keeps_only_bounded_error_code(self):
        for failure, expected in ((UpdateError('candidate_health_unverified'), 'candidate_health_unverified'),
                                  (RuntimeError('synthetic private token'), 'update_operation_failed')):
            with self.subTest(error=type(failure).__name__):
                # Each transaction starts from a new staged request, preserving its history.
                tx = self.prepared()
                original_verify = self.driver.verify
                def fail_candidate(root):
                    if Path(root).name != 'old':
                        raise failure
                    return original_verify(root)
                self.driver.verify = fail_candidate
                with self.assertRaisesRegex(UpdateError, 'update_rolled_back'):
                    tx.apply()
                self.assertEqual(read(tx.journal)['cause'], expected)
                self.assertNotIn('synthetic private token', tx.journal.read_text())
                self.driver.verify = original_verify

    def test_failed_rollback_is_reviewable(self):
        tx = self.prepared()
        candidate_name = read(self.home / "state" / "staged.json")["candidate"]["path"].split("/")[-1]
        self.driver.fail.update({"verify_" + candidate_name, "verify_old"})
        with self.assertRaisesRegex(UpdateError, "update_needs_review"):
            tx.apply()
        self.assertEqual(read(tx.journal)["phase"], "needs_review")
        self.assertEqual(read(tx.pointer), self.pointer)
        self.assertNotIn("activate", self.driver.events)

    def test_power_loss_recover_blocks_replay_without_driver_actions(self):
        tx = self.prepared()
        atomic(tx.journal, {"schema": 1, "phase": "switched", "old": self.pointer})
        self.assertEqual(tx.recover()["phase"], "needs_review")
        self.assertEqual(self.driver.events, [])
        with self.assertRaisesRegex(UpdateError, "unfinished_update_requires_review"):
            tx.apply()

    def test_preservation_change_never_restores_snapshot_or_old_tree(self):
        tx = self.prepared()
        original_start = self.driver.start
        def changed(root):
            original_start(root)
            self.driver.baseline["receipts"] = "new-receipt"
        self.driver.start = changed
        with self.assertRaisesRegex(UpdateError, "update_needs_review"):
            tx.apply()
        self.assertEqual(read(tx.journal)["phase"], "needs_review")
        self.assertEqual(self.driver.baseline["receipts"], "new-receipt")
        self.assertNotIn("start_old", self.driver.events)

    def test_activate_failure_must_not_roll_back_after_candidate_was_opened(self):
        tx = self.prepared()
        def opened_then_failed():
            self.driver.events.append("activate")
            self.driver.active = True
            raise UpdateError("activation_receipt_failed")
        self.driver.activate = opened_then_failed
        with self.assertRaisesRegex(UpdateError, "update_needs_review"):
            tx.apply()
        self.assertEqual(read(tx.journal)["phase"], "needs_review")
        self.assertNotEqual(read(tx.pointer), self.pointer)
        self.assertNotIn("cleanup_target", self.driver.events)
        self.assertNotIn("start_old", self.driver.events)


if __name__ == "__main__":
    unittest.main()
