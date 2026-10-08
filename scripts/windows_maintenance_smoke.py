"""Windows-only maintenance rehearsal with synthetic identity/trust and Tk GUIs.

No Desktop, relay, FRP or real account is contacted. Only signed TEST fixture
sources replace the Desktop probe/transport health with deterministic responses
and let the old Tk GUI receive a normal-close request over its test stdin.
Production maintenance, original process handles, DPAPI, file locks, signed
staging, transaction journals and Job containment are exercised unchanged.
"""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'windows'))
from updates import bootstrap, publish, windows_maintenance as maintenance
from updates.processes import OwnedChild
from updates.storage import atomic, read, lock, ensure_launchable
from updates.security import UpdateError
from update_driver import controller_for

SOURCE = 'https://updates.example.test/desktop-remote-updates/test/windows/latest.json'
PROBE = '''import json, sys
answer = {'idle': GOOD, 'version': VERSION, 'permissionsVerified': GOOD, 'receiptsCertain': GOOD}
if '--reviewer-alias-only' in sys.argv:
    answer.update(idle=True, permissionsVerified=True, receiptsCertain=True, reviewerAliasOnly=True)
print(json.dumps(answer), flush=True)
'''
TEST_CLOSE = '''        fixture_window = _Window(window, controller)
        if not quarantine:
            def fixture_close():
                from updates.processes import self_owner
                print(json.dumps({'schema': 1, 'fixtureGui': self_owner()}), flush=True)
                if json.loads(sys.stdin.buffer.readline(2049)) != {'fixtureClose': True}:
                    raise RuntimeError('fixture_close_unverified')
                fixture_window.close()
            window.after(100, fixture_close)
'''


def signed(base, version, sequence, key, good):
    source = base / ('source-' + version)
    for name, data in publish._files(ROOT, 'windows').items():
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    catalog = read(source / 'windows/releases.json')
    catalog['releases'] = [dict(catalog['releases'][0], version=version)]
    atomic(source / 'windows/releases.json', catalog)
    (source / 'scripts/update_probe.mjs').write_text(
        PROBE.replace('GOOD', repr(good)).replace('VERSION', repr(version)), encoding='utf-8')
    entry = source / 'windows/ota_entry.py'
    code = entry.read_text(encoding='utf-8')
    assert code.count('    if paired:\n') == 1
    assert code.count('        _Window(window, controller)\n') == 1
    code = code.replace('    if paired:\n', '    if paired and False:  # SYNTHETIC: no transport/network\n')
    code = code.replace('        _Window(window, controller)\n', TEST_CLOSE)
    entry.write_text(code, encoding='utf-8')
    archive, metadata = base / f'package-{sequence}.zip', base / f'metadata-{sequence}.json'
    publish.pack('windows', archive, root=source)
    publish.sign('windows', archive, metadata, private_key=key, key_id='synthetic-maintenance',
                 source=SOURCE, url=SOURCE.rsplit('/', 1)[0] + f'/windows-{version}.zip',
                 sequence=sequence, root=source)
    return archive, metadata


