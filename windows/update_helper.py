# OTA 独立助手：原 GUI 退出后继续完成升级，所以助手不能依赖原 GUI 的生命周期。
# 只等待已验证的原进程句柄，不按旧 PID 重新寻找并终止一个可能无关的进程。

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
from updates.storage import atomic, no_links
from update_driver import WindowsDriver


# 把安装指针所指目录正规化后，与助手自身代码目录比较，拒绝运行错版本。
def same_running_slot(slot):
    # installed() has verified the package tree. Keep its no-link boundary,
    # then normalize a Windows 8.3/long-path alias before comparing roots.
    path = no_links(slot)
    canonical = path.resolve(strict=True)
    no_links(path)
    return canonical == ROOT


# 读取有限长度的交接消息，核对所属安装，固定原 GUI 句柄并等待正常退出。
# 只有旧 GUI 退出后才调用 Transaction.apply；等待不等于强制终止旧窗口。
def main(home):
    raw = sys.stdin.buffer.readline(4097)
    if len(raw) > 4096:
        raise UpdateError('invalid_update_handoff')
    request = json.loads(raw)
    if set(request) != {'schema','owner'} or request['schema'] != 1:
        raise UpdateError('invalid_update_handoff')
    config, _, slot = installed(home)
    if config['component'] != 'windows' or not same_running_slot(slot):
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
