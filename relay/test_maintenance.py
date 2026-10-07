import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import secrets
import tempfile
import threading
import unittest

from auth import auth_server
from control_server import make_server
from maintenance import (MaintenanceBusy, MaintenanceClosed, MaintenanceGate,
                         critical_digest, database_digest, inspect_registry, probe_update_status)
from managed import make_managed_auth_handler
from registry import Registry


class MaintenanceTests(unittest.TestCase):
    def test_prepare_refuses_inflight_without_terminating_it(self):
        gate = MaintenanceGate()
        entered = threading.Event()
        release = threading.Event()
        def request():
            with gate.admit():
                entered.set()
                release.wait(2)
        worker = threading.Thread(target=request)
        worker.start()
        self.assertTrue(entered.wait(1))
        with self.assertRaises(MaintenanceBusy):
            gate.prepare(lambda: None)
        self.assertFalse(gate.snapshot()["closed"])
        self.assertEqual(gate.snapshot()["active"], 1)
        release.set()
        worker.join(2)
        self.assertFalse(worker.is_alive())
        self.assertEqual(gate.prepare(lambda: None)["active"], 0)
        with self.assertRaises(MaintenanceClosed):
            with gate.admit():
                pass
        gate.abort()
        with gate.admit():
            pass

    def test_failed_preflight_reopens_but_uncertain_is_sticky(self):
        gate = MaintenanceGate()
        with self.assertRaisesRegex(MaintenanceBusy, "pending"):
            gate.prepare(lambda: (_ for _ in ()).throw(MaintenanceBusy("pending")))
        self.assertFalse(gate.snapshot()["closed"])
        gate.mark_uncertain()
        with self.assertRaises(MaintenanceBusy):
            gate.prepare(lambda: None)
        self.assertTrue(gate.snapshot()["uncertain"])

    def test_registry_read_only_schema_and_pending_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "registry.db"
            registry = Registry(path)
            with registry.transaction() as db:
                db.execute("PRAGMA user_version=1")
            first = inspect_registry(path)
            self.assertEqual(first, {"pendingPairings": 0, "activeDevices": []})
            registry.create_pairing("fixture")
            self.assertEqual(inspect_registry(path)["pendingPairings"], 1)
            self.assertEqual(database_digest(path), database_digest(path))
            with registry.transaction() as db:
                db.execute("PRAGMA user_version=2")
            with self.assertRaisesRegex(MaintenanceBusy, "schema"):
                inspect_registry(path)

    def test_critical_digest_excludes_only_heartbeat_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "registry.db"
            registry = Registry(path)
            with registry.transaction() as db:
                db.execute("PRAGMA user_version=1")
            pair = registry.create_pairing("owner")
            token = secrets.token_hex(32)
            registry.claim(pair["code"], token, "fixture", "windows", "loopback")
            device = registry.confirm("owner", pair["id"], "fixture")
            before = critical_digest(path, "registry")
            registry.heartbeat(device["id"], token, "ready", "1.0")
            self.assertEqual(critical_digest(path, "registry"), before)
            registry.change("owner", device["id"], "pause")
            self.assertNotEqual(critical_digest(path, "registry"), before)

    def test_quarantine_allows_only_existing_connector_recovery(self):
        gate = MaintenanceGate(initially_closed=True, recovery=True)
        for kind in ("plugin_recovery", "connector_recovery"):
            with gate.admit(kind):
                pass
        with self.assertRaises(MaintenanceClosed):
            with gate.admit():
                pass
        gate.abort()
        with gate.admit():
            pass

    def test_adapter_status_must_be_authenticated_and_certain(self):
        token = secrets.token_hex(32)
        status = {"idle": True, "version": "fixture", "permissionsVerified": True,
                  "receiptsCertain": True}
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass
            def do_GET(self):
                value = status if self.path == "/api/connector/update-status" and self.headers.get("Authorization") == "Bearer " + token else {}
                body = json.dumps(value).encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever)
        thread.start()
        try:
            self.assertEqual(probe_update_status(server.server_port, token), {"version": "fixture"})
            status["receiptsCertain"] = False
            with self.assertRaises(MaintenanceBusy):
                probe_update_status(server.server_port, token)
            with self.assertRaises(MaintenanceBusy):
                probe_update_status(server.server_port, "0" * 64)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(2)

    def test_gate_covers_api_plugin_and_managed_auth(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = Registry(Path(directory) / "registry.db")
            auth_server.DB_PATH = str(Path(directory) / "auth.db")
            gate = MaintenanceGate()
            api = make_server(registry, account=lambda _: "owner", gate=gate)
            plugin = make_server(registry, plugin=True, gate=gate)
            auth = ThreadingHTTPServer(("127.0.0.1", 0), make_managed_auth_handler(gate))
            servers = (api, plugin, auth)
            threads = [threading.Thread(target=server.serve_forever) for server in servers]
            for thread in threads:
                thread.start()
            def request(server, method, path):
                connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=3)
                try:
                    connection.request(method, path)
                    response = connection.getresponse()
                    return response.status, response.read()
                finally:
                    connection.close()
            try:
                self.assertEqual(request(auth, "GET", "/auth/status")[0], 200)
                gate.prepare(lambda: None)
                self.assertEqual(request(api, "GET", "/control-api/version")[0], 503)
                status, body = request(plugin, "POST", "/internal/frp?version=0.1.0&op=Login")
                self.assertEqual(status, 200)
                self.assertTrue(json.loads(body)["reject"])
                self.assertEqual(request(auth, "GET", "/auth/status")[0], 503)
            finally:
                for server in servers:
                    server.shutdown()
                    server.server_close()
                for thread in threads:
                    thread.join(2)


if __name__ == "__main__":
    unittest.main()
