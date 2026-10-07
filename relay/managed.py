"""Explicitly managed, loopback-only relay child. No legacy process adoption."""

import argparse
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import stat
import sys
import tempfile
import threading
import time

from auth import auth_server
from control_server import RelayAccount, make_server
from forwarder import Forwarder
from gateway_link import GatewayLinks
from maintenance import (MaintenanceBusy, MaintenanceClosed, MaintenanceGate,
                         critical_digest, inspect_registry, probe_update_status,
                         _read_only, _columns)
from registry import Registry
from release_notes import load_catalog
from transport_settings import TransportSettings


class ManagedError(Exception):
    pass


def _private(path, *, directory=False):
    path = Path(path)
    if not path.is_absolute() or any(part.is_symlink() for part in (path, *path.parents)):
        raise ManagedError("private_path_required")
    info = path.stat()
    if not (stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode)):
        raise ManagedError("private_path_required")
    if info.st_uid != os.geteuid() or info.st_mode & 0o077:
        raise ManagedError("private_permissions_required")
    return path


def _small_json(path, limit=8192):
    file = _private(path)
    if file.stat().st_size > limit:
        raise ManagedError("invalid_managed_config")
    try:
        return json.loads(file.read_bytes())
    except (ValueError, UnicodeError):
        raise ManagedError("invalid_managed_config") from None


def _port(value):
    if type(value) is not int or not 1024 <= value <= 65535:
        raise ManagedError("invalid_managed_port")
    return value


def read_home(home):
    """The installer/supervisor provisions home, token, config and data first."""
    if os.name != "posix":
        raise ManagedError("linux_managed_runtime_required")
    home = _private(home, directory=True)
    code = Path(__file__).resolve().parent
    if home == code or code in home.parents:
        raise ManagedError("code_and_home_must_be_separate")
    data = _private(home / "data", directory=True)
    config = _small_json(home / "config.json")
    if not isinstance(config, dict) or set(config) != {"schema", "authPort", "apiPort", "pluginPort", "portStart", "portEnd", "transportConfig"} or config["schema"] != 1:
        raise ManagedError("invalid_managed_config")
    ports = [_port(config[key]) for key in ("authPort", "apiPort", "pluginPort", "portStart", "portEnd")]
    if len(set(ports[:3])) != 3 or ports[3] > ports[4]:
        raise ManagedError("invalid_managed_port")
    transport = config["transportConfig"]
    if transport is not None:
        transport = _private(data / "transport.json")
        if str(transport) != config["transportConfig"]:
            raise ManagedError("transport_config_outside_home")
    token_file = _private(home / "control.token")
    if token_file.stat().st_size > 128:
        raise ManagedError("invalid_control_token")
    token = token_file.read_text(encoding="ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{64}", token):
        raise ManagedError("invalid_control_token")
    return home, data, config, token


def inspect_auth(path):
    try:
        from contextlib import closing
        with closing(_read_only(path)) as db:
            if db.execute("PRAGMA user_version").fetchone()[0] != 1:
                raise ManagedError("auth_schema_unknown")
            if not {"id", "username", "salt", "password_hash"} <= _columns(db, "account"):
                raise ManagedError("auth_schema_unknown")
            if not {"token_hash", "expires_at"} <= _columns(db, "sessions"):
                raise ManagedError("auth_schema_unknown")
            if not {"ip", "at"} <= _columns(db, "failures"):
                raise ManagedError("auth_schema_unknown")
    except Exception as error:
        if isinstance(error, ManagedError):
            raise
        raise ManagedError("auth_schema_unknown") from None


def _write_ready(home, record):
    target = home / "ready.json"
    if target.exists():
        raise ManagedError("previous_ready_requires_review")
    descriptor, temporary = tempfile.mkstemp(prefix="ready-", suffix=".tmp", dir=home)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(record, stream, separators=(",", ":"))
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def make_managed_auth_handler(gate):
    class ManagedAuthHandler(auth_server.Handler):
        def _admitted(self, action):
            try:
                with gate.admit():
                    return action()
            except MaintenanceClosed:
                return self.respond(503, {"error": "relay_maintenance"})

        def do_GET(self):
            return self._admitted(lambda: super(ManagedAuthHandler, self).do_GET())

        def do_POST(self):
            return self._admitted(lambda: super(ManagedAuthHandler, self).do_POST())

    return ManagedAuthHandler


