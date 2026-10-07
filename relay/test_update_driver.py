from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from updates.security import UpdateError
from update_driver import RelayDriver


class FakeChild:
    def __init__(self, pid, instance, version, *, quarantine=False):
        self.pid, self.managed_pid, self.instance, self.version = pid, pid + 1, instance, version
        self.quarantine = quarantine
        self.commands = []
        self.alive = True
        self.tree_stopped = False
        self.pending = None
        self.first = quarantine

    def poll(self):
        return None if self.alive else 0

    def write(self, message):
        self.commands.append(message["op"])
        self.pending = message["op"]

    def read(self, timeout=30):
        if self.first:
            self.first = False
            return {"schema": 1, "ready": {"pid": self.managed_pid, "version": self.version,
                    "gate": {"closed": True}}}
        op = self.pending
        if op == "stop":
            self.alive = False
            return {"schema": 1, "stopped": True}
        if op == "snapshot":
            value = {"schema": 1, "dataSchema": 1, "authSha256": "a" * 64,
                     "registrySha256": "b" * 64}
        elif op == "prepare":
            value = {"closed": True, "active": 0, "uncertain": False}
        else:
            closed = self.quarantine and op != "activate"
            value = {"schema": 1, "pid": self.managed_pid, "instance": self.instance,
                     "version": self.version, "dataSchema": 1,
                     "listenersAlive": True, "gate": {"closed": closed}}
        return {"schema": 1, "result": value}

    def stop_tree(self, timeout=10):
        self.alive = False
        self.tree_stopped = True
        return True


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.old_slot = self.home / "versions" / "old"
        self.new_slot = self.home / "versions" / "new"
        (self.old_slot / "relay").mkdir(parents=True)
        (self.new_slot / "relay").mkdir(parents=True)
        (self.new_slot / "relay" / "managed.py").write_text("# fixture", encoding="utf-8")
        self.old = FakeChild(101, "old-instance", "1.0")
        self.new = FakeChild(202, "new-instance", "2.0", quarantine=True)
        self.ready = {"instance": "old-instance", "pid": 102, "version": "1.0"}
        patches = (
            patch("update_driver.read_home", return_value=(self.home, self.home / "data", {}, "f" * 64)),
            patch("update_driver._small_json", side_effect=lambda _: self.ready),
            patch("update_driver.critical_digest", side_effect=lambda _, component: ("a" if component == "auth" else "b") * 64),
            patch("update_driver.OwnedChild", return_value=self.new),
            patch.object(RelayDriver, "_version", side_effect=lambda slot: "1.0" if Path(slot) == self.old_slot else "2.0"),
        )
        for item in patches:
            item.start()
            self.addCleanup(item.stop)
        self.driver = RelayDriver(self.home, self.old_slot,
                                  {"child": self.old, "cgroup_root": "/synthetic/delegated"})

    def test_normal_switch_keeps_owned_candidate_for_host(self):
        self.driver.preflight()
        preserved = self.driver.snapshot()
        self.driver.stop_old()
        self.assertTrue(self.old.tree_stopped)
        self.assertEqual(self.driver.snapshot(), preserved)
        self.driver.start(self.new_slot)
        self.ready = {"instance": "new-instance", "pid": 203, "version": "2.0"}
        self.driver.verify(self.new_slot)
        self.driver.activate()
        self.driver.release("committed")
        self.assertIs(self.driver.child, self.new)
        self.assertFalse(self.new.tree_stopped)
        self.assertEqual(self.old.commands, ["health", "prepare", "snapshot", "stop"])
        self.assertEqual(self.new.commands, ["health", "verify_recovery", "activate"])

    def test_cleanup_only_own_quarantined_candidate(self):
        self.driver.preflight()
        self.driver.stop_old()
        self.driver.start(self.new_slot)
        self.driver.cleanup_target()
        self.assertTrue(self.new.tree_stopped)
        self.assertIsNone(self.driver.child)
        self.assertTrue(self.old.tree_stopped)

    def test_ambiguous_activation_never_kills_candidate(self):
        self.driver.preflight()
        self.driver.stop_old()
        self.driver.start(self.new_slot)
        self.driver.activate_attempted = True
        with self.assertRaisesRegex(UpdateError, "activation_ambiguous"):
            self.driver.cleanup_target()
        self.assertFalse(self.new.tree_stopped)

    def test_managed_pid_must_match_ready_not_gate_wrapper(self):
        self.driver.preflight()
        self.assertNotEqual(self.old.pid, self.old.managed_pid)
        self.ready["pid"] = self.old.pid
        with self.assertRaisesRegex(UpdateError, "binding_unverified"):
            self.driver._binding(self.old, self.old_slot)


if __name__ == "__main__":
    unittest.main()
