"""Signed staging and a journaled switch. Drivers retain ownership and admission gates."""
import hashlib
from pathlib import Path
import re
import secrets

from updates.security import UpdateError, verify, fetch, download, unpack, verify_tree
from updates.storage import read, atomic, digest, lock, slot, no_links


def installed(home):
    home = no_links(home)
    config, current = read(home / 'install.json'), read(home / 'current.json')
    if config.get('schema') != 1 or config.get('component') not in ('windows', 'relay'):
        raise UpdateError('invalid_installation')
    root, manifest = slot(home, current)
    if any(manifest.get(k) != config.get(k) for k in ('component', 'platform')):
        raise UpdateError('wrong_installed_component')
    return config, current, root


def check(home, context=None, authorization=None):
    home = no_links(home)
    with lock(home / 'state' / 'update.lock'):
        config, current, _ = installed(home)
        trust = read(home / 'trust.json')
        source = trust.get('sources', {}).get(config['component'])
        if not source or not trust.get('keys'):
            raise UpdateError('update_trust_not_provisioned')
        options = {} if authorization is None else {'authorization': authorization}
        envelope = fetch(source, context=context, **options)
        ledger_path = home / 'state' / 'ledger.json'
        ledger = read(ledger_path) if ledger_path.exists() else {}
        release, ledger = verify(envelope, trust, config['component'], config['platform'],
                                 config['compatibility'], current['version'], ledger)
        atomic(ledger_path, ledger)
        atomic(home / 'state' / 'candidate.json', {'old': current, 'envelope': envelope})
        return release


def stage(home, progress=None, context=None, authorization=None):
    home = no_links(home)
    with lock(home / 'state' / 'update.lock'):
        config, current, _ = installed(home)
        candidate = read(home / 'state' / 'candidate.json')
        if candidate['old'] != current:
            raise UpdateError('update_pointer_changed')
        release, _ = verify(candidate['envelope'], read(home / 'trust.json'), config['component'],
                            config['platform'], config['compatibility'], current['version'],
                            read(home / 'state' / 'ledger.json'))
        directory = home / 'staging' / secrets.token_hex(12)
        directory.mkdir(parents=True)
        archive = directory / 'package.zip'
        options = {} if authorization is None else {'authorization': authorization}
        download(release, archive, progress=progress, context=context, **options)
        target = unpack(archive, directory / 'package', release)
        manifest = verify_tree(target, expected_component=config['component'], expected_version=release['version'])
        if manifest['platform'] != config['platform']:
            raise UpdateError('wrong_release_target')
        name = release['version'] + '-' + release['sha256'][:16]
        version = home / 'versions' / name
        pointer = {'path': 'versions/' + name, 'version': release['version'],
                   'manifestSha256': hashlib.sha256((target / 'package.json').read_bytes()).hexdigest()}
        if version.exists():
            slot(home, pointer)
        else:
            target.rename(version)
        request = {'schema': 1, 'old': current, 'candidate': pointer, 'envelope': candidate['envelope']}
        atomic(home / 'state' / 'staged.json', request)
        return release


