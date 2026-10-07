"""Offline Windows two-version OTA transaction rehearsal; no Desktop or network.

Uses synthetic RSA trust, copies allowlisted repository source into two temporary
version trees, and exercises the real bootstrap and Transaction.apply paths.
The lifecycle driver is deliberately synthetic: GUI handoff, Job teardown,
HTTPS transfer and real Desktop ownership need separate acceptance evidence.
"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import types
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from updates import bootstrap, publish
from updates.engine import Transaction
from updates.security import COMPATIBILITY, TARGETS, unpack, verify, verify_tree
from updates.storage import atomic, read


SOURCE = "https://updates.example.test/test/windows/latest.json"


def source_tree(base, version):
    root = base / ("source-" + version)
    for name in (*publish.UPDATE_REQUIRED, *publish.WINDOWS_REQUIRED):
        destination = root / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / name, destination)
    catalog_path = root / "windows" / "releases.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    release = next((item for item in catalog["releases"] if item["version"] == version), None)
    if release is None:
        # A synthetic next version is only for this isolated rehearsal.
        release = dict(catalog["releases"][0], version=version)
    catalog["releases"] = [release]
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
    return root


class FixtureDriver:
    """Stable fake health and admission state; never starts a real task."""

    def __init__(self, home):
        self.home = home
        self.events = []
        self.baseline = (home / "data" / "sentinel.txt").read_bytes()

    def preflight(self):
        self.events.append("preflight")

    def snapshot(self):
        return hashlib.sha256((self.home / "data" / "sentinel.txt").read_bytes()).hexdigest()

    def stop_old(self):
        self.events.append("stop_old")

    def start(self, root):
        verify_tree(root, "windows")
        self.events.append("start")

    def verify(self, root):
        verify_tree(root, "windows")
        self.events.append("verify")

    def activate(self):
        self.events.append("activate")

    def cleanup_target(self):
        self.events.append("cleanup_target")

    def release(self, phase):
        self.events.append("release:" + phase)


def rehearse():
    if os.name != "nt":
        raise RuntimeError("windows_only_smoke")
    with tempfile.TemporaryDirectory(prefix="desktop-remote-ota-smoke-") as directory:
        base = Path(directory)
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        private = base / "synthetic-publisher.pem"
        private.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                        serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        public = key.public_key().public_bytes(serialization.Encoding.PEM,
                        serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        trust = {"schema": 1, "product": publish.PRODUCT, "channel": "test",
                 "keys": {"synthetic-smoke": public}, "sources": {"windows": SOURCE}}
        trust_path = base / "trust.json"
        trust_path.write_text(json.dumps(trust), encoding="utf-8")
        packages = []
        for sequence, version in enumerate(("0.1.0-alpha.2", "0.1.0-alpha.3"), start=1):
            root = source_tree(base, version)
            archive = base / f"version-{sequence}.zip"
            metadata = base / f"version-{sequence}.json"
            publish.pack("windows", archive, root=root)
            publish.sign("windows", archive, metadata, private_key=private,
                         key_id="synthetic-smoke", source=SOURCE,
                         url=f"https://updates.example.test/test/windows/version-{sequence}.zip",
                         sequence=sequence, root=root)
            packages.append((archive, metadata))
        home = base / "fresh-installation"
        fake_core = types.SimpleNamespace(private_directory=lambda path: None)
        with patch.dict(sys.modules, {"connector_core": fake_core}):
            bootstrap.install(home, trust_path, packages[0][1], packages[0][0], "windows")
        sentinel = home / "data" / "sentinel.txt"
        sentinel.write_bytes(b"synthetic-data-preserved")
        old = read(home / "current.json")
        envelope = read(packages[1][1])
        release, ledger = verify(envelope, trust, "windows", TARGETS["windows"],
                                 COMPATIBILITY["windows"], old["version"],
                                 read(home / "state" / "ledger.json"))
        atomic(home / "state" / "ledger.json", ledger)
        stage_dir = home / "staging" / "synthetic-candidate"
        stage_dir.mkdir()
        archive_copy = stage_dir / "package.zip"
        shutil.copyfile(packages[1][0], archive_copy)
        extracted = unpack(archive_copy, stage_dir / "package", release)
        name = release["version"] + "-" + release["sha256"][:16]
        target = home / "versions" / name
        extracted.rename(target)
        candidate = {"path": "versions/" + name, "version": release["version"],
                     "manifestSha256": hashlib.sha256((target / "package.json").read_bytes()).hexdigest()}
        atomic(home / "state" / "staged.json",
               {"schema": 1, "old": old, "candidate": candidate, "envelope": envelope})
        driver = FixtureDriver(home)
        journal = Transaction(home, driver).apply()
        assert journal["phase"] == "committed" and journal["success"] is True
        assert read(home / "current.json") == candidate
        assert sentinel.read_bytes() == driver.baseline
        assert (home / old["path"]).is_dir()
        assert driver.events == ["preflight", "stop_old", "start", "verify", "activate", "release:committed"]
        return {"old": old["version"], "new": candidate["version"], "phase": journal["phase"]}


if __name__ == "__main__":
    try:
        result = rehearse()
        print("offline_windows_transaction_ok " + result["old"] + " -> " + result["new"])
    except Exception:
        print("offline_windows_transaction_failed", file=sys.stderr)
        raise SystemExit(1)
