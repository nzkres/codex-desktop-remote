"""UI-only synthetic preview. No identity store, sockets, pairing or Desktop RPC."""
import tkinter as tk
from tkinter import ttk
from gui import _Window, enable_dpi


class Demo:
    def status(self):
        return {'state': 'not_paired'}

    def pair(self, relay, code, name):
        return {'state': 'awaiting_confirmation'}

    def connect(self, stop, on_status):
        on_status({'state': 'connected', 'dataChannel': 'unverified'})
        stop.wait()
        return {'state': 'not_paired', 'runtimeNeedsReview': False}


if __name__ == '__main__':
    enable_dpi()
    root = tk.Tk()
    window = _Window(root, Demo())
    root.title('界面演示 · 合成数据 · 不连接服务器或 Desktop')
    ttk.Label(root, text='演示模式：所有状态均为合成数据，不代表实际连通。', padding=12).grid(row=1)
    window.relay.set('https://relay.example.test')
    window.name.set('演示电脑（不会登记）')
    root.mainloop()
