"""Bounded HTTP-only terminal forwarding. No retries, redirects or arbitrary URLs.

One Forwarder must serve all management/data requests in a service process.
Credentials are supplied by a server-side provider, NEVER by the mobile request.
WebSockets and global APK/auth endpoints intentionally remain unavailable here.
"""

import http.client
import re
import select
import socket
import threading
import time
from urllib.parse import urlsplit

from registry import ControlError


def target_path(raw):
    if len(raw) > 8192 or any(ord(c) < 33 or ord(c) > 126 for c in raw):
        raise ControlError(400, "invalid_target")
    parsed = urlsplit(raw)
    match = re.fullmatch(r"/terminal-api/([0-9a-f]{32})(/api/(?:sessions|projects|activity)(?:/[A-Za-z0-9_.-]+)*)", parsed.path)
    if parsed.scheme or parsed.netloc or parsed.fragment or not match:
        raise ControlError(404, "terminal_route_not_found")
    if any(part in (".", "..") for part in match[2].split("/")):
        raise ControlError(400, "invalid_target")
    return match[1], match[2] + ("?" + parsed.query if parsed.query else "")


class Lease:
    def __init__(self, terminal_id, owner, epoch, downstream):
        self.terminal_id, self.owner, self.epoch = terminal_id, owner, epoch
        self.downstream = downstream
        self.upstream = None
        self.cancelled = threading.Event()
        self.done = threading.Event()
        self.reason = "terminal_access_revoked"
        self.started = False
        self.dispatched = False

    def abort(self, reason):
        self.reason = reason
        self.cancelled.set()
        # shutdown, not close: unblock readers even when HTTPResponse owns a file.
        for sock in (self.upstream, self.downstream if self.started else None):
            if sock is not None:
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except (OSError, ValueError):
                    pass


