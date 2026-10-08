"""Windows update lifecycle. Only a normally exited GUI can hand off its installation."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

from updates.processes import OwnedChild
from updates.security import UpdateError
from updates.storage import digest, lock, no_links, read


def controller_for(home, quarantine=False):
    from controller import Controller
    home = no_links(home)
    cfg = read(home / 'data' / 'fixture.local.json')
    data = no_links(cfg['dataDir'])
    if not data.is_relative_to(home / 'data') or data == home / 'data':
        raise UpdateError('managed_data_directory_required')
    return Controller(home / 'data' / 'fixture.local.json', home / 'data', quarantine)


def probe(root, controller):
    cfg = controller.config
    binary = no_links(cfg['node']['path'])
    with binary.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != cfg['node']['sha256']:
            raise UpdateError('runtime_binary_changed')
    child = OwnedChild([binary, Path(root) / 'scripts' / 'update_probe.mjs', controller.config_file], cwd=root)
    try:
        answer = child.read(timeout=50)
        if (set(answer) != {'idle', 'version', 'permissionsVerified', 'receiptsCertain'}
                or answer['idle'] is not True or answer['permissionsVerified'] is not True
                or answer['receiptsCertain'] is not True):
            raise UpdateError('active_or_unverified_desktop_task')
        return answer
    finally:
        child.stop_tree()


def snapshot(controller):
    with controller.store.lock():
        state = controller.store.read()
    if state and (state.get('runtimeNeedsReview') or state.get('state') != 'connected'):
        raise UpdateError('identity_or_runtime_requires_review')
    # Full decrypted identity CAS apart from explicit disposable runtime fields.
    stable = None if state is None else {k:v for k,v in state.items() if k not in (
        'adapterBinding', 'transportCrashContained', 'lastError')}
    files = {}
    directory = no_links(controller.config['dataDir'])
    for path in sorted(directory.rglob('*')):
        no_links(path)
        if path.is_file():
            with path.open('rb') as stream:
                files[path.relative_to(directory).as_posix()] = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'identity': digest(stable), 'configuration': digest(controller.config), 'taskData': files}


class WindowsDriver:
    def __init__(self, home, old_slot):
        self.home, self.old_slot = Path(home), Path(old_slot)
        self.controller = controller_for(home)
        self.application_lock = None
        self.target = None
        self.activation_started = False

    def preflight(self):
        self.application_lock = lock(self.home / 'state' / 'application.lock')
        self.application_lock.__enter__()
        try:
            with self.controller.store.lock('runtime.lock'):
                snapshot(self.controller)
                probe(self.old_slot, self.controller)
        except BaseException:
            self.release('preflight_failed')
            raise

    def snapshot(self):
        return snapshot(self.controller)

    def stop_old(self):
        # The helper pinned and waited on the ORIGINAL GUI handle before apply.
        # No runtime is adopted; the exclusive runtime lock must still be free.
        with self.controller.store.lock('runtime.lock'):
            snapshot(self.controller)

    def start(self, root):
        if self.target is not None:
            raise UpdateError('candidate_tree_exit_unconfirmed')
        self.target = OwnedChild([sys.executable, '-B', Path(root) / 'windows' / 'ota_entry.py',
                                  '--home', self.home, '--quarantine'], cwd=root)

    def verify(self, root):
        message = self.target.read(timeout=100)
        expected = read(Path(root) / 'windows' / 'releases.json')['releases'][0]['version']
        if message != {'schema': 1, 'ready': True, 'version': expected, 'probe': 'verified'}:
            raise UpdateError('candidate_health_unverified')

    def activate(self):
        self.target.allow_survive_owner_exit()
        self.activation_started = True
        self.application_lock.__exit__(None, None, None)
        self.application_lock = None
        self.target.write({'schema': 1, 'op': 'activate'})
        if self.target.read(timeout=15) != {'schema': 1, 'active': True}:
            raise UpdateError('candidate_activation_unverified')

    def cleanup_target(self):
        if self.activation_started:
            raise UpdateError('activation_outcome_requires_review')
        if self.target is not None:
            self.target.stop_tree()
            self.target = None

    def release(self, phase):
        if self.application_lock is not None:
            self.application_lock.__exit__(None, None, None)
            self.application_lock = None
        if self.target is not None:
            if self.activation_started:
                # Never kill a potentially activated GUI on ambiguous acknowledgement.
                self.target.detach_windows()
            else:
                self.target.stop_tree()

