"""Process-local admission barrier for an explicitly managed relay."""

from contextlib import closing, contextmanager
import hashlib
import http.client
import json
from pathlib import Path
import sqlite3
import threading
import time


class MaintenanceClosed(Exception):
    pass


class MaintenanceBusy(Exception):
    pass


class MaintenanceGate:
    def __init__(self, initially_closed=False, recovery=False):
        self._lock = threading.Lock()
        self._closed = initially_closed
        self._recovery = recovery
        self._preparing = False
        self._active = 0
        self._uncertain = False

    @contextmanager
    def admit(self, kind="ordinary"):
        with self._lock:
            if self._closed and not (self._recovery and kind in ("connector_recovery", "plugin_recovery")):
                raise MaintenanceClosed()
            self._active += 1
        try:
            yield
        finally:
            with self._lock:
                self._active -= 1

    def mark_uncertain(self):
        """Sticky until a separately reviewed receipt reconciliation occurs."""
        with self._lock:
            self._uncertain = True

    def snapshot(self):
        with self._lock:
            return {"closed": self._closed, "recovery": self._recovery,
                    "preparing": self._preparing, "active": self._active,
                    "uncertain": self._uncertain}

    def prepare(self, preflight):
        """Close admission first; leave it closed only after a clean preflight."""
        with self._lock:
            if self._closed or self._active or self._uncertain:
                raise MaintenanceBusy("active_or_uncertain")
            self._closed = True
            self._preparing = True
        try:
            preflight()
            with self._lock:
                if self._active or self._uncertain:
                    raise MaintenanceBusy("active_or_uncertain")
        except BaseException:
            with self._lock:
                self._preparing = False
                self._closed = False
            raise
        with self._lock:
            self._preparing = False
        return self.snapshot()

    def abort(self):
        with self._lock:
            if self._preparing:
                raise MaintenanceBusy("preparing")
            self._closed = False
            self._recovery = False

    def seal(self):
        """Fail closed after the owner pipe is lost; never abort in-flight work."""
        with self._lock:
            self._closed = True
            self._recovery = False


def _read_only(path):
    file = Path(path)
    if not file.is_file() or file.is_symlink():
        raise MaintenanceBusy("data_unavailable")
    connection = sqlite3.connect(file.resolve().as_uri() + "?mode=ro", uri=True, timeout=2)
    connection.execute("PRAGMA query_only=ON")
    return connection


def _columns(connection, table):
    return {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}


def inspect_registry(path, now=None):
    """Read-only schema-1 and pending/active channel evidence."""
    try:
        with closing(_read_only(path)) as db:
            if db.execute("PRAGMA user_version").fetchone()[0] != 1:
                raise MaintenanceBusy("registry_schema_unknown")
            if not {"id", "owner", "code_hash", "expires", "state", "device_hash", "terminal_id"} <= _columns(db, "pairings"):
                raise MaintenanceBusy("registry_schema_unknown")
            if not {"id", "owner", "device_hash", "port", "state", "last_seen", "access_epoch"} <= _columns(db, "terminals"):
                raise MaintenanceBusy("registry_schema_unknown")
            instant = int(time.time() if now is None else now)
            pending = db.execute("SELECT COUNT(*) FROM pairings WHERE expires>? AND state IN ('requested','claimed')", (instant,)).fetchone()[0]
            devices = [dict(zip(("id", "port", "last_seen"), row)) for row in
                       db.execute("SELECT id,port,last_seen FROM terminals WHERE state='active' ORDER BY id")]
            return {"pendingPairings": pending, "activeDevices": devices}
    except (OSError, sqlite3.Error):
        raise MaintenanceBusy("registry_unavailable") from None


def database_digest(path):
    """Hash one consistent read-only SQLite view; never return database rows."""
    try:
        with closing(_read_only(path)) as db:
            db.execute("BEGIN")
            digest = hashlib.sha256()
            for line in db.iterdump():
                digest.update(line.encode("utf-8"))
                digest.update(b"\n")
            db.rollback()
            return digest.hexdigest()
    except (OSError, sqlite3.Error):
        raise MaintenanceBusy("data_unavailable") from None


def critical_digest(path, component):
    """Protect schema and durable rows; omit only relay heartbeat telemetry."""
    if component == "registry":
        queries = (
            "SELECT * FROM pairings ORDER BY id",
            "SELECT id,owner,name,platform,device_hash,port,state,created,access_epoch FROM terminals ORDER BY id",
            "SELECT source,at FROM failures ORDER BY source,at",
        )
    elif component == "auth":
        queries = (
            "SELECT * FROM account ORDER BY id",
            "SELECT * FROM sessions ORDER BY token_hash",
            "SELECT * FROM failures ORDER BY ip,at",
        )
    else:
        raise ValueError("invalid_component")
    try:
        with closing(_read_only(path)) as db:
            db.execute("BEGIN")
            digest = hashlib.sha256()
            def add(value):
                digest.update(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8"))
                digest.update(b"\n")
            add(db.execute("PRAGMA user_version").fetchone()[0])
            for row in db.execute("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type,name"):
                add(tuple(row))
            for query in queries:
                add(query)
                for row in db.execute(query):
                    add([value.hex() if isinstance(value, bytes) else value for value in row])
            db.rollback()
            return digest.hexdigest()
    except (OSError, sqlite3.Error):
        raise MaintenanceBusy("data_unavailable") from None


def probe_update_status(port, token):
    """One bounded, authenticated, side-effect-free adapter read on loopback."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=2)
    try:
        connection.request("GET", "/api/connector/update-status",
                           headers={"Authorization": "Bearer " + token, "Connection": "close"})
        response = connection.getresponse()
        length = response.getheader("Content-Length", "")
        if response.status != 200 or response.getheader("Transfer-Encoding") or not length.isdigit() or not 0 < int(length) <= 2048:
            raise MaintenanceBusy("adapter_status_unknown")
        body = json.loads(response.read(int(length)))
        if (not isinstance(body, dict) or set(body) != {"idle", "version", "permissionsVerified", "receiptsCertain"}
                or body.get("idle") is not True or body.get("permissionsVerified") is not True
                or body.get("receiptsCertain") is not True or not isinstance(body.get("version"), str)
                or not 0 < len(body["version"]) <= 80):
            raise MaintenanceBusy("adapter_not_idle_or_uncertain")
        return {"version": body["version"]}
    except (OSError, ValueError, http.client.HTTPException):
        raise MaintenanceBusy("adapter_status_unknown") from None
    finally:
        connection.close()
