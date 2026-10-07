"""Windows-only containment proof using fresh synthetic Python children."""

import os
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

from updates.security import UpdateError
from updates.processes import OwnedChild


@unittest.skipUnless(os.name == "nt", "Windows Job integration")
class OwnedChildWindowsTests(unittest.TestCase):
    def launch(self, source, cwd):
        return OwnedChild([sys.executable, "-I", "-c", source], cwd=cwd)

    @staticmethod
    def cleanup(child):
        if child.tree_stopped:
            return
        try:
            child.stop_tree(timeout=5)
        finally:
            # Only this test's unnamed Job is closed; no PID/name discovery.
            if child.group.handle:
                child.group.close()

    def test_parent_exit_with_live_descendant_requires_job_tree_cleanup(self):
        with TemporaryDirectory(prefix="ota-owned-tree-") as directory:
            child = self.launch(
                "import subprocess,sys;"
                "subprocess.Popen([sys.executable,'-I','-c','import time;time.sleep(30)'],"
                "creationflags=0x08000000)", directory)
            try:
                self.assertEqual(child.wait(timeout=8), 0)
                self.assertGreater(child.group.active(), 0)
                self.assertFalse(child.tree_stopped)
                self.assertTrue(child.stop_tree(timeout=8))
                self.assertTrue(child.tree_stopped)
                self.assertIsNone(child.group.handle)
                self.assertTrue(child.stop_tree())
            finally:
                self.cleanup(child)

    def test_natural_exit_race_uses_original_handle_and_empty_job(self):
        with TemporaryDirectory(prefix="ota-natural-exit-") as directory:
            child = self.launch("pass", directory)
            try:
                self.assertEqual(child.wait(timeout=8), 0)
                self.assertFalse(child.tree_stopped)
                self.assertTrue(child.stop_tree(timeout=8))
                self.assertTrue(child.tree_stopped)
                self.assertIsNone(child.group.handle)
            finally:
                self.cleanup(child)


if __name__ == "__main__":
    unittest.main()

@unittest.skipUnless(os.name == "nt", "Windows Job containment")
class BrokenControlPipeTests(unittest.TestCase):
    def test_pipe_close_failure_after_proven_exit_does_not_block_recovery(self):
        child = OwnedChild([sys.executable, "-c", "raise SystemExit(2)"])
        try:
            child.wait(timeout=10)
            with self.assertRaises(UpdateError):
                child.write({"schema":1,"op":"stop"})
            self.assertTrue(child.stop_tree())
            self.assertTrue(child.tree_stopped)
        finally:
            child.stop_tree()
