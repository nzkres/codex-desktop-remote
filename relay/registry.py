"""Isolated terminal registry. No Codex state, passwords, or plaintext credentials."""

import hashlib
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager


class ControlError(Exception):
    def __init__(self, status, code):
        super().__init__(code)
        self.status, self.code = status, code


def digest(value):
    return hashlib.sha256(value.encode("ascii")).digest()


def text(value, maximum=80):
    if not isinstance(value, str) or not value.strip() or len(value) > maximum:
        raise ControlError(400, "invalid_text")
    if any(ord(char) < 32 or ord(char) == 127 for char in value):
        raise ControlError(400, "invalid_text")
    return value.strip()


def credential(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ControlError(401, "invalid_device_credential")
    return digest(value)


class Registry:
    PAIR_SECONDS = 300
    ONLINE_SECONDS = 45
    FAILURE_SECONDS = 300
    MAX_FAILURES = 10

    def __init__(self, path, port_start=21000, port_end=21999, clock=time.time,
                 initialize=True):
        if not 1024 <= port_start <= port_end <= 65535:
            raise ValueError("Invalid private port pool")
        self.path, self.port_start, self.port_end, self.clock = str(path), port_start, port_end, clock
        if not initialize:
            return  # Managed startup performs a separate read-only schema preflight.
        with self.transaction() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS pairings (
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, code_hash BLOB UNIQUE NOT NULL,
                    expires INTEGER NOT NULL, state TEXT NOT NULL,
                    device_hash BLOB, name TEXT, platform TEXT, terminal_id TEXT
                );
                CREATE TABLE IF NOT EXISTS terminals (
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL,
                    platform TEXT NOT NULL, device_hash BLOB UNIQUE NOT NULL,
                    port INTEGER UNIQUE NOT NULL, state TEXT NOT NULL,
                    created INTEGER NOT NULL, last_seen INTEGER,
                    executor TEXT NOT NULL DEFAULT 'unknown', version TEXT NOT NULL DEFAULT ''
                );
                CREATE TABLE IF NOT EXISTS failures (source TEXT NOT NULL, at INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS failure_source ON failures(source, at);
            """)
            if "access_epoch" not in {row["name"] for row in db.execute("PRAGMA table_info(terminals)")}:
                db.execute("ALTER TABLE terminals ADD COLUMN access_epoch INTEGER NOT NULL DEFAULT 0")

    @contextmanager
    def transaction(self):
        db = sqlite3.connect(self.path, timeout=5, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("BEGIN IMMEDIATE")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    def now(self):
        return int(self.clock())

    def create_pairing(self, owner):
        with self.transaction() as db:
            count = db.execute("SELECT COUNT(*) FROM pairings WHERE owner=? AND expires>? AND state IN ('requested','claimed')", (owner, self.now())).fetchone()[0]
            if count >= 5:
                raise ControlError(429, "too_many_pending_pairings")
            pairing_id, code = secrets.token_hex(16), secrets.token_hex(6).upper()
            expires = self.now() + self.PAIR_SECONDS
            db.execute("INSERT INTO pairings(id,owner,code_hash,expires,state) VALUES(?,?,?,?,'requested')", (pairing_id, owner, digest(code), expires))
        return {"id": pairing_id, "code": code, "expiresAt": expires}

    def claim(self, code, device_token, name, platform, source):
        device_hash = credential(device_token)
        name = text(name)
        if platform != "windows":
            raise ControlError(400, "unsupported_platform")
        # Only the server's trusted peer address may supply source, never request JSON.
        failure = False
        with self.transaction() as db:
            now = self.now()
            db.execute("DELETE FROM failures WHERE at<=?", (now - self.FAILURE_SECONDS,))
            if db.execute("SELECT COUNT(*) FROM failures WHERE source=?", (source,)).fetchone()[0] >= self.MAX_FAILURES:
                raise ControlError(429, "pairing_rate_limited")
            valid = isinstance(code, str) and re.fullmatch(r"[0-9A-F]{12}", code)
            row = db.execute("SELECT * FROM pairings WHERE code_hash=?", (digest(code) if valid else bytes(32),)).fetchone()
            if row is not None and row["device_hash"] == device_hash and row["state"] in ("claimed", "confirmed") and (row["expires"] > now or row["state"] == "confirmed"):
                # Recover a lost response using the SAME credential, never a new
                # enrollment or a different holder of the spent pairing code.
                return {"id": row["id"], "state": "awaiting_confirmation", "expiresAt": row["expires"]}
            if row is None or row["expires"] <= now or row["state"] != "requested":
                db.execute("INSERT INTO failures VALUES(?,?)", (source, now))
                failure = True
            else:
                # A connector must generate a fresh credential for each enrollment.
                used = db.execute("SELECT 1 FROM terminals WHERE device_hash=? UNION SELECT 1 FROM pairings WHERE device_hash=?", (device_hash, device_hash)).fetchone()
                if used:
                    raise ControlError(409, "credential_already_used")
                db.execute("UPDATE pairings SET state='claimed',device_hash=?,name=?,platform=? WHERE id=?", (device_hash, name, platform, row["id"]))
                result = {"id": row["id"], "state": "awaiting_confirmation", "expiresAt": row["expires"]}
        # Commit failed-attempt accounting before returning an error.
        if failure:
            raise ControlError(401, "pairing_invalid_or_used")
        return result

    def pairings(self, owner):
        with self.transaction() as db:
            rows = db.execute("SELECT id,expires,state,name,platform FROM pairings WHERE owner=? AND expires>? AND state IN ('requested','claimed') ORDER BY rowid", (owner, self.now())).fetchall()
            return [dict(row) for row in rows]

    def confirm(self, owner, pairing_id, name):
        name = text(name)
        with self.transaction() as db:
            row = db.execute("SELECT * FROM pairings WHERE id=? AND owner=?", (pairing_id, owner)).fetchone()
            if row is None:
                raise ControlError(404, "pairing_not_found")
            if row["state"] == "confirmed":
                return self.public(self._owned(db, owner, row["terminal_id"]))
            if row["expires"] <= self.now() or row["state"] != "claimed":
                raise ControlError(409, "pairing_not_confirmable")
            # Never recycle a removed device's port into another device identity.
            maximum = db.execute("SELECT MAX(port) FROM terminals").fetchone()[0]
            port = max(self.port_start, (maximum + 1) if maximum is not None else self.port_start)
            if port > self.port_end:
                raise ControlError(409, "private_port_pool_exhausted")
            terminal_id = secrets.token_hex(16)
            db.execute("INSERT INTO terminals(id,owner,name,platform,device_hash,port,state,created) VALUES(?,?,?,?,?,?,'active',?)", (terminal_id, owner, name, row["platform"], row["device_hash"], port, self.now()))
            db.execute("UPDATE pairings SET state='confirmed',terminal_id=? WHERE id=?", (terminal_id, pairing_id))
            return self.public(self._owned(db, owner, terminal_id))

    def cancel_pairing(self, owner, pairing_id):
        with self.transaction() as db:
            row = db.execute("SELECT state FROM pairings WHERE id=? AND owner=?", (pairing_id, owner)).fetchone()
            if row is None:
                raise ControlError(404, "pairing_not_found")
            if row["state"] == "confirmed":
                raise ControlError(409, "already_confirmed_remove_terminal_instead")
            db.execute("UPDATE pairings SET state='rejected' WHERE id=?", (pairing_id,))

    def pairing_status(self, pairing_id, device_token):
        with self.transaction() as db:
            row = db.execute("SELECT * FROM pairings WHERE id=? AND device_hash=?", (pairing_id, credential(device_token))).fetchone()
            if row is None:
                raise ControlError(401, "invalid_device_credential")
            if row["state"] == "confirmed":
                terminal = db.execute("SELECT * FROM terminals WHERE id=?", (row["terminal_id"],)).fetchone()
                if terminal["state"] == "revoked":
                    raise ControlError(401, "device_revoked")
                return {"state": "confirmed", "terminalState": terminal["state"], "terminalId": terminal["id"], "remotePort": terminal["port"], "proxyName": terminal["id"] + ".gateway"}
            return {"state": "expired" if row["expires"] <= self.now() else row["state"]}

    def _owned(self, db, owner, terminal_id):
        row = db.execute("SELECT * FROM terminals WHERE id=? AND owner=? AND state!='revoked'", (terminal_id, owner)).fetchone()
        if row is None:
            raise ControlError(404, "terminal_not_found")
        return row

    def public(self, row):
        online = row["state"] == "active" and row["last_seen"] is not None and self.now() - row["last_seen"] < self.ONLINE_SECONDS
        return {"id": row["id"], "name": row["name"], "platform": row["platform"], "state": row["state"], "online": online, "lastSeen": row["last_seen"], "executor": row["executor"] if online else "unknown", "connectorVersion": row["version"]}

    def terminals(self, owner):
        with self.transaction() as db:
            return [self.public(row) for row in db.execute("SELECT * FROM terminals WHERE owner=? AND state!='revoked' ORDER BY created,id", (owner,))]

    def change(self, owner, terminal_id, action, name=None):
        with self.transaction() as db:
            self._owned(db, owner, terminal_id)
            if action == "rename":
                db.execute("UPDATE terminals SET name=? WHERE id=?", (text(name), terminal_id))
            elif action in ("pause", "resume", "remove"):
                state = {"pause": "paused", "resume": "active", "remove": "revoked"}[action]
                db.execute("UPDATE terminals SET state=?,last_seen=NULL,executor='unknown',access_epoch=access_epoch+1 WHERE id=?", (state, terminal_id))
            else:
                raise ControlError(400, "invalid_action")

    def _device(self, db, terminal_id, device_token):
        row = db.execute("SELECT * FROM terminals WHERE id=? AND device_hash=? AND state='active'", (terminal_id, credential(device_token))).fetchone()
        if row is None:
            raise ControlError(403, "device_not_authorized")
        return row

    def device(self, terminal_id, device_token):
        with self.transaction() as db:
            row = self._device(db, terminal_id, device_token)
            return {"id": row["id"], "port": row["port"], "proxyName": row["id"] + ".gateway", "epoch": row["access_epoch"]}

    def gateway_epoch_active(self, terminal_id, epoch):
        with self.transaction() as db:
            return db.execute("SELECT 1 FROM terminals WHERE id=? AND state='active' AND access_epoch=?", (terminal_id, epoch)).fetchone() is not None

    def heartbeat(self, terminal_id, device_token, executor, version):
        if executor not in ("unknown", "ready", "login_required", "incompatible", "unavailable"):
            raise ControlError(400, "invalid_executor_state")
        version = text(version, 40)
        with self.transaction() as db:
            self._device(db, terminal_id, device_token)
            db.execute("UPDATE terminals SET last_seen=?,executor=?,version=? WHERE id=?", (self.now(), executor, version, terminal_id))

    def route(self, owner, terminal_id):
        """Internal dispatch decision only. Never accept a client-provided URL or port."""
        with self.transaction() as db:
            row = self._owned(db, owner, terminal_id)
            if row["state"] != "active":
                raise ControlError(403, "terminal_paused")
            if not self.public(row)["online"]:
                raise ControlError(503, "terminal_offline")
            return {"host": "127.0.0.1", "port": row["port"], "terminalId": row["id"], "epoch": row["access_epoch"]}
