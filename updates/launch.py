# 稳定安装入口：根据当前版本指针启动正确的版本，而不是让快捷方式固定在旧版本目录。

"""Stable, standard-library-only launcher, copied into a NEW installation home."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def verified_slot(home):
    # 启动前检查交易日志、当前指针、包清单和每个文件哈希。
    # 返回(版本目录, 组件名)；校验失败就阻止运行被替换或半更新的代码。
    home = Path(home).absolute()
    def safe(path):
        # 链接可能将验证路径导向安装外部，因此目标及父目录都不得为链接。
        if any(p.is_symlink() or (hasattr(p, 'is_junction') and p.is_junction()) for p in (path, *path.parents)):
            raise ValueError('linked_installation')
        return path
    def load(path):
        # 状态 JSON 体积受限，避免读取异常大的本地文件。
        if safe(path).stat().st_size > 1024*1024:
            raise ValueError('invalid_installation')
        return json.loads(path.read_bytes())
    safe(home)
    journal = home / 'state' / 'transaction.json'
    if journal.exists() and load(journal).get('phase') not in ('committed', 'rolled_back'):
        raise ValueError('unfinished_update_requires_review')
    pointer = load(home / 'current.json')
    parts = Path(pointer['path']).parts
    if len(parts) != 2 or parts[0] != 'versions' or '..' in parts or '\\' in pointer['path']:
        raise ValueError('invalid_installation')
    root = safe(home / pointer['path'])
    manifest = load(root / 'package.json')
    if hashlib.sha256((root/'package.json').read_bytes()).hexdigest() != pointer['manifestSha256']:
        raise ValueError('installed_version_changed')
    if manifest.get('version') != pointer['version'] or manifest.get('product') != 'codex-desktop-remote':
        raise ValueError('installed_version_changed')
    actual = {}
    for current, dirs, files in os.walk(root, followlinks=False):
        for name in dirs:
            safe(Path(current)/name)
        for name in files:
            path = safe(Path(current)/name)
            with path.open('rb') as stream:
                actual[path.relative_to(root).as_posix()] = hashlib.file_digest(stream,'sha256').hexdigest()
    actual.pop('package.json',None)
    if actual != manifest['files']:
        raise ValueError('installed_version_changed')
    return root, manifest['component']


def main(home, arguments=()):
    # 验证后以隔离 Python 进程启动对应组件；arguments 传给组件入口。
    # 清除会影响代码装载的环境变量，返回组件进程退出码。
    root, component = verified_slot(home)
    entry = root / ('windows/ota_entry.py' if component == 'windows' else 'updates/relay_host.py')
    if component not in ('windows','relay'):
        raise ValueError('invalid_installation')
    env = {k:v for k,v in os.environ.items() if k.upper() not in ('PYTHONPATH','PYTHONSTARTUP','PYTHONINSPECT','NODE_OPTIONS','NODE_PATH')}
    return subprocess.call([sys.executable, '-B', '-I', str(entry), '--home', str(home), *arguments],
                           env=env, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)


if __name__ == '__main__':
    # 无法确认安装完整性时给出复核提示，不猜测哪个版本可安全启动。
    try:
        sys.exit(main(Path(__file__).absolute().parent, sys.argv[1:]))
    except Exception:
        if os.name == 'nt':
            import tkinter as tk
            from tkinter import messagebox
            window=tk.Tk();window.withdraw()
            messagebox.showerror('安装现场需要复核','版本文件或更新记录无法验证。请保留 data 和 state 目录，由维护者核对。')
        else:
            print('installation_requires_review',file=sys.stderr)
        sys.exit(1)

