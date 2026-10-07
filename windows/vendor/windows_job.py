"""Contain this app's disposable adapter/transport trees, never Desktop tasks."""
import ctypes
import hashlib
from ctypes import wintypes as w
import os
from pathlib import Path
import subprocess
import sys
import time


class BasicLimits(ctypes.Structure):
    _fields_ = [("process_time", ctypes.c_longlong), ("job_time", ctypes.c_longlong),
                ("flags", w.DWORD), ("min_working", ctypes.c_size_t), ("max_working", ctypes.c_size_t),
                ("active", w.DWORD), ("affinity", ctypes.c_size_t), ("priority", w.DWORD), ("scheduling", w.DWORD)]


class ExtendedLimits(ctypes.Structure):
    _fields_ = [("basic", BasicLimits), ("io", ctypes.c_ulonglong * 6),
                ("process_memory", ctypes.c_size_t), ("job_memory", ctypes.c_size_t),
                ("peak_process", ctypes.c_size_t), ("peak_job", ctypes.c_size_t)]


class Accounting(ctypes.Structure):
    _fields_ = [("user", ctypes.c_longlong), ("kernel", ctypes.c_longlong),
                ("period_user", ctypes.c_longlong), ("period_kernel", ctypes.c_longlong),
                ("faults", w.DWORD), ("total", w.DWORD),
                ("active", w.DWORD), ("terminated", w.DWORD)]


class Job:
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

    def assign(self, owned_process):
        # Popen's original Windows process handle cannot refer to a reused PID.
        # The trusted worker has not passed its gate yet.
        if not self.api.AssignProcessToJobObject(self.handle, int(owned_process._handle)):
            raise OSError("job_assignment_failed")

    def terminate(self):
        if not self.handle or not self.api.TerminateJobObject(self.handle, 1):
            raise OSError("job_termination_unconfirmed")

    def active(self):
        info = Accounting()
        if not self.handle or not self.api.QueryInformationJobObject(
                self.handle, 1, ctypes.byref(info), ctypes.sizeof(info), None):
            raise OSError("job_accounting_unavailable")
        return info.active

    def close(self):
        if self.handle:
            self.api.CloseHandle(self.handle)
            self.handle = None


class GuardedProcess:
    def __init__(self, process, job):
        self.process, self.job = process, job
        self.tree_stopped = False

    def __getattr__(self, name):
        return getattr(self.process, name)

    def poll(self):
        return self.process.poll()  # Parent exit alone does not prove tree exit.

    def terminate(self):
        self.job.terminate()

    kill = terminate

    def wait(self, timeout=None):
        return self.process.wait(timeout=timeout)

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


def helper_pins():
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in
            (Path(__file__), Path(__file__).with_name("transport_worker.py"), Path(sys.executable))}


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
