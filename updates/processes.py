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
    def __init__(self, root):
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
        # The child waits at the trusted gate; it has not executed package code.
        (self.root / 'cgroup.procs').write_text(str(process.pid), encoding='ascii')

    def active(self):
        events = dict(line.split() for line in (self.root / 'cgroup.events').read_text().splitlines())
        return int(events['populated'])

    def terminate(self):
        (self.root / 'cgroup.kill').write_text('1', encoding='ascii')

    def close(self):
        if self.active():
            self.terminate()
        # Never recursively delete cgroups or discover unrelated processes.
        if not self.active():
            self.root.rmdir()


class OwnedChild:
    def __init__(self, argv, cwd=None, environment=None, cgroup_root=None):
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
        return self.process.pid

    def _read_loop(self):
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
        raw = json.dumps(message, separators=(',', ':'), ensure_ascii=True).encode() + b'\n'
        if len(raw) > 65536:
            raise UpdateError('update_command_too_large')
        try:
            self.process.stdin.write(raw); self.process.stdin.flush()
        except (OSError, ValueError):
            raise UpdateError('candidate_control_closed') from None

    def read(self, timeout=30):
        try:
            value = self.messages.get(timeout=timeout)
        except queue.Empty:
            raise UpdateError('candidate_health_timeout') from None
        if isinstance(value, Exception):
            raise value
        return value

    def poll(self):
        return self.process.poll()  # Never interpreted as proof of tree exit.

    def wait(self, timeout=10):
        return self.process.wait(timeout=timeout)

    def stop_tree(self, timeout=10):
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
                    pipe.close()
            return True
        except (OSError, subprocess.TimeoutExpired):
            raise UpdateError('candidate_tree_exit_unconfirmed') from None

    def detach_windows(self):
        """Transfer an activated GUI's lifetime to itself after the journal commits."""
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
        self.group.close()
        # The child treats EOF after activation as a normal end of the update helper.
        self.process.stdin.close()


def self_owner():
    if os.name != 'nt':
        raise UpdateError('windows_owner_required')
    with PinnedProcess({'pid': os.getpid()}, current=True) as handle:
        return handle.identity


class PinnedProcess:
    """Wait only, never terminate an externally started GUI; pin original Windows handle."""
    def __init__(self, expected, current=False):
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
        if self.api.WaitForSingleObject(self.handle, int(timeout * 1000)) != 0:
            raise UpdateError('old_process_exit_unconfirmed')

    def close(self):
        if self.handle:
            self.api.CloseHandle(self.handle); self.handle = None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
