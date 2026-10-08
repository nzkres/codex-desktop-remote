"""Running-slot identity accepts path aliases only after the no-link check."""

import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]


def _load(name):
    path = ROOT / "windows" / (name + ".py")
    spec = importlib.util.spec_from_file_location("_test_" + name, path)
    module = importlib.util.module_from_spec(spec)
    with patch.object(sys, "path", [str(ROOT / "windows"), str(ROOT / "windows/vendor"), *sys.path]):
        spec.loader.exec_module(module)
    return module


@unittest.skipUnless(os.name == "nt", "Windows helper/GUI path identity")
class RunningSlotAliasTests(unittest.TestCase):
    def test_helper_and_candidate_accept_canonical_alias_but_not_other_slot(self):
        helper = _load("update_helper")
        candidate = _load("ota_entry")
        with tempfile.TemporaryDirectory(prefix="ota-slot-alias-") as directory:
            versions = Path(directory) / "versions"
            slot = versions / "v1"
            slot.mkdir(parents=True)
            (versions / "alias").mkdir()
            other = versions / "v2"
            other.mkdir()
            alias = versions / "alias" / ".." / "v1"
            self.assertNotEqual(alias, slot)
            for module in (helper, candidate):
                with self.subTest(module=module.__name__), patch.object(module, "ROOT", slot.resolve(strict=True)):
                    self.assertTrue(module.same_running_slot(alias))
                    self.assertFalse(module.same_running_slot(other))


if __name__ == "__main__":
    unittest.main()
