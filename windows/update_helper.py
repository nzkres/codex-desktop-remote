"""Independent helper: wait on a pinned old GUI handle, then complete its transaction."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from updates.engine import installed, Transaction
from updates.processes import PinnedProcess
from updates.security import UpdateError
from updates.storage import atomic
from update_driver import WindowsDriver


def main(home):
    raw = sys.stdin.buffer.readline(4097)
    if len(raw) > 4096:
        raise UpdateError('invalid_update_handoff')
    request = json.loads(raw)
    if set(request) != {'schema','owner'} or request['schema'] != 1:
        raise UpdateError('invalid_update_handoff')
    config, _, slot = installed(home)
    if config['component'] != 'windows' or slot != ROOT:
        raise UpdateError('running_slot_changed')
    with PinnedProcess(request['owner']) as old:
        print(json.dumps({'schema':1,'pinned':True}), flush=True)
        old.wait(timeout=120)  # Never kills the old GUI or reopens its PID.
        Transaction(home, WindowsDriver(home, slot)).apply()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', required=True, type=Path)
    args = parser.parse_args()
    try:
        main(args.home)
    except Exception as error:
        # Do not persist exceptions, paths, HTTP bodies or credentials.
        code = error.code if isinstance(error, UpdateError) else 'update_helper_failed'
        atomic(args.home / 'state' / 'helper-result.json', {'schema':1,'success':False,'code':code})
        sys.exit(1)
