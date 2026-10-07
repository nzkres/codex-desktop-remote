"""Linux-only, synthetic two-version relay OTA with real owned cgroup children.

Requires DESKTOP_REMOTE_TEST_CGROUP to name a dedicated writable delegated
cgroup v2 directory. It never uses a real feed, account, or public listener.
"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import threading
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "relay"))

from relay.configure_managed import configure
from relay.maintenance import critical_digest
from updates import bootstrap, engine, publish
from updates.processes import OwnedChild
from updates.relay_host import RelayHost, request
from updates.security import UpdateError
from updates.storage import read


SOURCE = "https://updates.example.test/test/relay/latest.json"
VERSIONS = ("0.0.1", "0.0.2", "0.0.3")


def source_tree(base, version, *, broken=False):
    root = base / ("source-" + version)
    for name in (*publish.UPDATE_REQUIRED, *publish.RELAY_REQUIRED):
        destination = root / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / name, destination)
    catalog = {"schema": 1, "component": "relay", "name": "Synthetic relay",
               "releases": [{"version": version, "date": "2026-10-07",
                             "summary": "Local smoke fixture", "changes": [], "limitations": []}]}
    (root / "relay" / "releases.json").write_text(json.dumps(catalog), encoding="utf-8")
    if broken:
        (root / "relay" / "managed.py").write_text("raise SystemExit(2)\n", encoding="utf-8")
    return root


def ports():
    values = []
    for _ in range(3):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            values.append(probe.getsockname()[1])
    if len(set(values)) != 3 or any(21000 <= value <= 21020 for value in values):
        return ports()
    return values


def stage_fixture(home, envelope, archive):
    def download(_release, destination, **_kwargs):
        shutil.copyfile(archive, destination)
        return Path(destination)

    with patch.object(engine, "fetch", return_value=read(envelope)), \
         patch.object(engine, "download", side_effect=download):
        release = request(home, "check")["release"]
        staged = request(home, "stage")["release"]
    assert staged["sha256"] == release["sha256"] == hashlib.sha256(archive.read_bytes()).hexdigest()


def smoke(cgroup_root):
    if not sys.platform.startswith("linux"):
        raise RuntimeError("linux_only")
    cgroup_root = Path(cgroup_root)
    if (not cgroup_root.is_absolute() or not str(cgroup_root).startswith("/sys/fs/cgroup/")
            or not (cgroup_root / "cgroup.procs").is_file()
            or not os.access(cgroup_root, os.W_OK)):
        raise RuntimeError("dedicated_delegated_cgroup_required")
    with tempfile.TemporaryDirectory(prefix="relay-ota-smoke-") as directory:
        base = Path(directory)
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        private = base / "fixture-only-private.pem"
        private.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                           serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        private.chmod(0o600)
        public = key.public_key().public_bytes(serialization.Encoding.PEM,
                                               serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        trust = {"schema": 1, "product": publish.PRODUCT, "channel": "test",
                 "keys": {"synthetic-smoke": public}, "sources": {"relay": SOURCE}}
        trust_path = base / "trust.json"
        trust_path.write_text(json.dumps(trust), encoding="utf-8")
        packages = []
        for sequence, version in enumerate(VERSIONS, start=1):
            root = source_tree(base, version, broken=sequence == 3)
            archive = base / (version + ".zip")
            metadata = base / (version + ".json")
            publish.pack("relay", archive, root=root)
            publish.sign("relay", archive, metadata, private_key=private,
                         key_id="synthetic-smoke", source=SOURCE,
                         url=f"https://updates.example.test/test/relay/{version}.zip",
                         sequence=sequence, root=root)
            packages.append((archive, metadata))
        home = base / "fresh-home"
        bootstrap.install(home, trust_path, packages[0][1], packages[0][0], "relay")
        auth_port, api_port, plugin_port = ports()
        configure(home, "synthetic-owner", auth_port, api_port, plugin_port,
                  21000, 21020, password_provider=lambda _: "synthetic-password-123")
        protected_before = (critical_digest(home / "data" / "auth.sqlite3", "auth"),
                            critical_digest(home / "data" / "registry.sqlite3", "registry"))
        spawned = []

        def record_child(*args, **kwargs):
            child = OwnedChild(*args, **kwargs)
            spawned.append(child)
            return child

        host = None
        server = None
        try:
            with patch("updates.relay_host.OwnedChild", side_effect=record_child), \
                 patch("relay.update_driver.OwnedChild", side_effect=record_child):
                host = RelayHost(home, cgroup_root)
                server = threading.Thread(target=host.serve, daemon=True)
                server.start()
                import time
                deadline = time.monotonic() + 5
                while not (home / "update.sock").exists() and time.monotonic() < deadline:
                    time.sleep(0.01)
                if not (home / "update.sock").exists():
                    raise RuntimeError("relay_host_socket_unavailable")
                first = spawned[0]
                assert request(home, "status")["version"] == VERSIONS[0]

                stage_fixture(home, packages[1][1], packages[1][0])
                result = request(home, "apply")
                assert result == {"phase": "committed", "version": VERSIONS[1]}
                assert request(home, "status")["healthy"] is True
                assert read(home / "current.json")["version"] == VERSIONS[1]
                assert first.tree_stopped and not first.group.root.exists()
                protected_after = (critical_digest(home / "data" / "auth.sqlite3", "auth"),
                                   critical_digest(home / "data" / "registry.sqlite3", "registry"))
                assert protected_after == protected_before

                stage_fixture(home, packages[2][1], packages[2][0])
                try:
                    request(home, "apply")
                except UpdateError as error:
                    assert error.code == "update_rolled_back", error.code
                else:
                    raise AssertionError("broken candidate unexpectedly activated")
                assert read(home / "state" / "transaction.json")["phase"] == "rolled_back"
                assert read(home / "current.json")["version"] == VERSIONS[1]
                assert request(home, "status")["healthy"] is True
                assert len(spawned) == 4, len(spawned)
                assert spawned[1].tree_stopped and not spawned[1].group.root.exists()
                assert spawned[2].tree_stopped and not spawned[2].group.root.exists()
                assert protected_before == (critical_digest(home / "data" / "auth.sqlite3", "auth"),
                                            critical_digest(home / "data" / "registry.sqlite3", "registry"))
                assert request(home, "stop") == {"stopped": True}
                server.join(timeout=5)
                assert not server.is_alive()
                assert spawned[3].tree_stopped and not spawned[3].group.root.exists()
        finally:
            if host is not None and host.child is not None:
                try:
                    host.child.stop_tree(timeout=10)
                except Exception:
                    pass
            if host is not None:
                host.stopping = True
            if server is not None and server.is_alive():
                try:
                    request(home, "status")
                except Exception:
                    pass
                server.join(timeout=5)
            for child in spawned:
                if not child.tree_stopped:
                    try:
                        child.stop_tree(timeout=10)
                    except Exception:
                        pass
    return {"committed": VERSIONS[1], "rolledBack": VERSIONS[1], "ownedTrees": 4}


if __name__ == "__main__":
    cgroup = os.environ.get("DESKTOP_REMOTE_TEST_CGROUP")
    if not cgroup:
        print("SKIP relay_ota_smoke: DESKTOP_REMOTE_TEST_CGROUP is unset")
        raise SystemExit(0)
    try:
        result = smoke(cgroup)
        print("relay_ota_smoke_ok " + json.dumps(result, sort_keys=True))
    except Exception:
        import traceback
        traceback.print_exc()  # All paths, data and keys here are temporary synthetic fixtures.
        print("relay_ota_smoke_failed", file=sys.stderr)
        raise SystemExit(1)
