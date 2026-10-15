# 更新候选的进程树管理：Windows 用 Job，Linux 用专用 cgroup。
# 目标是保留原始归属与退出证据；父进程退出不代表全部后代已经退出。

"""Own candidate trees with a Windows Job or delegated Linux cgroup v2."""
import json
import os
from pathlib import Path
import queue
import secrets
import subprocess
import sys
import threading
import time

from updates.security import UpdateError
from updates.storage import no_links


class LinuxGroup:
    # 用委派给当前服务的 cgroup v2 子组管理候选进程树，避免误碰其他进程。
    def __init__(self, root):
        # root 必须是已委派的 cgroup 目录；创建仅属于本次更新的子组。
        if not root:
            raise UpdateError('delegated_cgroup_required')
        root = no_links(root)
        if not root.is_absolute() or not str(root).startswith('/sys/fs/cgroup/') or not (root / 'cgroup.procs').is_file():
            raise UpdateError('delegated_cgroup_required')
        self.root = root / ('desktop-remote-' + secrets.token_hex(12))
        self.root.mkdir()
        if not (self.root / 'cgroup.kill').is_file():
            self.root.rmdir()
            raise UpdateError('cgroup_tree_kill_required')

    def assign(self, process):
        # 在子进程执行候选代码前完成归属，失败就让可信 gate 退出。
        # The child waits at the trusted gate; it has not executed package code.
        (self.root / 'cgroup.procs').write_text(str(process.pid), encoding='ascii')

    def active(self):
        # 返回组内是否仍有进程，而非只看最初 PID 是否退出。
        events = dict(line.split() for line in (self.root / 'cgroup.events').read_text().splitlines())
        return int(events['populated'])

    def terminate(self):
        # cgroup.kill 作用于整个候选进程树。
        (self.root / 'cgroup.kill').write_text('1', encoding='ascii')

    def close(self):
        # 确认进程树为空后才删本次创建的组。
        if self.active():
            self.terminate()
        # Never recursively delete cgroups or discover unrelated processes.
        if not self.active():
            self.root.rmdir()


