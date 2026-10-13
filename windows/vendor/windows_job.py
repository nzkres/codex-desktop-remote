# Windows Job 把本程序创建的进程及其后代放在同一个可管理的容器里。
# 先让工作进程在门口等待，再把它加入 Job，最后允许它创建子进程，避免漏管后代。
# Job 只容纳连接器的适配器/传输进程，不容纳用户的 Codex Desktop。

"""Contain this app's disposable adapter/transport trees, never Desktop tasks."""
import ctypes
import hashlib
from ctypes import wintypes as w
import os
from pathlib import Path
import subprocess
import sys
import time


# Windows Job 基础限制的内存布局，字段顺序要与原生 ABI 一致。
class BasicLimits(ctypes.Structure):
    _fields_ = [("process_time", ctypes.c_longlong), ("job_time", ctypes.c_longlong),
                ("flags", w.DWORD), ("min_working", ctypes.c_size_t), ("max_working", ctypes.c_size_t),
                ("active", w.DWORD), ("affinity", ctypes.c_size_t), ("priority", w.DWORD), ("scheduling", w.DWORD)]


# 扩展限制结构，用于设置最后一个 Job 句柄关闭时清理成员进程。
class ExtendedLimits(ctypes.Structure):
    _fields_ = [("basic", BasicLimits), ("io", ctypes.c_ulonglong * 6),
                ("process_memory", ctypes.c_size_t), ("job_memory", ctypes.c_size_t),
                ("peak_process", ctypes.c_size_t), ("peak_job", ctypes.c_size_t)]


# 查询 Job 活跃进程数量所需的原生结构；用于证明后代也已退出。
class Accounting(ctypes.Structure):
    _fields_ = [("user", ctypes.c_longlong), ("kernel", ctypes.c_longlong),
                ("period_user", ctypes.c_longlong), ("period_kernel", ctypes.c_longlong),
                ("faults", w.DWORD), ("total", w.DWORD),
                ("active", w.DWORD), ("terminated", w.DWORD)]


