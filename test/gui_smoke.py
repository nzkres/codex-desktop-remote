"""Synthetic native-window smoke test. Never instantiates a credential store."""
import sys
import time
import tkinter as tk
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'windows'))
from gui import _Window, enable_dpi
class Fake:
    review=False
    def __init__(self):
        self.report={'state':'not_paired'}
        self.pair_calls=0
    def status(self):return dict(self.report)
    def pair(self,relay,code,name):
        assert code=='012345ABCDEF'
        self.pair_calls+=1
        self.report=({'state':'claim_pending','lastError':'connection_unavailable','relay':relay}
                     if self.pair_calls==1 else {'state':'awaiting_confirmation','lastError':None,'relay':relay})
        return dict(self.report)
    def connect(self,stop,on_status):
        on_status({'state':'connected','dataChannel':'probe_verified'})
        stop.wait(2);return {**self.status(),'runtimeNeedsReview':self.review}
enable_dpi();root=tk.Tk();window=_Window(root,Fake())
def pump(seconds=.25):
    until=time.monotonic()+seconds
    while time.monotonic()<until:root.update();time.sleep(.02)
try:
 pump();assert window.status_text.get()=='未配对'
 window.relay.set('https://relay.example.test')
 window.code.set('012345ABCDEF');window.pair();assert window.code.get()==''
 pump();pending=window.status_text.get()
 assert '未确认' in pending and '服务器' in pending and '端口' in pending
 assert '012345ABCDEF' not in pending
 window.commands.put(('status',));pump();assert window.status_text.get()==pending
 if len(sys.argv)>3:
    from PIL import ImageGrab
    root.lift();pump()
    x,y=root.winfo_rootx(),root.winfo_rooty()
    ImageGrab.grab(bbox=(x,y,x+root.winfo_width(),y+root.winfo_height())).save(sys.argv[3])
 # The existing claim progresses; do not model re-pairing over a pending identity.
 window.controller.report={'state':'awaiting_confirmation','lastError':None}
 window.commands.put(('status',));pump()
 assert '手机确认配对' in window.status_text.get()
 assert window.controller.pair_calls == 1
 window.connect();pump();assert '手机任务待验证' in window.status_text.get()
 window.notes_button.invoke();pump()
 notes = window.notes_text.get('1.0', 'end')
 assert '0.1.0-alpha.2' in notes and '0.1.0-alpha.1' in notes
 assert '体验：' in notes and '验证：' in notes
 assert window.notes_text.cget('state') == 'disabled'
 assert not window.stop_event.is_set()
 if len(sys.argv)>2:
    from PIL import ImageGrab
    dialog=window.notes_window;dialog.lift();pump()
    x,y=dialog.winfo_rootx(),dialog.winfo_rooty()
    ImageGrab.grab(bbox=(x,y,x+dialog.winfo_width(),y+dialog.winfo_height())).save(sys.argv[2])
 window.notes_window.destroy();pump();assert window.root.winfo_exists()
 if len(sys.argv)>1:
    from PIL import ImageGrab
    root.lift();root.update();pump()
    x,y=root.winfo_rootx(),root.winfo_rooty()
    ImageGrab.grab(bbox=(x,y,x+root.winfo_width(),y+root.winfo_height())).save(sys.argv[1])
 window.disconnect();pump();assert '可再次连接' in window.status_text.get()
 window.connect();pump();assert '手机任务待验证' in window.status_text.get()
 window.disconnect();pump();assert '可再次连接' in window.status_text.get()
 window.controller.review=True;window.connect();pump()
 with patch('gui.messagebox.showerror') as warning:
    window.close();pump();warning.assert_called_once()
 print('gui_smoke_passed')
finally:
 window.stop_event.set();window.commands.put(('close',));window.worker.join(3)
 try:root.destroy()
 except tk.TclError:pass
