"""Synthetic Windows OTA driver and helper checks; no Desktop or identity is used."""

from contextlib import contextmanager
import io
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

WINDOWS = Path(__file__).resolve().parents[1] / "windows"
if str(WINDOWS) not in sys.path:
    sys.path.insert(0, str(WINDOWS))

import update_driver
import update_helper
import update_ui
from updates.security import UpdateError


class FakeStore:
    def __init__(self, state):
        self.state = state
        self.leases = []

    @contextmanager
    def lock(self, name="identity.lock"):
        self.leases.append(name)
        yield

    def read(self):
        return self.state


class FakeChild:
    def __init__(self, reply=None, fail_cleanup=False):
        self.reply = reply
        self.fail_cleanup = fail_cleanup
        self.calls = []

    def read(self, timeout=None):
        self.calls.append("read")
        return self.reply

    def write(self, message):
        self.calls.append("write")
        raise UpdateError("activation_ack_unknown")

    def stop_tree(self):
        self.calls.append("stop_tree")
        if self.fail_cleanup:
            raise UpdateError("candidate_tree_exit_unconfirmed")

    def allow_survive_owner_exit(self):
        self.calls.append("allow_survive_owner_exit")

    def detach_windows(self):
        self.calls.append("detach_windows")


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory(prefix="ota-driver-synthetic-")
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.data = self.home / "data" / "fixture"
        self.data.mkdir(parents=True)
        self.controller = type("SyntheticController", (), {})()
        self.controller.config = {"dataDir": str(self.data), "port": 27001}
        self.controller.store = FakeStore({"state": "connected", "identity": "synthetic"})
        self.driver = update_driver.WindowsDriver.__new__(update_driver.WindowsDriver)
        self.driver.home = self.home
        self.driver.old_slot = self.home / "old-slot"
        self.driver.controller = self.controller
        self.driver.application_lock = None
        self.driver.target = None
        self.driver.activation_started = False

    def test_snapshot_detects_data_change_and_protected_identity_change(self):
        receipt = self.data / "receipt.json"
        receipt.write_text("synthetic accepted", encoding="utf-8")
        before = update_driver.snapshot(self.controller)
        self.controller.store.state["adapterBinding"] = {"port": 27001}
        self.assertEqual(update_driver.snapshot(self.controller), before)
        receipt.write_text("synthetic unknown", encoding="utf-8")
        self.assertNotEqual(update_driver.snapshot(self.controller), before)
        receipt.write_text("synthetic accepted", encoding="utf-8")
        self.controller.store.state["identity"] = "changed"
        self.assertNotEqual(update_driver.snapshot(self.controller), before)

    def test_snapshot_blocks_needs_review_and_unconnected_identity(self):
        self.controller.store.state["runtimeNeedsReview"] = True
        with self.assertRaisesRegex(UpdateError, "identity_or_runtime_requires_review"):
            update_driver.snapshot(self.controller)
        self.controller.store.state = {"state": "reconnecting", "identity": "synthetic"}
        with self.assertRaisesRegex(UpdateError, "identity_or_runtime_requires_review"):
            update_driver.snapshot(self.controller)

    def test_preflight_requires_exclusive_application_and_runtime_leases(self):
        events = []

        @contextmanager
        def application_lock(_):
            events.append("application_enter")
            yield
            events.append("application_exit")

        with patch.object(update_driver, "lock", application_lock), patch.object(
            update_driver, "probe", side_effect=lambda *_: events.append("probe")
        ):
            self.driver.preflight()
            self.assertEqual(self.controller.store.leases, ["runtime.lock", "identity.lock"])
            self.driver.stop_old()
            self.assertEqual(self.controller.store.leases[-2:], ["runtime.lock", "identity.lock"])
            self.driver.release("checked")
        self.assertEqual(events, ["application_enter", "probe", "application_exit"])

    def test_failed_preflight_releases_application_lease_without_starting(self):
        events = []

        @contextmanager
        def application_lock(_):
            events.append("enter")
            try:
                yield
            finally:
                events.append("exit")

        with patch.object(update_driver, "lock", application_lock), patch.object(
            update_driver, "probe", side_effect=UpdateError("active_or_unverified_desktop_task")
        ):
            with self.assertRaisesRegex(UpdateError, "active_or_unverified_desktop_task"):
                self.driver.preflight()
        self.assertEqual(events, ["enter", "exit"])
        self.assertIsNone(self.driver.target)

    def test_unproven_candidate_tree_cleanup_retains_handle(self):
        child = FakeChild(fail_cleanup=True)
        self.driver.target = child
        with self.assertRaisesRegex(UpdateError, "candidate_tree_exit_unconfirmed"):
            self.driver.cleanup_target()
        self.assertIs(self.driver.target, child)
        self.assertEqual(child.calls, ["stop_tree"])

    def test_candidate_health_requires_exact_receipt(self):
        child = FakeChild({"schema": 1, "ready": True, "version": "wrong", "probe": "verified"})
        self.driver.target = child
        with patch.object(update_driver, "read", return_value={"releases": [{"version": "expected"}]}):
            with self.assertRaisesRegex(UpdateError, "candidate_health_unverified"):
                self.driver.verify(self.home / "candidate")

    def test_ambiguous_activation_never_kills_candidate(self):
        child = FakeChild()
        self.driver.target = child
        lease = type("Lease", (), {"__exit__": lambda self, *args: None})()
        self.driver.application_lock = lease
        with self.assertRaisesRegex(UpdateError, "activation_ack_unknown"):
            self.driver.activate()
        with self.assertRaisesRegex(UpdateError, "activation_outcome_requires_review"):
            self.driver.cleanup_target()
        self.driver.release("needs_review")
        self.assertEqual(child.calls, ["allow_survive_owner_exit", "write", "detach_windows"])

    def test_failed_candidate_returns_only_catalogued_reason(self):
        for supplied,expected in [('active_or_unverified_desktop_task','active_or_unverified_desktop_task'),
                                  ('credential=synthetic-secret','operation_failed')]:
            self.driver.target = FakeChild({'schema':1,'ready':False,'code':supplied})
            with patch.object(update_driver,'read',return_value={'releases':[{'version':'expected'}]}):
                with self.assertRaisesRegex(UpdateError,expected):
                    self.driver.verify(self.home/'candidate')


