"""GUI entry for a verified immutable version and a separate stable data directory."""
import argparse
import json
from pathlib import Path
import queue
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "windows"))
from updates.engine import installed
from updates.storage import ensure_launchable, lock, read
from updates.security import UpdateError
from update_driver import controller_for, snapshot, probe
from gui import _Window, enable_dpi


def health(controller):
    baseline = snapshot(controller)
    probe(ROOT, controller)
    with controller.store.lock():
        paired = controller.store.read() is not None
    if paired:
        stop = threading.Event()
        outcome = queue.Queue()
        verified = threading.Event()
        def report(value):
            if value.get('state') == 'connected' and value.get('dataChannel') == 'probe_verified':
                verified.set(); stop.set()
        def connect():
            try:
                outcome.put(controller.connect(stop, report))
            except Exception:
                outcome.put(None)
        worker = threading.Thread(target=connect, daemon=True)
        worker.start()
        worker.join(65)
        stop.set()
        worker.join(15)
        if worker.is_alive() or not verified.is_set() or outcome.empty() or not isinstance(outcome.get(), dict):
            raise UpdateError('candidate_transport_unverified')
    probe(ROOT, controller)
    if snapshot(controller) != baseline:
        raise UpdateError('preservation_changed')


def run(home, quarantine=False):
    import tkinter as tk
    config, pointer, root = installed(home)
    if root != ROOT or config['component'] != 'windows':
        raise UpdateError('running_slot_changed')
    if not quarantine:
        ensure_launchable(home)
    controller = controller_for(home, quarantine)
    enable_dpi()
    window = tk.Tk()
    window.withdraw()
    lease = None
    try:
        if quarantine:
            # UI exists, but no user or phone writes are admitted during validation.
            health(controller)
            version = read(ROOT / 'windows' / 'releases.json')['releases'][0]['version']
            print(json.dumps({'schema':1, 'ready':True, 'version':version, 'probe':'verified'}), flush=True)
            line = sys.stdin.buffer.readline(2049)
            if len(line) > 2048 or json.loads(line) != {'schema':1, 'op':'activate'}:
                raise UpdateError('activation_not_authorized')
        lease = lock(Path(home) / 'state' / 'application.lock')
        lease.__enter__()
        controller.quarantine = False
        controller.update_home = Path(home)
        _Window(window, controller)
        if quarantine:
            print(json.dumps({'schema':1, 'active':True}), flush=True)
        window.deiconify()
        window.mainloop()
    finally:
        if lease is not None:
            lease.__exit__(None, None, None)
        try:
            window.destroy()
        except tk.TclError:
            pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--quarantine', action='store_true')
    args = parser.parse_args()
    try:
        run(args.home, args.quarantine)
    except Exception:
        if args.quarantine:
            print(json.dumps({'schema':1,'ready':False}), flush=True)
        else:
            import tkinter as tk
            from tkinter import messagebox
            root=tk.Tk();root.withdraw()
            messagebox.showerror('连接器未启动', '安装或更新现场需要复核。请查看 state/transaction.json；不要删除身份或保护标志。')
            root.destroy()
        sys.exit(1)