# 封装 Windows Job 句柄；无名称、不允许子进程脱离该管理范围。
class Job:
    # 创建 Job 并设置 KILL_ON_JOB_CLOSE。若设置失败，关闭句柄并拒绝降级启动。
    def __init__(self):
        if os.name != "nt":
            raise OSError("windows_job_required")
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        self.api.CreateJobObjectW.argtypes = [ctypes.c_void_p, w.LPCWSTR]
        self.api.CreateJobObjectW.restype = w.HANDLE
        self.api.CloseHandle.argtypes = [w.HANDLE]
        self.api.CloseHandle.restype = w.BOOL
        self.api.SetInformationJobObject.argtypes = [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD]
        self.api.SetInformationJobObject.restype = w.BOOL
        self.api.AssignProcessToJobObject.argtypes = [w.HANDLE, w.HANDLE]
        self.api.AssignProcessToJobObject.restype = w.BOOL
        self.api.TerminateJobObject.argtypes = [w.HANDLE, w.UINT]
        self.api.TerminateJobObject.restype = w.BOOL
        self.api.QueryInformationJobObject.argtypes = [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD, ctypes.c_void_p]
        self.api.QueryInformationJobObject.restype = w.BOOL
        self.handle = self.api.CreateJobObjectW(None, None)  # Unnamed, non-inheritable.
        if not self.handle:
            raise OSError("job_creation_failed")
        limits = ExtendedLimits()
        limits.basic.flags = 0x2000  # KILL_ON_JOB_CLOSE; no breakaway allowed.
        if not self.api.SetInformationJobObject(self.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
            self.close()
            raise OSError("job_limits_failed")

    # 把刚创建且仍在启动门等待的进程加入 Job，使用其原始句柄而非 PID。
    def assign(self, owned_process):
        # Popen's original Windows process handle cannot refer to a reused PID.
        # The trusted worker has not passed its gate yet.
        if not self.api.AssignProcessToJobObject(self.handle, int(owned_process._handle)):
            raise OSError("job_assignment_failed")

    # 请求终止 Job 成员；调用成功本身还不代表整棵树已经退出。
    def terminate(self):
        if not self.handle or not self.api.TerminateJobObject(self.handle, 1):
            raise OSError("job_termination_unconfirmed")

    # 读取活跃进程数；查询失败抛错，不能把未知状态当作 0。
    def active(self):
        info = Accounting()
        if not self.handle or not self.api.QueryInformationJobObject(
                self.handle, 1, ctypes.byref(info), ctypes.sizeof(info), None):
            raise OSError("job_accounting_unavailable")
        return info.active

    # 释放本对象持有的 Job 句柄，避免句柄泄漏。
    def close(self):
        if self.handle:
            self.api.CloseHandle(self.handle)
            self.handle = None


# 对外类似 Popen，同时保存 Job，扩展整棵树的退出验证。
class GuardedProcess:
    # 保存原始 Popen 和 Job 引用；尚未有树退出证明。
    def __init__(self, process, job):
        self.process, self.job = process, job
        self.tree_stopped = False

    # 把 stdin 等未包装属性交给原 Popen，保持启动接口兼容。
    def __getattr__(self, name):
        return getattr(self.process, name)

    # 只查询父进程返回码；父进程退出不代表后代全退出。
    def poll(self):
        return self.process.poll()  # Parent exit alone does not prove tree exit.

    # 终止范围为这个 Job，而不是按系统进程名批量终止。
    def terminate(self):
        self.job.terminate()

    kill = terminate

    # 等待原父进程，完整树验证另由 stop_tree 负责。
    def wait(self, timeout=None):
        return self.process.wait(timeout=timeout)

    # 用原始进程句柄等待并检查 Job 活跃数归零；自然退出与终止请求竞争也能处理。
    # 超时或查询失败都不能标记 tree_stopped；成功后关闭 Job 并可重复返回成功。
    def stop_tree(self, timeout=5):
        if self.tree_stopped:
            return True
        deadline = time.monotonic() + timeout
        # A natural exit racing termination is acceptable only when both the
        # ORIGINAL process handle and the retained Job prove all exits.
        try:
            self.job.terminate()
        except OSError:
            pass
        self.process.wait(timeout=max(0, deadline - time.monotonic()))
        while self.job.active() != 0:
            if time.monotonic() >= deadline:
                raise OSError("job_tree_exit_unconfirmed")
            time.sleep(0.02)
        self.tree_stopped = True
        self.job.close()
        return True


# 创建等待启动指令的 worker，加入 Job 后才放行。
# 异常时关闭本次 Job 并清理原 worker，不退回不受保护的启动方式。
def spawn_guarded(arguments, **options):
    """Popen-compatible transport factory. Gate prevents pre-assignment spawning.

    No adoption of existing processes; no fallback to unguarded launch on error.
    The connector alone owns the Job handle. Its death closes the process tree.
    """
    if options.get("shell") or options.get("stdin") not in (None, subprocess.DEVNULL, subprocess.PIPE):
        raise ValueError("invalid_guarded_input")
    bootstrap = options.get("stdin") == subprocess.PIPE
    job = Job()
    process = None
    try:
        options = {**options, "stdin": subprocess.PIPE, "close_fds": True}
        process = subprocess.Popen([sys.executable, "-I", str(Path(__file__).with_name("transport_worker.py")), *arguments], **options)
        job.assign(process)
        process.stdin.write(b"GB" if bootstrap else b"GN")
        process.stdin.flush()
        if not bootstrap:
            process.stdin.close()
        return GuardedProcess(process, job)
    except BaseException:
        job.close()
        if process is not None:
            if process.stdin and not process.stdin.closed:
                try:
                    process.stdin.close()  # EOF exits the gate without launching target.
                except OSError:
                    pass  # The Job may already have terminated the worker.
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=3)
        raise


# 固定 Job 实现、worker 和 Python 可执行文件的内容，避免后续重启时被替换。
def helper_pins():
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in
            (Path(__file__), Path(__file__).with_name("transport_worker.py"), Path(sys.executable))}


# 核对公开 FRP 模板/证书并构造 OwnedProcess，实际启动由 tick 触发。
def transport_process(*, executable, sha256, config_path, template, environment, cwd, ca_file, ca_sha256, probe):
    """Package-local FRP adapter. Template file is nonsecret and caller-owned.

    The caller supplies launch_material output; this is not a remote command API.
    Full package signatures are still an installation requirement.
    """
    from runtime_core import OwnedProcess
    path = Path(config_path)
    if not path.is_absolute() or path.read_text(encoding="utf-8") != template:
        raise ValueError("frp_template_mismatch")
    pins = {str(path): hashlib.sha256(path.read_bytes()).hexdigest(), str(ca_file): ca_sha256}
    # Reject helper replacement on a later restart of this same owned process.
    pins.update(helper_pins())
    process = OwnedProcess(executable, ["-c", str(path)], cwd, sha256, probe,
                           environment=environment, pinned_files=pins, process_factory=spawn_guarded)
    process.crash_contained = True
    return process
