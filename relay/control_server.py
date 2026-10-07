"""Isolated loopback control service with optional HTTP terminal forwarding."""

import argparse
from contextlib import nullcontext
import http.client
import json
import re
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

from frp_policy import evaluate
from maintenance import MaintenanceClosed
from release_notes import ReleaseNotesError, format_history, load_catalog
from registry import ControlError, Registry


class RelayAccount:
    """Reuse existing single-account login without reading its database or password.

    The current identity service has ONE owner. Never use a client-supplied account
    header as identity. A multi-user service will need a verified immutable subject.
    """

    def __init__(self, port=18081):
        self.port = port

    def __call__(self, authorization):
        if not re.fullmatch(r"Bearer [0-9a-f]{64}", authorization):
            raise ControlError(401, "login_required")
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            connection.request("GET", "/auth/check", headers={"Authorization": authorization})
            response = connection.getresponse()
            status = response.status
            if status == 204:
                return "relay-owner"
            if status in (401, 403):
                raise ControlError(401, "login_required")
            raise ControlError(503, "identity_unavailable")
        except (OSError, http.client.HTTPException):
            raise ControlError(503, "identity_unavailable") from None
        finally:
            connection.close()


def handler_for(registry, account=None, plugin=False, forwarder=None, gateway_links=None,
                transport_settings=None, release_catalog=None, gate=None):
    class Handler(BaseHTTPRequestHandler):
        server_version = "TerminalControl/0.1"
        protocol_version = "HTTP/1.1"

        def handle_expect_100(self):
            self.respond(417, {"error": "expect_not_supported"})
            return False

        def setup(self):
            super().setup()
            self.connection.settimeout(5)

        def log_message(self, _format, *_args):
            pass  # No request paths, pairing codes, credentials, or bodies in logs.

        def respond(self, status, body=None):
            encoded = b"" if body is None else json.dumps(body).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(encoded)
            self.close_connection = True

        def body(self):
            lengths = self.headers.get_all("Content-Length", [])
            if len(lengths) != 1 or self.headers.get("Transfer-Encoding"):
                raise ControlError(400, "invalid_body")
            try:
                length = int(lengths[0])
            except ValueError:
                raise ControlError(400, "invalid_body") from None
            if not 1 <= length <= 16384:
                raise ControlError(413, "invalid_body_size")
            if self.headers.get_content_type() != "application/json":
                raise ControlError(415, "json_required")
            try:
                body = json.loads(self.rfile.read(length))
            except (ValueError, UnicodeError):
                raise ControlError(400, "invalid_json") from None
            if not isinstance(body, dict):
                raise ControlError(400, "object_required")
            return body

        def bearer(self):
            values = self.headers.get_all("Authorization", [])
            if len(values) != 1 or not values[0].startswith("Bearer "):
                raise ControlError(401, "credential_required")
            return values[0][7:]

        def dispatch(self):
            parsed = urlsplit(self.path)
            path = parsed.path
            if plugin:
                if path != "/internal/frp" or self.command != "POST":
                    raise ControlError(404, "not_found")
                query = parse_qs(parsed.query)
                if query.get("version") != ["0.1.0"] or len(query.get("op", [])) != 1:
                    return 200, {"reject": True, "reject_reason": "Unsupported plugin protocol"}
                return 200, evaluate(registry, query["op"][0], self.body().get("content"))
            if path.startswith("/control-api/"):
                owner = account("Bearer " + self.bearer())
                if path == "/control-api/version" and self.command == "GET":
                    return 200, release_catalog
                if path == "/control-api/pairings":
                    if self.command == "POST":
                        self.body()
                        return 201, registry.create_pairing(owner)
                    if self.command == "GET":
                        return 200, {"pairings": registry.pairings(owner)}
                match = re.fullmatch(r"/control-api/pairings/([0-9a-f]{32})/(confirm|cancel)", path)
                if match and self.command == "POST":
                    body = self.body()
                    if match[2] == "confirm":
                        return 200, registry.confirm(owner, match[1], body.get("name"))
                    registry.cancel_pairing(owner, match[1])
                    return 204, None
                if path == "/control-api/terminals" and self.command == "GET":
                    return 200, {"terminals": registry.terminals(owner)}
                match = re.fullmatch(r"/control-api/terminals/([0-9a-f]{32})/(rename|pause|resume|remove)", path)
                if match and self.command == "POST":
                    body = self.body()
                    manager = forwarder if forwarder is not None else registry
                    manager.change(owner, match[1], match[2], body.get("name"))
                    return 204, None
            if self.command == "POST" and path.startswith("/connector-api/"):
                match = re.fullmatch(r"/connector-api/terminals/([0-9a-f]{32})/connection", path)
                if match and transport_settings is not None:
                    if self.body():
                        raise ControlError(400, "empty_body_required")
                    return 200, transport_settings.connection(match[1], self.bearer())
                match = re.fullmatch(r"/connector-api/terminals/([0-9a-f]{32})/gateway", path)
                if match and gateway_links is not None:
                    return 200, gateway_links.register(match[1], self.bearer(), self.body())
                if path == "/connector-api/claim":
                    body = self.body()
                    return 200, registry.claim(body.get("code"), self.bearer(), body.get("name"), body.get("platform"), self.client_address[0])
                match = re.fullmatch(r"/connector-api/pairings/([0-9a-f]{32})", path)
                if match:
                    self.body()
                    return 200, registry.pairing_status(match[1], self.bearer())
                match = re.fullmatch(r"/connector-api/terminals/([0-9a-f]{32})/heartbeat", path)
                if match:
                    body = self.body()
                    registry.heartbeat(match[1], self.bearer(), body.get("executor"), body.get("version"))
                    return 204, None
            raise ControlError(404, "not_found")

        def handle_request(self):
            recovery = (plugin or (self.command == "POST" and re.fullmatch(
                r"/connector-api/terminals/[0-9a-f]{32}/(connection|gateway|heartbeat)",
                urlsplit(self.path).path) is not None))
            kind = ("plugin_recovery" if plugin else "connector_recovery") if recovery else "ordinary"
            try:
                with gate.admit(kind) if gate is not None else nullcontext():
                    self._handle_admitted_request()
            except MaintenanceClosed:
                if plugin:
                    self.respond(200, {"reject": True, "reject_reason": "Relay maintenance"})
                else:
                    self.respond(503, {"error": "relay_maintenance"})

        def _handle_admitted_request(self):
            try:
                if not plugin and self.path.startswith("/terminal-api/"):
                    if forwarder is None:
                        raise ControlError(503, "terminal_forwarding_unavailable")
                    forwarder.forward(self)
                    return
                status, body = self.dispatch()
                self.respond(status, body)
            except ControlError as error:
                self.respond(error.status, {"error": error.code})
            except (OSError, TimeoutError):
                self.close_connection = True
            except Exception:
                # A registry outage is fail-closed; never allow an FRP operation.
                self.respond(503, {"error": "control_unavailable"})

        do_GET = handle_request
        do_POST = handle_request
        do_HEAD = handle_request
        do_PUT = handle_request
        do_PATCH = handle_request
        do_DELETE = handle_request

    return Handler


