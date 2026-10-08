"""Deterministic timing checks for the isolated Windows maintenance fixture."""

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/windows_maintenance_smoke.py"
SPEC = importlib.util.spec_from_file_location("windows_maintenance_smoke", SCRIPT)
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


class Clock:
    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds


class Group:
    def __init__(self, values):
        self.values = iter(values)
        self.calls = 0

    def active(self):
        self.calls += 1
        return next(self.values)


class WaitEmptyGroupTests(unittest.TestCase):
    def run_with_clock(self, group, clock, timeout):
        with patch.object(smoke.time, "monotonic", clock.monotonic), patch.object(
                smoke.time, "sleep", clock.sleep):
            return smoke.wait_empty_group(group, timeout=timeout)

    def test_observes_same_group_until_it_becomes_empty(self):
        clock = Clock()
        group = Group([3, 1, 0])
        self.run_with_clock(group, clock, timeout=1)
        self.assertEqual(group.calls, 3)
        self.assertEqual(len(clock.sleeps), 2)
        self.assertGreater(clock.now, 0)
        self.assertLess(clock.now, 1)

    def test_persistent_active_processes_fail_within_timeout(self):
        clock = Clock()

        class Busy:
            calls = 0

            def active(self):
                self.calls += 1
                return 1

        group = Busy()
        with self.assertRaisesRegex(AssertionError, "maintenance fixture tree exit unconfirmed"):
            self.run_with_clock(group, clock, timeout=0.2)
        self.assertGreater(group.calls, 1)
        self.assertGreaterEqual(clock.now, 0.2)
        self.assertLessEqual(clock.now, 0.3)

    def test_observation_error_is_not_treated_as_empty(self):
        clock = Clock()
        failure = RuntimeError("synthetic Job observation failed")

        class Fault:
            def active(self):
                raise failure

        with self.assertRaises(RuntimeError) as caught:
            self.run_with_clock(Fault(), clock, timeout=1)
        self.assertIs(caught.exception, failure)
        self.assertEqual(clock.sleeps, [])


if __name__ == "__main__":
    unittest.main()
