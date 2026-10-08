"""Windows-only containment proof using fresh synthetic Python children."""

import os
from pathlib import Path
import sys
from tempfile import mkdtemp
import time
import unittest

from updates.security import UpdateError
from updates.processes import OwnedChild


@unittest.skipUnless(os.name == "nt", "Windows Job integration")
class OwnedChildWindowsTests(unittest.TestCase):
    @staticmethod
    def empty_workdir():
        class Workdir:
            def __enter__(self):
                self.path = Path(mkdtemp(prefix="ota-owned-tree-"))
                return self.path

            def __exit__(self, *_):
                # The test creates no files. Windows may release the exited
                # child's cwd handle just after Job.active reaches zero.
                # Do not recursively erase evidence or ignore a stuck handle.
                deadline = time.monotonic() + 3
                while True:
                    try:
                        os.rmdir(self.path)
                        return
                    except PermissionError as error:
                        if time.monotonic() >= deadline:
                            raise error
                        time.sleep(0.05)

        return Workdir()

    def launch(self, source, cwd):
        return OwnedChild([sys.executable, "-I", "-c", source], cwd=cwd)

    @staticmethod
    def cleanup(child):
        try:
            if not child.tree_stopped:
                child.stop_tree(timeout=5)
        finally:
            child.reader.join(timeout=3)
            if child.reader.is_alive():
                raise AssertionError("owned_child_reader_still_active")
            # Only this test's unnamed Job is closed; no PID/name discovery.
            if child.group.handle:
                child.group.close()

    def test_parent_exit_with_live_descendant_requires_job_tree_cleanup(self):
        with self.empty_workdir() as directory:
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
        with self.empty_workdir() as directory:
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
