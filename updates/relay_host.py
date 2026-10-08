"""Owner-only Linux relay update host. It owns, never adopts, the relay tree."""

import argparse
import json
import os
from pathlib import Path
import re
import socket
import stat
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(ROOT / "relay") not in sys.path:
    sys.path.insert(0, str(ROOT / "relay"))

from relay.update_driver import RelayDriver
from relay.managed import read_home
from updates.engine import Transaction, check, installed, stage
from updates.credentials import for_installation
from updates.processes import OwnedChild
from updates.security import UpdateError
from updates.storage import ensure_launchable, read


SOCKET_NAME = "update.sock"
MAX_COMMAND = 4096
COMMANDS = frozenset(("check", "stage", "apply", "status", "stop"))


def request(home, operation):
    """Send one fixed operation to this user's protected, already-running host."""
    if not sys.platform.startswith("linux") or type(operation) is not str or operation not in COMMANDS:
        raise UpdateError("invalid_host_command")
    home = Path(home).absolute()
    try:
        for part in (home, *home.parents):
            if part.is_symlink():
                raise UpdateError("host_path_link_rejected")
        info = home.stat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or info.st_mode & 0o077:
            raise UpdateError("host_home_permissions_invalid")
        path = home / SOCKET_NAME
        info = path.lstat()
        if not stat.S_ISSOCK(info.st_mode) or info.st_uid != os.geteuid() or info.st_mode & 0o077:
            raise UpdateError("host_socket_permissions_invalid")
    except OSError:
        raise UpdateError("relay_host_unavailable") from None
    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(600 if operation == "stage" else 90)
            connection.connect(str(path))
            credentials = connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED,
                                                 struct.calcsize("3i"))
            _, uid, _ = struct.unpack("3i", credentials)
            if uid != os.geteuid():
                raise UpdateError("host_peer_not_owner")
            connection.sendall(json.dumps({"schema": 1, "op": operation}, separators=(",", ":")).encode() + b"\n")
            raw = bytearray()
            while len(raw) <= 65536:
                part = connection.recv(min(4096, 65537 - len(raw)))
                if not part:
                    break
                raw.extend(part)
                if b"\n" in part:
                    break
    except OSError:
        raise UpdateError("relay_host_unavailable") from None
    if len(raw) > 65536 or not raw.endswith(b"\n"):
        raise UpdateError("relay_host_response_invalid")
    try:
        def unique(pairs):
            value = {}
            for key, item in pairs:
                if key in value:
                    raise ValueError()
                value[key] = item
            return value
        value = json.loads(raw[:-1], object_pairs_hook=unique)
    except (ValueError, UnicodeError):
        raise UpdateError("relay_host_response_invalid") from None
    if not isinstance(value, dict) or value.get("schema") != 1:
        raise UpdateError("relay_host_response_invalid")
    if set(value) == {"schema", "error"} and isinstance(value["error"], str) and re.fullmatch(r"[a-z0-9_]{1,80}", value["error"]):
        raise UpdateError(value["error"])
    if set(value) != {"schema", "result"} or not isinstance(value["result"], dict):
        raise UpdateError("relay_host_response_invalid")
    return value["result"]