class ManagedRelay:
    def __init__(self, home, *, quarantine=False):
        self.home, self.data, self.config, self.token = read_home(home)
        self.registry_path = self.data / "registry.sqlite3"
        self.auth_path = self.data / "auth.sqlite3"
        _private(self.registry_path)
        _private(self.auth_path)
        inspect_auth(self.auth_path)
        inspect_registry(self.registry_path)
        self.registry = Registry(self.registry_path, self.config["portStart"],
                                 self.config["portEnd"], initialize=False)
        auth_server.DB_PATH = str(self.auth_path)
        self.gate = MaintenanceGate(initially_closed=quarantine, recovery=quarantine)
        self.quarantine = quarantine
        self.account = RelayAccount(self.config["authPort"])
        self.links = GatewayLinks(self.registry)
        self.forwarder = Forwarder(self.registry, self.account, self.links.credential,
                                   on_uncertain=self.gate.mark_uncertain)
        settings = None
        if self.config["transportConfig"] is not None:
            settings = TransportSettings.from_file(self.registry, self.config["transportConfig"])
        bound = []
        try:
            self.auth_server = auth_server.ThreadingHTTPServer(
                ("127.0.0.1", self.config["authPort"]), make_managed_auth_handler(self.gate))
            bound.append(self.auth_server)
            self.plugin_server = make_server(self.registry, self.config["pluginPort"], plugin=True, gate=self.gate)
            bound.append(self.plugin_server)
            self.api_server = make_server(self.registry, self.config["apiPort"], account=self.account,
                                          forwarder=self.forwarder, gateway_links=self.links,
                                          transport_settings=settings, gate=self.gate)
            bound.append(self.api_server)
        except BaseException:
            for server in bound:
                server.server_close()
            raise
        self.threads = []
        self.instance = secrets.token_hex(16)
        self.version = load_catalog()["version"]
        self.stopped = False

    def start(self):
        try:
            for server in (self.auth_server, self.plugin_server, self.api_server):
                thread = threading.Thread(target=server.serve_forever, daemon=False)
                thread.start()
                self.threads.append(thread)
            _write_ready(self.home, {"schema": 1, "instance": self.instance, "pid": os.getpid(),
                                     "ownerUid": os.geteuid(), "version": self.version,
                                     "quarantine": self.quarantine,
                                     "ports": {"auth": self.config["authPort"], "api": self.config["apiPort"],
                                               "plugin": self.config["pluginPort"]}})
        except BaseException:
            self.gate.seal()
            for server in (self.api_server, self.plugin_server, self.auth_server):
                server.shutdown()
                server.server_close()
            for thread in self.threads:
                thread.join(timeout=5)
            raise

    def health(self):
        return {"schema": 1, "instance": self.instance, "pid": os.getpid(),
                "version": self.version, "dataSchema": 1,
                "listenersAlive": len(self.threads) == 3 and all(thread.is_alive() for thread in self.threads),
                "gate": self.gate.snapshot()}

    def _preflight(self):
        if self.quarantine:
            raise MaintenanceBusy("candidate_quarantined")
        with self.forwarder.lock:
            if self.forwarder.leases:
                raise MaintenanceBusy("active_forwarding")
        self._check_devices()

    def _check_devices(self):
        state = inspect_registry(self.registry_path)
        if state["pendingPairings"]:
            raise MaintenanceBusy("pending_pairings")
        now = int(time.time())
        for device in state["activeDevices"]:
            if device["last_seen"] is None or now - device["last_seen"] >= self.registry.ONLINE_SECONDS:
                raise MaintenanceBusy("active_device_offline")
            token = self.links.credential(device["id"])
            if token is None:
                raise MaintenanceBusy("active_device_unverified")
            probe_update_status(device["port"], token)

    def verify_recovery(self):
        state = self.gate.snapshot()
        if not self.quarantine or not state["closed"] or not state["recovery"]:
            raise MaintenanceBusy("candidate_not_quarantined")
        inspect_auth(self.auth_path)
        if not self.health()["listenersAlive"]:
            raise MaintenanceBusy("listener_unavailable")
        self._check_devices()
        return self.health()

    def prepare(self):
        return self.gate.prepare(self._preflight)

    def snapshot(self):
        if not self.gate.snapshot()["closed"]:
            raise MaintenanceBusy("admission_not_closed")
        return {"schema": 1, "dataSchema": 1,
                "authSha256": critical_digest(self.auth_path, "auth"),
                "registrySha256": critical_digest(self.registry_path, "registry")}

    def activate(self):
        if not self.quarantine or not self.health()["listenersAlive"]:
            raise MaintenanceBusy("not_ready_to_activate")
        self.verify_recovery()
        self.gate.abort()
        self.quarantine = False
        return self.health()

    def abort(self):
        state = self.gate.snapshot()
        if self.quarantine or not state["closed"] or state["uncertain"]:
            raise MaintenanceBusy("not_prepared")
        self.gate.abort()
        return self.health()

    def stop(self):
        status = self.gate.snapshot()
        if self.stopped or not status["closed"] or status["active"]:
            raise MaintenanceBusy("admission_not_closed")
        self.stopped = True
        for server in (self.api_server, self.plugin_server, self.auth_server):
            server.shutdown()
            server.server_close()
        for thread in self.threads:
            thread.join(timeout=5)
        if any(thread.is_alive() for thread in self.threads):
            raise MaintenanceBusy("listener_shutdown_unproved")
        self.forwarder.close()  # No admitted requests remain after prepare.
        ready = self.home / "ready.json"
        try:
            value = _small_json(ready)
            if value.get("instance") == self.instance and value.get("pid") == os.getpid():
                ready.unlink()
        except (OSError, ManagedError):
            pass  # A stale readiness record remains fail-closed for review.


