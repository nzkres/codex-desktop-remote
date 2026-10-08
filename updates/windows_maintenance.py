"""Operator-only alpha.5 -> alpha.6 reviewer-alias recovery. No force/adoption mode.

Run from a reviewed repository checkout, independently of the installed GUI.
Review never stages or stops it. Apply requires that exact review, pins the GUI,
and waits for its normal exit. A failed candidate cannot restart the buggy old
version: the existing transaction retains needs_review and the unchanged data.
"""
import argparse
import ctypes
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'windows'))
from updates.engine import installed, Transaction
from updates.processes import OwnedChild, PinnedProcess
from updates.security import UpdateError, verify, unpack
from updates.storage import atomic, digest, lock, no_links, read, slot, ensure_launchable
from update_driver import WindowsDriver, controller_for

OLD_VERSION = '0.1.0-alpha.5'
NEW_VERSION = '0.1.0-alpha.6'
REVIEW_SECONDS = 1800


def sha(path):
    with no_links(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def physical_home(home):
    path = no_links(home)
    resolved = path.resolve(strict=True)
    no_links(resolved)
    return resolved


def binding(home, allow_running=False):
    """Hash full stable identity and task files; never persist their contents."""
    controller = controller_for(home)
    if not (home / 'data/identity/identity.dpapi').is_file():
        raise UpdateError('paired_installation_required')
    with controller.store.lock():
        state = controller.store.read()
    if not state or state.get('state') != 'connected':
        raise UpdateError('connected_identity_required')
    if 'runtimeNeedsReview' in state and (not allow_running or state['runtimeNeedsReview'] is not True):
        raise UpdateError('identity_or_runtime_requires_review')
    stable = {k: v for k, v in state.items() if k not in (
        'runtimeNeedsReview', 'adapterBinding', 'transportCrashContained', 'lastError')}
    files = {}
    directory = no_links(controller.config['dataDir'])
    if not (directory / 'receipts').is_dir():
        raise UpdateError('existing_receipts_required')
    for item in sorted(directory.rglob('*')):
        no_links(item)
        files[item.relative_to(directory).as_posix()] = sha(item) if item.is_file() else 'directory'
    return {'current': read(home / 'current.json'), 'identity': digest(stable), 'taskData': files,
            'fixture': sha(home / 'data/fixture.local.json'), 'install': sha(home / 'install.json'),
            'trust': sha(home / 'trust.json'), 'ledger': digest(read(home / 'state/ledger.json')),
            'launcher': sha(home / 'Start.pyw')}


def bundle(home, metadata, archive):
    ensure_launchable(home)
    config, current, old_root = installed(home)
    if config['component'] != 'windows' or current['version'] != OLD_VERSION:
        raise UpdateError('maintenance_old_version_mismatch')
    trust, envelope = read(home / 'trust.json'), read(metadata)
    if trust['channel'] != 'test':
        raise UpdateError('maintenance_test_channel_required')
    release, ledger = verify(envelope, trust, 'windows', config['platform'], config['compatibility'],
                             current['version'], read(home / 'state/ledger.json'))
    if release['version'] != NEW_VERSION:
        raise UpdateError('maintenance_candidate_version_mismatch')
    archive = no_links(archive)
    if archive.stat().st_size != release['bytes'] or sha(archive) != release['sha256']:
        raise UpdateError('maintenance_package_changed')
    return envelope, release, ledger, old_root


def alias_probe(root, home):
    controller = controller_for(home)
    binary = no_links(controller.config['node']['path'])
    if sha(binary) != controller.config['node']['sha256']:
        raise UpdateError('runtime_binary_changed')
    child = OwnedChild([binary, root / 'scripts/update_probe.mjs', controller.config_file,
                        '--reviewer-alias-only'], cwd=root)
    try:
        answer = child.read(timeout=50)
        if answer != {'idle': True, 'version': NEW_VERSION, 'permissionsVerified': True,
                      'receiptsCertain': True, 'reviewerAliasOnly': True}:
            raise UpdateError('reviewer_alias_recovery_not_verified')
    finally:
        child.stop_tree()


def command_args(command):
    from ctypes import wintypes as w
    api = ctypes.WinDLL('shell32', use_last_error=True)
    api.CommandLineToArgvW.argtypes = [w.LPCWSTR, ctypes.POINTER(ctypes.c_int)]
    api.CommandLineToArgvW.restype = ctypes.POINTER(w.LPWSTR)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [w.HLOCAL]
    kernel.LocalFree.restype = w.HLOCAL
    count = ctypes.c_int()
    pointer = api.CommandLineToArgvW(command, ctypes.byref(count))
    if not pointer:
        raise UpdateError('gui_command_unverified')
    try:
        return [pointer[i] for i in range(count.value)]
    finally:
        kernel.LocalFree(pointer)


def verify_gui(pin, home, old_root):
    """CIM is auxiliary evidence; all waiting uses this already pinned handle."""
    if pin.api.WaitForSingleObject(pin.handle, 0) != 258:
        raise UpdateError('reviewed_gui_not_running')
    pid = pin.identity['pid']
    if type(pid) is not int or not 0 < pid <= 0xffffffff:
        raise UpdateError('invalid_gui_identity')
    command = "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); " + f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId = {pid}'; " + (
        "if ($null -eq $p) {exit 1}; $o=Invoke-CimMethod -InputObject $p -MethodName GetOwnerSid; "
        "$u=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value; "
        "@{command=$p.CommandLine; sameUser=($o.ReturnValue -eq 0 -and $o.Sid -eq $u); "
        "sameSession=($p.SessionId -eq (Get-Process -Id $PID).SessionId)} | ConvertTo-Json -Compress")
    result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', command],
                            capture_output=True, timeout=20, creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode or len(result.stdout) > 65536:
        raise UpdateError('gui_command_unverified')
    try:
        info = json.loads(result.stdout.decode('utf-8-sig'))
        args = command_args(info['command'])
        matches = (info['sameUser'] is True and info['sameSession'] is True and len(args) == 6
                   and args[1:3] == ['-B', '-I'] and args[4] == '--home'
                   and no_links(args[0]).resolve(strict=True) == Path(pin.identity['path']).resolve(strict=True)
                   and no_links(args[3]).resolve(strict=True) == old_root / 'windows/ota_entry.py'
                   and no_links(args[5]).resolve(strict=True) == home)
    except (KeyError, TypeError, ValueError, OSError):
        matches = False
    if not matches or pin.api.WaitForSingleObject(pin.handle, 0) != 258:
        raise UpdateError('gui_command_unverified')