def command(raw):
    if len(raw) > MAX_COMMAND:
        raise UpdateError("invalid_host_command")
    def unique(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError()
            value[key] = item
        return value
    try:
        value = json.loads(raw, object_pairs_hook=unique)
    except (ValueError, UnicodeError):
        raise UpdateError("invalid_host_command") from None
    if (not isinstance(value, dict) or set(value) != {"schema", "op"}
            or type(value["schema"]) is not int or value["schema"] != 1
            or type(value["op"]) is not str or value["op"] not in COMMANDS):
        raise UpdateError("invalid_host_command")
    return value["op"]


class RelayHost:
    def __init__(self, home: Path, cgroup_root: Path):
        if not sys.platform.startswith("linux"):
            raise UpdateError("linux_relay_host_required")
        self.home, _, _, _ = read_home(Path(home))
        self.cgroup_root = Path(cgroup_root)
        ensure_launchable(self.home)
        config, _, slot = installed(self.home)
        if config["component"] != "relay":
            raise UpdateError("wrong_installed_component")
        self.slot = slot
        self.child = None
        self.review_children = []
        self.stopping = False
        self._start_initial()

    def _start_initial(self):
        entry = self.slot / "relay" / "managed.py"
        if not entry.is_file() or entry.is_symlink():
            raise UpdateError("managed_relay_slot_invalid")
        child = OwnedChild([sys.executable, "-B", str(entry), "--home", str(self.home)],
                           cwd=self.slot, cgroup_root=self.cgroup_root)
        try:
            reply = child.read(timeout=20)
            if not isinstance(reply, dict) or set(reply) != {"schema", "ready"} or reply["schema"] != 1:
                raise UpdateError("managed_relay_ready_invalid")
            evidence = reply["ready"]
            if (not isinstance(evidence, dict) or evidence.get("gate", {}).get("closed") is not False
                    or type(evidence.get("pid")) is not int or evidence["pid"] <= 0):
                raise UpdateError("managed_relay_ready_invalid")
            driver = self._driver(child)
            health = driver._binding(child, self.slot)
            if health["gate"].get("closed") is not False or child.poll() is not None:
                raise UpdateError("managed_relay_binding_unverified")
        except BaseException:
            child.stop_tree(timeout=15)
            raise
        self.child = child

    def _driver(self, child=None):
        return RelayDriver(self.home, self.slot,
                           {"child": self.child if child is None else child,
                            "cgroup_root": self.cgroup_root})

    def _phase(self):
        path = self.home / "state" / "transaction.json"
        if not path.exists():
            return "ready"
        return read(path).get("phase", "needs_review")

    def status(self):
        phase = self._phase()
        if phase not in ("ready", "committed", "rolled_back"):
            return {"component": "relay", "version": None, "phase": "needs_review", "healthy": False}
        if self.child is None or self.child.poll() is not None:
            return {"component": "relay", "version": None, "phase": "needs_review", "healthy": False}
        _, pointer, slot = installed(self.home)
        if slot != self.slot:
            return {"component": "relay", "version": None, "phase": "needs_review", "healthy": False}
        health = self._driver()._binding(self.child, self.slot)
        return {"component": "relay", "version": pointer["version"],
                "phase": phase, "healthy": health["listenersAlive"] is True
                and health["gate"].get("closed") is False}

    def _apply(self):
        if self.child is None or self.child.poll() is not None:
            raise UpdateError("managed_relay_owner_lost")
        driver = self._driver()
        try:
            result = Transaction(self.home, driver).apply()
        except BaseException:
            # Preserve every original handle while the outcome needs review.
            # In particular, ambiguous activation or cleanup may leave a live
            # candidate whose stdin must remain owned by this host.
            for child in (driver.old_child, driver.target, driver.child):
                if child is not None and all(child is not held for held in self.review_children):
                    self.review_children.append(child)
            if driver.child is not None:
                self.child = driver.child
            # A successful rollback also raises the original apply failure.
            # Its new OwnedChild belongs to this host, not a revived PID.
            if self._phase() == "rolled_back" and driver.child is not None:
                _, _, self.slot = installed(self.home)
            raise
        if result.get("phase") != "committed" or driver.child is None:
            raise UpdateError("managed_relay_commit_unverified")
        self.child = driver.child
        _, _, self.slot = installed(self.home)
        return {"phase": "committed", "version": result["candidate"]["version"]}

    def _stop(self):
        if self._phase() not in ("ready", "committed", "rolled_back"):
            raise UpdateError("unfinished_update_requires_review")
        if self.child is None or self.child.poll() is not None:
            raise UpdateError("managed_relay_owner_lost")
        driver = self._driver()
        driver.preflight()
        driver.stop_old()
        self.child = None
        self.stopping = True
        return {"stopped": True}

    def dispatch(self, op):
        if op == "status":
            return self.status()
        if op == "check":
            return {"release": check(self.home, authorization=for_installation(self.home))}
        if op == "stage":
            return {"release": stage(self.home, authorization=for_installation(self.home))}
        if op == "apply":
            return self._apply()
        if op == "stop":
            return self._stop()
        raise UpdateError("invalid_host_command")

    def serve(self):
        path = self.home / SOCKET_NAME
        if path.exists() or path.is_symlink():
            raise UpdateError("host_socket_already_exists")
        if len(os.fsencode(path)) >= 108:
            raise UpdateError("host_socket_path_too_long")
        listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        bound_inode = None
        old_mask = os.umask(0o077)
        try:
            listener.bind(str(path))
            os.chmod(path, 0o600)
        finally:
            os.umask(old_mask)
        try:
            info = path.stat()
            if info.st_uid != os.geteuid() or not stat.S_ISSOCK(info.st_mode) or info.st_mode & 0o077:
                raise UpdateError("host_socket_permissions_invalid")
            bound_inode = info.st_ino
            listener.listen(4)
            while not self.stopping:
                connection, _ = listener.accept()
                with connection:
                    self._serve_one(connection)
        finally:
            listener.close()
            # Remove only the socket inode created by this host.
            if bound_inode is not None and path.exists() and path.stat().st_ino == bound_inode:
                path.unlink()

    def _serve_one(self, connection):
        try:
            credentials = connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED,
                                                 struct.calcsize("3i"))
            _, uid, _ = struct.unpack("3i", credentials)
            if uid != os.geteuid():
                raise UpdateError("host_peer_not_owner")
            connection.settimeout(60)
            raw = bytearray()
            while len(raw) <= MAX_COMMAND:
                part = connection.recv(min(1024, MAX_COMMAND + 1 - len(raw)))
                if not part:
                    break
                raw.extend(part)
                if b"\n" in part:
                    break
            if not raw.endswith(b"\n"):
                raise UpdateError("invalid_host_command")
            op = command(raw[:-1])
            value = {"schema": 1, "result": self.dispatch(op)}
        except UpdateError as error:
            value = {"schema": 1, "error": error.code}
        except (OSError, ValueError):
            value = {"schema": 1, "error": "relay_host_unavailable"}
        except Exception:
            value = {"schema": 1, "error": "relay_host_unavailable"}
        try:
            connection.sendall(json.dumps(value, separators=(",", ":"), ensure_ascii=True).encode() + b"\n")
        except OSError:
            pass  # The update state remains in its journal if a client disconnects.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--cgroup-root", type=Path, required=True)
    args = parser.parse_args()
    try:
        RelayHost(args.home, args.cgroup_root).serve()
    except (UpdateError, OSError):
        print("relay_host_unavailable", file=sys.stderr)
        raise SystemExit(2)


if __name__ == "__main__":
    main()
