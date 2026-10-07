import json
import http.client
import secrets
import tempfile
import threading
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from control_server import make_server
from forwarder import Forwarder
from gateway_link import GatewayLinks
from registry import Registry


class LocalClient:
    """Synthetic loopback client; no Windows identity or external service."""

    def __init__(self, port):
        self.port = port

    def post(self, path, token, body):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            connection.request("POST", path, json.dumps(body),
                               {"Authorization": "Bearer " + token, "Content-Type": "application/json"})
            response = connection.getresponse()
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
        finally:
            connection.close()


class GatewayLinkTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.key, self.device_key, self.instance = secrets.token_hex(32), secrets.token_hex(32), secrets.token_hex(16)
        owner = self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass
            def do_GET(self):
                owner.requests.append(self.path)
                body = json.dumps({"instance": owner.instance, "healthy": True}).encode()
                self.send_response(200 if self.headers.get("Authorization") == "Bearer " + owner.key else 401)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        self.requests = []
        self.backend = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.start(self.backend)
        self.registry = Registry(Path(self.temp.name) / "registry.db", self.backend.server_port, self.backend.server_port)
        pair = self.registry.create_pairing("owner")
        self.registry.claim(pair["code"], self.device_key, "fixture", "windows", "loopback")
        self.terminal = self.registry.confirm("owner", pair["id"], "fixture")["id"]
        self.registry.heartbeat(self.terminal, self.device_key, "unknown", "synthetic")
        self.links = GatewayLinks(self.registry)
        self.account = lambda _: "owner"
        self.forwarder = Forwarder(self.registry, self.account, self.links.credential)
        self.addCleanup(self.forwarder.close)
        self.api = make_server(self.registry, account=self.account, gateway_links=self.links,
                               forwarder=self.forwarder)
        self.start(self.api)
        self.client = LocalClient(self.api.server_port)

    def start(self, server):
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        def stop():
            server.shutdown(); server.server_close(); thread.join()
        self.addCleanup(stop)

    def register(self, **values):
        return self.client.post(f"/connector-api/terminals/{self.terminal}/gateway", self.device_key,
                                {"token": self.key, "instance": self.instance, **values})

    def test_authenticated_callback_registers_only_in_memory(self):
        self.assertEqual(self.register()[0], 200)
        self.assertEqual(self.links.credential(self.terminal), self.key)
        self.assertNotIn(self.key.encode(), Path(self.registry.path).read_bytes())
        self.assertIsNone(GatewayLinks(self.registry).credential(self.terminal))
        self.assertEqual(self.register()[0], 200)
        self.assertEqual(self.requests, ["/api/connector/health"] * 2)

    def test_registration_enables_forwarding_only_for_the_assigned_device(self):
        path = f"/terminal-api/{self.terminal}/api/sessions"
        def read():
            connection = http.client.HTTPConnection("127.0.0.1", self.api.server_port, timeout=3)
            try:
                connection.request("GET", path, headers={"Authorization": "Bearer " + "a" * 64})
                response = connection.getresponse()
                return response.status, response.read()
            finally:
                connection.close()
        self.assertEqual(read()[0], 503)
        self.assertEqual(self.register()[0], 200)
        status, body = read()
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), {"instance": self.instance, "healthy": True})
        self.registry.change("owner", self.terminal, "pause")
        self.assertEqual(read()[0], 403)

    def test_bad_token_instance_and_cross_device_fail_closed(self):
        self.assertEqual(self.register(token="0" * 64)[0], 503)
        self.assertEqual(self.register(instance="0" * 32)[0], 503)
        self.assertIsNone(self.links.credential(self.terminal))
        self.assertEqual(self.register(token=self.device_key)[0], 400)
        before = len(self.requests)
        result, _ = self.client.post(f"/connector-api/terminals/{self.terminal}/gateway", "0" * 64,
                                    {"token": self.key, "instance": self.instance})
        self.assertEqual(result, 403)
        self.assertEqual(len(self.requests), before)

    def test_pause_resume_requires_new_probe_and_remove_denies(self):
        self.assertEqual(self.register()[0], 200)
        self.registry.change("owner", self.terminal, "pause")
        self.assertIsNone(self.links.credential(self.terminal))
        self.assertEqual(self.register()[0], 403)
        self.registry.change("owner", self.terminal, "resume")
        self.assertIsNone(self.links.credential(self.terminal))
        self.assertEqual(self.register()[0], 200)
        self.registry.change("owner", self.terminal, "remove")
        self.assertIsNone(self.links.credential(self.terminal))
        self.assertEqual(self.register()[0], 403)

    def test_pause_resume_during_probe_rejects_stale_registration(self):
        original = self.registry.device
        calls = [0]
        def changed_epoch(*args):
            calls[0] += 1
            if calls[0] == 2:
                self.registry.change("owner", self.terminal, "pause")
                self.registry.change("owner", self.terminal, "resume")
            return original(*args)
        with patch.object(self.registry, "device", side_effect=changed_epoch):
            self.assertEqual(self.register()[0], 409)
        self.assertIsNone(self.links.credential(self.terminal))

    def test_probe_concurrency_is_bounded(self):
        for _ in range(4):
            self.links.probes.acquire()
        try:
            self.assertEqual(self.register()[0], 429)
            self.assertEqual(self.requests, [])
        finally:
            for _ in range(4):
                self.links.probes.release()
