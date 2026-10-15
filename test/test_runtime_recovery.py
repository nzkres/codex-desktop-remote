import copy
from contextlib import nullcontext
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'windows'))
from runtime_recovery import assert_no_owned_processes,commit_recovery,validate_guard,validate_recovery_state
from updates.storage import digest
from updates.security import UpdateError

class Store:
    def __init__(self,state): self.state=copy.deepcopy(state);self.writes=0
    def lock(self): return nullcontext()
    def read(self): return copy.deepcopy(self.state)
    def write(self,state): self.state=copy.deepcopy(state);self.writes+=1

class RecoveryTests(unittest.TestCase):
    def state(self): return {'state':'connected','deviceToken':'synthetic-token','adapterBinding':{'port':12345},
                            'runtimeNeedsReview':True,'transportCrashContained':True}
    def test_missing_containment_or_unknown_identity_never_recovers(self):
        validate_guard(self.state())
        for field in ('state','adapterBinding','runtimeNeedsReview','transportCrashContained'):
            state=self.state();state.pop(field)
            with self.assertRaises(UpdateError):validate_guard(state)
    def test_only_exact_idle_gui_is_exempt_and_other_apps_remain_untouched(self):
        roots=[r'C:\fixture\connector']
        assert_no_owned_processes([{'ProcessId':4,'Name':'frpc.exe','CommandLine':r'frpc -c C:\unrelated\frp.toml'},
           {'ProcessId':5,'Name':'pythonw.exe','CommandLine':r'pythonw "C:\fixture\connector\Start.pyw"'}],roots,1)
        for name,cmd in [('node.exe',r'node C:\fixture\connector\src\main.js'),
                         ('python.exe',r'python C:\fixture\connector\windows\vendor\transport_worker.py'),
                         ('pythonw.exe',r'pythonw C:\fixture\connector\other.py')]:
            with self.assertRaises(UpdateError):assert_no_owned_processes([{'ProcessId':8,'Name':name,'CommandLine':cmd}],roots,1)
    def test_inventory_failure_is_never_absence(self):
        with self.assertRaises(UpdateError):assert_no_owned_processes([{'ProcessId':3,'Name':'node.exe','CommandLine':None}],['C:/fixture'],1)
    def test_full_identity_cas_only_removes_guard_and_journal_precedes_write(self):
        before=self.state();store=Store(before);events=[]
        commit_recovery(store,before,lambda:True,lambda:events.append('config'),
            lambda phase,*hashes:events.append(phase),lambda:True)
        expected=copy.deepcopy(before);expected.pop('runtimeNeedsReview')
        self.assertEqual(store.state,expected);self.assertEqual(events,['prepared','config','committed'])
    def test_concurrent_identity_configuration_or_receipt_change_blocks_before_write(self):
        for change in ('identity','data'):
            before=self.state();store=Store(before);events=[]
            if change=='identity':store.state['deviceToken']='other'
            with self.assertRaises(UpdateError):commit_recovery(store,before,lambda:change!='data',lambda:events.append('config'),
                lambda *args:events.append('journal'),lambda:True)
            self.assertEqual(store.writes,0);self.assertEqual(events,[])
    def test_failed_prepared_journal_keeps_protection(self):
        before=self.state();store=Store(before)
        def fail(*args):raise OSError('synthetic disk failure')
        with self.assertRaises(OSError):commit_recovery(store,before,lambda:True,lambda:None,fail,lambda:True)
        self.assertEqual(store.state,before)
    def test_preservation_failure_never_records_commit(self):
        before=self.state();store=Store(before);phases=[]
        with self.assertRaises(UpdateError):commit_recovery(store,before,lambda:True,lambda:None,
            lambda p,*a:phases.append(p),lambda:False)
        self.assertEqual(phases,['prepared'])
    def test_interrupted_commit_rechecks_current_state_without_restore(self):
        before=self.state();after=dict(before);after.pop('runtimeNeedsReview')
        cfg={'desktopRoot':'synthetic-old'};new={'desktopRoot':'synthetic-new'};data={'receipts':'synthetic-digest'};pointer={'version':'fixture'}
        journal={'schema':1,'phase':'prepared','beforeIdentity':digest(before),'afterIdentity':digest(after),
            'beforeConfiguration':digest(cfg),'afterConfiguration':digest(new),'taskData':digest(data),'oldSlot':pointer}
        for state,config in [(before,cfg),(before,new),(after,new)]:
            validate_recovery_state(state,journal,config,data,pointer)
        for field in ('deviceToken','transportCrashContained'):
            changed=dict(after);changed[field]='changed'
            with self.assertRaises(UpdateError):validate_recovery_state(changed,journal,new,data,pointer)
        with self.assertRaises(UpdateError):validate_recovery_state(after,journal,new,{'receipts':'new'},pointer)

if __name__=='__main__':unittest.main()
