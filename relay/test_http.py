import hashlib
import http.client
import importlib.util
import json
import secrets
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from pathlib import Path

from control_server import RelayAccount, make_server
from release_notes import load_catalog
from registry import Registry


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.registry = Registry(Path(self.temp.name) / "terminals.db")
        # Use the real legacy auth implementation with an isolated synthetic DB.
        spec = importlib.util.spec_from_file_location("isolated_auth", Path(__file__).resolve().parent / "auth" / "auth_server.py")
        self.auth = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.auth)
        self.auth.DB_PATH = str(Path(self.temp.name) / "auth.db")
        self.session = secrets.token_hex(32)
        with self.auth.db_session() as db:
            db.execute("INSERT INTO sessions VALUES (?,?)", (hashlib.sha256(self.session.encode()).digest(), int(time.time()) + 1000))
        self.auth_server = self.auth.ThreadingHTTPServer(("127.0.0.1", 0), self.auth.Handler)
        self.api = make_server(self.registry, account=RelayAccount(self.auth_server.server_port))
        self.plugin = make_server(self.registry, plugin=True)
        for server in (self.auth_server, self.api, self.plugin):
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            self.addCleanup(self.stop, server, thread)

    @staticmethod
    def stop(server, thread):
        server.shutdown()
        server.server_close()
        thread.join(timeout=3)

    def request(self, method, path, body=None, token=None, server=None, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", (server or self.api).server_port, timeout=5)
        request_headers = {"Content-Type": "application/json", **(headers or {})}
        if token is not None:
            request_headers["Authorization"] = "Bearer " + token
        try:
            connection.request(method, path, body=None if body is None else json.dumps(body), headers=request_headers)
            response = connection.getresponse()
            raw = response.read()
            self.assertEqual(response.getheader("Cache-Control"), "no-store")
            return response.status, json.loads(raw) if raw else None
        finally:
            connection.close()

    def test_complete_http_lifecycle_with_real_account_check(self):
        token = secrets.token_hex(32)
        status, pair = self.request("POST", "/control-api/pairings", {}, self.session)
        self.assertEqual(status, 201)
        self.assertEqual(self.request("POST", "/connector-api/claim", {"code": pair["code"], "name": "Home", "platform": "windows"}, token)[0], 200)
        status, pending = self.request("GET", "/control-api/pairings", token=self.session)
        self.assertEqual(pending["pairings"][0]["name"], "Home")
        status, terminal = self.request("POST", f"/control-api/pairings/{pair['id']}/confirm", {"name": "Home"}, self.session)
        self.assertEqual(status, 200)
        status, confirmed = self.request("POST", f"/connector-api/pairings/{pair['id']}", {}, token)
        self.assertEqual(confirmed["terminalId"], terminal["id"])
        path = f"/connector-api/terminals/{terminal['id']}/heartbeat"
        self.assertEqual(self.request("POST", path, {"executor": "ready", "version": "test"}, token)[0], 204)
        _, listing = self.request("GET", "/control-api/terminals", token=self.session)
        self.assertTrue(listing["terminals"][0]["online"])
        self.assertEqual(self.request("POST", f"/control-api/terminals/{terminal['id']}/pause", {}, self.session)[0], 204)
        # A malformed heartbeat fails validation first; authorization probes must
        # use a valid body to actually exercise the paused-device boundary.
        self.assertEqual(self.request("POST", path, {}, token)[0], 400)
        self.assertEqual(self.request("POST", path, {"executor": "unknown", "version": "test"}, token)[0], 403)
        self.assertEqual(self.request("POST", f"/control-api/terminals/{terminal['id']}/resume", {}, self.session)[0], 204)
        self.assertEqual(self.request("POST", path, {"executor": "unknown", "version": "test"}, token)[0], 204)
        self.assertEqual(self.request("POST", f"/control-api/terminals/{terminal['id']}/remove", {}, self.session)[0], 204)
        self.assertEqual(self.request("POST", path, {"executor": "ready", "version": "test"}, token)[0], 403)

    def test_version_history_requires_account_and_is_not_on_plugin_listener(self):
        self.assertEqual(self.request("GET", "/control-api/version")[0], 401)
        self.assertEqual(self.request("GET", "/control-api/version", token=secrets.token_hex(32))[0], 401)
        status, body = self.request("GET", "/control-api/version", token=self.session)
        self.assertEqual(status, 200)
        self.assertEqual(body, load_catalog())
        self.assertEqual(body["component"], "relay")
        self.assertEqual(body["version"], body["releases"][0]["version"])
        self.assertEqual(self.request("GET", "/control-api/version", token=self.session, server=self.plugin)[0], 404)

    def test_version_is_frozen_for_the_running_process(self):
        original = load_catalog()
        with patch("control_server.load_catalog", side_effect=AssertionError("must not reread")):
            status, body = self.request("GET", "/control-api/version", token=self.session)
        self.assertEqual(status, 200)
        self.assertEqual(body, original)

    def test_no_header_or_query_identity_bypass_and_logout_revokes(self):
        for headers in ({}, {"X-Account-Id": "relay-owner"}, {"X-Forwarded-User": "relay-owner"}):
            self.assertEqual(self.request("GET", "/control-api/terminals?token=" + self.session, headers=headers)[0], 401)
        with self.auth.db_session() as db:
            db.execute("DELETE FROM sessions")
        self.assertEqual(self.request("GET", "/control-api/terminals", token=self.session)[0], 401)

    def test_plugin_is_on_separate_private_listener(self):
        path = "/internal/frp?version=0.1.0&op=Login"
        self.assertEqual(self.request("POST", path, {"content": {}})[0], 404)
        status, result = self.request("POST", path, {"content": {}}, server=self.plugin)
        self.assertEqual(status, 200)
        self.assertTrue(result["reject"])
        self.assertEqual(self.request("GET", "/control-api/terminals", token=self.session, server=self.plugin)[0], 404)

    def test_bad_bodies_and_device_token_cannot_manage_account(self):
        self.assertEqual(self.request("POST", "/control-api/pairings", [], self.session)[0], 400)
        self.assertEqual(self.request("POST", "/control-api/pairings", {}, self.session, headers={"Content-Type": "text/plain"})[0], 415)
        self.assertEqual(self.request("POST", "/control-api/pairings", {"padding": "x" * 17000}, self.session)[0], 413)
        self.assertEqual(self.request("GET", "/control-api/terminals", token=secrets.token_hex(32))[0], 401)

    def test_auth_outage_fails_closed(self):
        self.auth_server.shutdown()
        self.auth_server.server_close()
        self.assertEqual(self.request("GET", "/control-api/terminals", token=self.session)[0], 503)


if __name__ == "__main__":
    unittest.main()
