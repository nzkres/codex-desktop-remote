import sys
import unittest
from unittest.mock import patch
from contextlib import nullcontext
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows'))
from controller import LiteRuntime, Controller

class Child:
    def __init__(self, state='healthy'):
        self.state=state; self.ever_started=False; self.tree_stopped=True; self.starts=0; self.stops=0
    def tick(self):
        self.starts+=1; self.ever_started=True; self.tree_stopped=False
        return self.state
    def stop(self):
        self.stops+=1; self.state='stopped'; self.tree_stopped=True

class Tests(unittest.TestCase):
    def test_open_resumes_only_a_complete_safe_paired_identity(self):
        state = {'state':'connected', 'relay':'https://relay.example.test',
                 'terminalId':'synthetic-terminal', 'deviceToken':'synthetic-token'}
        class Store:
            def lock(self): return nullcontext()
            def read(self): return self.state
        c=Controller.__new__(Controller);c.store=Store();c.quarantine=False
        c.store.state=state
        self.assertTrue(c.auto_connect_on_open())
        for changed in (None, {}, {**state,'runtimeNeedsReview':True},
                        *({**state,'state':s} for s in ('paused','revoked','claim_pending','awaiting_confirmation')),
                        *({**state,k:''} for k in ('relay','terminalId','deviceToken'))):
            with self.subTest(state=changed):
                c.store.state=changed
                self.assertFalse(c.auto_connect_on_open())
        c.store.state=state;c.quarantine=True
        self.assertFalse(c.auto_connect_on_open())

    def test_adapter_snapshot_is_unverified_not_missing_or_healthy(self):
        class Store:
            def lock(self): return nullcontext()
            def read(self): return {'adapterBinding': {'port':18789}}
        c=Controller.__new__(Controller);c.store=Store()
        with patch('controller.Connector') as core:
            core.return_value.status.return_value={'state':'connected','dataChannel':'not_configured'}
            self.assertEqual(c.status()['dataChannel'],'unverified')
    def test_pause_never_starts_a_child(self):
        a,t=Child(),Child();r=LiteRuntime(a,t)
        self.assertEqual(r.reconcile(False)['dataChannel'],'unavailable')
        self.assertEqual(a.starts+t.starts,0)
    def test_transport_waits_for_adapter(self):
        a,t=Child('starting'),Child();r=LiteRuntime(a,t)
        r.reconcile(True);self.assertEqual(t.starts,0)
    def test_abnormal_exit_stops_only_supplied_children_and_retains_review_guard(self):
        a,t=Child(),Child();r=LiteRuntime(a,t)
        self.assertFalse(r.quiescent());self.assertEqual(r.reconcile(True)['dataChannel'],'probe_verified')
        r.disconnect();self.assertEqual((a.stops,t.stops),(1,1));self.assertFalse(r.quiescent())
        self.assertFalse(r.resume_existing({'runtimeNeedsReview':True}))
if __name__=='__main__':unittest.main()