class Forwarder:
    def __init__(self, registry, account, gateway_credential, *, io_timeout=10,
                 total_timeout=120, poll_interval=.25, max_active=32, max_body=1048576):
        if min(io_timeout, total_timeout, poll_interval, max_active, max_body) <= 0:
            raise ValueError("Forwarding limits must be positive")
        self.registry, self.account, self.gateway_credential = registry, account, gateway_credential
        self.io_timeout, self.total_timeout, self.poll_interval = io_timeout, total_timeout, poll_interval
        self.max_body, self.max_active = max_body, max_active
        self.lock = threading.RLock()
        self.leases = set()
        self.closed = False

    def change(self, owner, terminal_id, action, name=None):
        # Serialize admission and revocation; revoke both directions before ACK.
        with self.lock:
            self.registry.change(owner, terminal_id, action, name)
            if action in ("pause", "resume", "remove"):
                for lease in self.leases:
                    if lease.terminal_id == terminal_id:
                        lease.abort("terminal_access_revoked")

    def close(self):
        with self.lock:
            self.closed = True
            for lease in self.leases:
                lease.abort("relay_stopping")

    def _watch(self, lease, authorization, deadline):
        while not lease.done.wait(self.poll_interval):
            with self.lock:
                if lease.cancelled.is_set():
                    return
                if time.monotonic() >= deadline:
                    lease.abort("terminal_request_timeout")
                    return
                try:
                    readable, _, _ = select.select([lease.downstream], [], [], 0)
                    if readable and lease.downstream.recv(1, socket.MSG_PEEK) == b"":
                        lease.abort("client_disconnected")
                        return
                except (OSError, ValueError):
                    lease.abort("client_disconnected")
                    return
            # Remote verification outside lock so an auth outage cannot block remove.
            try:
                owner = self.account(authorization)
                route = self.registry.route(lease.owner, lease.terminal_id)
                if owner != lease.owner or route["epoch"] != lease.epoch:
                    raise ControlError(403, "terminal_access_revoked")
            except Exception:
                with self.lock:
                    if not lease.done.is_set():
                        lease.abort("terminal_access_revoked")
                return

    def _request_body(self, handler):
        if handler.command not in ("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"):
            raise ControlError(405, "method_not_allowed")
        if handler.headers.get_all("Transfer-Encoding") or handler.headers.get_all("Expect") or handler.headers.get_all("Upgrade"):
            raise ControlError(400, "unsupported_request_framing")
        lengths = handler.headers.get_all("Content-Length", [])
        if len(lengths) > 1 or (lengths and not re.fullmatch(r"[0-9]{1,10}", lengths[0])):
            raise ControlError(400, "invalid_content_length")
        length = int(lengths[0]) if lengths else 0
        if length > self.max_body:
            raise ControlError(413, "request_too_large")
        if length and handler.command in ("GET", "HEAD"):
            raise ControlError(400, "unexpected_request_body")
        if length and handler.headers.get_content_type() != "application/json":
            raise ControlError(415, "json_required")
        original_timeout = handler.connection.gettimeout()
        deadline = time.monotonic() + self.io_timeout
        body = bytearray()
        try:
            while len(body) < length:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError()
                handler.connection.settimeout(remaining)
                chunk = handler.rfile.read1(min(16384, length - len(body)))
                if not chunk:
                    break
                body.extend(chunk)
        except TimeoutError:
            raise ControlError(408, "request_body_timeout") from None
        finally:
            handler.connection.settimeout(original_timeout)
        if len(body) != length:
            raise ControlError(400, "incomplete_request_body")
        return bytes(body)

    def forward(self, handler):
        terminal_id, path = target_path(handler.path)
        authorization = "Bearer " + handler.bearer()
        owner = self.account(authorization)
        # Reject unauthorized or offline devices before reading their request body.
        self.registry.route(owner, terminal_id)
        body = self._request_body(handler)
        owner_after_body = self.account(authorization)
        if owner_after_body != owner:
            raise ControlError(401, "login_required")
        with self.lock:
            if self.closed or len(self.leases) >= self.max_active:
                raise ControlError(503, "relay_busy")
            route = self.registry.route(owner, terminal_id)
            credential = self.gateway_credential(terminal_id)
            if not isinstance(credential, str) or not re.fullmatch(r"[0-9a-f]{64}", credential):
                raise ControlError(503, "terminal_credential_unavailable")
            lease = Lease(terminal_id, owner, route["epoch"], handler.connection)
            self.leases.add(lease)
        connection = http.client.HTTPConnection("127.0.0.1", route["port"], timeout=self.io_timeout)
        watcher = threading.Thread(target=self._watch, args=(lease, authorization, time.monotonic() + self.total_timeout), daemon=True)
        watcher.start()
        response = None
        try:
            # connect() has a timeout. Register its socket before sending any bytes.
            connection.connect()
            with self.lock:
                lease.upstream = connection.sock
                if lease.cancelled.is_set():
                    lease.abort(lease.reason)
                    raise ControlError(403, lease.reason)
                # Do not pass phone auth, cookies, Host, X-Forwarded-* or hop headers.
                headers = {"Authorization": "Bearer " + credential, "Accept-Encoding": "identity", "Connection": "close", "Content-Type": "application/json"}
                for name in ("Accept", "If-None-Match"):
                    value = handler.headers.get(name)
                    if value is not None:
                        headers[name] = value
                lease.dispatched = True
            # No retry, including failure after the backend may have accepted a POST.
            connection.request(handler.command, path, body=body, headers=headers)
            response = connection.getresponse()
            if 300 <= response.status < 400 and response.status != 304:
                raise ControlError(502, "terminal_redirect_blocked")
            if response.status < 200:
                raise ControlError(502, "terminal_protocol_error")
            if response.status in (401, 403):
                raise ControlError(502, "terminal_gateway_auth_failed")
            lengths = response.headers.get_all("Content-Length", [])
            transfers = response.headers.get_all("Transfer-Encoding", [])
            if len(lengths) > 1 or (lengths and not re.fullmatch(r"[0-9]+", lengths[0])) or (transfers and (lengths or transfers != ["chunked"])):
                raise ControlError(502, "terminal_protocol_error")
            no_body = handler.command == "HEAD" or response.status in (204, 304)
            with self.lock:
                if lease.cancelled.is_set():
                    raise ControlError(403, lease.reason)
                lease.started = True
            # Never hold admission/revocation lock across downstream network I/O.
            handler.send_response(response.status)
            for name in ("Content-Type", "ETag", "Last-Modified"):
                value = response.getheader(name)
                if value is not None and not any(ord(c) < 32 or ord(c) == 127 for c in value):
                    handler.send_header(name, value)
            handler.send_header("Cache-Control", "no-store")
            handler.send_header("X-Content-Type-Options", "nosniff")
            handler.send_header("X-Terminal-Id", terminal_id)
            handler.send_header("Connection", "close")
            if not no_body:
                handler.send_header("Transfer-Encoding", "chunked")
            handler.end_headers()
            if not no_body:
                while True:
                    chunk = response.read1(16384)
                    if not chunk:
                        if response.length not in (None, 0):
                            raise ControlError(502, "terminal_response_incomplete")
                        break
                    with self.lock:
                        if lease.cancelled.is_set():
                            raise ControlError(403, lease.reason)
                    handler.wfile.write(f"{len(chunk):X}\r\n".encode() + chunk + b"\r\n")
                with self.lock:
                    if lease.cancelled.is_set():
                        raise ControlError(403, lease.reason)
                handler.wfile.write(b"0\r\n\r\n")
        except (OSError, http.client.HTTPException, ControlError) as error:
            if not lease.started:
                if lease.cancelled.is_set():
                    code = lease.reason
                    status = 504 if code == "terminal_request_timeout" else 403
                elif isinstance(error, ControlError):
                    status, code = error.status, error.code
                elif not lease.dispatched:
                    status, code = 503, "terminal_unreachable"
                elif isinstance(error, TimeoutError):
                    status, code = 504, "terminal_response_timeout"
                else:
                    status, code = 502, "terminal_connection_lost"
                try:
                    handler.respond(status, {"error": code, "retryable": False,
                        "outcome": "unknown" if lease.dispatched and handler.command not in ("GET", "HEAD") else "not_confirmed"})
                except OSError:
                    pass
            # After headers: no fake JSON/error appended to output and no final chunk.
            # Client sees an incomplete response, not successful end-of-stream.
        finally:
            handler.close_connection = True
            lease.done.set()
            with self.lock:
                self.leases.discard(lease)
            connection.close()
            if response is not None:
                response.close()