class OwnedChild:
    # 启动并拥有候选进程树，通过逐行 JSON 与之通信。
    def __init__(self, argv, cwd=None, environment=None, cgroup_root=None):
        # argv 是候选命令；cwd/env 是运行环境；Linux 还需委派的 cgroup 根目录。
        # 先创建 Windows Job 或 Linux cgroup，再放行 child_gate 执行候选代码。
        self.group = None
        self.process = None
        self.tree_stopped = False
        self.messages = queue.Queue()
        if os.name == 'nt':
            import importlib.util
            source = Path(__file__).resolve().parents[1] / 'windows' / 'vendor' / 'windows_job.py'
            spec = importlib.util.spec_from_file_location('_update_windows_job', source)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            self.group = module.Job()
        elif sys.platform.startswith('linux'):
            self.group = LinuxGroup(cgroup_root)
        else:
            raise UpdateError('unsupported_update_platform')
        env = dict(os.environ if environment is None else environment)
        for key in list(env):
            if key.upper() in ('PYTHONPATH', 'PYTHONSTARTUP', 'PYTHONINSPECT', 'NODE_OPTIONS', 'NODE_PATH'):
                env.pop(key)
        env['PYTHONDONTWRITEBYTECODE'] = '1'
        try:
            self.process = subprocess.Popen(
                [sys.executable, '-B', '-I', str(Path(__file__).with_name('child_gate.py')), *map(str, argv)],
                cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0, close_fds=True)
            self.group.assign(self.process)
            self.process.stdin.write(b'G'); self.process.stdin.flush()
            self.reader = threading.Thread(target=self._read_loop, daemon=True)
            self.reader.start()
        except BaseException:
            if self.process and self.process.stdin:
                self.process.stdin.close()  # EOF releases an unassigned gate without executing code.
            if self.group:
                try:
                    self.group.terminate()
                finally:
                    if self.process:
                        self.process.wait(timeout=5)
                    self.group.close()
            raise UpdateError('candidate_start_failed') from None

    @property
    def pid(self):
        # 供宿主识别最初子进程；不能用它单独证明整个进程树已退出。
        return self.process.pid

    def _read_loop(self):
        # 后台读取有长度上限的 JSON 响应，放进队列交给同步 read 使用。
        while True:
            try:
                line = self.process.stdout.readline(65537)
            except (OSError, ValueError):
                return
            if not line:
                self.messages.put(UpdateError('candidate_control_closed'))
                return
            if len(line) > 65536 or not line.endswith(b'\n'):
                self.messages.put(UpdateError('invalid_candidate_response'))
                return
            try:
                message = json.loads(line)
                if not isinstance(message, dict):
                    raise ValueError()
                self.messages.put(message)
            except (ValueError, UnicodeError):
                self.messages.put(UpdateError('invalid_candidate_response'))
                return

    def write(self, message):
        # 向候选程序发送单条 JSON 命令；消息过大或管道关闭时报固定错误。
        raw = json.dumps(message, separators=(',', ':'), ensure_ascii=True).encode() + b'\n'
        if len(raw) > 65536:
            raise UpdateError('update_command_too_large')
        try:
            self.process.stdin.write(raw); self.process.stdin.flush()
        except (OSError, ValueError):
            raise UpdateError('candidate_control_closed') from None

    def read(self, timeout=30):
        # 在限定秒数内等一条响应；超时不能被解释为健康。
        try:
            value = self.messages.get(timeout=timeout)
        except queue.Empty:
            raise UpdateError('candidate_health_timeout') from None
        if isinstance(value, Exception):
            raise value
        return value

    def poll(self):
        # 只返回最初子进程状态，调用方还需要进程树证据。
        return self.process.poll()  # Never interpreted as proof of tree exit.

    def wait(self, timeout=10):
        # 等待最初子进程，timeout 单位为秒。
        return self.process.wait(timeout=timeout)

    def stop_tree(self, timeout=10):
        # 终止候选树并同时确认原始句柄退出和组为空；成功返回 True。
        # 超时或无法证明完整退出时拒绝继续切换。
        if self.tree_stopped:
            return True
        deadline = time.monotonic() + timeout
        try:
            self.group.terminate()
        except OSError:
            pass  # Natural exit is accepted only through original handle AND tree proof.
        try:
            self.process.wait(timeout=max(0.01, deadline - time.monotonic()))
            while self.group.active():
                if time.monotonic() >= deadline:
                    raise UpdateError('candidate_tree_exit_unconfirmed')
                time.sleep(0.02)
            self.tree_stopped = True
            self.group.close()
            for pipe in (self.process.stdin, self.process.stdout):
                if pipe:
                    try:
                        pipe.close()
                    except (OSError, ValueError):
                        pass  # Closed control pipes cannot negate original-handle + empty-tree proof.
            return True
        except (OSError, subprocess.TimeoutExpired):
            raise UpdateError('candidate_tree_exit_unconfirmed') from None

    def allow_survive_owner_exit(self):
        """Release kill-on-close BEFORE any user work can be admitted."""
        # Windows 新进程正式接管前移除 Job 的关闭即杀设置，使更新助手退出后服务能存活。
        if getattr(self, "lifetime_transferred", False):
            return
        if os.name != 'nt':
            raise UpdateError('windows_lifetime_transfer_required')
        import ctypes
        module = sys.modules.get('_update_windows_job')
        # Build the same layout as the group module loaded above without global imports.
        from importlib.util import spec_from_file_location, module_from_spec
        spec = spec_from_file_location('_update_job_limits', Path(__file__).resolve().parents[1] / 'windows/vendor/windows_job.py')
        module = module_from_spec(spec); spec.loader.exec_module(module)
        limits = module.ExtendedLimits()
        limits.basic.flags = 0
        if not self.group.api.SetInformationJobObject(self.group.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
            raise UpdateError('candidate_lifetime_transfer_failed')
        self.lifetime_transferred = True

    def detach_windows(self):
        # 完成生命周期转移，再关闭 Job 和控制管道。
        self.allow_survive_owner_exit()
        self.group.close()
        # The child treats EOF after activation as a normal end of the update helper.
        self.process.stdin.close()


def self_owner():
    # 返回当前 Windows 进程的可复核身份（PID、创建时间、路径、哈希）。
    if os.name != 'nt':
        raise UpdateError('windows_owner_required')
    with PinnedProcess({'pid': os.getpid()}, current=True) as handle:
        return handle.identity


class PinnedProcess:
    """Wait only, never terminate an externally started GUI; pin original Windows handle."""
    def __init__(self, expected, current=False):
        # 固定已有进程句柄并核对完整身份，避免 PID 重用误认另一进程。
        # current=True 用于获取自己当前的身份；否则 expected 必须完全匹配。
        if os.name != 'nt':
            raise UpdateError('windows_owner_required')
        import ctypes
        from ctypes import wintypes as w
        import hashlib
        self.api = ctypes.WinDLL('kernel32', use_last_error=True)
        self.api.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
        self.api.OpenProcess.restype = w.HANDLE
        self.api.GetProcessTimes.argtypes = [w.HANDLE, ctypes.POINTER(w.FILETIME), ctypes.POINTER(w.FILETIME), ctypes.POINTER(w.FILETIME), ctypes.POINTER(w.FILETIME)]
        self.api.QueryFullProcessImageNameW.argtypes = [w.HANDLE, w.DWORD, w.LPWSTR, ctypes.POINTER(w.DWORD)]
        self.api.WaitForSingleObject.argtypes = [w.HANDLE, w.DWORD]
        self.api.CloseHandle.argtypes = [w.HANDLE]
        self.handle = self.api.OpenProcess(0x1000 | 0x100000, False, expected['pid'])
        if not self.handle:
            raise UpdateError('old_process_owner_unverified')
        try:
            times = [w.FILETIME() for _ in range(4)]
            path = ctypes.create_unicode_buffer(32768); size = w.DWORD(len(path))
            if not self.api.GetProcessTimes(self.handle, *(ctypes.byref(x) for x in times)) or not self.api.QueryFullProcessImageNameW(self.handle, 0, path, ctypes.byref(size)):
                raise UpdateError('old_process_owner_unverified')
            with Path(path.value).open('rb') as stream:
                binary = hashlib.file_digest(stream, 'sha256').hexdigest()
            self.identity = {'pid': expected['pid'], 'created': (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime,
                             'path': path.value, 'sha256': binary}
            if not current and self.identity != expected:
                raise UpdateError('old_process_owner_changed')
        except BaseException:
            self.close(); raise

    def wait(self, timeout=60):
        # 只等待原句柄退出；这里不会主动终止外部 GUI。
        if self.api.WaitForSingleObject(self.handle, int(timeout * 1000)) != 0:
            raise UpdateError('old_process_exit_unconfirmed')

    def close(self):
        # 释放 Windows 句柄，不改变目标进程状态。
        if self.handle:
            self.api.CloseHandle(self.handle); self.handle = None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
