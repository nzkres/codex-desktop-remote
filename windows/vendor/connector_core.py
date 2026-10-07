"""Windows connector: enrollment, with an explicit opt-in managed task runtime."""

import csv
import ctypes
from contextlib import contextmanager, nullcontext
from ctypes import wintypes
import io
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import threading
import urllib.error
import urllib.parse
import urllib.request


class ConnectorError(Exception):
    pass


class Blob(ctypes.Structure):
    _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]


def dpapi(data, decrypt=False):
    if os.name != "nt":
        raise ConnectorError("windows_required")
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    output = Blob()
    crypt = ctypes.WinDLL("crypt32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    # CurrentUser scope (no LOCAL_MACHINE flag), UI forbidden for unattended use.
    if decrypt:
        method = crypt.CryptUnprotectData
        method.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
        arguments = (ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output))
    else:
        method = crypt.CryptProtectData
        method.argtypes = [ctypes.POINTER(Blob), wintypes.LPCWSTR, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
        arguments = (ctypes.byref(source), "Terminal Connector", None, None, None, 1, ctypes.byref(output))
    method.restype = wintypes.BOOL
    if not method(*arguments):
        raise ConnectorError("credential_decryption_failed" if decrypt else "credential_encryption_failed")
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        kernel.LocalFree(output.data)


def no_links(path):
    for part in (path, *path.parents):
        if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
            raise ConnectorError("state_path_link_rejected")


def private_directory(path):
    if os.name != "nt":
        raise ConnectorError("windows_required")
    system = Path(os.environ["SystemRoot"]) / "System32"
    result = subprocess.run([str(system / "whoami.exe"), "/user", "/fo", "csv", "/nh"], capture_output=True, text=True, check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    sid = next(csv.reader(io.StringIO(result.stdout)))[-1]
    if not re.fullmatch(r"S-1-[0-9-]+", sid):
        raise ConnectorError("windows_identity_unavailable")
    # Replace the entire DACL, not only named grants: a newly created directory
    # can contain explicit default ACEs that icacls /inheritance:r leaves behind.
    # This function is used only for our freshly created, dedicated state folder.
    advapi = ctypes.WinDLL("advapi32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    convert = advapi.ConvertStringSecurityDescriptorToSecurityDescriptorW
    convert.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(ctypes.c_void_p), ctypes.c_void_p]
    convert.restype = wintypes.BOOL
    apply_security = advapi.SetFileSecurityW
    apply_security.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, ctypes.c_void_p]
    apply_security.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    descriptor = ctypes.c_void_p()
    if not convert(f"D:P(A;OICI;FA;;;{sid})(A;OICI;FA;;;SY)", 1, ctypes.byref(descriptor), None):
        raise ConnectorError("private_directory_failed")
    try:
        # DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION
        if not apply_security(str(path), 0x80000004, descriptor):
            raise ConnectorError("private_directory_failed")
    finally:
        kernel.LocalFree(descriptor)


class CredentialStore:
    """One new dedicated directory; refuse existing unrelated directories."""
    def __init__(self, directory):
        self.root = Path(directory).absolute()
        no_links(self.root)
        self.file = self.root / "identity.dpapi"
        if not self.root.exists():
            # No recursive changes to parents, their ACLs, or the existing installation.
            self.root.mkdir()
            private_directory(self.root)
            self.write({"schema": 1, "unpaired": True})
        elif not self.file.is_file():
            raise ConnectorError("state_directory_not_owned")
        no_links(self.file)

    def read(self):
        no_links(self.file)
        if not self.file.exists():
            return None
        if self.file.stat().st_size > 65536:
            raise ConnectorError("invalid_credential_file")
        try:
            state = json.loads(dpapi(self.file.read_bytes(), decrypt=True))
            if state == {"schema": 1, "unpaired": True}:
                return None
            if state.get("schema") != 1 or not re.fullmatch(r"[0-9a-f]{64}", state.get("deviceToken", "")):
                raise ValueError()
            return state
        except (ValueError, TypeError, AttributeError):
            raise ConnectorError("invalid_credential_file") from None

    @contextmanager
    def lock(self, name="operation.lock"):
        if name not in ("operation.lock", "runtime.lock"):
            raise ConnectorError("invalid_lock_name")
        no_links(self.root / name)
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
        kernel.CreateFileW.restype = wintypes.HANDLE
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.CreateFileW(str(self.root / name), 0xC0000000, 0, None, 4, 0x80, None)
        if handle == ctypes.c_void_p(-1).value:
            raise ConnectorError("connector_already_running")
        try:
            yield
        finally:
            kernel.CloseHandle(handle)

    def write(self, state):
        no_links(self.root)
        no_links(self.file)
        protected = dpapi(json.dumps(state, ensure_ascii=False).encode("utf-8"))
        descriptor, temporary = tempfile.mkstemp(prefix="pending-", suffix=".dpapi", dir=self.root)
        try:
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(protected)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, self.file)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)  # Only this function's exact owned temp file.


