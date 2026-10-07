"""Windows isolated reconnect: real DPAPI/locks/Node/Jobs, synthetic relay/state.

Reads only the supplied NONSECRET compatibility config. Uses new temp identity,
receipts and ports. No pairing, relay network, Desktop turn or existing PID use.
"""
import hashlib
import json
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'windows'))
from controller import LiteRuntime, Controller, prepare_disconnect
from connector_core import Connector, ConnectorError, CredentialStore
from runtime_core import OwnedProcess, LoopbackHealthProbe
from windows_job import spawn_guarded, helper_pins


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def main():
    cfg = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
    (ROOT / 'data').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='reconnect-isolated-', dir=ROOT / 'data') as directory:
        temp = Path(directory)
        cfg.update(port=free_port(), dataDir=str(temp / 'fixture'))
        Path(cfg['dataDir']).mkdir()
        config_file = temp / 'config.json'
        config_file.write_text(json.dumps(cfg), encoding='utf-8')
        receipts = Path(cfg['dataDir']) / 'receipts';receipts.mkdir()
        receipt = receipts / ('a' * 64 + '.json')
        receipt.write_text(json.dumps({'schema': 1, 'digest': 'b' * 64, 'state': 'accepted', 'turnId': 'synthetic-turn'}))
        receipt_hash = sha(receipt)
        store = CredentialStore(temp / 'identity')
        initial = {'schema': 1, 'state': 'connected', 'name': 'synthetic',
                   'relay': 'https://example.invalid', 'deviceToken': secrets.token_hex(32),
                   'terminalId': secrets.token_hex(16), 'pairingId': secrets.token_hex(16),
                   'permissions': {'scope': 'synthetic-read-only'}}
        store.write(initial)
        runtimes = []
        def factory(_store):
            with _store.lock():
                state = _store.read()
                if state.get('runtimeNeedsReview'):
                    raise ConnectorError('previous_runtime_requires_review')
            auth = {'schema': 1, 'token': secrets.token_hex(32), 'instance': secrets.token_hex(16),
                    'controlToken': secrets.token_hex(32)}
            pins = {str(p): sha(p) for p in (ROOT / 'src').rglob('*.js')}
            pins.update(helper_pins());pins[str(config_file)] = sha(config_file)
            node = OwnedProcess(cfg['node']['path'], [str(ROOT / 'src' / 'main.js'), str(config_file)], str(ROOT),
                cfg['node']['sha256'], LoopbackHealthProbe(cfg['port'], auth['token'], auth['instance']),
                bootstrap=lambda: json.dumps(auth).encode(), pinned_files=pins,
                process_factory=spawn_guarded, max_restarts=0)
            transport = OwnedProcess(sys.executable, ['-I', '-c', 'import time;time.sleep(60)'], str(temp),
                sha(sys.executable), lambda: True, process_factory=spawn_guarded)
            transport.crash_contained = True
            runtime = LiteRuntime(node, transport, lambda: prepare_disconnect(cfg['port'], auth['controlToken'], auth['instance']))
            runtimes.append(runtime)
            return runtime

        class Cycle(Connector):
            def _run(self, stop, on_status):
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    if self.runtime.reconcile(True)['dataChannel'] == 'probe_verified':
                        break
                    time.sleep(.1)
                else:
                    raise AssertionError('isolated_startup_failed')
                stop.set()
                return self.status()
        try:
            # Exclusive lease prevents even the runtime factory from executing.
            with store.lock('runtime.lock'):
                try:
                    Cycle(store, runtime_factory=factory).run(threading.Event())
                except ConnectorError as error:
                    assert str(error) == 'connector_already_running'
                else:
                    raise AssertionError('duplicate_runtime_admitted')
                assert not runtimes
            # Real occupied port rejected before network/config registration.
            controller = Controller.__new__(Controller);controller.config = cfg
            with socket.socket() as occupied:
                occupied.bind(('127.0.0.1', cfg['port']));occupied.listen()
                try:
                    controller._runtime(store)
                except OSError:
                    pass
                else:
                    raise AssertionError('occupied_port_adopted')
            for _ in range(2):
                Cycle(store, runtime_factory=factory).run(threading.Event())
                actual = store.read()
                assert not actual.get('runtimeNeedsReview')
                assert all(actual[k] == value for k, value in initial.items())
                assert runtimes[-1].quiescent()
                assert all(p.process is None and p.tree_stopped for p in (runtimes[-1].adapter, runtimes[-1].transport))
                assert sha(receipt) == receipt_hash
            # Real main's exclusive named pipe; no Desktop read/send needed.
            subprocess.run([sys.executable, '-B', str(ROOT / 'test' / 'main_smoke.py'), str(config_file)], check=True)
            unknown = receipts / ('c' * 64 + '.json')
            unknown.write_text(json.dumps({'schema': 1, 'state': 'pending', 'digest': 'd' * 64}))
            unknown_hash = sha(unknown)
            try:
                Cycle(store, runtime_factory=factory).run(threading.Event())
            except ConnectorError as error:
                assert str(error) == 'runtime_cleanup_requires_review'
            else:
                raise AssertionError('unknown_send_was_cleared')
            assert store.read()['runtimeNeedsReview'] is True
            assert sha(unknown) == unknown_hash and sha(receipt) == receipt_hash
            count = len(runtimes)
            try:
                Cycle(store, runtime_factory=factory).run(threading.Event())
            except ConnectorError as error:
                assert str(error) == 'previous_runtime_requires_review'
            else:
                raise AssertionError('review_guard_bypassed')
            assert len(runtimes) == count
            print(json.dumps({'normalReconnectCycles': 2, 'dpapiIdentityPreserved': True,
                'receiptsPreserved': True, 'unknownSendBlocksReconnect': True,
                'runtimeLockAndPortConflictRejected': True, 'bothJobTreesExited': True,
                'realServicesTouched': False, 'modelTurnsStarted': 0}))
        finally:
            for runtime in runtimes:
                for child in (runtime.transport, runtime.adapter):
                    if child.process is not None:
                        try:
                            child.stop()
                        finally:
                            if child.process is not None:
                                child.process.job.close()


if __name__ == '__main__':
    main()
