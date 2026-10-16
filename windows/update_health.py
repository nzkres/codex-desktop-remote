"""Quarantined health is not a second installation-idle admission gate.

Preflight still requires idle. Once admitted, Desktop can start its own work;
health verifies ownership, permissions, receipts, normal transport exit and
unchanged connector data without stopping or sending to those native tasks.
"""
import queue
import re
import threading
from pathlib import Path

from updates.storage import atomic
from updates.security import UpdateError
from update_driver import snapshot
from error_catalog import safe_code
from runtime_recovery import desktop_proof


def native_check(root, controller):
    return desktop_proof(root, controller, Path(controller.config['desktopRoot']))


def health(root, controller):
    baseline = snapshot(controller)
    native_check(root, controller)
    with controller.store.lock():
        paired = controller.store.read() is not None
    if paired:
        stop, verified = threading.Event(), threading.Event()
        outcome = queue.Queue()

        def report(value):
            if value.get('state') == 'connected' and value.get('dataChannel') == 'probe_verified':
                verified.set()
                stop.set()

        def connect():
            try:
                outcome.put(controller.connect(stop, report))
            except Exception as error:
                # Codes only; arbitrary exception text cannot enter diagnostics.
                outcome.put(safe_code(getattr(error, 'code', None) or str(error)))

        worker = threading.Thread(target=connect, daemon=True)
        worker.start()
        worker.join(65)
        stop.set()
        worker.join(15)
        if worker.is_alive() or outcome.empty():
            raise UpdateError('candidate_transport_unverified')
        result = outcome.get()
        if isinstance(result, str):
            raise UpdateError(result)
        if not verified.is_set() or not isinstance(result, dict):
            raise UpdateError('candidate_transport_unverified')
    native_check(root, controller)
    if snapshot(controller) != baseline:
        raise UpdateError('preservation_changed')


def record_health_failure(home, role, version, code):
    """Keep candidate and rollback evidence separate and exclude raw errors."""
    if role not in ('candidate', 'rollback') or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?', version):
        raise UpdateError('invalid_candidate_response')
    record = {'schema': 1, 'version': version, 'ready': False, 'code': safe_code(code)}
    atomic(Path(home) / 'state' / f'{role}-health-{version}.json', record)
    if role == 'candidate':
        # Compatibility for existing helpers; rollback NEVER overwrites this.
        atomic(Path(home) / 'state' / 'candidate-health.json', record)
