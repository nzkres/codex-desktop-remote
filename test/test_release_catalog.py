"""Release catalog validation uses only synthetic temporary files."""

from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

from scripts.check_versions import CatalogError, DIRECTORIES, check_repository, version_key


def release(version, day, code=None):
    item = {
        "version": version,
        "date": day,
        "summary": "本版说明",
        "changes": [{"title": "功能", "experience": "在页面体验", "verify": "按步骤核对"}],
        "limitations": ["仅测试数据"],
    }
    if code is not None:
        item["versionCode"] = code
    return item


class ReleaseCatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.catalogs = {}
        for component in ("android", "windows", "relay"):
            releases = [
                release("0.1.0-alpha.2", "2026-02-02", 2 if component == "android" else None),
                release("0.1.0-alpha.1", "2026-02-01", 1 if component == "android" else None),
            ]
            self.catalogs[component] = {
                "schema": 1, "component": component, "name": f"合成{component}",
                "releases": releases,
            }
        self.flush()

    def flush(self):
        for component, catalog in self.catalogs.items():
            path = self.root / DIRECTORIES[component] / "releases.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
        (self.root / "package.json").write_text(
            json.dumps({"version": "0.1.0-alpha.2"}), encoding="utf-8")

    def test_write_then_check_and_readable_details(self):
        check_repository(self.root, write=True)
        check_repository(self.root)
        text = (self.root / "docs" / "versions" / "ANDROID.md").read_text(encoding="utf-8")
        self.assertIn("## 0.1.0-alpha.2", text)
        self.assertIn("体验：在页面体验", text)
        self.assertIn("验证：按步骤核对", text)
        self.assertIn("### 限制", text)

    def test_default_check_never_creates_histories(self):
        with self.assertRaisesRegex(CatalogError, "generated history missing"):
            check_repository(self.root)
        self.assertFalse((self.root / "docs").exists())

    def test_missing_experience_or_verify_is_rejected(self):
        for field in ("experience", "verify"):
            with self.subTest(field=field):
                catalog = deepcopy(self.catalogs["relay"])
                del catalog["releases"][0]["changes"][0][field]
                self.catalogs["relay"] = catalog
                self.flush()
                with self.assertRaisesRegex(CatalogError, field):
                    check_repository(self.root, write=True)
                self.assertFalse((self.root / "docs").exists())
                self.catalogs["relay"]["releases"][0]["changes"][0][field] = "核对方法"

    def test_duplicate_and_backward_versions_are_rejected(self):
        for version, message in (("0.1.0-alpha.2", "duplicate version"),
                                 ("0.1.0-alpha.3", "newest first")):
            with self.subTest(version=version):
                self.catalogs["windows"]["releases"][1]["version"] = version
                self.flush()
                with self.assertRaisesRegex(CatalogError, message):
                    check_repository(self.root, write=True)
                self.assertFalse((self.root / "docs").exists())

    def test_android_version_code_must_increase_chronologically(self):
        self.catalogs["android"]["releases"][1]["versionCode"] = 3
        self.flush()
        with self.assertRaisesRegex(CatalogError, "versionCode"):
            check_repository(self.root, write=True)

    def test_package_version_must_match_latest_windows(self):
        (self.root / "package.json").write_text(
            json.dumps({"version": "0.1.0-alpha.1"}), encoding="utf-8")
        with self.assertRaisesRegex(CatalogError, "package.json.version"):
            check_repository(self.root, write=True)

    def test_components_do_not_need_the_same_latest_version(self):
        self.catalogs["android"]["releases"].insert(
            0, release("0.1.0-alpha.3", "2026-02-03", 3))
        self.catalogs["relay"]["releases"] = [
            release("0.1.0-alpha.1", "2026-02-01")]
        self.flush()
        check_repository(self.root, write=True)
        check_repository(self.root)

    def test_stale_generated_history_is_rejected_without_rewrite(self):
        check_repository(self.root, write=True)
        path = self.root / "docs" / "versions" / "RELAY.md"
        path.write_text("stale", encoding="utf-8")
        with self.assertRaisesRegex(CatalogError, "out of date"):
            check_repository(self.root)
        self.assertEqual(path.read_text(encoding="utf-8"), "stale")

    def test_stable_and_prerelease_ordering(self):
        self.assertGreater(version_key("1.0.0"), version_key("1.0.0-rc.9"))
        self.assertGreater(version_key("1.0.0-rc.1"), version_key("1.0.0-beta.9"))
        self.assertGreater(version_key("1.0.0-beta.1"), version_key("1.0.0-alpha.9"))
        with self.assertRaises(CatalogError):
            version_key("01.0.0")


if __name__ == "__main__":
    unittest.main()
