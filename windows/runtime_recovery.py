"""Explicit cold recovery of this connector; never replay or restore user data.

Legacy recovery relies on verified kill-on-close Job containment AND a complete
current process inventory, not a claim that historical Job handles were recovered.
"""
import argparse
import ctypes
from ctypes import wintypes
from contextlib import contextmanager
import hashlib
import json
import os
import re
from pathlib import Path
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'windows'))
from updates.storage import atomic, digest, ensure_launchable, lock, no_links, read
from updates.engine import installed
from updates.processes import OwnedChild
from updates.security import UpdateError


def sha(path):
    path = no_links(path)
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def task_data(controller):
    directory = no_links(controller.config['dataDir'])
    return {p.relative_to(directory).as_posix(): sha(p) for p in sorted(directory.rglob('*')) if p.is_file()}


def assert_no_owned_processes(rows, roots, own_pid):
    """Unrelated services are untouched. Unknown runtime command lines fail closed."""
    markers = [str(p).replace('/', '\\').lower() for p in roots]
    for row in rows:
        if row['ProcessId'] == own_pid:
            continue
        name = str(row['Name']).lower()
        if name not in ('node.exe', 'frpc.exe', 'python.exe', 'pythonw.exe'):
            continue
        command = row.get('CommandLine')
        if not isinstance(command, str) or not command:
            raise UpdateError('recovery_inventory_incomplete')
        normalized = command.replace('/', '\\').lower()
        if not any(marker in normalized for marker in markers):
            continue
        # The original idle GUI may stay open. runtime.lock prevents it starting
        # a new worker during recovery; a CLI/helper/worker is never exempted.
        gui = name == 'pythonw.exe' and re.search(r'\\(?:ota_entry\.py|start\.pyw)(?:"|\s|$)',normalized)
        if not gui:
            raise UpdateError('recovery_owned_process_alive')


