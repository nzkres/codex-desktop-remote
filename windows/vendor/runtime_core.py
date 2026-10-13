# 连接器自己启动的子进程监督器，适用于 Node 适配器和 FRP 通道。
# 进程还活着、HTTP 探针健康、手机任务完成，是三个不同层面的结论。
# 只管理保存着原始进程对象的实例；不扫描或接管外部同名进程。

"""Owned-child supervision. Packaging supplies trusted launch specs and probes.

Not an installer: never discovers, adopts or kills existing processes by PID/name.
"""

import hashlib
import http.client
import json
import os
from pathlib import Path
import subprocess
import time




# 检查 127.0.0.1 上当前实例的认证健康接口，不通过任务列表推断服务健康。
class LoopbackHealthProbe:
    """Bounded authenticated probe; identity prevents accepting a different service.

    Adapter must supply a dedicated health endpoint, never a task-list request.
    Token stays in memory, not argv. No proxy, redirects or remote addresses.
    """
    # 保存端口、内存令牌和预期实例标识；构造时不发请求。
    def __init__(self, port, token, instance, path="/api/connector/health"):
        if not isinstance(port, int) or not 1 <= port <= 65535:
            raise ValueError("invalid_health_port")
        self.port, self.token, self.instance, self.path = port, token, instance, path

    # 调用对象即执行探针；响应大小、时间和实例均匹配才返回 True。
    def __call__(self):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=1)
        deadline = time.monotonic() + 2
        try:
            connection.request("GET", self.path, headers={"Authorization": "Bearer " + self.token,
                                                         "Connection": "close"})
            response = connection.getresponse()
            if response.status != 200 or response.getheader("Content-Length") is None:
                return False
            size = int(response.getheader("Content-Length"))
            if not 0 < size <= 2048:
                return False
            raw = bytearray()
            while len(raw) < size and time.monotonic() < deadline:
                chunk = response.read1(size - len(raw))
                if not chunk:
                    break
                raw.extend(chunk)
            body = json.loads(raw) if len(raw) == size else {}
            return isinstance(body, dict) and body.get("instance") == self.instance and body.get("healthy") is True
        except (OSError, ValueError, http.client.HTTPException):
            return False
        finally:
            connection.close()