def make_server(registry, port=0, account=None, plugin=False, forwarder=None,
                gateway_links=None, transport_settings=None, gate=None):
    if not plugin and account is None:
        raise ValueError("An account verifier is required")
    if forwarder is not None and (plugin or forwarder.registry is not registry or forwarder.account is not account):
        raise ValueError("Forwarder must share the same registry and account verifier")
    if gateway_links is not None and (plugin or gateway_links.registry is not registry):
        raise ValueError("Gateway links must share the API registry")
    if transport_settings is not None and (plugin or transport_settings.registry is not registry or gateway_links is None or forwarder is None):
        raise ValueError("Transport settings require the same registry and enabled forwarding")
    catalog = None if plugin else load_catalog()
    return ThreadingHTTPServer(("127.0.0.1", port), handler_for(
        registry, account, plugin, forwarder, gateway_links, transport_settings, catalog, gate))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", help="Dedicated test registry, never the legacy auth DB")
    notes = parser.add_mutually_exclusive_group()
    notes.add_argument("--version", action="store_true", help="Print installed relay version and exit")
    notes.add_argument("--release-notes", action="store_true", help="Print installed relay release history and exit")
    parser.add_argument("--port", type=int, default=18091)
    parser.add_argument("--plugin-port", type=int, default=18092)
    parser.add_argument("--auth-port", type=int, default=18081)
    parser.add_argument("--enable-terminal-forwarding", action="store_true", help="Opt-in isolated Gateway registration and forwarding; public deployment requires verified HTTPS")
    parser.add_argument("--transport-config", help="Private server-side FRP settings; requires terminal forwarding")
    args = parser.parse_args()
    if args.version or args.release_notes:
        try:
            catalog = load_catalog()
        except ReleaseNotesError:
            parser.exit(2, "release_notes_unavailable\n")
        print(catalog["version"] if args.version else format_history(catalog))
        return
    if not args.db:
        parser.error("--db is required to start the relay")
    if args.transport_config and not args.enable_terminal_forwarding:
        parser.error("--transport-config requires --enable-terminal-forwarding")
    registry = Registry(args.db)
    plugin_server = make_server(registry, args.plugin_port, plugin=True)
    account = RelayAccount(args.auth_port)
    links = forwarder = None
    if args.enable_terminal_forwarding:
        from gateway_link import GatewayLinks
        from forwarder import Forwarder
        links = GatewayLinks(registry)
        forwarder = Forwarder(registry, account, links.credential)
    settings = None
    if args.transport_config:
        from transport_settings import TransportSettings
        settings = TransportSettings.from_file(registry, args.transport_config)
    api_server = make_server(registry, args.port, account=account, forwarder=forwarder, gateway_links=links, transport_settings=settings)
    threading.Thread(target=plugin_server.serve_forever, daemon=True).start()
    try:
        api_server.serve_forever()
    finally:
        if forwarder is not None:
            forwarder.close()
        plugin_server.shutdown()
        plugin_server.server_close()
        api_server.server_close()


if __name__ == "__main__":
    main()
