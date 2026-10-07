import concurrent.futures
import http.client
import json
import secrets
import socket
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from control_server import make_server
from forwarder import Forwarder, target_path
from registry import ControlError, Registry


class ForwarderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.registry = Registry(Path(self.temp.name) / "devices.db", 1024, 65535)
        self.sessions = {secrets.token_hex(32): "alice", secrets.token_hex(32): "bob"}
        self.alice, self.bob = self.sessions.keys()
        self.credentials = {}
        self.requests = []
        self.release = threading.Event()
        self.backend_closed = threading.Event()
        self.accepted = threading.Event()
        self.account_down = False
        def account(value):
            if self.account_down:
                raise ControlError(503, "identity_unavailable")
            owner = self.sessions.get(value.removeprefix("Bearer "))
            if owner is None:
                raise ControlError(401, "login_required")
            return owner
        self.account = account
        self.forwarder = Forwarder(self.registry, self.account, self.credentials.get,
                                   io_timeout=1, total_timeout=5, poll_interval=.05)
        self.server = make_server(self.registry, account=self.account, forwarder=self.forwarder)
        self.start(self.server)
        self.addCleanup(self.forwarder.close)
        self.addCleanup(self.release.set)

    def start(self, server):
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        def stop():
            self.release.set()
            self.forwarder.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)
        self.addCleanup(stop)

    def device(self, name="A", owner="alice"):
        test = self
        gateway_token = secrets.token_hex(32)
        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            def log_message(self, *_args):
                pass
            def do_GET(self):
                self.run_request()
            def do_POST(self):
                self.run_request()
            def do_HEAD(self):
                self.run_request()
            def run_request(self):
                self.connection.settimeout(3)
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                test.requests.append({"device": name, "method": self.command, "path": self.path, "body": body, "headers": dict(self.headers)})
                if self.headers.get("Authorization") != "Bearer " + gateway_token:
                    self.send_error(401)
                    return
                test.accepted.set()
                try:
                    if self.path.endswith("/drop"):
                        self.close_connection = True
                        return
                    if self.path.endswith("/slow"):
                        test.release.wait(2)
                    if self.path.endswith("/redirect"):
                        self.send_response(302)
                        self.send_header("Location", "http://127.0.0.1:8787/api/sessions")
                        self.send_header("Content-Length", "0")
                        self.end_headers()
                        return
                    if self.path.endswith("/ambiguous"):
                        self.send_response(200)
                        self.send_header("Content-Length", "1")
                        self.send_header("Content-Length", "2")
                        self.end_headers()
                        return
                    if self.path.endswith("/truncate"):
                        self.send_response(200)
                        self.send_header("Content-Length", "100")
                        self.end_headers()
                        self.wfile.write(b"short")
                        self.close_connection = True
                        return
                    if self.path.endswith(("/stream", "/silent", "/ticks")):
                        self.send_response(200)
                        self.send_header("Content-Type", "text/event-stream")
                        self.send_header("Transfer-Encoding", "chunked")
                        self.end_headers()
                        self.wfile.write(b"6\r\nfirst\n\r\n")
                        if self.path.endswith("/silent"):
                            if self.rfile.read(1) == b"":
                                test.backend_closed.set()
                            return
                        if self.path.endswith("/ticks"):
                            for _ in range(50):
                                if test.release.wait(.05):
                                    break
                                self.wfile.write(b"1\r\nx\r\n")
                        else:
                            test.release.wait(2)
                        self.wfile.write(b"5\r\nlast\n\r\n0\r\n\r\n")
                        return
                    status = 409 if self.path.endswith("/conflict") else 200
                    payload = json.dumps({"device": name, "path": self.path}).encode()
                    self.send_response(status)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(payload)))
                    self.send_header("Set-Cookie", "backend-secret=hidden")
                    self.send_header("X-Private-Token", gateway_token)
                    self.end_headers()
                    if self.command != "HEAD":
                        self.wfile.write(payload)
                except OSError:
                    test.backend_closed.set()
        backend = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.start(backend)
        pair = self.registry.create_pairing(owner)
        token = secrets.token_hex(32)
        self.registry.claim(pair["code"], token, name, "windows", "fixture")
        device = self.registry.confirm(owner, pair["id"], name)
        # Synthetic fixture: stand in for the assigned FRP loopback listener.
        with self.registry.transaction() as db:
            db.execute("UPDATE terminals SET port=? WHERE id=?", (backend.server_port, device["id"]))
        self.registry.heartbeat(device["id"], token, "ready", "test")
        self.credentials[device["id"]] = gateway_token
        return device["id"], backend

    def test_update_status_is_get_only_and_stays_on_selected_device(self):
        device, _ = self.device()
        route = f"/terminal-api/{device}/api/connector/update-status"
        status, body, _ = self.request(route)
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)["path"], "/api/connector/update-status")
        self.assertEqual(self.request(route, method="POST", body=b"{}")[0], 405)
        self.assertEqual(self.request(route + "?token=anything")[0], 400)
        self.assertEqual(self.request(f"/terminal-api/{device}/api/connector/control")[0], 404)

    def request(self, path, method="GET", token=None, body=None, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        combined = {"Authorization": "Bearer " + (self.alice if token is None else token), "Content-Type": "application/json", **(headers or {})}
        try:
            conn.request(method, path, body=body, headers=combined)
            response = conn.getresponse()
            return response.status, response.read(), dict(response.getheaders())
        finally:
            conn.close()

    def stream(self, device, suffix="silent"):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        self.addCleanup(conn.close)
        conn.request("GET", f"/terminal-api/{device}/api/sessions/{suffix}", headers={"Authorization": "Bearer " + self.alice})
        response = conn.getresponse()
        self.addCleanup(response.close)
        self.assertEqual(response.status, 200)
        self.assertEqual(response.read(6), b"first\n")
        return conn, response

    def manage(self, device, action):
        return self.request(f"/control-api/terminals/{device}/{action}", "POST", body="{}")[0]

    def test_two_devices_routing_query_and_credential_boundary(self):
        a, _ = self.device("A")
        b, _ = self.device("B")
        for device, name in ((a, "A"), (b, "B")):
            status, raw, headers = self.request(f"/terminal-api/{device}/api/sessions?cursor=abc%2F123&older=1", headers={"Cookie": "phone-secret", "X-Forwarded-Host": "evil", "X-Terminal-Id": b})
            self.assertEqual(status, 200)
            self.assertEqual(json.loads(raw), {"device": name, "path": "/api/sessions?cursor=abc%2F123&older=1"})
            self.assertEqual(headers["X-Terminal-Id"], device)
            self.assertNotIn("Set-Cookie", headers)
            self.assertNotIn("X-Private-Token", headers)
            forwarded = self.requests[-1]["headers"]
            self.assertEqual(forwarded["Authorization"], "Bearer " + self.credentials[device])
            self.assertNotIn("Cookie", forwarded)
            self.assertNotIn("X-Forwarded-Host", forwarded)
            self.assertNotIn("X-Terminal-Id", forwarded)

    def test_no_access_for_wrong_account_missing_session_or_revoked_login(self):
        a, _ = self.device()
        path = f"/terminal-api/{a}/api/sessions"
        self.assertEqual(self.request(path, token=self.bob)[0], 404)
        self.assertEqual(self.request(path, token="invalid")[0], 401)
        del self.sessions[self.alice]
        self.assertEqual(self.request(path)[0], 401)
        self.assertEqual(self.requests, [])

    def test_offline_unreachable_missing_credentials_and_auth_outage(self):
        a, backend = self.device()
        path = f"/terminal-api/{a}/api/sessions"
        self.account_down = True
        self.assertEqual(json.loads(self.request(path)[1])["error"], "identity_unavailable")
        self.account_down = False
        self.credentials.clear()
        self.assertEqual(json.loads(self.request(path)[1])["error"], "terminal_credential_unavailable")
        self.credentials[a] = secrets.token_hex(32)
        backend.shutdown()
        backend.server_close()
        self.assertEqual(json.loads(self.request(path)[1])["error"], "terminal_unreachable")
        with self.registry.transaction() as db:
            db.execute("UPDATE terminals SET last_seen=0 WHERE id=?", (a,))
        self.assertEqual(json.loads(self.request(path)[1])["error"], "terminal_offline")

    def test_pause_aborts_existing_stream_and_preserves_other_device(self):
        a, _ = self.device("A")
        b, _ = self.device("B")
        _, response = self.stream(a)
        self.assertEqual(self.manage(a, "pause"), 204)
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        self.assertTrue(self.backend_closed.wait(1), "upstream was not disconnected")
        self.assertEqual(self.request(f"/terminal-api/{a}/api/sessions")[0], 403)
        self.assertEqual(self.request(f"/terminal-api/{b}/api/sessions")[0], 200)

    def test_remove_aborts_existing_stream_and_does_not_resume(self):
        a, _ = self.device()
        _, response = self.stream(a)
        self.assertEqual(self.manage(a, "remove"), 204)
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        self.assertTrue(self.backend_closed.wait(1))
        self.assertEqual(self.manage(a, "resume"), 404)

    def test_external_pause_resume_epoch_cannot_resurrect_old_stream(self):
        a, _ = self.device()
        _, response = self.stream(a)
        self.registry.change("alice", a, "pause")
        self.registry.change("alice", a, "resume")
        # Restore heartbeat directly as a fixture, to prove epoch rather than offline rejection.
        with self.registry.transaction() as db:
            db.execute("UPDATE terminals SET last_seen=? WHERE id=?", (self.registry.now(), a))
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        self.assertTrue(self.backend_closed.wait(1))

    def test_login_revocation_closes_live_stream(self):
        a, _ = self.device()
        _, response = self.stream(a)
        del self.sessions[self.alice]
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        self.assertTrue(self.backend_closed.wait(1))

    def test_phone_disconnect_cancels_silent_upstream(self):
        a, _ = self.device()
        conn, response = self.stream(a)
        # HTTPResponse may own the descriptor after Connection: close.
        sock = response.fp.raw._sock
        sock.shutdown(socket.SHUT_RDWR)
        response.close()
        conn.close()
        self.assertTrue(self.backend_closed.wait(1))

    def test_gateway_credential_failure_is_not_phone_login_failure(self):
        a, _ = self.device()
        self.credentials[a] = secrets.token_hex(32)
        status, raw, _ = self.request(f"/terminal-api/{a}/api/sessions")
        self.assertEqual(status, 502)
        self.assertEqual(json.loads(raw)["error"], "terminal_gateway_auth_failed")

    def test_stream_is_incremental_and_completes_normally(self):
        a, _ = self.device()
        _, response = self.stream(a, "stream")
        self.assertFalse(self.release.is_set())
        self.release.set()
        self.assertEqual(response.read(), b"last\n")

    def test_truncated_response_is_not_reported_as_success(self):
        a, _ = self.device()
        with self.assertRaises(http.client.IncompleteRead):
            self.request(f"/terminal-api/{a}/api/sessions/truncate")

    def test_sending_then_disconnect_is_unknown_and_never_retried(self):
        a, _ = self.device()
        body = '{"text":"hello\\nworld","clientMessageId":"stable"}'
        status, raw, _ = self.request(f"/terminal-api/{a}/api/sessions/drop", "POST", body=body)
        self.assertEqual(status, 502)
        self.assertEqual(json.loads(raw), {"error": "terminal_connection_lost", "retryable": False, "outcome": "unknown"})
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(self.requests[0]["body"], body.encode())

    def test_response_timeout_does_not_retry_post(self):
        a, _ = self.device()
        self.forwarder.io_timeout = .15
        status, raw, _ = self.request(f"/terminal-api/{a}/api/sessions/slow", "POST", body="{}")
        self.assertEqual(status, 504)
        self.assertEqual(json.loads(raw)["outcome"], "unknown")
        self.assertEqual(len(self.requests), 1)

    def test_total_deadline_interrupts_continuously_active_output(self):
        a, _ = self.device()
        self.forwarder.total_timeout = .2
        _, response = self.stream(a, "ticks")
        started = time.monotonic()
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        self.assertLess(time.monotonic() - started, 1)

    def test_request_framing_limits_and_route_escape(self):
        a, _ = self.device()
        path = f"/terminal-api/{a}/api/sessions"
        self.forwarder.max_body = 5
        self.assertEqual(self.request(path, "POST", body="123456")[0], 413)
        self.assertEqual(self.request(path, "POST", body="1", headers={"Content-Type": "text/plain"})[0], 415)
        self.assertEqual(self.request(path, headers={"Upgrade": "websocket"})[0], 400)
        for tail in ("/api/auth", "/api/events", "/api/android/update", "/api/sessions/../auth", "/api/sessions/%2e%2e/auth", "/api/sessions//x"):
            with self.assertRaises(ControlError):
                target_path(f"/terminal-api/{a}" + tail)
        self.assertEqual(self.requests, [])

    def test_duplicate_lengths_chunked_and_short_request_rejected_before_dispatch(self):
        a, _ = self.device()
        common = (f"POST /terminal-api/{a}/api/sessions HTTP/1.1\r\n"
                  f"Host: localhost\r\nAuthorization: Bearer {self.alice}\r\nContent-Type: application/json\r\n")
        for extra, data in (("Content-Length: 1\r\nContent-Length: 2\r\n", b"{}"),
                            ("Transfer-Encoding: chunked\r\n", b"0\r\n\r\n"),
                            ("Content-Length: 4\r\n", b"{}")):
            with socket.create_connection(("127.0.0.1", self.server.server_port), timeout=2) as sock:
                sock.sendall((common + extra + "\r\n").encode() + data)
                sock.shutdown(socket.SHUT_WR)
                response = http.client.HTTPResponse(sock)
                response.begin()
                self.assertEqual(response.status, 400)
                response.read()
                response.close()
        self.assertEqual(self.requests, [])

    def test_stalled_request_body_has_deadline_without_dispatch(self):
        a, _ = self.device()
        self.forwarder.io_timeout = .15
        with socket.create_connection(("127.0.0.1", self.server.server_port), timeout=2) as sock:
            sock.sendall((f"POST /terminal-api/{a}/api/sessions HTTP/1.1\r\nHost: localhost\r\n"
                          f"Authorization: Bearer {self.alice}\r\nContent-Type: application/json\r\nContent-Length: 20\r\n\r\nx").encode())
            response = http.client.HTTPResponse(sock)
            response.begin()
            self.assertEqual(response.status, 408)
            self.assertEqual(json.loads(response.read())["error"], "request_body_timeout")
            response.close()
        self.assertEqual(self.requests, [])

    def test_cancel_during_connect_sends_no_request(self):
        from unittest.mock import patch
        a, _ = self.device()
        entered, proceed = threading.Event(), threading.Event()
        original = http.client.HTTPConnection.connect
        target_port = self.registry.route("alice", a)["port"]
        def delayed(connection):
            if connection.port == target_port:
                entered.set()
                proceed.wait(2)
            return original(connection)
        with patch.object(http.client.HTTPConnection, "connect", delayed):
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                pending = pool.submit(self.request, f"/terminal-api/{a}/api/sessions", "POST", None, "{}")
                try:
                    self.assertTrue(entered.wait(1))
                    self.assertEqual(self.manage(a, "remove"), 204)
                finally:
                    proceed.set()
                self.assertEqual(pending.result(timeout=2)[0], 403)
        self.assertEqual(self.requests, [])

    def test_redirects_and_ambiguous_responses_blocked_but_business_errors_preserved(self):
        a, _ = self.device()
        prefix = f"/terminal-api/{a}/api/sessions/"
        self.assertEqual(json.loads(self.request(prefix + "redirect")[1])["error"], "terminal_redirect_blocked")
        self.assertEqual(json.loads(self.request(prefix + "ambiguous")[1])["error"], "terminal_protocol_error")
        self.assertEqual(self.request(prefix + "conflict")[0], 409)
        self.assertEqual(self.request(prefix + "normal", "HEAD")[1], b"")
        self.assertEqual(len(self.requests), 4)

    def test_capacity_limit_and_cleanup_after_abort(self):
        a, _ = self.device()
        self.forwarder.max_active = 1
        _, response = self.stream(a)
        self.assertEqual(json.loads(self.request(f"/terminal-api/{a}/api/sessions")[1])["error"], "relay_busy")
        self.assertEqual(self.manage(a, "pause"), 204)
        with self.assertRaises((http.client.IncompleteRead, OSError)):
            response.read()
        deadline = time.monotonic() + 1
        while self.forwarder.leases and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertEqual(len(self.forwarder.leases), 0)

    def test_removal_while_request_waits_for_headers(self):
        a, _ = self.device()
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(self.request, f"/terminal-api/{a}/api/sessions/slow", "POST", None, "{}")
            self.assertTrue(self.accepted.wait(1))
            self.assertEqual(self.manage(a, "remove"), 204)
            status, raw, _ = future.result(timeout=2)
            self.assertEqual(status, 403)
            self.assertEqual(json.loads(raw)["outcome"], "unknown")
        self.assertEqual(len(self.requests), 1)


if __name__ == "__main__":
    unittest.main()
