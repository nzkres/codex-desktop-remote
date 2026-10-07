"""Initialize a new local managed relay home; never import an existing identity."""

import argparse
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import stat
import sys

from auth import auth_server
from managed import inspect_auth, read_home
from maintenance import inspect_registry
from registry import Registry


class ConfigureError(Exception):
    pass


def _private_directory(path):
    info = path.stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or info.st_mode & 0o077:
        raise ConfigureError("private_home_required")


def _exclusive(path, payload):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())


def configure(home, username, auth_port, api_port, plugin_port, port_start, port_end,
              *, password_provider=getpass.getpass):
    if os.name != "posix":
        raise ConfigureError("linux_managed_runtime_required")
    home = Path(home).absolute()
    if any(item.is_symlink() for item in (home, *home.parents)):
        raise ConfigureError("linked_home_rejected")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", username):
        raise ConfigureError("invalid_username")
    ports = (auth_port, api_port, plugin_port, port_start, port_end)
    if (any(type(value) is not int or not 1024 <= value <= 65535 for value in ports)
            or len(set(ports[:3])) != 3 or port_start > port_end
            or any(port_start <= port <= port_end for port in ports[:3])):
        raise ConfigureError("invalid_managed_ports")
    if any((home / name).exists() or (home / name).is_symlink()
           for name in ("config.json", "control.token", "ready.json", "update.sock", "configure.lock")):
        raise ConfigureError("managed_home_already_configured")
    data = home / "data"
    if any((data / name).exists() or (data / name).is_symlink()
           for name in ("auth.sqlite3", "registry.sqlite3")):
        raise ConfigureError("managed_identity_already_exists")
    password = password_provider("New relay password (12+ characters): ")
    confirm = password_provider("Confirm relay password: ")
    if not isinstance(password, str) or password != confirm or not 12 <= len(password) <= 1024:
        raise ConfigureError("invalid_password_confirmation")
    salt = secrets.token_bytes(32)
    password_digest = auth_server.password_hash(password, salt)
    del password, confirm

    old_mask = os.umask(0o077)
    try:
        home.mkdir(mode=0o700, exist_ok=True)
        _private_directory(home)
        lock_path = home / "configure.lock"
        _exclusive(lock_path, b"in_progress\n")
        if data.is_symlink():
            raise ConfigureError("linked_home_rejected")
        data.mkdir(mode=0o700, exist_ok=True)
        _private_directory(data)
        # Recheck after creating directories; a pre-existing identity is never
        # opened by sqlite3 or replaced, even if the caller retries.
        for name in ("auth.sqlite3", "registry.sqlite3"):
            if (data / name).exists() or (data / name).is_symlink():
                raise ConfigureError("managed_identity_already_exists")
        for name in ("config.json", "control.token"):
            if (home / name).exists() or (home / name).is_symlink():
                raise ConfigureError("managed_home_already_configured")
        registry_path = data / "registry.sqlite3"
        Registry(registry_path, port_start, port_end)
        with sqlite3.connect(registry_path) as db:
            db.execute("PRAGMA user_version=1")
        registry_path.chmod(0o600)

        auth_path = data / "auth.sqlite3"
        previous = auth_server.DB_PATH
        try:
            auth_server.DB_PATH = str(auth_path)
            with auth_server.db_session() as db:
                db.execute("INSERT INTO account(id,username,salt,password_hash) VALUES(1,?,?,?)",
                           (username, salt, password_digest))
                db.execute("PRAGMA user_version=1")
        finally:
            auth_server.DB_PATH = previous
        auth_path.chmod(0o600)
        inspect_auth(auth_path)
        inspect_registry(registry_path)

        config = {"schema": 1, "authPort": auth_port, "apiPort": api_port,
                  "pluginPort": plugin_port, "portStart": port_start, "portEnd": port_end,
                  "transportConfig": None}
        _exclusive(home / "config.json", json.dumps(config, separators=(",", ":")).encode())
        _exclusive(home / "control.token", (secrets.token_hex(32) + "\n").encode("ascii"))
        read_home(home)
        lock_path.unlink()
    finally:
        os.umask(old_mask)
    return {"configured": True, "home": str(home), "dataSchema": 1}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--auth-port", type=int, required=True)
    parser.add_argument("--api-port", type=int, required=True)
    parser.add_argument("--plugin-port", type=int, required=True)
    parser.add_argument("--port-start", type=int, required=True)
    parser.add_argument("--port-end", type=int, required=True)
    args = parser.parse_args()
    try:
        configure(args.home, args.username, args.auth_port, args.api_port,
                  args.plugin_port, args.port_start, args.port_end)
    except (ConfigureError, OSError, sqlite3.Error):
        print("managed_relay_configuration_refused", file=sys.stderr)
        raise SystemExit(2)
    print("managed_relay_home_configured")


if __name__ == "__main__":
    main()
