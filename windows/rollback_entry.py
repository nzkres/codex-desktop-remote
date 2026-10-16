"""Quarantined host for verified old GUI code; never replay a failed operation.

The new updater owns this process tree. It proves native permissions/receipts
and normal transport exit using its reviewed probe, instead of re-running a
possibly broken old installation-idle check. Old code opens only after the
original parent explicitly activates it and records the rollback.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'windows'), str(ROOT)]
from updates.engine import installed
from updates.storage import no_links, read, slot, lock
from updates.security import UpdateError, verify
from update_driver import controller_for
from update_health import health, record_health_failure
from error_catalog import safe_code


def restore_context(home):
    config, pointer, old_root = installed(home)
    journal = read(Path(home) / 'state/transaction.json')
    if (config['component'] != 'windows' or journal.get('phase') != 'rollback_restoring'
            or journal.get('old') != pointer or journal.get('candidate') == pointer):
        raise UpdateError('rollback_context_unverified')
    # Standard update: this host is in the verified original slot. Maintenance
    # from a legacy version: bind the host to the exact signed staged archive.
    if ROOT != old_root.resolve(strict=True):
        staged = read(Path(home) / 'state/staged.json')
        if staged.get('old') != pointer or staged.get('candidate') != journal.get('candidate'):
            raise UpdateError('rollback_context_unverified')
        release, _ = verify(staged['envelope'], read(Path(home) / 'trust.json'), 'windows',
                            config['platform'], config['compatibility'], pointer['version'],
                            read(Path(home) / 'state/ledger.json'))
        checker, _ = slot(home, staged['candidate'])
        if checker.resolve(strict=True) != ROOT:
            raise UpdateError('rollback_context_unverified')
        bound = False
        for archive in (Path(home) / 'staging').glob('*/package.zip'):
            no_links(archive)
            if archive.stat().st_size != release['bytes']:
                continue
            with archive.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != release['sha256']:
                    continue
            with zipfile.ZipFile(archive) as bundle:
                bound = hashlib.sha256(bundle.read('package.json')).hexdigest() == staged['candidate']['manifestSha256']
            if bound:
                break
        if not bound:
            raise UpdateError('staged_package_changed')
    return pointer, old_root


def connect_restored(window, gui, attempts=120):
    if gui.closing:
        return
    if not gui.update_blocked and gui.update_phase == 'rolled_back':
        gui.connect()
    elif gui.update_phase in ('rollback_required', 'rollback_restoring') and attempts:
        window.after(250, lambda: connect_restored(window, gui, attempts - 1))


def legacy_ui(old_root):
    # Both immutable trees were checked above. Only the already-installed old
    # Controller/GUI execute; the new host supplies health and activation gates.
    sys.path[:0] = [str(old_root / 'windows'), str(old_root)]
    import gui
    import controller
    if (Path(gui.__file__).resolve() != (old_root / 'windows/gui.py').resolve()
            or Path(controller.__file__).resolve() != (old_root / 'windows/controller.py').resolve()):
        raise UpdateError('rollback_context_unverified')
    return gui


def run(home):
    import tkinter as tk
    pointer, old_root = restore_context(home)
    ui = legacy_ui(old_root)
    controller = controller_for(home, quarantine=True)
    ui.enable_dpi()
    window = tk.Tk()
    window.withdraw()
    lease = None
    try:
        try:
            health(ROOT, controller)
        except Exception as error:
            record_health_failure(home, 'rollback', pointer['version'], getattr(error, 'code', None) or str(error))
            raise
        print(json.dumps({'schema': 1, 'ready': True, 'version': pointer['version'], 'probe': 'verified'}), flush=True)
        line = sys.stdin.buffer.readline(2049)
        if len(line) > 2048 or json.loads(line) != {'schema': 1, 'op': 'activate'}:
            raise UpdateError('activation_not_authorized')
        lease = lock(Path(home) / 'state/application.lock')
        lease.__enter__()
        controller.quarantine = False
        controller.update_home = Path(home)
        eligible = controller.auto_connect_on_open()
        gui = ui._Window(window, controller)
        if eligible:
            window.after(0, lambda: connect_restored(window, gui))
        print(json.dumps({'schema': 1, 'active': True}), flush=True)
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
    args = parser.parse_args()
    try:
        run(args.home)
    except Exception as error:
        print(json.dumps({'schema': 1, 'ready': False, 'code': safe_code(getattr(error, 'code', None) or str(error))}), flush=True)
        sys.exit(1)
