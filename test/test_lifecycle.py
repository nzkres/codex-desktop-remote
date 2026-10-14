"""Synthetic lifecycle checks: no processes, credentials, Desktop RPC, or network."""
import copy
import sys
import threading
import unittest
from contextlib import nullcontext
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows'))
from controller import LiteRuntime
from connector_core import Connector, ConnectorError, CONNECTOR_VERSION
from release_notes import VERSION
from runtime_core import OwnedProcess


class Store:
    def __init__(self):
        self.state = {'state': 'connected', 'deviceToken': 'synthetic-identity',
                      'terminalId': 'synthetic-terminal', 'name': 'fixture'}
        self.receipts = {'accepted-message': 'synthetic-turn'}

    def lock(self, name=None):
        return nullcontext()

    def read(self):
        return copy.deepcopy(self.state)

    def write(self, value):
        self.state = copy.deepcopy(value)


class Child:
    def __init__(self, *, started=True, stop_error=False, tree_stopped=None, state='healthy'):
        self.ever_started = started
        self.tree_stopped = not started if tree_stopped is None else tree_stopped
        self.stop_error = stop_error
        self.state = state
        self.stops = 0

    def tick(self):
        return self.state

    def stop(self):
        self.stops += 1
        if self.stop_error:
            raise OSError('synthetic cleanup failure')
        self.tree_stopped = True
        self.state = 'stopped'


def runtime(*, adapter=None, transport=None, drain=None):
    calls = []
    def accepted_drain():
        calls.append('drain')
        return True
    r = LiteRuntime(adapter or Child(), transport or Child(), drain or accepted_drain)
    r.allowed = True
    return r, calls


def run(core, stop, result):
    with patch.object(core, '_run', side_effect=result if isinstance(result, Exception) else None,
                      return_value=None if isinstance(result, Exception) else result):
        return core._owned_run(stop)


