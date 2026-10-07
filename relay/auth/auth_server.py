"""Single-user relay identity service. Bind to loopback behind Caddy only."""

import argparse
import getpass
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import sys
import time
import threading
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DB_PATH = os.environ.get("CODEX_RELAY_AUTH_DB", "/var/lib/codex-remote-auth/auth.sqlite3")
HOST = "127.0.0.1"
PORT = int(os.environ.get("CODEX_RELAY_AUTH_PORT", "18081"))
SESSION_SECONDS = 30 * 24 * 60 * 60
FAILURE_WINDOW = 5 * 60
MAX_FAILURES = 5
SCRYPT_N = 1 << 15
HASH_SLOTS = threading.BoundedSemaphore(2)


def connect():
    db = sqlite3.connect(DB_PATH, timeout=5)
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("CREATE TABLE IF NOT EXISTS account (id INTEGER PRIMARY KEY CHECK(id=1), username TEXT NOT NULL, salt BLOB NOT NULL, password_hash BLOB NOT NULL)")
    db.execute("CREATE TABLE IF NOT EXISTS sessions (token_hash BLOB PRIMARY KEY, expires_at INTEGER NOT NULL)")
    db.execute("CREATE TABLE IF NOT EXISTS failures (ip TEXT NOT NULL, at INTEGER NOT NULL)")
    db.commit()
    return db


@contextmanager
def db_session():
    db = connect()
    try:
        with db:
            yield db
    finally:
        db.close()


def password_hash(password, salt):
    return hashlib.scrypt(password.encode("utf-8"), salt=salt, n=SCRYPT_N, r=8, p=1, dklen=32, maxmem=64 * 1024 * 1024)


def set_account(username):
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", username):
        raise ValueError("Username must be 1-64 ASCII letters, numbers, _, ., or -")
    password = getpass.getpass("New relay password (12+ characters): ")
    confirm = getpass.getpass("Confirm relay password: ")
    if password != confirm or not 12 <= len(password) <= 1024:
        raise ValueError("Passwords differ or length is outside 12-1024 characters")
    salt = secrets.token_bytes(32)
    digest = password_hash(password, salt)
    with db_session() as db:
        db.execute("INSERT OR REPLACE INTO account (id, username, salt, password_hash) VALUES (1, ?, ?, ?)", (username, salt, digest))
        db.execute("DELETE FROM sessions")
        db.execute("DELETE FROM failures")
    print("Account saved; all previous relay sessions revoked.")


class Handler(BaseHTTPRequestHandler):
    server_version = "CodexRelayAuth/1"

    def log_message(self, _format, *_args):
        # Do not log credentials, paths, bearer values, or session identifiers.
        pass

    def respond(self, status, payload=None):
        body = b"" if payload is None else json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def client_ip(self):
        # Caddy overwrites this header; this service accepts loopback clients only.
        return self.headers.get("X-Real-IP", self.client_address[0])[:128]

    def session_hash(self):
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            return None
        token = authorization[7:]
        if len(token) != 64 or any(char not in "0123456789abcdef" for char in token):
            return None
        return hashlib.sha256(token.encode("ascii")).digest()

    def authenticated(self, db):
        digest = self.session_hash()
        if digest is None:
            return False
        now = int(time.time())
        row = db.execute("SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?", (digest, now)).fetchone()
        return row is not None

    def do_GET(self):
        # Caddy forward_auth rewrites the path but can preserve the original
        # API query string. Route by the exact path only; query parameters are
        # never credentials and never change the Authorization-header check.
        path = self.path.partition("?")[0]
        if path == "/auth/status":
            with db_session() as db:
                configured = db.execute("SELECT 1 FROM account WHERE id=1").fetchone() is not None
            return self.respond(200, {"configured": configured})
        if path == "/auth/check":
            with db_session() as db:
                return self.respond(204 if self.authenticated(db) else 401)
        return self.respond(404, {"error": "Not found"})

    def do_POST(self):
        if self.path == "/auth/logout":
            with db_session() as db:
                digest = self.session_hash()
                if digest is not None:
                    db.execute("DELETE FROM sessions WHERE token_hash=?", (digest,))
            return self.respond(204)
        if self.path != "/auth/login":
            return self.respond(404, {"error": "Not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.respond(400, {"error": "Invalid request"})
        if not 1 <= length <= 4096:
            return self.respond(400, {"error": "Invalid request"})
        try:
            body = json.loads(self.rfile.read(length))
            username, password = body.get("username"), body.get("password")
        except (ValueError, AttributeError, TypeError):
            return self.respond(400, {"error": "Invalid request"})
        if not isinstance(username, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", username) or not isinstance(password, str) or len(password) > 1024:
            return self.respond(400, {"error": "Invalid request"})
        now = int(time.time())
        ip = self.client_ip()
        if not HASH_SLOTS.acquire(blocking=False):
            return self.respond(503, {"error": "Login is busy; try again shortly"})
        try:
            self.login_with_hash(ip, now, username, password)
        finally:
            HASH_SLOTS.release()

    def login_with_hash(self, ip, now, username, password):
        with db_session() as db:
            db.execute("DELETE FROM failures WHERE at<?", (now - FAILURE_WINDOW,))
            failures = db.execute("SELECT COUNT(*) FROM failures WHERE ip=?", (ip,)).fetchone()[0]
            if failures >= MAX_FAILURES:
                return self.respond(429, {"error": "Too many attempts; try again later"})
            row = db.execute("SELECT username, salt, password_hash FROM account WHERE id=1").fetchone()
            # Always compute a slow hash, including for unknown user or unconfigured service.
            salt = row[1] if row else bytes(32)
            candidate = password_hash(password, salt)
            if row is None or not hmac.compare_digest(username, row[0]) or not hmac.compare_digest(candidate, row[2]):
                db.execute("INSERT INTO failures (ip, at) VALUES (?, ?)", (ip, now))
                return self.respond(401, {"error": "Invalid username or password"})
            db.execute("DELETE FROM failures WHERE ip=?", (ip,))
            db.execute("DELETE FROM sessions WHERE expires_at<=?", (now,))
            token = secrets.token_hex(32)
            db.execute("INSERT INTO sessions (token_hash, expires_at) VALUES (?, ?)", (hashlib.sha256(token.encode("ascii")).digest(), now + SESSION_SECONDS))
        return self.respond(200, {"token": token, "expiresAt": now + SESSION_SECONDS})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["serve", "set-account"])
    parser.add_argument("--username", default="admin")
    args = parser.parse_args()
    if args.command == "set-account":
        set_account(args.username)
    else:
        with db_session():
            pass
        server = ThreadingHTTPServer((HOST, PORT), Handler)
        print(f"Relay identity listening on {HOST}:{PORT}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        print(error, file=sys.stderr)
        sys.exit(2)
