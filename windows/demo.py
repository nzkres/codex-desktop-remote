# 纯演示入口：把假的控制器传给真实窗口，练习按钮而不创建真实身份或连接。
# 这里的状态是人为返回的，不代表服务器、FRP 或 Desktop 健康。

"""UI-only synthetic preview. No identity store, sockets, pairing or Desktop RPC."""
import tkinter as tk
from tkinter import ttk
from gui import _Window, enable_dpi


# 替代真实 Controller 的教学对象；返回合成状态，不访问身份文件。
class Demo:
    # 固定返回未配对，用于看到初始界面。
    def status(self):
        return {'state': 'not_paired'}

    # 模拟已提交申请，实际不会联系手机或服务器。
    def pair(self, relay, code, name):
        return {'state': 'awaiting_confirmation'}

    # 模拟进入连接状态后等待 stop 信号；不验证真实通道，不启动 Node/FRP。
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
