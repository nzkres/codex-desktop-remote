"""Actual adapter startup/duplicate-instance smoke. No pairing or model turns."""
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'windows'/'vendor'))
from runtime_core import LoopbackHealthProbe
from windows_job import spawn_guarded
filename=Path(sys.argv[1]).resolve();cfg=json.loads(filename.read_text(encoding='utf-8'))
root=Path(__file__).resolve().parents[1]
payload={'schema':1,'token':secrets.token_hex(32),'instance':secrets.token_hex(16),'controlToken':secrets.token_hex(32)}
def launch():
    process=spawn_guarded([cfg['node']['path'],str(root/'src'/'main.js'),str(filename)],cwd=root,
        stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    process.stdin.write(json.dumps(payload).encode());process.stdin.close()
    return process
one=launch();two=None
try:
    probe=LoopbackHealthProbe(cfg['port'],payload['token'],payload['instance'])
    deadline=time.monotonic()+10
    while not probe() and time.monotonic()<deadline:
        assert one.poll() is None,'adapter_exited'
        time.sleep(.2)
    assert probe(),'adapter_not_healthy'
    two=launch();assert two.wait(timeout=10)!=0,'duplicate_adapter_admitted'
    assert probe(),'original_adapter_disturbed'
    print(json.dumps({'adapterStarted':True,'duplicateRejected':True,'originalHealthy':True,'modelTurnsStarted':0}))
finally:
    for process in (two,one):
        if process is not None:
            process.stop_tree()
