"""Process-local Gateway credentials, admitted only after an authenticated tunnel probe.

No secret persistence: a relay restart requires connectors to register again.
The public HTTPS terminator is mandatory outside isolated loopback tests.
"""
import http.client
import json
import re
import threading

from registry import ControlError


class GatewayLinks:
    def __init__(self, registry):
        self.registry = registry
        self.entries = {}
        self.lock = threading.RLock()
        self.probes = threading.BoundedSemaphore(4)

    def register(self, terminal_id, device_token, body):
        route = self.registry.device(terminal_id, device_token)
        token, instance = body.get("token"), body.get("instance")
        if not isinstance(token, str) or not re.fullmatch(r"[0-9a-f]{64}", token) or not isinstance(instance, str) or not re.fullmatch(r"[0-9a-f]{32}", instance):
            raise ControlError(400, "invalid_gateway_registration")
        if token == device_token:
            raise ControlError(400, "distinct_gateway_credential_required")
        if not self.probes.acquire(blocking=False):
            raise ControlError(429, "gateway_probe_busy")
        connection = http.client.HTTPConnection("127.0.0.1", route["port"], timeout=2)
        # A socket shutdown bounds slow headers/body as well as idle reads.
        timer = None
        try:
            connection.connect()
            sock = connection.sock
            def abort():
                import socket
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            timer = threading.Timer(3, abort)
            timer.daemon = True
            timer.start()
            connection.request("GET", "/api/connector/health", headers={"Authorization": "Bearer " + token, "Connection": "close"})
            response = connection.getresponse()
            length = response.getheader("Content-Length", "")
            if response.status != 200 or response.getheader("Transfer-Encoding") or not length.isdigit() or not 0 < int(length) <= 2048:
                raise ValueError()
            value = json.loads(response.read(int(length)))
            if not isinstance(value, dict) or value.get("instance") != instance or value.get("healthy") is not True:
                raise ValueError()
            with self.lock:
                current = self.registry.device(terminal_id, device_token)
                if current != route:
                    raise ControlError(409, "gateway_registration_stale")
                self.entries[terminal_id] = (route["epoch"], token)
            return {"dataChannel": "verified", "executor": "unknown"}
        except ControlError:
            raise
        except (OSError, ValueError, http.client.HTTPException):
            raise ControlError(503, "gateway_tunnel_unverified") from None
        finally:
            if timer:
                timer.cancel()
            connection.close()
            self.probes.release()

    def credential(self, terminal_id):
        with self.lock:
            entry = self.entries.get(terminal_id)
            if entry is None or not self.registry.gateway_epoch_active(terminal_id, entry[0]):
                self.entries.pop(terminal_id, None)
                return None
            return entry[1]