def endpoint(value, loopback_test=False):
    try:
        parsed = urllib.parse.urlsplit(value)
        port = parsed.port
    except (ValueError, TypeError):
        raise ConnectorError("invalid_relay_url") from None
    if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ConnectorError("invalid_relay_url")
    if parsed.scheme != "https" and not (loopback_test and parsed.scheme == "http" and parsed.hostname == "127.0.0.1"):
        raise ConnectorError("https_required")
    if port is not None and not 1 <= port <= 65535:
        raise ConnectorError("invalid_relay_url")
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, "", "", ""))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class RelayClient:
    def __init__(self, base, loopback_test=False):
        self.base = endpoint(base, loopback_test)
        handlers = [NoRedirect()]
        if loopback_test and self.base.startswith("http://127.0.0.1:"):
            handlers.append(urllib.request.ProxyHandler({}))
        self.opener = urllib.request.build_opener(*handlers)

    def post(self, path, token, body):
        request = urllib.request.Request(self.base + path, data=json.dumps(body).encode(), headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"}, method="POST")
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        except (OSError, urllib.error.URLError):
            return 0, {"error": "connection_unavailable"}
        with response:
            raw = response.read(65537)
            if len(raw) > 65536:
                raise ConnectorError("relay_response_too_large")
            try:
                body = json.loads(raw) if raw else {}
                if not isinstance(body, dict):
                    raise ValueError()
            except (ValueError, UnicodeError):
                raise ConnectorError("invalid_relay_response") from None
            return response.status, body


class Connector:
    def __init__(self, store, *, loopback_test=False, runtime=None, runtime_factory=None):
        self.store, self.loopback_test = store, loopback_test
        if runtime is not None and runtime_factory is not None:
            raise ConnectorError("ambiguous_runtime")
        self.runtime = runtime
        self.runtime_factory = runtime_factory

    def begin(self, relay, code, name):
        relay = endpoint(relay, self.loopback_test)
        if not isinstance(code, str) or not re.fullmatch(r"[0-9A-F]{12}", code):
            raise ConnectorError("invalid_pairing_code")
        if not isinstance(name, str) or not name.strip() or len(name) > 80 or any(ord(c) < 32 or ord(c) == 127 for c in name):
            raise ConnectorError("invalid_device_name")
        with self.store.lock():
            if self.store.read() is not None:
                raise ConnectorError("already_enrolled_or_pending")
            state = {"schema": 1, "relay": relay, "name": name.strip(), "deviceToken": secrets.token_hex(32), "pairingCode": code, "state": "claim_pending"}
            # Persist first: a crash/response loss must not rotate the credential.
            self.store.write(state)
        return self.tick()

    def status(self):
        state = self.store.read()
        if state is None:
            return {"state": "not_paired", "executor": "unknown"}
        return {"state": state["state"], "relay": state["relay"], "name": state["name"], "terminalId": state.get("terminalId"), "executor": "unknown", "lastError": state.get("lastError"), "dataChannel": "unverified" if state.get("gatewayBinding") else "not_configured"}

    def tick(self):
        if (self.runtime is not None or self.runtime_factory is not None) and getattr(self, "_run_owner", None) != threading.get_ident():
            raise ConnectorError("managed_runtime_requires_run")
        try:
            lease = nullcontext() if getattr(self, "_run_owner", None) == threading.get_ident() else self.store.lock("runtime.lock")
            with lease, self.store.lock():
                report = self._tick()
                if self.runtime is not None:
                    report.update(self.runtime.reconcile(report["state"] == "connected"))
                return report
        except BaseException:
            if self.runtime is not None:
                self.runtime.disconnect()
            raise

    def run(self, stop=None, on_status=None):
        with self.store.lock("runtime.lock"):
            self._run_owner = threading.get_ident()
            try:
                if self.runtime_factory is not None:
                    # No configuration fetch, state mutation or child assembly
                    # until the exclusive runtime lease has been acquired.
                    self.runtime = self.runtime_factory(self.store)
                return self._owned_run(stop, on_status)
            finally:
                self._run_owner = None

    def _owned_run(self, stop=None, on_status=None):
        # Persist before any child can be started. Never silently adopt leftover
        # processes or erase this guard after a crash.
        if self.runtime is not None:
            with self.store.lock():
                state = self.store.read()
                if state is None:
                    raise ConnectorError("not_paired")
                if state.get("runtimeNeedsReview") and self.runtime.resume_existing(state) is not True:
                    raise ConnectorError("previous_runtime_requires_review")
                state["runtimeNeedsReview"] = True
                state["transportCrashContained"] = getattr(self.runtime.transport, "crash_contained", False) is True
                self.store.write(state)
                protected_state = {k: v for k, v in state.items() if k not in ('state', 'lastError')}
        normal_stop = False
        try:
            result = self._run(stop, on_status)
            normal_stop = stop is not None and stop.is_set() and result['state'] == 'connected'
            return result
        finally:
            if self.runtime is not None:
                with self.store.lock():
                    expected = self.store.read()
                binding_matches = isinstance(expected, dict) and {
                    k: v for k, v in expected.items() if k not in ('state', 'lastError')
                } == protected_state
                self.runtime.disconnect(normal=normal_stop and binding_matches and expected['state'] == 'connected')
                if not binding_matches:
                    raise ConnectorError('identity_changed')
                # Clearing requires the full expected state, runtime lock,
                # drain acknowledgement and both owned trees proven empty.
                if self.runtime.quiescent() is True:
                    with self.store.lock():
                        state = self.store.read()
                        if state != expected:
                            raise ConnectorError('identity_changed')
                        state.pop("runtimeNeedsReview", None)
                        self.store.write(state)

    def _run(self, stop=None, on_status=None):
        """Foreground loop; never creates a startup task or a background process."""
        stop = stop or threading.Event()
        delay = 1
        while not stop.is_set():
            report = self.tick()
            if on_status:
                on_status(report)
            if report["state"] in ("revoked", "expired", "rejected", "claim_failed"):
                return report
            if report.get("lastError"):
                wait = delay
                delay = min(30, delay * 2)
            else:
                delay = 1
                wait = 15 if report["state"] == "connected" else 3
            stop.wait(wait)
        return self.status()

    def _tick(self):
        state = self.store.read()
        if state is None:
            raise ConnectorError("not_paired")
        if state["state"] in ("revoked", "expired", "rejected", "claim_failed"):
            return self.status()
        client = RelayClient(state["relay"], self.loopback_test)
        token = state["deviceToken"]
        if "pairingId" not in state:
            status, body = client.post("/connector-api/claim", token, {"code": state["pairingCode"], "name": state["name"], "platform": "windows"})
            if status == 200:
                if not re.fullmatch(r"[0-9a-f]{32}", body.get("id", "")):
                    raise ConnectorError("invalid_pairing_response")
                state["pairingId"] = body["id"]
                state.pop("pairingCode", None)
                state["state"] = "awaiting_confirmation"
                state["lastError"] = None
            elif status in (400, 401, 409):
                state["state"] = "claim_failed"
            else:
                state["lastError"] = "connection_unavailable"
                self.store.write(state)
                return self.status()
            self.store.write(state)
            return self.status()
        status, body = client.post("/connector-api/pairings/" + state["pairingId"], token, {})
        if status == 401:
            state["state"] = "revoked"
        elif status == 200 and body.get("state") == "confirmed":
            terminal_id = body.get("terminalId", "")
            if not re.fullmatch(r"[0-9a-f]{32}", terminal_id) or (state.get("terminalId") and state["terminalId"] != terminal_id):
                raise ConnectorError("terminal_identity_changed")
            state["terminalId"] = terminal_id
            if body.get("terminalState") == "paused":
                state["state"] = "paused"
            elif body.get("terminalState") == "active":
                result, _ = client.post(f"/connector-api/terminals/{terminal_id}/heartbeat", token, {"executor": "unknown", "version": "0.1.0-core"})
                state["state"] = "connected" if result == 204 else "reconnecting"
            else:
                raise ConnectorError("invalid_terminal_state")
        elif status == 200 and body.get("state") in ("claimed", "expired", "rejected"):
            state["state"] = "awaiting_confirmation" if body["state"] == "claimed" else body["state"]
        else:
            state["state"] = "reconnecting"
        state["lastError"] = "connection_unavailable" if state["state"] == "reconnecting" else None
        self.store.write(state)
        return self.status()
