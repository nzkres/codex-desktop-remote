# OTA 第一步交接：GUI 启动独立助手，助手确认已固定旧窗口身份后，GUI 才开始退出。
# 这里只证明交接成立；下载完成或助手启动成功都不能当成更新成功。

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


# 输入稳定安装目录 home，启动该安装当前版本的助手并发送窗口归属信息。
# 等待 pinned 确认后返回；超时只终止这个助手，GUI 仍未完成交接。
# 凭据不通过命令行传递，owner 元数据通过父子管道发送。
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
    # 专用读线程避免 stdout.readline 阻塞 GUI 的工作流程；外层队列设定超时。
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
