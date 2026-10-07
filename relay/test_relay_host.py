import json
import os
from pathlib import Path
import socket
import stat
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

from updates.security import UpdateError
from updates.relay_host import RelayHost, command, request


class HostCommandTests(unittest.TestCase):
    def test_exact_local_verbs_only(self):
        for op in ("check", "stage", "apply", "status", "stop"):
            self.assertEqual(command(json.dumps({"schema": 1, "op": op}).encode()), op)
        for value in ({"schema": 1, "op": "apply", "pid": 123},
                      {"schema": 1, "op": "apply", "url": "https://example.invalid"},
                      {"schema": True, "op": "apply"},
                      {"schema": 1, "op": "kill"},
                      ["apply"]):
            with self.assertRaisesRegex(UpdateError, "invalid_host_command"):
                command(json.dumps(value).encode())
        with self.assertRaisesRegex(UpdateError, "invalid_host_command"):
            command(b'{"schema":1,"op":"status","op":"apply"}')

    def test_apply_adopts_only_confirmed_transaction_child(self):
        host = object.__new__(RelayHost)
        host.home = Path("/synthetic/home")
        host.slot = Path("/synthetic/old")
        old = type("Child", (), {"poll": lambda self: None})()
        new = object()
        host.child = old
        driver = type("Driver", (), {"child": new})()
        host._driver = lambda: driver
        with patch("updates.relay_host.Transaction") as transaction, \
             patch("updates.relay_host.installed", return_value=({}, {}, Path("/synthetic/new"))):
            transaction.return_value.apply.return_value = {
                "phase": "committed", "candidate": {"version": "2.0"}}
            self.assertEqual(host._apply(), {"phase": "committed", "version": "2.0"})
            self.assertIs(host.child, new)
            self.assertEqual(host.slot, Path("/synthetic/new"))

        host.child, host.slot = old, Path("/synthetic/old")
        host._phase = lambda: "needs_review"
        with patch("updates.relay_host.Transaction") as transaction:
            transaction.return_value.apply.side_effect = UpdateError("update_needs_review")
            with self.assertRaisesRegex(UpdateError, "update_needs_review"):
                host._apply()
            self.assertIs(host.child, old)

        host._phase = lambda: "rolled_back"
        with patch("updates.relay_host.Transaction") as transaction, \
             patch("updates.relay_host.installed", return_value=({}, {}, Path("/synthetic/old"))):
            transaction.return_value.apply.side_effect = UpdateError("update_rolled_back")
            with self.assertRaisesRegex(UpdateError, "update_rolled_back"):
                host._apply()
            self.assertIs(host.child, new)
            self.assertEqual(host.slot, Path("/synthetic/old"))


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux Unix socket contract")
class HostSocketTests(unittest.TestCase):
    def test_owner_only_socket_and_no_external_pid(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            home.chmod(0o700)
            host = object.__new__(RelayHost)
            host.home = home
            host.stopping = False
            seen = []

            def dispatch(op):
                seen.append(op)
                if op == "stop":
                    host.stopping = True
                return {"op": op}

            host.dispatch = dispatch
            thread = threading.Thread(target=host.serve)
            thread.start()
            path = home / "update.sock"
            deadline = time.monotonic() + 5
            while not path.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(path.exists())
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(home.stat().st_mode), 0o700)

            def wire_request(value):
                with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
                    connection.connect(str(path))
                    connection.sendall(json.dumps(value).encode() + b"\n")
                    return json.loads(connection.recv(4096))

            try:
                rejected = wire_request({"schema": 1, "op": "stop", "pid": os.getpid()})
                self.assertEqual(rejected["error"], "invalid_host_command")
                self.assertEqual(seen, [])
                self.assertEqual(wire_request({"schema": 1, "op": "status"})["result"], {"op": "status"})
                self.assertEqual(request(home, "status"), {"op": "status"})
                with self.assertRaisesRegex(UpdateError, "invalid_host_command"):
                    request(home, "kill")
                self.assertEqual(wire_request({"schema": 1, "op": "stop"})["result"], {"op": "stop"})
            finally:
                host.stopping = True
                if thread.is_alive():
                    try:
                        wire_request({"schema": 1, "op": "status"})
                    except OSError:
                        pass
                thread.join(timeout=5)
            self.assertFalse(thread.is_alive())
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