class LifecycleTests(unittest.TestCase):
    def test_heartbeat_uses_the_bundled_window_version(self):
        store = Store()
        terminal = 'a' * 32
        store.state.update(relay='https://relay.example.test', pairingId='b' * 32, terminalId=terminal)
        with patch('connector_core.RelayClient') as client:
            client.return_value.post.side_effect = [
                (200, {'state': 'confirmed', 'terminalId': terminal, 'terminalState': 'active'}),
                (204, None),
            ]
            Connector(store).tick()
            self.assertEqual(client.return_value.post.call_args.args[2], {'executor': 'unknown', 'version': VERSION})
            self.assertEqual(CONNECTOR_VERSION, VERSION)

    def test_launch_factory_failure_cannot_claim_empty_tree_or_retry(self):
        process = OwnedProcess('unused', [], 'unused', 'unused', lambda: True,
                               process_factory=lambda *a, **kw: (_ for _ in ()).throw(OSError('synthetic gate failure')))
        with patch.object(process, '_validated', return_value=True):
            with self.assertRaisesRegex(OSError, 'owned_launch_exit_unconfirmed'):
                process.tick()
        self.assertTrue(process.ever_started)
        self.assertFalse(process.tree_stopped)
        self.assertEqual(process.tick(), 'failed')
        with self.assertRaisesRegex(OSError, 'owned_launch_exit_unconfirmed'):
            process.stop()

    def test_binding_change_before_drain_is_not_accepted_as_new_baseline(self):
        store = Store(); r, calls = runtime()
        core = Connector(store, runtime=r)
        stop = threading.Event(); stop.set()
        def changed(*args):
            store.state['deviceToken'] = 'unexpected-synthetic-replacement'
            return {'state': 'connected'}
        with patch.object(core, '_run', side_effect=changed):
            with self.assertRaisesRegex(ConnectorError, 'identity_changed'):
                core._owned_run(stop)
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(calls, [])
        self.assertFalse(r.quiescent())

    def test_normal_stop_clears_only_guard_and_same_identity_can_reconnect(self):
        store = Store()
        stop = threading.Event(); stop.set()
        for _ in range(2):
            r, calls = runtime()
            result = run(Connector(store, runtime=r), stop, {'state': 'connected'})
            self.assertEqual(result['state'], 'connected')
            self.assertEqual(calls, ['drain'])
            self.assertEqual((r.adapter.stops, r.transport.stops), (1, 1))
            self.assertTrue(r.quiescent())
            self.assertNotIn('runtimeNeedsReview', store.read())
            self.assertEqual(store.read()['deviceToken'], 'synthetic-identity')
            self.assertEqual(store.receipts, {'accepted-message': 'synthetic-turn'})

    def test_offline_close_drains_locally_then_reuses_original_identity(self):
        store = Store()
        store.state.update(relay='https://relay.example.test', pairingId='b' * 32, terminalId='a' * 32)
        original = store.read()
        stop = threading.Event()
        r, calls = runtime()
        with patch('connector_core.RelayClient') as client:
            client.return_value.post.return_value = (0, {'error': 'relay_network_unavailable'})
            client.return_value.failure_code.return_value = 'relay_network_unavailable'
            result = Connector(store, runtime=r).run(stop, lambda report: stop.set())
        self.assertEqual(result['state'], 'reconnecting')
        self.assertFalse(r.allowed)
        self.assertEqual(calls, ['drain'])
        self.assertTrue(r.quiescent())
        self.assertNotIn('runtimeNeedsReview', store.read())
        self.assertEqual(store.read()['deviceToken'], original['deviceToken'])
        self.assertEqual(store.receipts, {'accepted-message': 'synthetic-turn'})
        # A new run obtains its own children; it reuses the same device, never claims again.
        stop.clear()
        next_runtime, next_calls = runtime()
        with patch('connector_core.RelayClient') as client:
            client.return_value.post.side_effect = [
                (200, {'state': 'confirmed', 'terminalId': 'a' * 32, 'terminalState': 'active'}),
                (204, {}),
            ]
            result = Connector(store, runtime=next_runtime).run(stop, lambda report: stop.set())
            self.assertTrue(all('/claim' not in c.args[0] for c in client.return_value.post.call_args_list))
        self.assertEqual(result['state'], 'connected')
        self.assertEqual(next_calls, ['drain'])
        self.assertEqual(store.read()['deviceToken'], original['deviceToken'])
        self.assertNotIn('runtimeNeedsReview', store.read())

    def test_offline_exit_still_refuses_uncertain_drain_failed_tree_and_changed_identity(self):
        for failure in ('drain', 'tree', 'identity'):
            with self.subTest(failure=failure):
                store = Store()
                r, calls = runtime(transport=Child(),
                                   drain=(lambda: False) if failure == 'drain' else None)
                stop = threading.Event(); stop.set()
                def offline(*args):
                    r.reconcile(False)
                    r.transport.stop_error = failure == 'tree'
                    store.state['state'] = 'reconnecting'
                    if failure == 'identity':
                        store.state['deviceToken'] = 'unexpected-synthetic-replacement'
                    return {'state': 'reconnecting'}
                with patch.object(Connector, '_run', side_effect=offline):
                    with self.assertRaises(ConnectorError):
                        Connector(store, runtime=r)._owned_run(stop)
                self.assertTrue(store.read()['runtimeNeedsReview'])
                self.assertFalse(r.quiescent())
                self.assertGreaterEqual(r.adapter.stops, 1)
                self.assertGreaterEqual(r.transport.stops, 1)

    def test_network_status_change_at_exit_does_not_accept_a_different_snapshot(self):
        store = Store(); r, calls = runtime()
        stop = threading.Event(); stop.set()
        store.state['state'] = 'paused'
        run(Connector(store, runtime=r), stop, {'state': 'reconnecting'})
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(calls, [])

    def test_abnormal_exception_keeps_guard_even_when_both_trees_stop(self):
        store = Store(); r, calls = runtime()
        with self.assertRaisesRegex(RuntimeError, 'synthetic failure'):
            run(Connector(store, runtime=r), threading.Event(), RuntimeError('synthetic failure'))
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(calls, [])
        self.assertEqual((r.adapter.stops, r.transport.stops), (1, 1))
        self.assertFalse(r.quiescent())

    def test_paused_or_revoked_result_never_becomes_normal_stop(self):
        for state in ('paused', 'revoked'):
            with self.subTest(state=state):
                store = Store(); r, calls = runtime()
                stop = threading.Event(); stop.set()
                run(Connector(store, runtime=r), stop, {'state': state})
                self.assertTrue(store.read()['runtimeNeedsReview'])
                self.assertEqual(calls, [])

    def test_stop_event_is_required_even_if_run_reports_connected(self):
        store = Store(); r, calls = runtime()
        run(Connector(store, runtime=r), threading.Event(), {'state': 'connected'})
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(calls, [])

    def test_drain_rejection_or_exception_keeps_guard_but_stops_both_children(self):
        for drain in (lambda: False, lambda: (_ for _ in ()).throw(OSError('synthetic drain failure'))):
            with self.subTest(drain=drain):
                store = Store(); r, _ = runtime(drain=drain)
                stop = threading.Event(); stop.set()
                with self.assertRaisesRegex(ConnectorError, 'runtime_cleanup_requires_review'):
                    run(Connector(store, runtime=r), stop, {'state': 'connected'})
                self.assertTrue(store.read()['runtimeNeedsReview'])
                self.assertEqual((r.adapter.stops, r.transport.stops), (1, 1))
                self.assertFalse(r.quiescent())

    def test_each_child_tree_must_be_proven_empty_and_both_stops_attempted(self):
        for failing in ('adapter', 'transport'):
            with self.subTest(failing=failing):
                store = Store()
                a = Child(stop_error=failing == 'adapter')
                t = Child(stop_error=failing == 'transport')
                r, calls = runtime(adapter=a, transport=t)
                stop = threading.Event(); stop.set()
                with self.assertRaisesRegex(ConnectorError, 'runtime_cleanup_requires_review'):
                    run(Connector(store, runtime=r), stop, {'state': 'connected'})
                self.assertEqual(calls, ['drain'])
                self.assertEqual((a.stops, t.stops), (1, 1))
                self.assertTrue(store.read()['runtimeNeedsReview'])

    def test_never_started_and_empty_trees_can_retry_after_abnormal_exit(self):
        store = Store()
        r, calls = runtime(adapter=Child(started=False), transport=Child(started=False))
        with self.assertRaisesRegex(RuntimeError, 'synthetic failure'):
            run(Connector(store, runtime=r), threading.Event(), RuntimeError('synthetic failure'))
        self.assertTrue(r.quiescent())
        self.assertEqual(calls, [])
        self.assertNotIn('runtimeNeedsReview', store.read())

    def test_disconnect_is_once_only_and_abnormal_cannot_be_upgraded(self):
        r, calls = runtime()
        r.disconnect(normal=False)
        r.disconnect(normal=True)
        self.assertEqual(calls, [])
        self.assertEqual((r.adapter.stops, r.transport.stops), (1, 1))
        self.assertFalse(r.quiescent())
        clean, calls = runtime()
        clean.disconnect(normal=True)
        clean.disconnect(normal=False)
        self.assertEqual(calls, ['drain'])
        self.assertEqual((clean.adapter.stops, clean.transport.stops), (1, 1))
        self.assertTrue(clean.quiescent())

    def test_full_state_cas_change_during_drain_retains_guard(self):
        store = Store()
        def change_state():
            state = store.read(); state['name'] = 'changed-during-drain'; store.write(state)
            return True
        r, _ = runtime(drain=change_state)
        stop = threading.Event(); stop.set()
        with self.assertRaisesRegex(ConnectorError, 'identity_changed'):
            run(Connector(store, runtime=r), stop, {'state': 'connected'})
        self.assertTrue(r.quiescent())
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(store.read()['name'], 'changed-during-drain')

    def test_faulted_adapter_cannot_drain_as_normal(self):
        store = Store(); a = Child(state='failed'); t = Child()
        r, calls = runtime(adapter=a, transport=t)
        r.reconcile(True)
        stop = threading.Event(); stop.set()
        with self.assertRaisesRegex(ConnectorError, 'runtime_cleanup_requires_review'):
            run(Connector(store, runtime=r), stop, {'state': 'connected'})
        self.assertTrue(store.read()['runtimeNeedsReview'])
        self.assertEqual(calls, [])


if __name__ == '__main__':
    unittest.main()
