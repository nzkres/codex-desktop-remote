"""Small, foreground-only pairing window for a supplied Desktop controller."""

from __future__ import annotations

import queue
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox
from tkinter.scrolledtext import ScrolledText
from release_notes import VERSION, format_notes

def enable_dpi():
    import os
    if os.name == "nt":
        import ctypes
        ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))


_STATES = {
    "not_paired": "未配对",
    "claim_pending": "正在提交配对",
    "awaiting_confirmation": "等待手机确认配对",
    "connected": "登记心跳在线",
    "reconnecting": "正在重连",
    "paused": "手机端已暂停",
    "revoked": "设备已移除",
    "expired": "配对已过期",
    "rejected": "配对被拒绝",
    "claim_failed": "配对失败",
}


class _Window:
    def __init__(self, root: tk.Tk, controller: object):
        self.root = root
        self.controller = controller
        self.commands: queue.Queue[tuple] = queue.Queue()
        self.events: queue.Queue[tuple] = queue.Queue()
        self.stop_event = threading.Event()
        self.busy = False
        self.connecting = False
        self.closing = False
        self.close_started = 0.0
        self.close_notice = False
        self.close_error = False

        self.update_release = None
        self.notes_window = None
        root.title("Desktop 手机联调 · " + VERSION)
        root.resizable(False, False)
        frame = ttk.Frame(root, padding=16)
        frame.grid(sticky="nsew")
        frame.columnconfigure(1, weight=1)

        self.relay = tk.StringVar(value="")
        self.name = tk.StringVar(value="本机 Desktop 联调")
        self.code = tk.StringVar()
        self.status_text = tk.StringVar(value="正在读取状态…")

        ttk.Label(frame, text="服务器 HTTPS").grid(row=0, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.relay, width=39).grid(row=0, column=1, sticky="ew", pady=4)
        ttk.Label(frame, text="设备名").grid(row=1, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.name).grid(row=1, column=1, sticky="ew", pady=4)
        ttk.Label(frame, text="配对码").grid(row=2, column=0, sticky="w", pady=4)
        ttk.Entry(frame, textvariable=self.code, show="●").grid(row=2, column=1, sticky="ew", pady=4)

        actions = ttk.Frame(frame)
        actions.grid(row=3, column=0, columnspan=2, pady=(10, 8))
        self.pair_button = ttk.Button(actions, text="配对", command=self.pair)
        self.pair_button.pack(side="left", padx=4)
        self.connect_button = ttk.Button(actions, text="连接", command=self.connect)
        self.connect_button.pack(side="left", padx=4)
        self.disconnect_button = ttk.Button(actions, text="断开", command=self.disconnect)
        self.disconnect_button.pack(side="left", padx=4)

        ttk.Label(frame, textvariable=self.status_text, wraplength=600).grid(
            row=4, column=0, columnspan=2, sticky="w", pady=(2, 8)
        )
        ttk.Label(
            frame,
            text="仅联调指定 Desktop 任务；退出窗口不会停止 Desktop 模型任务。",
            wraplength=600,
        ).grid(row=5, column=0, columnspan=2, sticky="w")

        version_row = ttk.Frame(frame)
        version_row.grid(row=6, column=0, columnspan=2, sticky="ew", pady=(12, 0))
        ttk.Label(version_row, text="Windows 连接器 " + VERSION).pack(side="left", padx=(0, 12))
        self.notes_button = ttk.Button(version_row, text="版本与更新说明", command=self.show_release_notes)
        self.notes_button.pack(side="right")

        self.update_text = tk.StringVar(value='OTA：需使用受管安装基线' if not getattr(controller, 'update_home', None) else 'OTA：用户点击安装；更新前须先断开连接。')
        ttk.Label(frame, textvariable=self.update_text, wraplength=600).grid(row=7, column=0, columnspan=2, sticky='w', pady=(8,0))
        updates = ttk.Frame(frame)
        updates.grid(row=8, column=0, columnspan=2, sticky='e')
        self.check_update_button = ttk.Button(updates, text='检查更新', command=lambda:self.update('updatecheck'))
        self.check_update_button.pack(side='left', padx=4)
        self.install_update_button = ttk.Button(updates, text='下载并安装', command=lambda:self.update('updateinstall'))
        self.install_update_button.pack(side='left', padx=4)
        self._buttons()
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.worker = threading.Thread(target=self._work, name="desktop-remote-lite-network")
        self.worker.start()
        self.commands.put(("status",))
        root.after(100, self._drain)

    def show_release_notes(self):
        if self.notes_window is not None and self.notes_window.winfo_exists():
            self.notes_window.lift()
            return
        dialog = tk.Toplevel(self.root)
        self.notes_window = dialog
        dialog.title("版本与更新说明 · " + VERSION)
        dialog.transient(self.root)
        dialog.geometry("680x540")
        dialog.minsize(420, 320)
        body = ttk.Frame(dialog, padding=12)
        body.pack(fill="both", expand=True)
        body.rowconfigure(0, weight=1)
        body.columnconfigure(0, weight=1)
        notes = ScrolledText(body, wrap="word", font=("Microsoft YaHei UI", 10), padx=12, pady=12)
        notes.insert("1.0", format_notes())
        notes.configure(state="disabled")
        notes.grid(row=0, column=0, sticky="nsew")
        self.notes_text = notes
        ttk.Button(body, text="返回", command=dialog.destroy).grid(row=1, column=0, sticky="e", pady=(10, 0))
        dialog.bind("<Escape>", lambda _event: dialog.destroy())

    def _buttons(self):
        self.pair_button.configure(state="disabled" if self.busy or self.closing else "normal")
        self.connect_button.configure(state="disabled" if self.busy or self.closing else "normal")
        self.disconnect_button.configure(state="normal" if self.connecting and not self.closing else "disabled")

        if hasattr(self, 'check_update_button'):
            enabled = bool(getattr(self.controller, 'update_home', None)) and not self.busy and not self.closing
            self.check_update_button.configure(state='normal' if enabled else 'disabled')
            self.install_update_button.configure(state='normal' if enabled and self.update_release else 'disabled')

    def update(self, kind):
        if self.busy or self.closing or not getattr(self.controller, 'update_home', None):
            return
        self.busy = True
        self._buttons()
        self.update_text.set('正在检查签名版本…' if kind == 'updatecheck' else '正在下载、校验并准备独立更新助手…')
        self.commands.put((kind,))

    def pair(self):
        if self.busy or self.closing:
            return
        relay = self.relay.get().strip()
        name = self.name.get().strip()
        code = self.code.get().strip().upper()
        self.code.set("")
        if not relay.startswith("https://") or not name or not code:
            self.status_text.set("请填写 HTTPS 服务器、设备名和配对码。")
            return
        self.busy = True
        self._buttons()
        self.status_text.set("正在配对…")
        self.commands.put(("pair", relay, code, name))

    def connect(self):
        if self.busy or self.closing:
            return
        self.busy = True
        self.connecting = True
        self.stop_event.clear()
        self._buttons()
        self.status_text.set("正在连接…")
        self.commands.put(("connect",))

    def disconnect(self):
        if self.connecting:
            self.stop_event.set()
            self.disconnect_button.configure(state="disabled")
            self.status_text.set("正在断开连接…")

    def close(self):
        if self.closing:
            return
        self.closing = True
        self.close_started = time.monotonic()
        self.stop_event.set()
        self.commands.put(("close",))
        self._buttons()
        self.status_text.set("正在等待连接线程退出…")

    def _work(self):
        while True:
            command = self.commands.get()
            kind = command[0]
            if kind == "close":
                return
            try:
                if kind in ('updatecheck', 'updateinstall'):
                    from updates.engine import check, stage
                    from update_ui import handoff
                    home = self.controller.update_home
                    if kind == 'updatecheck':
                        self.events.put(('updatechecked', check(home)))
                    else:
                        stage(home, progress=lambda done,total:self.events.put(('updateprogress', int(done*100/total))))
                        handoff(home)
                        self.events.put(('updatehandoff', None))
                elif kind == "status":
                    self.events.put(("status", self.controller.status()))
                elif kind == "pair":
                    self.events.put(("paired", self.controller.pair(*command[1:])))
                elif kind == "connect":
                    result = self.controller.connect(
                        self.stop_event, lambda report: self.events.put(("status", report))
                    )
                    self.events.put(("disconnected", result))
            except Exception as error:
                if kind in ('updatecheck', 'updateinstall'):
                    from updates.security import UpdateError
                    self.events.put(('updatefailed', error.code if isinstance(error, UpdateError) else 'update_operation_failed'))
                    continue
                # Controller exceptions can contain credentials or HTTP bodies.
                safe_codes = {"previous_runtime_requires_review", "runtime_cleanup_requires_review", "identity_changed", "legacy_identity_rejected", "connector_already_running", "task_channel_unavailable", "already_enrolled_or_pending", "invalid_pairing_code"}
                code = str(error) if str(error) in safe_codes else "operation_failed"
                self.events.put(("failed", code))

    def _drain(self):
        while True:
            try:
                kind, value = self.events.get_nowait()
            except queue.Empty:
                break
            if self.closing:
                if kind == 'failed' or (kind == 'disconnected' and value.get('runtimeNeedsReview')):
                    self.close_error = True
                continue
            if kind == 'updateprogress':
                self.update_text.set(f'正在下载并校验：{value}%（尚未更新成功）')
            elif kind == 'updatechecked':
                self.busy = False
                self.update_release = value
                self.update_text.set('可更新至 ' + value['version'] + ' · ' + value['channel'] + '\n兼容：' + ', '.join(value['compatibility']))
                messagebox.showinfo('可用更新 · ' + value['version'], value['notes'] + '\n\n点击“下载并安装”后窗口会退出，独立助手将验证并重开。Desktop 任务不会被停止。', parent=self.root)
            elif kind == 'updatefailed':
                self.busy = False
                messages = {'no_update_available':'当前已是此渠道最新版本。', 'active_or_unverified_desktop_task':'任务活动或状态未确认，更新延期。', 'update_trust_not_provisioned':'更新源及发布公钥尚未配置。'}
                self.update_text.set(messages.get(value, '更新未完成：' + value + '；保留旧版本和现场，可查看 state/ 中的诊断。'))
            elif kind == 'updatehandoff':
                self.busy = False
                self.close()
            elif kind == "status":
                if isinstance(value, dict):
                    if not self.relay.get() and isinstance(value.get('relay'), str):
                        self.relay.set(value['relay'])
                    state = value.get("state")
                    if state in _STATES:
                        self.status_text.set(_STATES[state])
                    if "dataChannel" in value:
                        channels = {"probe_verified": "转发通道探针通过；手机任务待验证", "unavailable": "转发通道未就绪", "not_configured": "转发通道尚未启动", "unverified": "转发通道尚未实时检查"}
                        self.status_text.set(_STATES.get(state, "状态待确认") + "\n" + channels.get(value["dataChannel"], "转发通道待确认"))
                    if not self.connecting and value.get('runtimeNeedsReview'):
                        self.status_text.set('前次连接尚未确认安全退出；请保留现场，由维护者核对。')
            elif kind == "paired":
                self.busy = False
                state = value.get("state") if isinstance(value, dict) else None
                self.status_text.set(
                    "请在手机上确认配对，然后点击连接。"
                    if state == "awaiting_confirmation" else _STATES.get(state, "配对未完成，请检查状态。")
                )
            elif kind == "disconnected":
                self.busy = False
                self.connecting = False
                self.status_text.set("连接已结束，现场需复核；Desktop 模型任务未停止。" if value.get('runtimeNeedsReview')
                                     else "已安全断开，可再次连接；Desktop 模型任务未停止。")
            elif kind == "failed":
                self.busy = False
                self.connecting = False
                messages = {"previous_runtime_requires_review": "前次连接未能确认安全退出，保留现场，需维护者核对；不要删除身份或保护标志。",
                            "runtime_cleanup_requires_review": "断开结果未能完全确认，已保留保护。请在 Desktop 查看任务，由维护者核对连接现场。",
                            "identity_changed": "连接状态发生意外变化，已停止写入；请保留现场，由维护者核对。",
                            "legacy_identity_rejected": "发现旧连接器状态，已阻止接管。",
                            "connector_already_running": "这个测试连接器已经运行，请使用原窗口。",
                            "task_channel_unavailable": "服务器未授权或暂不可用，请在手机核对设备状态。",
                            "already_enrolled_or_pending": "已有配对身份，请在手机确认后点击连接。",
                            "invalid_pairing_code": "配对码格式错误，请使用手机生成的 12 位配对码。"}
                self.status_text.set(messages.get(value, "操作失败。请检查连接器状态后重试。"))
            self._buttons()

        if self.closing:
            if not self.worker.is_alive():
                if self.close_error:
                    messagebox.showerror('连接现场需要复核',
                        '退出时有操作未能完全确认，请保留现场。Desktop 任务未被停止。\n'
                        '请保留本机身份与发送记录，由维护者核对后再连接；不要删除保护标志或重新配对。', parent=self.root)
                self.root.destroy()
                return
            if not self.close_notice and time.monotonic() - self.close_started >= 5:
                self.close_notice = True
                self.status_text.set("仍在等待连接线程退出；不会强制结束 Desktop 模型任务。")
        self.root.after(100, self._drain)


def run_gui(controller: object) -> None:
    """Run the UI with a caller-owned controller; never creates an identity."""
    enable_dpi()
    root = tk.Tk()
    _Window(root, controller)
    root.mainloop()
