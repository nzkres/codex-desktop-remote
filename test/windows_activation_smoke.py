"""Actual Tk + worker startup in isolated state, never a real connector identity."""
from contextlib import contextmanager
import importlib.util
import io
import json
from pathlib import Path
import queue
import sys
import tempfile
import threading
import time
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT),str(ROOT/'windows')]
import ota_entry
from controller import Controller,ConnectorError
from gui import _Window
from updates.storage import atomic

class Store:
    def __init__(self):self.mutex=threading.Lock()
    @contextmanager
    def lock(self):
        if not self.mutex.acquire(False):raise ConnectorError('connector_already_running')
        try:yield
        finally:self.mutex.release()
    def read(self):return {'state':'connected','relay':'https://synthetic.example.test','terminalId':'synthetic','deviceToken':'synthetic'}

class ControllerFixture:
    auto_connect_on_open=Controller.auto_connect_on_open
    def __init__(self):
        self.store=Store();self.quarantine=True;self.status_started=threading.Event();self.connects=0
    def status(self):
        with self.store.lock():
            self.status_started.set();time.sleep(.4)
        return {'state':'connected','relay':'https://synthetic.example.test','runtimeNeedsReview':False}
    def connect(self,stop,report):
        assert json.loads((self.update_home/'state/transaction.json').read_bytes())['phase']=='committed'
        self.connects+=1
        report({'state':'connected','dataChannel':'probe_verified'})
        stop.wait(4)
        return {'state':'connected','runtimeNeedsReview':False}

def exercise(module,expect_old_failure=False):
    fixture=ControllerFixture();windows=[]
    with tempfile.TemporaryDirectory(prefix='synthetic-activation-') as directory:
        home=Path(directory);atomic(home/'state/transaction.json',{'phase':'validated'})
        def construct(root,c):
            gui=_Window(root,c);windows.append(gui)
            assert c.status_started.wait(2),'worker did not start'
            root.after(200,lambda:atomic(home/'state/transaction.json',{'phase':'committed'}))
            root.after(1300,gui.close)
            return gui
        stdin=Mock(buffer=io.BytesIO(b'{"schema":1,"op":"activate"}\n'))
        @contextmanager
        def lease(*args):yield
        try:
            with patch.multiple(module,installed=Mock(return_value=({'component':'windows'},{},module.ROOT)),
                                same_running_slot=Mock(return_value=True),controller_for=Mock(return_value=fixture),
                                health=Mock(),lock=lease,_Window=construct),patch.object(sys,'stdin',stdin):
                try:module.run(home,quarantine=True)
                except ConnectorError as e:
                    assert expect_old_failure and str(e)=='connector_already_running'
                else:assert not expect_old_failure,'old startup race was not reproduced'
            assert fixture.connects==(0 if expect_old_failure else 1)
        finally:
            for gui in windows:
                gui.stop_event.set();gui.commands.put(('close',));gui.worker.join(6)
                assert not gui.worker.is_alive(),'synthetic worker leaked'
    return {'oldRaceReproduced':expect_old_failure,'connections':fixture.connects,'workerExited':True}

if __name__=='__main__':
    results={}
    if len(sys.argv)>1:
        spec=importlib.util.spec_from_file_location('old_startup',sys.argv[1]);old=importlib.util.module_from_spec(spec);spec.loader.exec_module(old)
        results['old']=exercise(old,True)
    results['fixed']=exercise(ota_entry)
    print(json.dumps({'actualTkWindow':True,'syntheticIdentityOnly':True,'realModelInputs':0,**results}))
