"""Windows-only alpha.6 -> alpha.7 GUI OTA over a synthetic private HTTPS feed.

The Tk worker, signed staging, independent helper, transaction, DPAPI and Job
are real. Desktop/FRP health is explicitly replaced inside temporary signed
fixture packages. The TLS certificate, publisher key and device are synthetic.
Both fixture versions derive from this checkout, not the signed alpha.6 artifact.
No production identity, feed, package or trust store is read or modified.
"""

from datetime import datetime, timedelta, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import socket
import ssl
import sys
import tempfile
import textwrap
import threading
import time

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'windows'))
from scripts import windows_ota_smoke as gui_smoke
from updates import bootstrap, publish
from updates.processes import OwnedChild, PinnedProcess
from updates.storage import atomic, digest, lock, read
from windows.update_driver import controller_for

OLD = '0.1.0-alpha.6'
NEW = '0.1.0-alpha.7'
TOKEN = 'a' * 64
DEVICE = 'b' * 32
FEED = '/desktop-remote-updates/test/windows/'

# Only the fixture wrapper injects the temporary CA. The actual GUI worker
# still calls for_installation(), engine.check()/stage(), and handoff().
GUI = r'''import json, os, pathlib, ssl, sys, threading, time, tkinter as tk
home, old_slot, ca = map(pathlib.Path, sys.argv[1:4])
sys.path[:0] = [str(old_slot), str(old_slot / 'windows'), str(old_slot / 'windows/vendor')]
import updates.engine as engine
from updates.security import UpdateError
from updates.processes import PinnedProcess
from update_driver import controller_for
from update_ui import handoff
from gui import _Window
import gui
from updates.storage import lock
gui.messagebox.showinfo = lambda *_args, **_kwargs: None  # fixture-only modal dismissal
original_fetch, original_download = engine.fetch, engine.download
context = ssl.create_default_context(cafile=str(ca))
engine.fetch = lambda source, context=None, authorization=None: original_fetch(
    source, context=globals()['context'], authorization=authorization)
engine.download = lambda payload, destination, progress=None, context=None, authorization=None: original_download(
    payload, destination, progress=progress, context=globals()['context'], authorization=authorization)
controller = controller_for(home); controller.update_home = home
root = tk.Tk(); root.withdraw()
window = _Window(root, controller)
real_close = window.close
started = time.monotonic()
state = {'install': False, 'reported': False}
def fail(code):
    print(json.dumps({'schema': 1, 'handoff': False, 'code': code}), flush=True)
    real_close()
def gated_close():
    if state['reported']:
        return
    helper = getattr(handoff, 'helper', None)
    if helper is None:
        return fail('helper_not_created')
    try:
        with PinnedProcess({'pid': helper.pid}, current=True) as pinned:
            owner = pinned.identity
    except Exception:
        return fail('helper_pin_unverified')
    state['reported'] = True
    print(json.dumps({'schema': 1, 'handoff': True, 'helperOwner': owner}), flush=True)
    def acknowledge():
        try:
            value = json.loads(sys.stdin.buffer.readline(4097))
            if value != {'schema': 1, 'helperPinned': True}:
                raise ValueError('bad_ack')
        except Exception:
            root.after(0, lambda: fail('helper_ack_unverified'))
        else:
            root.after(0, real_close)
    threading.Thread(target=acknowledge, daemon=True).start()
window.close = gated_close
def advance():
    if state['reported'] or window.closing:
        return
    if time.monotonic() - started > 100:
        return fail('gui_worker_timeout')
    if window.update_release is not None and not window.busy and not state['install']:
        state['install'] = True
        window.update('updateinstall')
    root.after(50, advance)
root.after(100, lambda: window.update('updatecheck'))
root.after(150, advance)
with lock(home / 'state/application.lock'):
    root.mainloop()
'''


def file_sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def certificate(base):
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, '127.0.0.1')])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=1))
            .not_valid_after(now + timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName(
                [x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False)
            .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
            .sign(key, hashes.SHA256()))
    cert_path, key_path = base / 'fixture-ca.pem', base / 'fixture-tls-key.pem'
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                         serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    return cert_path, key_path


class PrivateFeed:
    def __init__(self, base):
        self.cert, key = certificate(base)
        self.files = {}
        self.requests = []
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                headers = self.headers
                valid = (headers.get('Authorization') == 'Bearer ' + TOKEN
                         and headers.get('X-Desktop-Remote-Principal') == 'device'
                         and headers.get('X-Desktop-Remote-Device') == DEVICE)
                fixture.requests.append((self.path, valid))
                if not valid:
                    self.send_response(401)
                    self.send_header('Content-Length', '0')
                    self.end_headers()
                    return
                body = fixture.files.get(self.path)
                if body is None:
                    self.send_response(404)
                    self.send_header('Content-Length', '0')
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                pass

        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(str(self.cert), str(key))
        self.server.socket = tls.wrap_socket(self.server.socket, server_side=True)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = f'https://127.0.0.1:{self.server.server_port}'

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)


