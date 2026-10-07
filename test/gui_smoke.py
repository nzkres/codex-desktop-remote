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
    def status(self):return {'state':'not_paired'}
    def pair(self,relay,code,name):
        assert code=='012345ABCDEF';return {'state':'awaiting_confirmation'}
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
 pump();assert '手机上确认' in window.status_text.get()
 window.connect();pump();assert '手机任务待验证' in window.status_text.get()
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