def run_case(base, bad=False):
    # CI TEMP can use an 8.3 username. Production requires a physical home and
    # a dataDir under that same spelling; make the fixture match that contract.
    base = Path(base).resolve(strict=True)
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    private = base / 'synthetic-key.pem'
    private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))
    public = key.public_key().public_bytes(serialization.Encoding.PEM,
                                         serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    trust = base / 'trust.json'
    atomic(trust, {'schema': 1, 'product': publish.PRODUCT, 'channel': 'test',
                  'keys': {'synthetic-maintenance': public}, 'sources': {'windows': SOURCE}})
    old_zip, old_meta = signed(base, maintenance.OLD_VERSION, 1, private, False)
    new_zip, new_meta = signed(base, maintenance.NEW_VERSION, 2, private, not bad)
    home = base / 'synthetic-home'
    bootstrap.install(home, trust, old_meta, old_zip, 'windows')
    old_pointer = read(home / 'current.json')
    (home / 'data/task/receipts').mkdir(parents=True)
    (home / 'data/task/preserve.txt').write_text('synthetic data must remain unchanged', encoding='utf-8')
    python = Path(sys.executable).resolve()
    atomic(home / 'data/fixture.local.json', {'schema': 1, 'port': 37652,
        'dataDir': str(home / 'data/task'), 'permissionsHash': '0' * 64,
        'node': {'path': str(python), 'sha256': hashlib.sha256(python.read_bytes()).hexdigest()}})
    controller = controller_for(home)
    with controller.store.lock():
        controller.store.write({'schema': 1, 'state': 'connected', 'deviceToken': 'a' * 64,
                                'terminalId': 'synthetic-only', 'relay': 'https://updates.example.test'})
    before = maintenance.binding(home)
    pythonw = python.with_name('pythonw.exe')
    old = OwnedChild([pythonw, '-B', '-I', home / old_pointer['path'] / 'windows/ota_entry.py',
                      '--home', home], cwd=base)
    helper = None
    try:
        message = old.read(timeout=30)
        owner = message['fixtureGui']
        report = base / 'review.json'
        review = maintenance.make_review(home, new_meta, new_zip, owner['pid'], report)
        assert not (home / 'state/staged.json').exists()
        helper = OwnedChild([python, '-B', ROOT / 'updates/windows_maintenance.py', 'apply',
            '--review', report, '--expect-review', review['reviewHash'],
            '--metadata', new_meta, '--package', new_zip], cwd=ROOT)
        ready = helper.read(timeout=60)
        assert ready == {'schema': 1, 'guiPinned': True, 'waitingNormalExit': True}, ready
        try:
            ensure_launchable(home)
        except UpdateError as error:
            assert error.code == 'interrupted_update_requires_review', error.code
        else:
            raise AssertionError('cold launch not fenced')
        old.write({'fixtureClose': True})
        assert old.wait(timeout=30) == 0
        # The independent helper continues after the old Tk application exits.
        outcome = helper.read(timeout=90)
        helper.wait(timeout=20)
        primary = read(home / 'state/transaction.json')
        secondary = read(home / 'state/maintenance-transaction.json')
        assert primary == secondary
        after = maintenance.binding(home)
        assert {k: v for k, v in before.items() if k not in ('current', 'ledger')} == {
            k: v for k, v in after.items() if k not in ('current', 'ledger')}
        assert (home / old_pointer['path']).is_dir()
        if bad:
            assert outcome['success'] is False and primary['phase'] == 'needs_review', outcome
            assert read(home / 'current.json') == old_pointer
            assert helper.group.active() == 0  # candidate and all descendants gone
            with lock(home / 'state/application.lock'):
                pass
            try:
                ensure_launchable(home)
            except UpdateError:
                pass
            else:
                raise AssertionError('failed candidate allowed old cold launch')
        else:
            assert outcome['success'] is True and primary['phase'] == 'committed', outcome
            assert read(home / 'current.json')['version'] == maintenance.NEW_VERSION
            ensure_launchable(home)
            assert helper.group.active() > 0  # new GUI survives helper exit in test outer Job
            try:
                with lock(home / 'state/application.lock'):
                    raise AssertionError('activated GUI lease missing')
            except UpdateError as error:
                assert error.code == 'update_or_application_already_running'
        return primary['phase']
    finally:
        for process in (helper, old):
            if process is not None:
                process.stop_tree(timeout=15)
                assert process.tree_stopped and process.group.handle is None
        # Bound cleanup to this temporary fixture's exact lock file.
        lease = home / 'state/application.lock'
        deadline = time.monotonic() + 8
        while lease.exists():
            try:
                lease.unlink()
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.05)


if __name__ == '__main__':
    if os.name != 'nt':
        raise SystemExit('Windows required')
    for bad in (False, True):
        with tempfile.TemporaryDirectory(prefix='cdr-maintenance-') as directory:
            phase = run_case(Path(directory), bad)
            print('synthetic_paired_maintenance_' + phase + ' identity_and_data_preserved', flush=True)