def signed(base, version, sequence, publisher, source, bad=False):
    tree = gui_smoke._source_tree(base, version, not bad)
    # This fixture omits live Desktop/FRP transport, including during the
    # candidate's quarantine health check. It never changes product sources.
    entry = tree / 'windows/ota_entry.py'
    code = entry.read_text(encoding='utf-8')
    if code.count('    if paired:\n') != 1:
        raise RuntimeError('fixture_transport_hook_missing')
    entry.write_text(code.replace('    if paired:\n',
                    '    if paired and False:  # synthetic no-transport health\n'), encoding='utf-8')
    archive = base / f'package-{sequence}.zip'
    metadata = base / f'metadata-{sequence}.json'
    publish.pack('windows', archive, root=tree)
    publish.sign('windows', archive, metadata, private_key=publisher,
                 key_id='synthetic-https-gui', source=source,
                 url=source.rsplit('/', 1)[0] + f'/windows-{version}.zip',
                 sequence=sequence, root=tree)
    return archive, metadata


def free_port():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        return probe.getsockname()[1]


def run_case(base, bad=False):
    base = Path(base).resolve(strict=True)  # Normalize TEMP's short/long aliases.
    feed = PrivateFeed(base)
    old = helper_pin = None
    home = base / 'synthetic-home'
    try:
        source = feed.origin + FEED + 'latest.json'
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        private = base / 'synthetic-publisher.pem'
        private.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        public = key.public_key().public_bytes(serialization.Encoding.PEM,
                                                serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        trust = base / 'trust.json'
        atomic(trust, {'schema': 1, 'product': publish.PRODUCT, 'channel': 'test',
                       'keys': {'synthetic-https-gui': public}, 'sources': {'windows': source}})
        old_zip, old_meta = signed(base, OLD, 2, private, source)
        new_zip, new_meta = signed(base, NEW, 3, private, source, bad=bad)
        bootstrap.install(home, trust, old_meta, old_zip, 'windows')
        old_pointer = read(home / 'current.json')
        old_ledger = read(home / 'state/ledger.json')
        atomic(home / 'state/transaction.json', {'schema': 1, 'phase': 'committed',
               'success': True, 'old': {'version': 'synthetic-prior'}, 'candidate': old_pointer})
        atomic(home / 'state/maintenance-transaction.json', {'schema': 1,
               'phase': 'committed', 'success': True, 'fixture': 'prior-maintenance'})
        prior_secondary = (home / 'state/maintenance-transaction.json').read_bytes()
        atomic(home / 'state/candidate.json', {'old': {'version': 'stale'},
                                                'envelope': read(old_meta)})
        atomic(home / 'state/staged.json', {'schema': 1, 'old': {'version': 'stale'},
                                            'candidate': old_pointer, 'envelope': read(old_meta)})
        task = home / 'data/task'
        (task / 'receipts').mkdir(parents=True)
        (task / 'preserve.txt').write_text('synthetic task data', encoding='utf-8')
        python = Path(sys.executable).resolve()
        atomic(home / 'data/fixture.local.json', {'schema': 1, 'port': free_port(),
              'dataDir': str(task), 'node': {'path': str(python), 'sha256': file_sha(python)},
              'permissionsHash': '0' * 64})
        controller = controller_for(home)
        with controller.store.lock():
            controller.store.write({'schema': 1, 'state': 'connected',
                                    'deviceToken': TOKEN, 'terminalId': DEVICE,
                                    'relay': feed.origin})
        from windows.update_driver import snapshot
        baseline = snapshot(controller)
        feed.files[FEED + 'latest.json'] = new_meta.read_bytes()
        feed.files[FEED + f'windows-{NEW}.zip'] = new_zip.read_bytes()
        from updates.security import UpdateError, download, fetch
        context = ssl.create_default_context(cafile=str(feed.cert))
        for attempt in (lambda: fetch(source, context=context),
                        lambda: download({'url': feed.origin + FEED + f'windows-{NEW}.zip',
                                          'bytes': new_zip.stat().st_size, 'sha256': file_sha(new_zip)},
                                         base / 'anonymous.zip', context=context)):
            try:
                attempt()
            except UpdateError as error:
                if error.code != 'update_authentication_required':
                    raise
            else:
                raise RuntimeError('anonymous_private_feed_accepted')
        wrapper = base / 'gui_wrapper.py'
        wrapper.write_text('import traceback, pathlib, sys\ntry:\n' + textwrap.indent(GUI, '    ') +
            '\nexcept BaseException:\n'
            '    (pathlib.Path(sys.argv[1]).parent / "wrapper-error.txt").write_text(traceback.format_exc(), encoding="utf-8")\n'
            '    print("{\\"schema\\":1,\\"handoff\\":false,\\"code\\":\\"wrapper_failed\\"}", flush=True)\n'
            '    raise\n', encoding='utf-8')
        pythonw = python.with_name('pythonw.exe')
        old = OwnedChild([pythonw if pythonw.is_file() else python, '-B', wrapper,
                          home, home / old_pointer['path'], feed.cert], cwd=base)
        message = old.read(timeout=110)
        if message.get('handoff') is not True:
            raise RuntimeError('gui_worker_failed:' + str(message.get('code', 'invalid')))
        helper_pin = PinnedProcess(message['helperOwner'])
        old.write({'schema': 1, 'helperPinned': True})
        old.wait(timeout=35)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            journal = read(home / 'state/transaction.json')
            if journal.get('phase') in ('committed', 'rolled_back', 'needs_review') and journal.get('old') == old_pointer:
                break
            time.sleep(0.1)
        else:
            raise RuntimeError('transaction_timeout')
        expected_phase = 'rolled_back' if bad else 'committed'
        if journal['phase'] != expected_phase:
            raise RuntimeError('unexpected_transaction_phase:' + journal['phase'] + ':' +
                               str(journal.get('cause', journal.get('error', 'unknown'))))
        helper_pin.wait(timeout=30)
        current = read(home / 'current.json')
        if current['version'] != (OLD if bad else NEW):
            raise RuntimeError('current_version_mismatch')
        if read(home / 'state/staged.json')['old'] != old_pointer:
            raise RuntimeError('stale_staged_reused')
        if read(home / 'state/candidate.json')['old'] != old_pointer:
            raise RuntimeError('stale_candidate_reused')
        if read(home / 'state/ledger.json')['sequence'] != 3 or old_ledger['sequence'] != 2:
            raise RuntimeError('ledger_sequence_mismatch')
        if (home / 'state/maintenance-transaction.json').read_bytes() != prior_secondary:
            raise RuntimeError('prior_maintenance_journal_changed')
        if snapshot(controller) != baseline:
            raise RuntimeError('identity_or_task_data_changed')
        wanted = [(FEED + 'latest.json', False), (FEED + f'windows-{NEW}.zip', False),
                  (FEED + 'latest.json', True), (FEED + f'windows-{NEW}.zip', True)]
        if feed.requests != wanted:
            raise RuntimeError('private_feed_requests_unverified:' + repr(feed.requests))
        if not (home / old_pointer['path']).is_dir():
            raise RuntimeError('old_slot_missing')
        if old.group.active() < 1:
            raise RuntimeError('activated_gui_not_owned_by_test_job')
        try:
            with lock(home / 'state/application.lock'):
                raise RuntimeError('application_lock_not_held')
        except Exception as error:
            from updates.security import UpdateError
            if not isinstance(error, UpdateError) or error.code != 'update_or_application_already_running':
                raise
        if bad and journal.get('success') is not False:
            raise RuntimeError('rollback_success_flag_wrong')
        return expected_phase
    finally:
        if old is not None:
            old.stop_tree(timeout=15)
            if not old.tree_stopped or old.group.handle is not None:
                raise RuntimeError('fixture_job_cleanup_unconfirmed')
        if helper_pin is not None:
            helper_pin.close()
        feed.close()
        lease = home / 'state/application.lock'
        deadline = time.monotonic() + 8
        while lease.exists():
            try:
                lease.unlink()
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise RuntimeError('fixture_application_lock_still_open') from None
                time.sleep(0.05)


def main():
    if os.name != 'nt':
        raise RuntimeError('windows_only_smoke')
    for bad in (False, True):
        with tempfile.TemporaryDirectory(prefix='cdr-https-gui-') as directory:
            phase = run_case(Path(directory), bad)
            print('synthetic_https_gui_' + phase + '_identity_and_task_preserved', flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('synthetic_https_gui_failed:' + str(error)[-500:], file=sys.stderr)
        raise SystemExit(1)
