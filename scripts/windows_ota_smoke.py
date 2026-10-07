"""Isolated Windows GUI/helper OTA rehearsal with synthetic trust and Desktop probe.

Creates a new private unpaired installation in TemporaryDirectory. The only
probe executable is this test's Python interpreter; the signed fixture .mjs is
Python source that reports a fixed idle state. No Desktop, FRP, phone or network
operation occurs. HTTPS transport has separate tests; check/stage only have
their fetch/download calls replaced with a local signed fixture.
"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import textwrap
import time

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from updates import bootstrap, publish
from updates.processes import OwnedChild
from updates.storage import read


SOURCE = "https://updates.example.test/test/windows/latest.json"
PROBE = '''import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
version = json.loads((root / "windows/releases.json").read_text(encoding="utf-8"))["releases"][0]["version"]
print(json.dumps({"idle": GOOD, "version": version, "permissionsVerified": GOOD, "receiptsCertain": GOOD}), flush=True)
'''
OLD_GUI = r'''import hashlib, json, os, pathlib, re, shutil, sys, time, tkinter as tk
home, metadata, archive, slot = map(pathlib.Path, sys.argv[1:5])
def mark(value):
    (home.parent / 'wrapper-stage.txt').write_text(value, encoding='ascii')
mark('imports')
sys.path.insert(0, str(slot))
sys.path.insert(0, str(slot / 'windows'))
sys.path.insert(0, str(slot / 'windows/vendor'))
import updates.engine as engine
from updates.storage import lock
from updates.security import UpdateError
from update_driver import controller_for
from update_ui import handoff
from gui import _Window

def local_fetch(source, context=None):
    return json.loads(metadata.read_text(encoding='utf-8'))

def local_download(payload, destination, progress=None, context=None):
    destination = pathlib.Path(destination)
    with archive.open('rb') as source, destination.open('xb') as target:
        shutil.copyfileobj(source, target)
        target.flush(); os.fsync(target.fileno())
    if progress:
        progress(destination.stat().st_size, payload['bytes'])
    return destination

engine.fetch = local_fetch
engine.download = local_download
mark('controller')
controller = controller_for(home)
controller.update_home = home
mark('tk_root')
root = tk.Tk(); root.withdraw()
mark('window')
window = _Window(root, controller)
mark('window_ready')

def begin():
    stage = 'check'
    try:
        mark(stage)
        engine.check(home)
        stage = 'stage'
        mark(stage)
        engine.stage(home)
        stage = 'handoff'
        mark(stage)
        handoff(home)
        mark('handoff_complete')
        print(json.dumps({'schema':1,'handoff':True}),flush=True)
    except Exception as error:
        code = error.code if isinstance(error, UpdateError) and isinstance(error.code, str) and re.fullmatch(r'[a-z0-9_]{1,80}', error.code) else 'smoke_operation_failed'
        if stage == 'handoff' and code == 'update_handoff_unverified':
            result = home / 'state/helper-result.json'
            deadline = time.monotonic() + 1
            while not result.exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            if result.exists():
                try:
                    helper = json.loads(result.read_text(encoding='utf-8'))
                    if helper.get('schema') == 1 and re.fullmatch(r'[a-z0-9_]{1,80}', helper.get('code', '')):
                        code = helper['code']
                except (OSError, ValueError, TypeError):
                    pass
        print(json.dumps({'schema':1,'handoff':False,'stage':stage,'code':code}),flush=True)
    finally:
        window.close()

with lock(home / 'state/application.lock'):
    root.after(100, begin)
    root.mainloop()
'''


def _digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _source_tree(base, version, good_probe):
    root = base / ("source-" + version + ("-bad" if not good_probe else ""))
    for name, data in publish._files(ROOT, "windows").items():
        destination = root / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    catalog_path = root / "windows/releases.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    release = next((item for item in catalog["releases"] if item["version"] == version), None)
    if release is None:
        release = dict(catalog["releases"][0], version=version)
    catalog["releases"] = [release]
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
    (root / "scripts/update_probe.mjs").write_text(
        PROBE.replace("GOOD", "True" if good_probe else "False"), encoding="utf-8")
    return root


def _signed(base, version, sequence, key_path, bad_probe=False):
    root = _source_tree(base, version, not bad_probe)
    archive = base / f"package-{sequence}.zip"
    metadata = base / f"metadata-{sequence}.json"
    publish.pack("windows", archive, root=root)
    publish.sign("windows", archive, metadata, private_key=key_path,
                 key_id="synthetic-gui-smoke", source=SOURCE,
                 url=f"https://updates.example.test/test/windows/package-{sequence}.zip",
                 sequence=sequence, root=root)
    return archive, metadata


def _run_case(base, bad_candidate=False):
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    key_path = base / "synthetic-publisher.pem"
    key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                         serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    public = key.public_key().public_bytes(serialization.Encoding.PEM,
                         serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    trust = {"schema": 1, "product": publish.PRODUCT, "channel": "test",
             "keys": {"synthetic-gui-smoke": public}, "sources": {"windows": SOURCE}}
    trust_path = base / "trust.json"
    trust_path.write_text(json.dumps(trust), encoding="utf-8")
    old_archive, old_metadata = _signed(base, "0.0.1", 1, key_path)
    candidate_archive, candidate_metadata = _signed(base, "0.0.2", 2, key_path,
                                                       bad_probe=bad_candidate)
    home = base / "fresh-home"
    bootstrap.install(home, trust_path, old_metadata, old_archive, "windows")
    old_pointer = read(home / "current.json")
    (home / "data" / "fixture-task").mkdir()
    python = Path(sys.executable).resolve()
    config = {"schema": 1, "port": 37651, "dataDir": str(home / "data" / "fixture-task"),
              "node": {"path": str(python), "sha256": _digest(python)},
              "permissionsHash": "0" * 64}
    (home / "data" / "fixture.local.json").write_text(json.dumps(config), encoding="utf-8")
    wrapper = base / "old_gui_wrapper.py"
    wrapper.write_text("import traceback, pathlib, sys\ntry:\n" +
                       textwrap.indent(OLD_GUI, "    ") +
                       "\nexcept BaseException:\n"
                       "    (pathlib.Path(sys.argv[1]).parent / 'wrapper-error.txt').write_text(traceback.format_exc(), encoding='utf-8')\n"
                       "    print('{\"schema\":1,\"handoff\":false,\"stage\":\"startup\",\"code\":\"smoke_wrapper_start_failed\"}', flush=True)\n"
                       "    raise\n", encoding="utf-8")
    pythonw = python.with_name("pythonw.exe")
    gui_python = pythonw if pythonw.is_file() else python
    old = OwnedChild([gui_python, "-B", wrapper, home, candidate_metadata,
                      candidate_archive, home / old_pointer["path"]], cwd=base)
    try:
        try:
            message = old.read(timeout=40)
        except Exception as failure:
            diagnostic = home.parent / "wrapper-error.txt"
            if diagnostic.exists():
                raise RuntimeError("wrapper:" + diagnostic.read_text(encoding="utf-8")[-800:]) from None
            stage_path = home.parent / 'wrapper-stage.txt'
            stage = stage_path.read_text(encoding='ascii') if stage_path.exists() else 'pre_wrapper'
            from updates.security import UpdateError
            code = failure.code if isinstance(failure, UpdateError) else 'fixture_response_failed'
            raise RuntimeError('gui_response_unverified:' + stage + ':' + code) from None
        if message != {"schema": 1, "handoff": True}:
            if (isinstance(message, dict) and message.get("schema") == 1
                    and message.get("handoff") is False
                    and message.get("stage") in ("startup", "check", "stage", "handoff")
                    and isinstance(message.get("code"), str)):
                raise RuntimeError("gui_handoff_unverified:" + message["stage"] + ":" + message["code"])
            raise RuntimeError("gui_handoff_unverified:invalid_response")
        old.wait(timeout=30)
        expected_phase = "rolled_back" if bad_candidate else "committed"
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            journal_path = home / "state/transaction.json"
            if journal_path.exists():
                try:
                    journal = read(journal_path)
                except (OSError, ValueError):
                    time.sleep(0.1)
                    continue
                if journal.get("phase") in ("committed", "rolled_back", "needs_review"):
                    break
            time.sleep(0.1)
        else:
            raise RuntimeError("helper_transaction_timeout")
        if journal["phase"] != expected_phase:
            raise RuntimeError("unexpected_transaction_phase:" + journal["phase"] +
                               ":" + journal.get('cause', 'unavailable'))
        current = read(home / "current.json")
        expected_version = "0.0.1" if bad_candidate else "0.0.2"
        if current["version"] != expected_version:
            raise RuntimeError("unexpected_current_version")
        if not (home / old_pointer["path"]).is_dir():
            raise RuntimeError("old_slot_missing")
        if not (home / "data/identity/identity.dpapi").is_file():
            raise RuntimeError("unpaired_dpapi_missing")
        if old.group.active() < 1:
            raise RuntimeError("activated_gui_not_owned_by_test_job")
        # The candidate or recovered old GUI should hold the application lease.
        from updates.storage import lock
        try:
            with lock(home / "state/application.lock"):
                raise RuntimeError("application_lock_not_held")
        except Exception as error:
            from updates.security import UpdateError
            if not isinstance(error, UpdateError) or error.code != "update_or_application_already_running":
                raise
        return {"phase": journal["phase"], "version": current["version"],
                "pythonw": gui_python == pythonw}
    finally:
        old.stop_tree(timeout=15)
        if not old.tree_stopped or old.group.handle is not None:
            raise RuntimeError("fixture_job_cleanup_unconfirmed")
        # Windows can retain a just-exited process's file handle briefly even
        # after the original Job reports zero active processes. The bounded
        # unlink proves this exact temporary lease is no longer held.
        lease = home / "state/application.lock"
        deadline = time.monotonic() + 8
        while lease.exists():
            try:
                lease.unlink()
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise RuntimeError("fixture_application_lock_still_open") from None
                time.sleep(0.05)


def main():
    if os.name != "nt":
        raise RuntimeError("windows_only_smoke")
    for bad in (False, True):
        with tempfile.TemporaryDirectory(prefix="desktop-remote-gui-ota-") as directory:
            result = _run_case(Path(directory), bad_candidate=bad)
            print("isolated_gui_helper_" + result["phase"] + " " + result["version"] +
                  (" pythonw" if result["pythonw"] else " python"), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("isolated_gui_helper_failed:" + str(error)[-800:], file=sys.stderr)
        raise SystemExit(1)
