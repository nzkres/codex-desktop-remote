"""Relay lifecycle bridge for the shared journaled update transaction."""

import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import time

from maintenance import critical_digest
from managed import _small_json, read_home
from updates.processes import OwnedChild
from updates.security import UpdateError


class RelayDriver:
    def __init__(self, home: Path, old_slot: Path, request: dict):
        if not isinstance(request, dict) or request.get("child") is None or not request.get("cgroup_root"):
            raise UpdateError("managed_relay_owner_required")
        self.home, self.data, _, self.token = read_home(Path(home))
        self.old_slot = Path(old_slot).resolve()
        self.old_child = request["child"]  # Original OwnedChild handle supplied by the host.
        self.cgroup_root = request["cgroup_root"]
        self.child = self.old_child
        self.target = None
        self.prepared = False
        self.stop_attempted = False
        self.old_stopped = False
        self.activate_attempted = False
        self.preflight_fingerprint = None

    @staticmethod
    def _file_sha(path, limit, *, optional=False):
        try:
            if optional and not path.exists() and not path.is_symlink():
                return None
            info = path.lstat()
            if (not stat.S_ISREG(info.st_mode) or info.st_size > limit
                    or (os.name == "posix" and (info.st_uid != os.geteuid() or info.st_mode & 0o077))):
                raise UpdateError("managed_relay_configuration_changed")
            with path.open("rb") as stream:
                content = stream.read(limit + 1)
            if len(content) > limit:
                raise UpdateError("managed_relay_configuration_changed")
            return hashlib.sha256(content).hexdigest()
        except OSError:
            raise UpdateError("managed_relay_configuration_changed") from None

    def _stable_fingerprint(self):
        return {"configSha256": self._file_sha(self.home / "config.json", 8192),
                "controlTokenSha256": self._file_sha(self.home / "control.token", 128),
                "transportSha256": self._file_sha(self.data / "transport.json", 32768, optional=True)}

    def _rpc(self, child, op, *, timeout=15):
        child.write({"schema": 1, "token": self.token, "op": op})
        reply = child.read(timeout=timeout)
        if not isinstance(reply, dict) or reply.get("schema") != 1:
            raise UpdateError("managed_relay_protocol_error")
        if reply.get("error"):
            raise UpdateError("managed_relay_" + ("busy" if op in ("prepare", "verify_recovery") else "refused"))
        if op == "stop":
            if reply != {"schema": 1, "stopped": True}:
                raise UpdateError("managed_relay_stop_unconfirmed")
            return True
        if set(reply) != {"schema", "result"} or not isinstance(reply["result"], dict):
            raise UpdateError("managed_relay_protocol_error")
        return reply["result"]

    @staticmethod
    def _version(slot):
        try:
            value = json.loads((Path(slot) / "relay" / "releases.json").read_bytes())
            if value["component"] != "relay" or not value["releases"]:
                raise ValueError()
            return value["releases"][0]["version"]
        except (OSError, ValueError, KeyError, TypeError, IndexError):
            raise UpdateError("managed_relay_slot_invalid") from None

    def _binding(self, child, slot):
        health = self._rpc(child, "health")
        ready = _small_json(self.home / "ready.json")
        # OwnedChild.pid belongs to child_gate.py. The managed child is a
        # different process in the same owned Job/cgroup; bind its two
        # independent readiness reports to each other, never to gate PID.
        managed_pid = health.get("pid")
        if (health.get("schema") != 1 or type(managed_pid) is not int or managed_pid <= 0
                or health.get("instance") != ready.get("instance") or ready.get("pid") != managed_pid
                or health.get("version") != self._version(slot)
                or health.get("version") != ready.get("version")
                or health.get("dataSchema") != 1 or health.get("listenersAlive") is not True):
            raise UpdateError("managed_relay_binding_unverified")
        return health

    def preflight(self):
        if self.old_child.poll() is not None:
            raise UpdateError("managed_relay_owner_lost")
        self.preflight_fingerprint = self._stable_fingerprint()
        health = self._binding(self.old_child, self.old_slot)
        if health.get("gate", {}).get("closed"):
            raise UpdateError("managed_relay_already_quiesced")
        try:
            prepared = self._rpc(self.old_child, "prepare", timeout=45)
            if prepared.get("closed") is not True or prepared.get("active") != 0 or prepared.get("uncertain"):
                raise UpdateError("managed_relay_prepare_unverified")
            if self._stable_fingerprint() != self.preflight_fingerprint:
                raise UpdateError("managed_relay_configuration_changed")
        except BaseException:
            # Engine has not entered its transaction try/finally yet.
            if self.old_child.poll() is None:
                try:
                    self._rpc(self.old_child, "abort")
                except BaseException:
                    pass  # Ambiguous gate state remains a host attention case.
            raise
        self.prepared = True

    def snapshot(self):
        if not self.prepared or self._stable_fingerprint() != self.preflight_fingerprint:
            raise UpdateError("managed_relay_configuration_changed")
        local = {"schema": 1, "dataSchema": 1,
                 "authSha256": critical_digest(self.data / "auth.sqlite3", "auth"),
                 "registrySha256": critical_digest(self.data / "registry.sqlite3", "registry")}
        if not self.old_stopped and self.target is None:
            if not self.prepared:
                raise UpdateError("managed_relay_not_prepared")
            remote = self._rpc(self.old_child, "snapshot")
            if remote != local:
                raise UpdateError("managed_relay_snapshot_mismatch")
        return {**local, **self.preflight_fingerprint}

    def stop_old(self):
        if not self.prepared or self.old_stopped:
            raise UpdateError("managed_relay_not_prepared")
        self.stop_attempted = True
        self._rpc(self.old_child, "stop", timeout=20)
        if self.old_child.stop_tree(timeout=15) is not True:
            raise UpdateError("managed_relay_tree_exit_unconfirmed")
        self.old_stopped = True
        self.child = None

    def start(self, slot):
        if not self.old_stopped or self.target is not None:
            raise UpdateError("managed_relay_switch_not_ready")
        slot = Path(slot).resolve()
        entry = slot / "relay" / "managed.py"
        if not entry.is_file() or entry.is_symlink():
            raise UpdateError("managed_relay_slot_invalid")
        self.target = OwnedChild([sys.executable, "-B", str(entry), "--home", str(self.home),
                                  "--quarantine"], cwd=slot, cgroup_root=self.cgroup_root)
        self.child = self.target
        ready = self.target.read(timeout=20)
        if not isinstance(ready, dict) or set(ready) != {"schema", "ready"} or ready["schema"] != 1:
            raise UpdateError("managed_relay_ready_invalid")
        evidence = ready["ready"]
        if (not isinstance(evidence, dict) or type(evidence.get("pid")) is not int
                or evidence["pid"] <= 0
                or evidence.get("version") != self._version(slot)
                or evidence.get("gate", {}).get("closed") is not True):
            raise UpdateError("managed_relay_ready_invalid")

    def verify(self, slot):
        if self.target is None or self.target.poll() is not None:
            raise UpdateError("managed_relay_target_lost")
        health = self._binding(self.target, slot)
        if health.get("gate", {}).get("closed") is not True:
            raise UpdateError("managed_relay_quarantine_lost")
        deadline = time.monotonic() + 45
        while True:
            if self.target.poll() is not None:
                raise UpdateError("managed_relay_target_lost")
            try:
                recovery = self._rpc(self.target, "verify_recovery", timeout=8)
                if recovery.get("gate", {}).get("closed") is True:
                    return
                raise UpdateError("managed_relay_recovery_unverified")
            except UpdateError as error:
                if error.code != "managed_relay_busy" or time.monotonic() >= deadline:
                    raise
                time.sleep(0.5)

    def activate(self):
        if self.target is None:
            raise UpdateError("managed_relay_target_lost")
        self.activate_attempted = True
        result = self._rpc(self.target, "activate", timeout=15)
        if result.get("gate", {}).get("closed") is not False or result.get("listenersAlive") is not True:
            raise UpdateError("managed_relay_activation_unverified")

    def cleanup_target(self):
        if self.target is None:
            return
        if self.activate_attempted:
            # An ambiguous activation may have admitted a request; no safe kill.
            raise UpdateError("managed_relay_activation_ambiguous")
        try:
            self._rpc(self.target, "stop", timeout=15)
        except BaseException:
            pass  # OwnedChild still must prove the entire quarantined tree exited.
        if self.target.stop_tree(timeout=15) is not True:
            raise UpdateError("managed_relay_tree_exit_unconfirmed")
        self.target = None
        self.child = None

    def release(self, phase):
        if (phase == "needs_review" and self.prepared and not self.stop_attempted
                and self.old_child.poll() is None):
            try:
                self._rpc(self.old_child, "abort")
            except BaseException:
                pass  # Leave the old gate closed if restoration is not proven.
        # For committed/rolled_back the host retains self.child and its original
        # OwnedChild handle. Never detach or kill it here.
