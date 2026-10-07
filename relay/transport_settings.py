"""Device-authenticated bootstrap. No client-selected ports or executable paths."""
import hashlib
import json
import os
from pathlib import Path
import re
import ssl


def validate_settings(value):
    fields = {"server", "serverPort", "serverName", "caPem", "caSha256", "transportToken"}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError("invalid_transport_settings")
    for field in ("server", "serverName"):
        if not isinstance(value[field], str) or not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?", value[field]):
            raise ValueError("invalid_transport_host")
    if type(value["serverPort"]) is not int or not 1024 <= value["serverPort"] <= 65535:
        raise ValueError("invalid_transport_port")
    for field in ("caSha256", "transportToken"):
        if not isinstance(value[field], str) or not re.fullmatch(r"[0-9a-f]{64}", value[field]):
            raise ValueError("invalid_transport_digest")
    pem = value["caPem"]
    if not isinstance(pem, str) or not 1 <= len(pem.encode()) <= 16384 or "PRIVATE KEY" in pem:
        raise ValueError("invalid_transport_ca")
    if hashlib.sha256(pem.encode()).hexdigest() != value["caSha256"]:
        raise ValueError("transport_ca_mismatch")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    try:
        context.load_verify_locations(cadata=pem)
    except (ValueError, ssl.SSLError):
        raise ValueError("invalid_transport_ca") from None
    return dict(value)


class TransportSettings:
    def __init__(self, registry, settings):
        self.registry = registry
        self._settings = validate_settings(settings)

    @classmethod
    def from_file(cls, registry, filename):
        path = Path(filename)
        if not path.is_absolute() or any(p.is_symlink() or (hasattr(p, "is_junction") and p.is_junction()) for p in (path, *path.parents)):
            raise ValueError("private_transport_path_required")
        if os.name != "nt" and path.stat().st_mode & 0o077:
            raise ValueError("private_transport_permissions_required")
        with path.open("rb") as stream:
            raw = stream.read(32769)
        if len(raw) > 32768:
            raise ValueError("transport_settings_too_large")
        return cls(registry, json.loads(raw))

    def connection(self, terminal_id, token):
        device = self.registry.device(terminal_id, token)
        return {"schema": 1, "terminalId": device["id"], "remotePort": device["port"],
                "proxyName": device["proxyName"], **self._settings}
