import http.client
import hashlib
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

from auth import auth_server
from control_server import RelayAccount, make_server
from maintenance import MaintenanceBusy, MaintenanceGate
from private_updates import PACKAGE_LIMIT, PrivateUpdates
from registry import ControlError, Registry


ACCOUNT = "a" * 64
DEVICE = "d" * 64
PREFIX = "/desktop-remote-updates/test/"


class PrivateUpdateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.data = self.root / "data"
        self.data.mkdir(mode=0o700)
        self.registry = Registry(self.root / "registry.sqlite3")
        pairing = self.registry.create_pairing("relay-owner")
        self.registry.claim(pairing["code"], DEVICE, "Synthetic Windows", "windows", "127.0.0.1")
        self.terminal = self.registry.confirm("relay-owner", pairing["id"], "Synthetic Windows")["id"]

        def account(authorization):
            if authorization != "Bearer " + ACCOUNT:
                raise ControlError(401, "login_required")
            return "relay-owner"

        self.account = account
        self.feed = PrivateUpdates(self.data, self.registry, self.account)
        self.gate = MaintenanceGate()
        self.server = make_server(self.registry, 0, account=self.account, gate=self.gate,
                                  private_updates=self.feed)
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.start()
        self.addCleanup(self._stop)

    def _stop(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    def put(self, component, name, content, channel="test"):
        directory = self.data / "update-feed" / channel / component
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        for ancestor in (self.data / "update-feed", self.data / "update-feed" / channel, directory):
            ancestor.chmod(0o700)
        path = directory / name
        path.write_bytes(content)
        path.chmod(0o600)
        return path

    def get(self, path, headers=None, *, method="GET", port=None):
        connection = http.client.HTTPConnection("127.0.0.1", port or self.server.server_port, timeout=5)
        try:
            connection.request(method, path, headers=headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def account_headers(self, token=ACCOUNT):
        return {"Authorization": "Bearer " + token,
                "X-Desktop-Remote-Principal": "account"}

    def device_headers(self, token=DEVICE, terminal=None):
        return {"Authorization": "Bearer " + token,
                "X-Desktop-Remote-Principal": "device",
                "X-Desktop-Remote-Device": terminal or self.terminal}

    def test_account_and_device_each_authenticate_manifest_and_package(self):
        for component, extension in (("android", "apk"), ("windows", "zip"), ("relay", "zip")):
            with self.subTest(component=component):
                manifest = b'{"schema":1,"synthetic":true}'
                package = b"synthetic opaque package bytes"
                filename = f"{component}-0.1.0-alpha.4.{extension}"
                self.put(component, "latest.json", manifest)
                self.put(component, filename, package)
                for name, expected in (("latest.json", manifest), (filename, package)):
                    status, headers, body = self.get(PREFIX + component + "/" + name,
                                                     self.account_headers())
                    self.assertEqual((status, body), (200, expected))
                    self.assertEqual(headers["Content-Length"], str(len(expected)))
                    self.assertEqual(headers["Cache-Control"], "private, no-store")
                    self.assertEqual(headers["X-Content-Type-Options"], "nosniff")
                    self.assertEqual(headers["Content-Type"],
                                     "application/json" if name == "latest.json" else
                                     "application/vnd.android.package-archive" if component == "android" else
                                     "application/zip")
                    device_status = self.get(PREFIX + component + "/" + name,
                                             self.device_headers())[0]
                    self.assertEqual(device_status, 200 if component == "windows" else 403)

    def test_missing_expired_wrong_and_revoked_identity_never_falls_back(self):
        self.put("windows", "latest.json", b"{}")
        path = PREFIX + "windows/latest.json"
        self.assertEqual(self.get(path)[0], 401)
        self.assertEqual(self.get(path, self.account_headers("f" * 64))[0], 401)
        self.assertEqual(self.get(path, self.device_headers("f" * 64))[0], 403)
        self.assertEqual(self.get(path, {"Authorization": "Bearer " + ACCOUNT})[0], 401)
        self.assertEqual(self.get(path, {**self.account_headers(),
                                         "X-Desktop-Remote-Device": self.terminal})[0], 401)
        self.assertEqual(self.get(path, {"Authorization": "Bearer " + ACCOUNT,
                                         "X-Desktop-Remote-Principal": "device",
                                         "X-Desktop-Remote-Device": self.terminal})[0], 403)
        self.assertEqual(self.get(path, self.device_headers(terminal="f" * 32))[0], 403)
        self.feed.account = lambda _: (_ for _ in ()).throw(ControlError(401, "login_required"))
        self.assertEqual(self.get(path, self.account_headers())[0], 401)  # Expired session.
        self.feed.account = self.account
        self.registry.change("relay-owner", self.terminal, "pause")
        self.assertEqual(self.get(path, self.device_headers())[0], 403)
        self.registry.change("relay-owner", self.terminal, "resume")
        self.assertEqual(self.get(path, self.device_headers())[0], 200)
        self.registry.change("relay-owner", self.terminal, "remove")
        self.assertEqual(self.get(path, self.device_headers())[0], 403)

    def test_strict_paths_missing_feed_limits_and_plugin_isolation(self):
        valid = PREFIX + "windows/latest.json"
        self.assertEqual(self.get(valid, self.account_headers())[0], 404)
        self.put("windows", "latest.json", b"{}")
        self.put("windows", "secret.db", b"synthetic db")
        for path in (valid + "?x=1", valid.replace("latest", "%6catest"),
                     PREFIX + "windows/../relay/latest.json",
                     PREFIX + "windows/secret.db", PREFIX + "windows/",
                     PREFIX + "windows/windows-0.1.0.apk"):
            with self.subTest(path=path):
                self.assertEqual(self.get(path, self.account_headers())[0], 404)
        self.assertEqual(self.get(valid, self.account_headers(), method="HEAD")[0], 405)
        self.put("windows", "latest.json", b"x" * (64 * 1024 + 1))
        self.assertEqual(self.get(valid, self.account_headers())[0], 404)
        package = self.put("windows", "windows-0.1.0.zip", b"synthetic")
        with package.open("r+b") as stream:
            stream.truncate(PACKAGE_LIMIT + 1)
        self.assertEqual(self.get(PREFIX + "windows/" + package.name,
                                  self.account_headers())[0], 404)
        if os.name == "posix":
            (self.data / "update-feed" / "test" / "windows" / "latest.json").chmod(0o644)
            self.assertEqual(self.get(valid, self.account_headers())[0], 404)
        plugin = make_server(self.registry, 0, plugin=True)
        thread = threading.Thread(target=plugin.serve_forever)
        thread.start()
        try:
            self.assertEqual(self.get(valid, self.account_headers(), port=plugin.server_port)[0], 404)
        finally:
            plugin.shutdown()
            plugin.server_close()
            thread.join(timeout=5)

    def test_symlink_refused_and_download_holds_maintenance_lease(self):
        path = self.put("windows", "windows-0.1.0.zip", b"synthetic package")
        link = path.with_name("windows-0.1.1.zip")
        try:
            link.symlink_to(path)
        except (OSError, NotImplementedError):
            pass
        else:
            self.assertEqual(self.get(PREFIX + "windows/" + link.name,
                                      self.account_headers())[0], 404)
        hardlink = path.with_name("windows-0.1.2.zip")
        try:
            os.link(path, hardlink)
        except OSError:
            pass
        else:
            self.assertEqual(self.get(PREFIX + "windows/" + hardlink.name,
                                      self.account_headers())[0], 404)
            hardlink.unlink()
        stable_link = self.data / "update-feed" / "stable"
        try:
            stable_link.symlink_to(self.data / "update-feed" / "test", target_is_directory=True)
        except (OSError, NotImplementedError):
            pass
        else:
            self.assertEqual(self.get("/desktop-remote-updates/stable/windows/" + path.name,
                                      self.account_headers())[0], 404)

        entered, resume, finished = threading.Event(), threading.Event(), threading.Event()
        original_stream = self.feed._stream

        class ObservedHandler(self.server.RequestHandlerClass):
            def do_GET(handler):
                try:
                    super().do_GET()
                finally:
                    # Receiving the final body byte does not prove the server
                    # has left gate.admit(). Observe the actual handler return.
                    finished.set()

        self.server.RequestHandlerClass = ObservedHandler

        def held_stream(handler, stream, length):
            entered.set()
            resume.wait(timeout=5)
            return original_stream(handler, stream, length)

        self.feed._stream = held_stream
        result = []
        worker = threading.Thread(target=lambda: result.append(self.get(
            PREFIX + "windows/" + path.name, self.account_headers())))
        worker.start()
        try:
            self.assertTrue(entered.wait(timeout=5))
            with self.assertRaises(MaintenanceBusy):
                self.gate.prepare(lambda: None)
        finally:
            resume.set()
            worker.join(timeout=5)
        self.assertEqual(result[0][0], 200)
        self.assertTrue(finished.wait(timeout=5), 'download handler did not release admission')
        self.assertTrue(self.gate.prepare(lambda: None)["closed"])
        self.assertEqual(self.get(PREFIX + "windows/" + path.name,
                                  self.account_headers())[0], 503)

    def test_real_auth_check_session_expiry_and_logout_on_both_files(self):
        self.put("android", "latest.json", b"{}")
        self.put("android", "android-0.1.0.apk", b"synthetic apk")
        auth_path = self.root / "auth.sqlite3"
        with patch.object(auth_server, "DB_PATH", str(auth_path)):
            with auth_server.db_session() as db:
                db.execute("INSERT INTO sessions(token_hash,expires_at) VALUES(?,?)",
                           (hashlib.sha256(ACCOUNT.encode("ascii")).digest(), int(time.time()) + 3600))
            identity = auth_server.ThreadingHTTPServer(("127.0.0.1", 0), auth_server.Handler)
            identity_thread = threading.Thread(target=identity.serve_forever)
            identity_thread.start()
            account = RelayAccount(identity.server_port)
            feed = PrivateUpdates(self.data, self.registry, account)
            server = make_server(self.registry, 0, account=account, private_updates=feed)
            server_thread = threading.Thread(target=server.serve_forever)
            server_thread.start()
            try:
                paths = (PREFIX + "android/latest.json", PREFIX + "android/android-0.1.0.apk")
                for path in paths:
                    self.assertEqual(self.get(path, self.account_headers(), port=server.server_port)[0], 200)
                with auth_server.db_session() as db:
                    db.execute("UPDATE sessions SET expires_at=?", (int(time.time()) - 1,))
                for path in paths:
                    self.assertEqual(self.get(path, self.account_headers(), port=server.server_port)[0], 401)
                with auth_server.db_session() as db:
                    db.execute("UPDATE sessions SET expires_at=?", (int(time.time()) + 3600,))
                connection = http.client.HTTPConnection("127.0.0.1", identity.server_port, timeout=5)
                try:
                    connection.request("POST", "/auth/logout", headers={"Authorization": "Bearer " + ACCOUNT})
                    response = connection.getresponse()
                    self.assertEqual(response.status, 204)
                    response.read()
                finally:
                    connection.close()
                for path in paths:
                    self.assertEqual(self.get(path, self.account_headers(), port=server.server_port)[0], 401)
            finally:
                server.shutdown()
                server.server_close()
                server_thread.join(timeout=5)
                identity.shutdown()
                identity.server_close()
                identity_thread.join(timeout=5)