def inventory(roots):
    if os.name != 'nt':
        raise UpdateError('windows_recovery_required')
    # Results (including command lines) stay in memory; only a refusal code is public.
    script = "Get-CimInstance Win32_Process -ErrorAction Stop | Select-Object ProcessId,Name,CommandLine | ConvertTo-Json -Compress"
    result = subprocess.run([str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'),
                             '-NoProfile', '-NonInteractive', '-Command', script],
                            capture_output=True, text=True, timeout=25,
                            creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        rows = json.loads(result.stdout)
        if result.returncode or not isinstance(rows, list) or not rows:
            raise ValueError()
        # WMI can still list an exited child while a test/owner retains its handle.
        # Only a signalled kernel process handle permits ignoring such a row.
        rows=[row for row in rows if row.get('CommandLine') or not process_exited(row['ProcessId'])]
        assert_no_owned_processes(rows, roots, os.getpid())
    except (ValueError, KeyError, TypeError):
        raise UpdateError('recovery_inventory_incomplete') from None


def process_exited(pid):
    api=ctypes.WinDLL('kernel32',use_last_error=True)
    api.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    api.OpenProcess.restype=wintypes.HANDLE
    api.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
    api.WaitForSingleObject.restype=wintypes.DWORD
    api.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=api.OpenProcess(0x100000,False,pid)
    if not handle:
        # ERROR_INVALID_PARAMETER means the enumerated PID no longer exists.
        return ctypes.get_last_error()==87
    try:
        return api.WaitForSingleObject(handle,0)==0
    finally:
        api.CloseHandle(handle)


@contextmanager
def reserve_port(port):
    with socket.socket() as reserved:
        if os.name == 'nt':
            reserved.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        try:
            reserved.bind(('127.0.0.1', port))
        except OSError:
            raise UpdateError('recovery_port_in_use') from None
        yield


def desktop_proof(root, controller, desktop_root, open_tasks=()):
    binary = no_links(controller.config['node']['path'])
    if sha(binary) != controller.config['node']['sha256']:
        raise UpdateError('runtime_binary_changed')
    argv = [binary, root/'scripts/recovery_probe.mjs', controller.config_file, desktop_root]
    argv.extend('--open-task='+identifier for identifier in open_tasks)
    child = OwnedChild(argv, cwd=root, environment={**os.environ, 'CDR_DESKTOP_PYTHON':sys.executable})
    try:
        answer = child.read(timeout=120)
        required = {'schema':1,'compatible':True,'tasksVerified':True,'permissionsVerified':True,'receiptsCertain':True,'modelInputs':0}
        count=answer.get('runningTaskCount')
        if (set(answer)!=set(required)|{'runningTaskCount'} or any(answer.get(k)!=v for k,v in required.items())
                or type(count) is not int or not 0<=count<=1008):
            code = answer.get('code', '')
            raise UpdateError(code if isinstance(code,str) and code in (
                'owner_unavailable','owner_unverified','thread_busy','permissions_changed','permissions_unverified',
                'send_unknown','update_uncertain','activity_unverified','missing_file','unsupported_version',
                'hash_mismatch','desktop_open_pending','launcher_unverified') else 'recovery_task_unverified')
        return answer
    finally:
        child.stop_tree()


def validate_guard(state):
    if not state or state.get('state') != 'connected' or not state.get('adapterBinding'):
        raise UpdateError('recovery_identity_unverified')
    if state.get('runtimeNeedsReview') is not True:
        raise UpdateError('recovery_not_required')
    if state.get('transportCrashContained') is not True:
        raise UpdateError('recovery_containment_unverified')


def commit_recovery(store, before, check_unchanged, save_configuration, save_journal, after_digest):
    """Full CAS while locked; pending journal blocks startup if interrupted."""
    with store.lock():
        if store.read() != before or not check_unchanged():
            raise UpdateError('recovery_state_changed')
        after = dict(before)
        after.pop('runtimeNeedsReview',None)
        save_journal('prepared', digest(before), digest(after))
        save_configuration()
        store.write(after)
        if store.read() != after or after_digest() is not True:
            raise UpdateError('recovery_preservation_changed')
        save_journal('committed', digest(before), digest(after))


def validate_recovery_state(state, previous, config, preserved, pointer):
    if previous and previous.get('phase')!='committed':
        if (previous.get('schema')!=1 or previous.get('phase')!='prepared'
                or digest(state) not in (previous.get('beforeIdentity'),previous.get('afterIdentity'))
                or digest(config) not in (previous.get('beforeConfiguration'),previous.get('afterConfiguration'))
                or digest(preserved)!=previous.get('taskData') or pointer!=previous.get('oldSlot')):
            raise UpdateError('recovery_interrupted')
        # Never restore an old snapshot. Recheck the CURRENT partially committed
        # state, then complete only after all live proofs pass again.
        validate_guard({**state,'runtimeNeedsReview':True})
    else:
        validate_guard(state)


def recover(controller, home, desktop_root, *, apply=False, open_tasks=()):
    home, desktop_root = no_links(home), no_links(desktop_root)
    ensure_launchable(home)
    journal = home/'state/runtime-recovery.json'
    with lock(home/'state/update.lock'), lock(home/'state/runtime-recovery.lock'), controller.store.lock('runtime.lock'):
        _, pointer, old_root = installed(home)
        # Prove the legacy containment implementation is exactly the reviewed one.
        for name in ('windows_job.py','transport_worker.py'):
            if sha(old_root/'windows/vendor'/name) != sha(ROOT/'windows/vendor'/name):
                raise UpdateError('recovery_containment_unverified')
        previous=read(journal) if journal.exists() else None
        with controller.store.lock():
            before = controller.store.read()
        config_before = read(controller.config_file)
        if config_before != controller.config:
            raise UpdateError('recovery_state_changed')
        preserved = task_data(controller)
        validate_recovery_state(before,previous,config_before,preserved,pointer)
        roots = (home, old_root, ROOT, controller.config_file, controller.config['dataDir'])
        inventory(roots)
        with reserve_port(controller.config['port']):
            proof = desktop_proof(ROOT, controller, desktop_root, open_tasks)
            inventory(roots)
            def unchanged():
                return (read(controller.config_file)==config_before and task_data(controller)==preserved
                        and read(home/'current.json')==pointer)
            config_after = {**config_before,'desktopRoot':str(desktop_root)}
            if not apply:
                with controller.store.lock():
                    if controller.store.read()!=before or not unchanged():
                        raise UpdateError('recovery_state_changed')
                return {'schema':1,'phase':'reviewed','ready':True,'proof':proof,
                        'configurationPathChanged':config_after!=config_before,'identityPreserved':True,'taskDataPreserved':True}
            def record(phase, before_hash, after_hash):
                atomic(journal,{'schema':1,'phase':phase,'time':int(time.time()),
                    'beforeIdentity':before_hash,'afterIdentity':after_hash,
                    'beforeConfiguration':digest(config_before),'afterConfiguration':digest(config_after),
                    'taskData':digest(preserved),'oldSlot':pointer,'proof':proof,
                    'containment':'verified-kill-on-close-and-no-current-owned-runtime'})
            commit_recovery(controller.store,before,unchanged,
                lambda:atomic(controller.config_file,config_after),record,
                lambda:task_data(controller)==preserved and read(controller.config_file)==config_after)
            controller.config=config_after
            return {'schema':1,'phase':'committed','identityPreserved':True,'taskDataPreserved':True,'proof':proof}


def assert_recovery_complete(controller):
    if not getattr(controller,'data_root',None):
        return
    journal=controller.data_root.parent/'state/runtime-recovery.json'
    if journal.exists() and read(journal).get('phase')!='committed':
        raise UpdateError('recovery_interrupted')


def current_desktop_root():
    if os.name!='nt':
        raise UpdateError('windows_recovery_required')
    script="@(Get-AppxPackage OpenAI.Codex -ErrorAction Stop | Select-Object -ExpandProperty InstallLocation) | ConvertTo-Json -Compress"
    result=subprocess.run([str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'),
        '-NoProfile','-NonInteractive','-Command',script],capture_output=True,text=True,timeout=15,
        creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        value=json.loads(result.stdout)
        if isinstance(value,list) and len(value)==1: value=value[0]
        if result.returncode or not isinstance(value,str) or not Path(value).is_absolute(): raise ValueError()
        return no_links(value)
    except (ValueError,TypeError):
        raise UpdateError('recovery_desktop_unverified') from None


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',required=True,type=Path)
    parser.add_argument('--desktop-root',required=True,type=Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--open-task',action='append',default=[],help='Explicit UUID of an existing recovery-scope task; at most eight, never catalog-wide')
    args=parser.parse_args()
    try:
        from update_driver import controller_for
        print(json.dumps(recover(controller_for(args.home),args.home,args.desktop_root,
                                 apply=args.apply,open_tasks=args.open_task)))
    except Exception as error:
        print(json.dumps({'schema':1,'phase':'blocked','code':error.code if isinstance(error,UpdateError) else 'recovery_failed'}))
        sys.exit(1)