class Transaction:
    def __init__(self, home, driver):
        self.home, self.driver = no_links(home), driver
        self.journal = self.home / 'state' / 'transaction.json'
        self.pointer = self.home / 'current.json'

    def record(self, phase, **extra):
        self.value.update(phase=phase, **extra)
        atomic(self.journal, self.value)

    def cas(self, old, new):
        if read(self.pointer) != old:
            raise UpdateError('update_pointer_changed')
        atomic(self.pointer, new)

    def recover(self):
        with lock(self.home / 'state' / 'update.lock'):
            self.value = read(self.journal)
            if self.value.get('phase') not in ('committed', 'rolled_back', 'needs_review'):
                self.record('needs_review', success=False, error='interrupted_update_requires_review')
            return self.value

    def apply(self):
        with lock(self.home / 'state' / 'update.lock'):
            if self.journal.exists():
                prior = read(self.journal)
                if prior.get('phase') not in ('committed', 'rolled_back'):
                    raise UpdateError('unfinished_update_requires_review')
                # Preserve prior evidence; never erase failed/pending transactions.
                atomic(self.home / 'state' / ('history-' + secrets.token_hex(8) + '.json'), prior)
            config, old, old_root = installed(self.home)
            request = read(self.home / 'state' / 'staged.json')
            if request.get('schema') != 1 or request.get('old') != old:
                raise UpdateError('update_pointer_changed')
            release, _ = verify(request['envelope'], read(self.home / 'trust.json'), config['component'],
                                config['platform'], config['compatibility'], old['version'],
                                read(self.home / 'state' / 'ledger.json'))
            candidate = request['candidate']
            new_root, manifest = slot(self.home, candidate)
            if manifest['component'] != config['component'] or manifest['platform'] != config['platform'] or candidate['version'] != release['version']:
                raise UpdateError('wrong_release_target')
            # Revalidate the archive-to-slot binding recorded after verified extraction.
            if candidate['path'] != 'versions/' + release['version'] + '-' + release['sha256'][:16]:
                raise UpdateError('staged_package_changed')
            # A fresh archive digest protects against a replaced manifest and pointer together.
            matches = []
            for archive in (self.home / 'staging').glob('*/package.zip'):
                no_links(archive)
                if archive.stat().st_size == release['bytes']:
                    with archive.open('rb') as stream:
                        if hashlib.file_digest(stream, 'sha256').hexdigest() == release['sha256']:
                            matches.append(archive)
            if not matches:
                raise UpdateError('staged_package_missing')
            import zipfile
            with zipfile.ZipFile(matches[-1]) as bundle:
                if hashlib.sha256(bundle.read('package.json')).hexdigest() != candidate['manifestSha256']:
                    raise UpdateError('staged_package_changed')
            stopped = False
            activation_started = False
            self.driver.preflight()
            try:
                self.value = {'schema': 1, 'old': old, 'candidate': candidate,
                              'releaseDigest': digest(request['envelope']),
                              'preserved': self.driver.snapshot(), 'success': False}
                self.record('prepared')
                self.driver.stop_old()
                stopped = True
                self.record('old_stopped')
                self.preserved()
                self.cas(old, candidate)
                self.record('switched')
                self.driver.start(new_root)
                self.driver.verify(new_root)
                self.preserved()
                self.record('validated')
                activation_started = True
                self.driver.activate()
                self.record('committed', success=True)
            except BaseException as failure:
                if not hasattr(self, 'value'):
                    raise
                cause = (failure.code if isinstance(failure, UpdateError)
                         and isinstance(failure.code, str)
                         and re.fullmatch(r'[a-z0-9_]{1,80}', failure.code)
                         else 'update_operation_failed')
                if activation_started:
                    # An activation acknowledgement or journal write can be lost
                    # after new work was admitted. Never roll it back or replay.
                    self.record('needs_review', success=False, error='activation_outcome_requires_review')
                elif not stopped:
                    self.record('needs_review', error='old_exit_unconfirmed')
                else:
                    self.record('rollback_required', cause=cause)
                    try:
                        self.driver.cleanup_target()
                        self.preserved()
                        current = read(self.pointer)
                        if current == candidate:
                            self.cas(candidate, old)
                        elif current != old:
                            raise UpdateError('update_pointer_changed')
                        self.driver.start(old_root)
                        self.driver.verify(old_root)
                        self.preserved()
                        self.driver.activate()
                        self.record('rolled_back', success=False)
                    except BaseException:
                        self.record('needs_review', error='rollback_or_cleanup_unconfirmed')
                raise UpdateError('update_' + self.value['phase']) from None
            finally:
                self.driver.release(getattr(self, 'value', {}).get('phase', 'needs_review'))
            return self.value

    def preserved(self):
        if self.driver.snapshot() != self.value['preserved']:
            raise UpdateError('preservation_changed')
