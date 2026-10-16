# 受管安装的真实窗口入口：启动前验证版本目录，防止从错误槽运行。
# quarantine 是更新验证阶段：候选先自检，不接受手机写操作；收到激活消息后才开放窗口。

"""GUI entry for a verified immutable version and a separate stable data directory."""
import argparse
import json
from pathlib import Path
import queue
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "windows"))
from updates.engine import installed
from updates.storage import atomic, ensure_launchable, lock, no_links, read
from updates.security import UpdateError
from update_driver import controller_for
from update_health import health as quarantined_health, record_health_failure
from gui import _Window, enable_dpi
from error_catalog import safe_code


def health_error(error):
    """Only catalogued identifiers, never exception text or credentials."""
    return safe_code(getattr(error, 'code', None) or str(error))


def connect_after_commit(window, gui, attempts=80):
    """Activation acknowledgement precedes the helper's durable commit."""
    if gui.closing:
        return
    if not gui.update_blocked:
        gui.connect()
    elif gui.update_phase in ('prepared', 'old_stopped', 'switched', 'validated', 'rollback_required', 'rollback_restoring') and attempts:
        window.after(250, lambda: connect_after_commit(window, gui, attempts - 1))


# 比较当前指针与实际运行源码目录，防止从另一个旧槽误启动。
def same_running_slot(slot):
    path = no_links(slot)
    canonical = path.resolve(strict=True)
    no_links(path)
    return canonical == ROOT


# 更新候选的真实健康检查：先检查任务，再短暂连接验证传输，最后比较保全摘要。
# 已配对安装会接触其配置的服务器；这不是纯 UI 演示或合成测试入口。
def health(controller):
    return quarantined_health(ROOT, controller)


# 普通启动需通过事务可启动检查；隔离启动先健康验证并等待助手激活。
# 窗口最初隐藏，取得应用锁后再开放；退出时释放锁并销毁本窗口。
def run(home, quarantine=False, connect=False):
    import tkinter as tk
    config, pointer, root = installed(home)
    if not same_running_slot(root) or config['component'] != 'windows':
        raise UpdateError('running_slot_changed')
    if not quarantine:
        ensure_launchable(home)
    controller = controller_for(home, quarantine)
    enable_dpi()
    window = tk.Tk()
    window.withdraw()
    lease = None
    try:
        if quarantine:
            # UI exists, but no user or phone writes are admitted during validation.
            version = read(ROOT / 'windows' / 'releases.json')['releases'][0]['version']
            try:
                health(controller)
            except Exception as error:
                # Old helpers only understand ready=False. Retain the bounded
                # reason in stable state as well, without altering the transaction.
                try:
                    record_health_failure(home, 'candidate', version, health_error(error))
                except Exception:
                    pass
                raise
            print(json.dumps({'schema':1, 'ready':True, 'version':version, 'probe':'verified'}), flush=True)
            line = sys.stdin.buffer.readline(2049)
            if len(line) > 2048 or json.loads(line) != {'schema':1, 'op':'activate'}:
                raise UpdateError('activation_not_authorized')
        lease = lock(Path(home) / 'state' / 'application.lock')
        lease.__enter__()
        controller.quarantine = False
        controller.update_home = Path(home)
        # _Window starts a status worker which takes the same nonblocking identity
        # lock. Read startup policy BEFORE that worker exists, not concurrently.
        auto_connect = controller.auto_connect_on_open() or (connect and not quarantine)
        gui = _Window(window, controller)
        # Quarantine has ended only after the helper explicitly activated this slot.
        # Reuse the normal worker once; never clear a guard or retry pairing here.
        if auto_connect:
            window.after(0, lambda: connect_after_commit(window, gui))
        if quarantine:
            print(json.dumps({'schema':1, 'active':True}), flush=True)
        window.deiconify()
        window.mainloop()
    finally:
        if lease is not None:
            lease.__exit__(None, None, None)
        try:
            window.destroy()
        except tk.TclError:
            pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--quarantine', action='store_true')
    parser.add_argument('--connect', action='store_true', help='Explicitly connect the existing identity after opening')
    args = parser.parse_args()
    try:
        run(args.home, args.quarantine, args.connect)
    except Exception as error:
        if args.quarantine:
            print(json.dumps({'schema':1,'ready':False,'code':health_error(error)}), flush=True)
        else:
            import tkinter as tk
            from tkinter import messagebox
            root=tk.Tk();root.withdraw()
            from updates.launch import describe_problem
            title, message, kind = describe_problem(args.home, error)
            (messagebox.showinfo if kind == 'info' else messagebox.showerror)(title, message)
            root.destroy()
        sys.exit(1)

