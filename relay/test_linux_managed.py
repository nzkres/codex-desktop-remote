"""Linux-only synthetic managed child; no FRP, public service or real account."""

import http.client
import json
import os
from pathlib import Path
import select
import socket
import subprocess
import sys
import tempfile
import unittest

from auth import auth_server
from registry import Registry


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux owner-only home fixture")
class LinuxManagedTests(unittest.TestCase):
    def test_quarantined_local_child_protocol(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            data = home / "data"
            data.mkdir(mode=0o700)
            registry_path = data / "registry.sqlite3"
            registry = Registry(registry_path)
            with registry.transaction() as db:
                db.execute("PRAGMA user_version=1")
            registry_path.chmod(0o600)
            auth_path = data / "auth.sqlite3"
            original = auth_server.DB_PATH
            try:
                auth_server.DB_PATH = str(auth_path)
                with auth_server.db_session() as db:
                    db.execute("PRAGMA user_version=1")
            finally:
                auth_server.DB_PATH = original
            auth_path.chmod(0o600)
            token = "e" * 64
            (home / "control.token").write_text(token, encoding="ascii")
            (home / "control.token").chmod(0o600)

            ports = []
            for _ in range(3):
                with socket.socket() as probe:
                    probe.bind(("127.0.0.1", 0))
                    ports.append(probe.getsockname()[1])
            config = {"schema": 1, "authPort": ports[0], "apiPort": ports[1],
                      "pluginPort": ports[2], "portStart": 32000, "portEnd": 32010,
                      "transportConfig": None}
            (home / "config.json").write_text(json.dumps(config), encoding="utf-8")
            (home / "config.json").chmod(0o600)
            command = [sys.executable, "-B", str(Path(__file__).with_name("managed.py")),
                       "--home", str(home), "--quarantine"]
            process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.DEVNULL, cwd=Path(__file__).resolve().parents[1],
                                       env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
            self.addCleanup(lambda: process.poll() is None and (process.terminate(), process.wait(timeout=5)))

            def reply(message=None):
                if message is not None:
                    process.stdin.write((json.dumps({"schema": 1, "token": token, "op": message}) + "\n").encode())
                    process.stdin.flush()
                self.assertTrue(select.select([process.stdout], [], [], 8)[0], "managed response timeout")
                return json.loads(process.stdout.readline())

            ready = reply()
            self.assertEqual(ready["ready"]["pid"], process.pid)
            self.assertTrue(ready["ready"]["gate"]["closed"])
            self.assertTrue((home / "ready.json").is_file())
            connection = http.client.HTTPConnection("127.0.0.1", ports[1], timeout=3)
            try:
                connection.request("GET", "/control-api/version")
                response = connection.getresponse()
                self.assertEqual(response.status, 503)
                response.read()
            finally:
                connection.close()
            self.assertTrue(reply("verify_recovery")["result"]["gate"]["closed"])
            before = reply("snapshot")["result"]
            self.assertFalse(reply("activate")["result"]["gate"]["closed"])
            self.assertTrue(reply("prepare")["result"]["closed"])
            self.assertEqual(reply("snapshot")["result"], before)
            self.assertEqual(reply("stop"), {"schema": 1, "stopped": True})
            self.assertEqual(process.wait(timeout=8), 0)
            self.assertFalse((home / "ready.json").exists())