class HandoffTests(unittest.TestCase):
    def test_helper_waits_on_pinned_original_before_transaction(self):
        events = []
        owner = {"pid": 123, "created": 456, "path": "synthetic", "sha256": "0" * 64}

        class Pinned:
            def __init__(self, supplied):
                self.assert_owner = supplied

            def __enter__(self):
                self_outer.assertEqual(self.assert_owner, owner)
                events.append("pin")
                return self

            def wait(self, timeout):
                self_outer.assertEqual(timeout, 120)
                events.append("wait")

            def __exit__(self, *args):
                events.append("unpin")

        class Transaction:
            def __init__(self, home, driver):
                events.append("transaction")

            def apply(self):
                events.append("apply")

        self_outer = self
        request = io.BytesIO((json.dumps({"schema": 1, "owner": owner}) + "\n").encode())
        stdout = io.StringIO()
        with patch.object(update_helper.sys, "stdin", type("Input", (), {"buffer": request})()), patch.object(
            update_helper.sys, "stdout", stdout
        ), patch.object(update_helper, "installed", return_value=(
            {"component": "windows"}, {}, update_helper.ROOT
        )), patch.object(update_helper, "PinnedProcess", Pinned), patch.object(
            update_helper, "WindowsDriver", lambda *_: object()
        ), patch.object(update_helper, "Transaction", Transaction):
            update_helper.main(Path("synthetic-home"))
        self.assertEqual(events, ["pin", "wait", "transaction", "apply", "unpin"])
        self.assertEqual(json.loads(stdout.getvalue()), {"schema": 1, "pinned": True})

    def test_ui_handoff_only_accepts_pinned_helper_receipt(self):
        class CapturedInput(io.BytesIO):
            def close(self):
                pass

        class Process:
            def __init__(self):
                self.stdin = CapturedInput()
                self.stdout = io.BytesIO(b'{"schema":1,"pinned":true}\n')

        child = Process()
        with patch.object(update_ui, "installed", return_value=({}, {}, WINDOWS.parent)), patch.object(
            update_ui, "self_owner", return_value={"pid": 123}
        ), patch.object(update_ui.subprocess, "Popen", return_value=child), patch.object(
            update_ui.subprocess, "CREATE_NO_WINDOW", 0, create=True
        ):
            update_ui.handoff(Path("synthetic-home"))
        self.assertIs(update_ui.handoff.helper, child)
        self.assertEqual(json.loads(child.stdin.getvalue()), {"schema": 1, "owner": {"pid": 123}})

    def test_ui_rejects_unpinned_helper_without_claiming_handoff(self):
        class Process:
            stdin = io.BytesIO()
            stdout = io.BytesIO(b'{"schema":1,"pinned":false}\n')

        with patch.object(update_ui, "installed", return_value=({}, {}, WINDOWS.parent)), patch.object(
            update_ui, "self_owner", return_value={"pid": 123}
        ), patch.object(update_ui.subprocess, "Popen", return_value=Process()), patch.object(
            update_ui.subprocess, "CREATE_NO_WINDOW", 0, create=True
        ):
            with self.assertRaisesRegex(UpdateError, "update_handoff_unverified"):
                update_ui.handoff(Path("synthetic-home"))


if __name__ == "__main__":
    unittest.main()
