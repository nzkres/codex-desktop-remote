"""Only fresh synthetic processes; no discovery/adoption of existing services."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows' / 'vendor'))
from windows_job import GuardedProcess, spawn_guarded


class ProofTests(unittest.TestCase):
    def test_parent_exit_does_not_close_job(self):
        p, job = Mock(), Mock()
        p.poll.return_value = 0
        guarded = GuardedProcess(p, job)
        self.assertEqual(guarded.poll(), 0)
        job.close.assert_not_called()

    def test_natural_exit_race_uses_original_handle_and_job(self):
        p, job = Mock(), Mock()
        job.terminate.side_effect = OSError('already exited')
        job.active.return_value = 0
        guarded = GuardedProcess(p, job)
        self.assertTrue(guarded.stop_tree())
        p.wait.assert_called_once()
        job.close.assert_called_once()
        p.terminate.assert_not_called()

    def test_query_failure_retains_handles_and_no_proof(self):
        p, job = Mock(), Mock()
        job.active.side_effect = OSError('query failed')
        guarded = GuardedProcess(p, job)
        with self.assertRaises(OSError):
            guarded.stop_tree()
        self.assertFalse(guarded.tree_stopped)
        job.close.assert_not_called()

    def test_remaining_descendant_or_parent_timeout_retains_job(self):
        for failed_wait in (False, True):
            with self.subTest(parent_timeout=failed_wait):
                p, job = Mock(), Mock()
                job.active.return_value = 1
                if failed_wait:
                    p.wait.side_effect = subprocess.TimeoutExpired('synthetic', 0)
                guarded = GuardedProcess(p, job)
                with self.assertRaises((OSError, subprocess.TimeoutExpired)):
                    guarded.stop_tree(timeout=0)
                self.assertFalse(guarded.tree_stopped)
                job.close.assert_not_called()


@unittest.skipUnless(os.name == 'nt', 'Windows Job integration')
class ActualWindowsTests(unittest.TestCase):
    def launch(self, source, *, bootstrap=False):
        process = spawn_guarded([sys.executable, '-I', '-c', source],
            stdin=subprocess.PIPE if bootstrap else subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NO_WINDOW)
        self.addCleanup(self.cleanup, process)
        return process

    @staticmethod
    def cleanup(process):
        try:
            process.stop_tree()
        finally:
            process.job.close()

    def test_original_parent_already_exited(self):
        p = self.launch('pass')
        p.wait(timeout=5)
        self.assertIsNotNone(p.job.handle)
        self.assertTrue(p.stop_tree())
        self.assertTrue(p.stop_tree())

    def test_descendant_survives_parent_until_job_cleanup(self):
        p = self.launch("import subprocess,sys; subprocess.Popen([sys.executable,'-I','-c','import time;time.sleep(30)'],creationflags=0x08000000)")
        p.wait(timeout=5)
        self.assertGreater(p.job.active(), 0)
        self.assertTrue(p.stop_tree())
        self.assertTrue(p.tree_stopped)

    def test_bootstrap_pipe_is_forwarded_exactly(self):
        with tempfile.TemporaryDirectory(prefix='lite-gate-') as directory:
            result = Path(directory) / 'payload.txt'
            p = self.launch(f'import sys;from pathlib import Path;Path({str(result)!r}).write_bytes(sys.stdin.buffer.read())', bootstrap=True)
            # Synthetic public payload only; no credentials are echoed.
            p.stdin.write(b'synthetic-bootstrap');p.stdin.close()
            self.assertEqual(p.wait(timeout=5), 0)
            self.assertEqual(result.read_bytes(), b'synthetic-bootstrap')
            self.assertTrue(p.stop_tree())

    def test_controller_death_closes_owned_job(self):
        with tempfile.TemporaryDirectory(prefix='lite-owner-') as directory:
            heartbeat = Path(directory) / 'heartbeat'
            trigger = Path(directory) / 'exit-owner'
            child = ("import time;from pathlib import Path;"
                     "\n"
                     f"while True: Path({str(heartbeat)!r}).write_text(str(time.monotonic()));time.sleep(.05)")
            parent = ("import os,sys,time,subprocess;from pathlib import Path;"
                      f"sys.path.insert(0,{str(Path(__file__).resolve().parents[1] / 'windows' / 'vendor')!r});"
                      "from windows_job import spawn_guarded;"
                      f"p=spawn_guarded([sys.executable,'-I','-c',{child!r}],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=0x08000000);"
                      f"\nwhile not Path({str(trigger)!r}).exists(): time.sleep(.02)\nos._exit(0)")
            # An outer Job provides test-owned containment if inner code fails.
            owner = self.launch(parent)
            deadline = time.monotonic() + 8
            while not heartbeat.exists() and time.monotonic() < deadline:
                time.sleep(.05)
            self.assertTrue(heartbeat.exists())
            # No terminate call: only the synthetic inner owner crashes. The
            # retained outer Job must become empty through inner containment.
            trigger.write_text('exit')
            owner.wait(timeout=5)
            deadline = time.monotonic() + 5
            while owner.job.active() and time.monotonic() < deadline:
                time.sleep(.02)
            self.assertEqual(owner.job.active(), 0)
            self.assertTrue(owner.stop_tree())


if __name__ == '__main__':
    unittest.main()
