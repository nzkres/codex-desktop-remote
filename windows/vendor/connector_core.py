# 身份与登记核心：首次配对、手机确认、心跳，以及携带运行锁的连接循环。
# 身份使用当前 Windows 用户的 DPAPI 加密，不应复制给另一台电脑。
# connected 在这里主要表示服务器登记/心跳正常，不等于 Desktop 已完成任何任务。

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

# Fixed for this process: changing a source checkout does not upgrade a running connector.
CONNECTOR_VERSION = json.loads((Path(__file__).resolve().parents[1] / 'releases.json').read_text(encoding='utf-8'))['releases'][0]['version']


# 对外使用固定错误码的异常类型；界面据此选择安全文案。
class ConnectorError(Exception):
    pass


# 与 Windows DATA_BLOB 对齐的数据结构，供 DPAPI 原生函数读写字节。
class Blob(ctypes.Structure):
    _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]


# data 为字节；默认加密，decrypt=True 时解密，返回 Windows 分配结果的字节副本。
# CurrentUser 作用域要求当前用户身份；finally 释放系统分配的内存。
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


# 逐级拒绝符号链接/目录联接，避免身份路径被转向其他位置。
def no_links(path):
    for part in (path, *path.parents):
        if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
            raise ConnectorError("state_path_link_rejected")


# 仅为新建的专用身份目录设置访问权限：当前用户与 SYSTEM。
# 不会递归修改父目录或其他已有安装的权限。
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


# 负责加密身份的原子读写和锁；持有目录不等于已经完成配对。
class CredentialStore:
    """One new dedicated directory; refuse existing unrelated directories."""
    # 新目录创建 unpaired 标记；已有目录必须带本程序的身份文件，否则拒绝接管。
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

    # 解密并验证有限大小的状态；未配对返回 None，损坏状态抛错而非自动重建。
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

    # 上下文锁：operation.lock 保护短时状态操作，runtime.lock 保护整个运行周期。
    # 使用 Windows 不共享文件句柄，finally 关闭句柄释放锁。
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

    # 先加密到同目录临时文件，刷盘后原子替换身份文件；不会留下半份 JSON。
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


# 只接受无账号、路径、查询参数的 HTTPS 服务器根地址；HTTP 仅显式回环测试允许。
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


# 不跟随服务端重定向，避免携带设备令牌跳到另一个地址。
class NoRedirect(urllib.request.HTTPRedirectHandler):
    # 返回 None 表示拒绝创建重定向请求。
    def redirect_request(self, *args, **kwargs):
        return None


# 有超时和响应大小限制的中继客户端；所有调用走已核对的 base 地址。
class RelayClient:
    # 构造 HTTPS 客户端；仅回环测试禁用代理，生产仍保留正常 TLS 校验。
    def __init__(self, base, loopback_test=False):
        self.base = endpoint(base, loopback_test)
        handlers = [NoRedirect()]
        if loopback_test and self.base.startswith("http://127.0.0.1:"):
            handlers.append(urllib.request.ProxyHandler({}))
        self.opener = urllib.request.build_opener(*handlers)

    # 发送 JSON，返回 (HTTP状态码, JSON对象)。网络不可达返回状态码 0；畸形响应抛错。
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


# 登记状态机与运行循环，界面通过 Controller 间接调用。
class Connector:
    # 可注入已构造 runtime 或延迟构造 factory，二者不能同时指定。
    def __init__(self, store, *, loopback_test=False, runtime=None, runtime_factory=None):
        self.store, self.loopback_test = store, loopback_test
        if runtime is not None and runtime_factory is not None:
            raise ConnectorError("ambiguous_runtime")
        self.runtime = runtime
        self.runtime_factory = runtime_factory

    # 第一次配对：核对输入并先持久化设备令牌，再发送认领请求。
    # 先保存可避免响应丢失后换身份重复认领；已有身份或申请时拒绝重新配对。
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

    # 输出不含令牌的状态投影；磁盘快照不证明实时通道健康。
    def status(self):
        state = self.store.read()
        if state is None:
            return {"state": "not_paired", "executor": "unknown"}
        return {"state": state["state"], "relay": state["relay"], "name": state["name"], "terminalId": state.get("terminalId"), "executor": "unknown", "lastError": state.get("lastError"), "dataChannel": "unverified" if state.get("gatewayBinding") else "not_configured"}

    # 执行一轮状态更新；受管 runtime 只能由持有运行锁的 run 线程驱动。
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

    # 取得独占 runtime.lock 后才组装通道并进入循环，阻止重复实例同时启动。
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

    # 运行前写入保护标志，退出时先验证完整预期身份，再核对本机清理结果。
    # 只有 quiescent=True 且最终状态未变化才清标志；崩溃后的旧现场不会被自动接受。
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
        normal_state = None
        try:
            result = self._run(stop, on_status)
            # 网络中断不是进程失控。用户主动退出时仍可用原句柄和本机 drain
            # 证明安全退出；暂停/撤销/异常返回不走这条路径。
            if stop is not None and stop.is_set() and result['state'] in ('connected', 'reconnecting'):
                normal_state = result['state']
            return result
        finally:
            if self.runtime is not None:
                with self.store.lock():
                    expected = self.store.read()
                binding_matches = isinstance(expected, dict) and {
                    k: v for k, v in expected.items() if k not in ('state', 'lastError')
                } == protected_state
                self.runtime.disconnect(normal=normal_state is not None and binding_matches
                                        and expected.get('state') == normal_state)
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

    # 长期循环：连接成功每 15 秒检查，其他等待一般 3 秒，网络错误采用 1→30 秒退避。
    # stop.wait 既可等待也能被用户断开唤醒；撤销等终态直接结束。
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

    # 推进一次配对/确认/心跳：没有 pairingId 时认领，否则查询确认并发送心跳。
    # 服务器暂停与撤销不是普通断网；终端 ID 变化也不能默默接受。
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
                result, _ = client.post(f"/connector-api/terminals/{terminal_id}/heartbeat", token, {"executor": "unknown", "version": CONNECTOR_VERSION})
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
