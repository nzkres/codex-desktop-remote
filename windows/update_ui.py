"""The GUI only stages and hands off; it never claims installation succeeded."""
import json
from pathlib import Path
import queue
import subprocess
import sys
import threading

from updates.engine import installed
from updates.processes import self_owner
from updates.security import UpdateError


def handoff(home):
    _, _, root = installed(home)
    owner = self_owner()
    helper = subprocess.Popen([sys.executable, '-B', str(root / 'windows' / 'update_helper.py'),
                               '--home', str(home)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, close_fds=True,
                              creationflags=subprocess.CREATE_NO_WINDOW)
    helper.stdin.write(json.dumps({'schema':1,'owner':owner}).encode() + b'\n')
    helper.stdin.flush()
    messages = queue.Queue()
    def receive():
        try:
            messages.put(json.loads(helper.stdout.readline(4097)))
        except Exception:
            messages.put(None)
    threading.Thread(target=receive, daemon=True).start()
    try:
        answer = messages.get(timeout=20)
    except queue.Empty:
        # Cancel only this helper through the ORIGINAL Popen handle; no children
        # can be started while this GUI is still alive. It never kills this GUI.
        helper.terminate(); helper.wait(timeout=10)
        raise UpdateError('update_handoff_timeout') from None
    finally:
        helper.stdin.close()
    if answer != {'schema':1,'pinned':True}:
        raise UpdateError('update_handoff_unverified')
    # Keep Popen alive until GUI shutdown; helper is independent and waits for it.
    handoff.helper = helper