# 一个自有进程的生命周期对象，保存原进程句柄、启动指纹和失败预算。
class OwnedProcess:
    # 参数描述可执行文件、参数、探针与启动工厂；此处只初始化状态，不启动。
    # process_factory 可在测试中注入假进程，生产使用 Job 包装。
    def __init__(self, executable, arguments, cwd, sha256, probe, *, clock=time.monotonic,
                 startup_grace=15, max_restarts=3, bootstrap=None, environment=None, pinned_files=None, process_factory=subprocess.Popen):
        self.executable = Path(executable)
        self.arguments = tuple(arguments)
        self.cwd = Path(cwd)
        self.sha256, self.probe, self.clock = sha256, probe, clock
        self.startup_grace, self.max_restarts = startup_grace, max_restarts
        self.process = None
        self.ever_started = False
        self.tree_stopped = True
        self.started = None
        self.retry_at = 0
        self.failures = 0
        self.state = "stopped"
        self.bootstrap, self.environment = bootstrap, environment
        self.pinned_files = dict(pinned_files or {})
        self.process_factory = process_factory

    # 启动前检查绝对路径、禁止链接，并验证可执行文件及固定文件指纹。
    def _validated(self):
        if not self.executable.is_absolute() or not self.cwd.is_absolute():
            return False
        for path in (self.executable, self.cwd, *(Path(p) for p in self.pinned_files)):
            if not path.is_absolute():
                return False
            for part in (path, *path.parents):
                if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
                    return False
        if not self.cwd.is_dir() or not self.executable.is_file():
            return False
        with self.executable.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != self.sha256:
                return False
        for path, digest in self.pinned_files.items():
            with Path(path).open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                    return False
        return True

    # 只使用原进程对象清理；生产 Job 包装必须证明整棵树退出。
    # 若曾启动却丢了句柄，不能凭父进程不在就认定退出。
    def _stop_child(self):
        process = self.process
        if process is None and self.ever_started and not self.tree_stopped:
            # A launch factory may fail after opening its gate. Without its
            # original handles, no retry or clean-disconnect claim is allowed.
            self.state = 'failed'
            raise OSError('owned_launch_exit_unconfirmed')
        if process is not None:
            if hasattr(process, "stop_tree"):
                # Retain handles and the review guard if any proof is missing.
                self.tree_stopped = process.stop_tree() is True
                if not self.tree_stopped:
                    raise OSError("job_tree_exit_unconfirmed")
            elif process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=3)
            if process.stdin is not None and not process.stdin.closed:
                try:
                    process.stdin.close()
                except OSError:
                    pass  # Child may already have closed its read end.
            self.process = None

    # 先确认旧树退出，再累计失败并设置退避时间；超过预算进入 failed。
    def _failed(self):
        self._stop_child()
        self.failures += 1
        self.retry_at = self.clock() + min(30, 2 ** (self.failures - 1))
        self.state = "failed" if self.failures > self.max_restarts else "backoff"

    # 一次推进：检查原进程是否退出、是否到重试时间、是否允许启动、探针是否通过。
    # 活着但探针不健康只标 unhealthy，不因单次探针失败就强杀。
    def tick(self):
        now = self.clock()
        if self.state in ("failed", "invalid_binary"):
            return self.state
        if self.process is not None and self.process.poll() is not None:
            self._failed()
            return self.state
        if self.process is None:
            if now < self.retry_at:
                return self.state
            try:
                if not self._validated():
                    self.state = "invalid_binary"
                    return self.state
                payload = self.bootstrap() if self.bootstrap else None
                if payload is not None and (not isinstance(payload, bytes) or len(payload) > 1024):
                    self.state = "invalid_binary"
                    return self.state
                self.ever_started = True
                self.tree_stopped = False
                self.process = self.process_factory(
                    [str(self.executable), *self.arguments], cwd=self.cwd,
                    stdin=subprocess.PIPE if payload is not None else subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL, shell=False, close_fds=True,
                    env=self.environment,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                if payload is not None:
                    self.process.stdin.write(payload)
                    self.process.stdin.close()
                self.started = now
            except OSError:
                self._failed()
                return self.state
        try:
            healthy = self.probe() is True
        except Exception:
            healthy = False
        if healthy and self.process.poll() is None:
            self.state = "healthy"
        elif now - self.started >= self.startup_grace:
            # A live but unhealthy Gateway may still own a task. Do not kill it
            # just because a probe timed out. Crash restart is a separate policy.
            self.state = "unhealthy"
        else:
            self.state = "starting"
        return self.state

    # 清理自有进程；暂停/继续不会重置失败预算，避免无限崩溃循环。
    def stop(self):
        self._stop_child()
        if self.state not in ("failed", "invalid_binary"):
            self.state = "stopped"
        # Pause/resume must not reset the crash budget.


# 返回一个可调用探针，通过中继注册当前实例并检查分配通道。
# 该请求具有注册副作用，不应误当纯本地只读检查。
def tunnel_probe(client, terminal_id, device_token, bootstrap):
    """FRP health callback: relay probes its assigned private port, never a supplied URL.

    RelayClient enforces HTTPS except explicit loopback fixtures. Re-registering
    is idempotent and restores the in-memory relay key after a relay restart.
    """
    import re
    if not re.fullmatch(r"[0-9a-f]{32}", terminal_id):
        raise ValueError("invalid_terminal_id")
    # 按设备身份登记 token/instance，只有服务端 dataChannel=verified 才通过。
    def probe():
        status, body = client.post(f"/connector-api/terminals/{terminal_id}/gateway", device_token,
                                   {"token": bootstrap["token"], "instance": bootstrap["instance"]})
        return status == 200 and body.get("dataChannel") == "verified"
    return probe