def _reply(value):
    sys.stdout.write(json.dumps(value, separators=(",", ":"), ensure_ascii=False) + "\n")
    sys.stdout.flush()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True)
    parser.add_argument("--quarantine", action="store_true")
    args = parser.parse_args()
    relay = ManagedRelay(Path(args.home), quarantine=args.quarantine)
    relay.start()
    _reply({"schema": 1, "ready": relay.health()})
    try:
        for raw in sys.stdin.buffer:
            if len(raw) > 4096:
                _reply({"schema": 1, "error": "invalid_command"})
                continue
            try:
                command = json.loads(raw)
                if (not isinstance(command, dict) or set(command) != {"schema", "token", "op"}
                        or command["schema"] != 1 or not isinstance(command["token"], str)
                        or not hmac.compare_digest(command["token"], relay.token)):
                    raise ManagedError("unauthorized")
                op = command["op"]
                if op == "health":
                    result = relay.health()
                elif op == "prepare":
                    result = relay.prepare()
                elif op == "snapshot":
                    result = relay.snapshot()
                elif op == "verify_recovery":
                    result = relay.verify_recovery()
                elif op == "abort":
                    result = relay.abort()
                elif op == "activate":
                    result = relay.activate()
                elif op == "stop":
                    relay.stop()
                    _reply({"schema": 1, "stopped": True})
                    return
                else:
                    raise ManagedError("invalid_command")
                _reply({"schema": 1, "result": result})
            except (ManagedError, MaintenanceBusy, ValueError):
                _reply({"schema": 1, "error": "update_control_denied"})
    finally:
        if not relay.stopped:
            # Parent pipe loss is not proof of a safe update. Stop admitting new
            # work, wait for existing handlers, then exit without claiming commit.
            relay.gate.seal()
            while relay.gate.snapshot()["active"]:
                time.sleep(0.1)
            relay.stop()


if __name__ == "__main__":
    try:
        main()
    except (ManagedError, MaintenanceBusy):
        print("managed_relay_unavailable", file=sys.stderr)
        raise SystemExit(2)
