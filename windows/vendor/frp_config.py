"""Strict FRP 0.71 launch material. Does not write files or start processes."""
import hashlib
import json
import os
from pathlib import Path
import re


def launch_material(*, server, server_port, server_name, ca_file, ca_sha256,
                    terminal_id, local_port, remote_port, device_token, transport_token):
    # JSON quoted strings are also TOML basic strings. Reject template syntax
    # before rendering; only hex secrets may enter environment substitutions.
    for value in (server, server_name):
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?", value):
            raise ValueError("invalid_frp_host")
    for value in (server_port, local_port, remote_port):
        if type(value) is not int or not 1024 <= value <= 65535:
            raise ValueError("invalid_frp_port")
    if not isinstance(terminal_id, str) or not re.fullmatch(r"[0-9a-f]{32}", terminal_id):
        raise ValueError("invalid_terminal_id")
    for token in (device_token, transport_token):
        if not isinstance(token, str) or not re.fullmatch(r"[0-9a-f]{64}", token):
            raise ValueError("invalid_frp_credential")
    path = Path(ca_file)
    if not path.is_absolute():
        raise ValueError("absolute_ca_path_required")
    for part in (path, *path.parents):
        if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
            raise ValueError("ca_link_rejected")
    with path.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != ca_sha256:
            raise ValueError("ca_fingerprint_mismatch")
    if "{{" in str(path) or "}}" in str(path):
        raise ValueError("invalid_ca_path")
    config = f'''serverAddr = {json.dumps(server)}
serverPort = {server_port}
user = "{terminal_id}"
metadatas.device_token = "{{{{ .Envs.CONNECTOR_DEVICE_TOKEN }}}}"
auth.token = "{{{{ .Envs.CONNECTOR_TRANSPORT_TOKEN }}}}"
transport.tls.enable = true
transport.tls.trustedCaFile = {json.dumps(str(path))}
transport.tls.serverName = {json.dumps(server_name)}
log.level = "error"
[[proxies]]
name = "gateway"
type = "tcp"
localIP = "127.0.0.1"
localPort = {local_port}
remotePort = {remote_port}
'''
    environment = {key: value for key, value in os.environ.items()
                   if key.upper() not in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "GODEBUG")
                   and not key.upper().startswith("CONNECTOR_")}
    environment.update(CONNECTOR_DEVICE_TOKEN=device_token, CONNECTOR_TRANSPORT_TOKEN=transport_token)
    return config, environment