def normal_exit(pin, timeout=120):
    from ctypes import wintypes as w
    pin.wait(timeout=timeout)
    api = pin.api
    api.GetExitCodeProcess.argtypes = [w.HANDLE, ctypes.POINTER(w.DWORD)]
    api.GetExitCodeProcess.restype = w.BOOL
    code = w.DWORD()
    if not api.GetExitCodeProcess(pin.handle, ctypes.byref(code)) or code.value != 0:
        raise UpdateError('gui_normal_exit_unverified')


def make_review(home, metadata, archive, gui_pid, output):
    if os.name != 'nt':
        raise UpdateError('windows_owner_required')
    home = physical_home(home)
    output = no_links(output)
    if output.is_relative_to(home) or output.exists() or not output.parent.is_dir():
        raise UpdateError('new_external_review_required')
    with lock(home / 'state/maintenance.lock'), lock(home / 'state/update.lock'):
        envelope, release, _, old_root = bundle(home, metadata, archive)
        before = binding(home, allow_running=True)
        with PinnedProcess({'pid': gui_pid}, current=True) as pin:
            verify_gui(pin, home, old_root)
            with tempfile.TemporaryDirectory(prefix='cdr-alias-review-') as directory:
                candidate = unpack(archive, Path(directory) / 'package', release)
                alias_probe(candidate, home)
            verify_gui(pin, home, old_root)
            if binding(home, allow_running=True) != before:
                raise UpdateError('maintenance_state_changed')
            now = int(time.time())
            value = {'schema': 1, 'kind': 'alpha5-reviewer-alias', 'home': str(home), 'owner': pin.identity,
                     'createdAt': now, 'expiresAt': now + REVIEW_SECONDS,
                     'binding': before, 'envelope': digest(envelope), 'package': release['sha256']}
        with open(output, 'x', encoding='utf-8', opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
            json.dump(value, stream, sort_keys=True)
            stream.flush(); os.fsync(stream.fileno())
    return {'reviewHash': digest(value), 'expiresAt': value['expiresAt'], 'from': OLD_VERSION,
            'to': NEW_VERSION, 'packageSha256': release['sha256'], 'aliasVerified': True}


def validate_review_integrity(value, expected):
    fields = {'schema', 'kind', 'home', 'owner', 'createdAt', 'expiresAt', 'binding', 'envelope', 'package'}
    if (not isinstance(value, dict) or set(value) != fields or value['schema'] != 1
            or value['kind'] != 'alpha5-reviewer-alias' or not re.fullmatch(r'[a-f0-9]{64}', expected)
            or not hmac.compare_digest(digest(value), expected)):
        raise UpdateError('maintenance_review_changed')
    if (type(value['createdAt']) is not int or type(value['expiresAt']) is not int
            or value['createdAt'] < 0
            or value['expiresAt'] - value['createdAt'] != REVIEW_SECONDS):
        raise UpdateError('maintenance_review_changed')


def validate_review(value, expected, now=None):
    validate_review_integrity(value, expected)
    now = int(time.time()) if now is None else now
    if not value['createdAt'] <= now < value['expiresAt']:
        raise UpdateError('maintenance_review_expired')


def stage_local(home, metadata, archive, review):
    """Verified local media only; never publishes or replaces an existing slot."""
    with lock(home / 'state/update.lock'):
        envelope, release, ledger, _ = bundle(home, metadata, archive)
        if (digest(envelope) != review['envelope'] or release['sha256'] != review['package']
                or binding(home, allow_running=True) != review['binding']):
            raise UpdateError('maintenance_state_changed')
        directory = Path(tempfile.mkdtemp(prefix='alias-', dir=home / 'staging'))
        local_archive = directory / 'package.zip'
        with no_links(archive).open('rb') as source, local_archive.open('xb') as target:
            shutil.copyfileobj(source, target)
            target.flush(); os.fsync(target.fileno())
        extracted = unpack(local_archive, directory / 'package', release)
        name = release['version'] + '-' + release['sha256'][:16]
        pointer = {'path': 'versions/' + name, 'version': release['version'],
                   'manifestSha256': sha(extracted / 'package.json')}
        destination = home / pointer['path']
        if destination.exists():
            slot(home, pointer)
        else:
            extracted.rename(destination)
        if binding(home, allow_running=True) != review['binding']:
            raise UpdateError('maintenance_state_changed')
        atomic(home / 'state/ledger.json', ledger)
        atomic(home / 'state/candidate.json', {'old': review['binding']['current'], 'envelope': envelope})
        atomic(home / 'state/staged.json', {'schema': 1, 'old': review['binding']['current'],
                                           'candidate': pointer, 'envelope': envelope})
        return pointer, {**review['binding'], 'ledger': digest(ledger)}


def fence(home, review_hash, expected):
    """Persist the existing launcher's refusal BEFORE the old GUI can exit.

    No recovery path deletes this fence. The reviewed transaction advances it
    through normal phases; errors/crashes keep a nonterminal primary journal.
    """
    with lock(home / 'state/update.lock'):
        ensure_launchable(home)
        if binding(home, allow_running=True) != expected:
            raise UpdateError('maintenance_state_changed')
        journal = home / 'state/transaction.json'
        prior = read(journal) if journal.exists() else None
        value = {'schema': 1, 'phase': 'needs_review', 'success': False,
                 'maintenanceReviewHash': review_hash, 'old': expected['current'],
                 'error': 'maintenance_waiting_normal_gui_exit'}
        if prior is not None:
            value['previousJournal'] = prior
        atomic(journal, value)
        if prior is not None:
            atomic(home / 'state' / ('history-' + secrets.token_hex(8) + '.json'), prior)
        return value


class MaintenanceTransaction(Transaction):
    """Keep the primary launch fence throughout the engine's early preflight.

    The secondary journal lets the unchanged engine do its normal checks.
    Recording it first means interruption between writes leaves the primary
    launcher/GUI gate conservative. Never infer success from the secondary alone.
    """
    def __init__(self, home, driver, review_hash):
        super().__init__(home, driver)
        self.primary = self.journal
        self.primary_expected = read(self.primary)
        if (self.primary_expected.get('phase') != 'needs_review'
                or self.primary_expected.get('maintenanceReviewHash') != review_hash
                or self.primary_expected.get('error') != 'maintenance_waiting_normal_gui_exit'):
            raise UpdateError('maintenance_fence_changed')
        self.review_hash = review_hash
        self.journal = self.home / 'state/maintenance-transaction.json'

    def record(self, phase, **extra):
        if read(self.primary) != self.primary_expected:
            raise UpdateError('maintenance_fence_changed')
        if phase == 'rolled_back':
            phase = 'needs_review'
            extra.update(success=False, error='legacy_alias_probe_requires_review')
        super().record(phase, maintenanceReviewHash=self.review_hash, **extra)
        atomic(self.primary, self.value)
        self.primary_expected = dict(self.value)

    def recover(self):
        # Review the two journals together; no automatic retry/activation.
        raise UpdateError('maintenance_interruption_requires_review')


class AliasRecoveryDriver(WindowsDriver):
    def __init__(self, home, old_root, candidate, expected):
        super().__init__(home, old_root)
        self.candidate = candidate
        self.candidate_root = home / candidate['path']
        self.expected = expected

    def snapshot(self):
        current = binding(self.home)
        if current.pop('current') not in (self.expected['current'], self.candidate):
            raise UpdateError('update_pointer_changed')
        if current != {k: v for k, v in self.expected.items() if k != 'current'}:
            raise UpdateError('preservation_changed')
        return current

    def preflight(self):
        self.application_lock = lock(self.home / 'state/application.lock')
        self.application_lock.__enter__()
        try:
            with self.controller.store.lock('runtime.lock'):
                if binding(self.home) != self.expected:
                    raise UpdateError('maintenance_state_changed')
                alias_probe(self.candidate_root, self.home)
                self.snapshot()
        except BaseException:
            self.release('preflight_failed')
            raise

    def stop_old(self):
        with self.controller.store.lock('runtime.lock'):
            if binding(self.home) != self.expected:
                raise UpdateError('maintenance_state_changed')

    def start(self, root):
        if Path(root) != self.candidate_root:
            # The retained alpha.5 has the known probe defect. The transaction
            # restores its pointer only after candidate tree cleanup, then
            # records needs_review rather than activating an unhealthy rollback.
            raise UpdateError('legacy_alias_probe_requires_review')
        super().start(root)


def apply_review(review_path, expected_hash, metadata, archive):
    if os.name != 'nt':
        raise UpdateError('windows_owner_required')
    review = read(review_path)
    validate_review(review, expected_hash)
    home = physical_home(review['home'])
    if str(home) != review['home']:
        raise UpdateError('maintenance_home_changed')
    with lock(home / 'state/maintenance.lock'):
        envelope, release, _, old_root = bundle(home, metadata, archive)
        if (digest(envelope) != review['envelope'] or release['sha256'] != review['package']
                or binding(home, allow_running=True) != review['binding']):
            raise UpdateError('maintenance_state_changed')
        with PinnedProcess(review['owner']) as pin:
            verify_gui(pin, home, old_root)
            with tempfile.TemporaryDirectory(prefix='cdr-alias-preflight-') as directory:
                candidate = unpack(archive, Path(directory) / 'package', release)
                alias_probe(candidate, home)
            pointer, expected = stage_local(home, metadata, archive, review)
            verify_gui(pin, home, old_root)
            fence(home, expected_hash, expected)
            atomic(home / 'state/maintenance.json', {'schema': 1, 'reviewHash': expected_hash,
                                                     'phase': 'waiting_normal_gui_exit'})
            print(json.dumps({'schema': 1, 'guiPinned': True, 'waitingNormalExit': True}), flush=True)
            try:
                normal_exit(pin)  # Wait only; never close windows, stop Desktop or terminate a PID.
                validate_review(review, expected_hash)
                driver = AliasRecoveryDriver(home, old_root, pointer, expected)
                result = MaintenanceTransaction(home, driver, expected_hash).apply()
            except BaseException as error:
                code = error.code if isinstance(error, UpdateError) else 'maintenance_failed_preserve_evidence'
                atomic(home / 'state/maintenance.json', {'schema': 1, 'reviewHash': expected_hash,
                                                         'phase': 'needs_review', 'code': code})
                raise
            atomic(home / 'state/maintenance.json', {'schema': 1, 'reviewHash': expected_hash,
                                                     'phase': result['phase']})
            return {'schema': 1, 'phase': result['phase'], 'success': result['success'], 'version': NEW_VERSION}


def resume_evidence(home, review, expected_hash, baseline_metadata, metadata, archive):
    """Verify a waiting-only interruption without resetting any protection.

    The old review is historical evidence only. Its exact original ledger is
    reconstructed from authenticated first-install metadata, then the sole
    permitted ledger change is recomputed from the SAME candidate release.
    """
    validate_review_integrity(review, expected_hash)
    if str(home) != review['home'] or review['createdAt'] > int(time.time()):
        raise UpdateError('maintenance_home_changed')
    if (home / 'state/maintenance-transaction.json').exists():
        raise UpdateError('maintenance_transaction_already_started')
    primary, status = read(home / 'state/transaction.json'), read(home / 'state/maintenance.json')
    fence_value = {'schema': 1, 'phase': 'needs_review', 'success': False,
                   'maintenanceReviewHash': expected_hash, 'old': review['binding']['current'],
                   'error': 'maintenance_waiting_normal_gui_exit'}
    if 'previousJournal' in primary:
        fence_value['previousJournal'] = primary['previousJournal']
    if primary != fence_value or status != {'schema': 1, 'reviewHash': expected_hash,
            'phase': 'needs_review', 'code': 'old_process_exit_unconfirmed'}:
        raise UpdateError('maintenance_wait_timeout_required')
    config, current, old_root = installed(home)
    trust, envelope = read(home / 'trust.json'), read(metadata)
    if config['component'] != 'windows' or current['version'] != OLD_VERSION or trust['channel'] != 'test':
        raise UpdateError('maintenance_old_version_mismatch')
    baseline, old_ledger = verify(read(baseline_metadata), trust, 'windows', config['platform'],
                                  config['compatibility'], None, {})
    if (baseline['version'] != OLD_VERSION or digest(old_ledger) != review['binding']['ledger']
            or current['path'] != 'versions/' + OLD_VERSION + '-' + baseline['sha256'][:16]):
        raise UpdateError('maintenance_baseline_ledger_changed')
    release, ledger = verify(envelope, trust, 'windows', config['platform'], config['compatibility'],
                             OLD_VERSION, old_ledger)
    if (release['version'] != NEW_VERSION or digest(envelope) != review['envelope']
            or release['sha256'] != review['package']):
        raise UpdateError('maintenance_candidate_changed')
    expected = {**review['binding'], 'ledger': digest(ledger)}
    if binding(home, allow_running=True) != expected:
        raise UpdateError('maintenance_state_changed')
    with tempfile.TemporaryDirectory(prefix='cdr-alias-resume-') as directory:
        extracted = unpack(no_links(archive), Path(directory) / 'package', release)
        pointer = {'path': 'versions/' + NEW_VERSION + '-' + release['sha256'][:16],
                   'version': NEW_VERSION, 'manifestSha256': sha(extracted / 'package.json')}
    slot(home, pointer)
    staged, candidate = read(home / 'state/staged.json'), read(home / 'state/candidate.json')
    if (staged != {'schema': 1, 'old': current, 'candidate': pointer, 'envelope': envelope}
            or candidate != {'old': current, 'envelope': envelope}):
        raise UpdateError('maintenance_staging_changed')
    return old_root, pointer, expected, {'primary': digest(primary), 'status': digest(status),
                                        'staged': digest(staged), 'candidate': digest(candidate)}


def resume_wait(review_path, expected_hash, baseline_metadata, metadata, archive):
    if os.name != 'nt':
        raise UpdateError('windows_owner_required')
    review = read(review_path)
    validate_review_integrity(review, expected_hash)
    home = physical_home(review['home'])
    with lock(home / 'state/maintenance.lock'):
        with lock(home / 'state/update.lock'):
            old_root, pointer, expected, evidence = resume_evidence(
                home, review, expected_hash, baseline_metadata, metadata, archive)
        with PinnedProcess(review['owner']) as pin:
            verify_gui(pin, home, old_root)
            alias_probe(home / pointer['path'], home)
            verify_gui(pin, home, old_root)
            with lock(home / 'state/update.lock'):
                if resume_evidence(home, review, expected_hash, baseline_metadata, metadata, archive) != (
                        old_root, pointer, expected, evidence):
                    raise UpdateError('maintenance_state_changed')
                now = int(time.time())
                # A new review follows fresh verification; the expired original
                # is never re-dated or used as current authorization/evidence.
                renewed = {'schema': 1, 'kind': 'alpha5-wait-timeout-review',
                           'priorReviewHash': expected_hash, 'owner': pin.identity,
                           'createdAt': now, 'expiresAt': now + REVIEW_SECONDS,
                           'binding': expected, 'evidence': evidence, 'package': review['package']}
                audit = home / 'state' / ('maintenance-resume-' + secrets.token_hex(12) + '.json')
                atomic(audit, renewed)
            print(json.dumps({'schema': 1, 'guiPinned': True, 'waitingNormalExit': True,
                              'reviewRenewed': True, 'waitSeconds': 600}), flush=True)
            try:
                normal_exit(pin, timeout=600)
                if read(audit) != renewed or int(time.time()) >= renewed['expiresAt']:
                    raise UpdateError('maintenance_review_expired')
                with lock(home / 'state/update.lock'):
                    if resume_evidence(home, review, expected_hash, baseline_metadata, metadata, archive) != (
                            old_root, pointer, expected, evidence):
                        raise UpdateError('maintenance_state_changed')
                driver = AliasRecoveryDriver(home, old_root, pointer, expected)
                result = MaintenanceTransaction(home, driver, expected_hash).apply()
            except BaseException as error:
                code = error.code if isinstance(error, UpdateError) else 'maintenance_failed_preserve_evidence'
                atomic(home / 'state/maintenance.json', {'schema': 1, 'reviewHash': expected_hash,
                                                         'phase': 'needs_review', 'code': code})
                raise
            atomic(home / 'state/maintenance.json', {'schema': 1, 'reviewHash': expected_hash,
                                                     'phase': result['phase']})
            return {'schema': 1, 'phase': result['phase'], 'success': result['success'], 'version': NEW_VERSION}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    review = sub.add_parser('review')
    review.add_argument('--home', required=True, type=Path)
    review.add_argument('--gui-pid', required=True, type=int)
    review.add_argument('--output', required=True, type=Path)
    apply = sub.add_parser('apply')
    apply.add_argument('--review', required=True, type=Path)
    apply.add_argument('--expect-review', required=True)
    resume = sub.add_parser('resume-wait', help='Freshly review only an unchanged waiting-for-GUI timeout')
    resume.add_argument('--review', required=True, type=Path)
    resume.add_argument('--expect-review', required=True)
    resume.add_argument('--baseline-metadata', required=True, type=Path)
    for action in (review, apply, resume):
        action.add_argument('--metadata', required=True, type=Path)
        action.add_argument('--package', required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'review':
            result = make_review(args.home, args.metadata, args.package, args.gui_pid, args.output)
        elif args.command == 'resume-wait':
            result = resume_wait(args.review, args.expect_review, args.baseline_metadata, args.metadata, args.package)
        else:
            result = apply_review(args.review, args.expect_review, args.metadata, args.package)
        print(json.dumps(result), flush=True)
    except Exception as error:
        code = error.code if isinstance(error, UpdateError) else 'maintenance_failed_preserve_evidence'
        print(json.dumps({'schema': 1, 'success': False, 'code': code}), flush=True)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
