# 稳定安装入口：根据当前版本指针启动正确的版本，而不是让快捷方式固定在旧版本目录。

"""Stable, standard-library-only launcher, copied into a NEW installation home."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

_PHASES = {'prepared':'准备安装', 'old_stopped':'原连接器已正常退出', 'switched':'验证候选版本',
           'validated':'候选验证通过，等待激活', 'rollback_required':'清理未激活候选',
           'rollback_restoring':'恢复原版本', 'needs_review':'等待维护核验'}
_REASONS = {
    'active_or_unverified_desktop_task':'任务仍在运行，或实时状态尚未核实',
    'thread_busy':'任务仍在运行或等待确认',
    'candidate_transport_unverified':'候选连接通道未通过核验',
    'candidate_health_unverified':'候选未返回完整健康确认',
    'candidate_tree_exit_unconfirmed':'候选进程树退出尚未确认',
    'preservation_changed':'身份、配置或回执摘要发生变化',
    'permissions_changed':'原生对话权限已变化',
    'permissions_unverified':'原生对话权限无法核实',
    'send_unknown':'存在结果未确认的发送',
    'update_uncertain':'存在结果未确认的停止操作',
    'rollback_or_cleanup_unconfirmed':'旧版恢复或候选清理尚未确认',
    'rollback_context_unverified':'旧版恢复上下文无法核实',
    'activation_outcome_requires_review':'激活结果不明，不能安全回退',
    'previous_runtime_requires_review':'前次连接未留下完整退出证据',
    'runtime_cleanup_requires_review':'连接清理结果尚未确认',
    'update_operation_failed':'更新操作未完成，详细原因尚未确认',
}


def updater_active(home):
    """Read-only lock probe. A phase name alone cannot prove a helper is alive."""
    path = Path(home) / 'state/update.lock'
    try:
        if path.is_symlink() or not path.is_file():
            return False
        with path.open('r+b') as stream:
            if os.name == 'nt':
                import msvcrt
                try:
                    msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                except OSError:
                    return True
                stream.seek(0)
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                try:
                    fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    return True
                fcntl.flock(stream, fcntl.LOCK_UN)
    except OSError:
        pass
    return False


def describe_problem(home, error):
    """Standalone bootstrap diagnostics: no imports from unverified code/data."""
    code = getattr(error, 'code', None) or str(error)
    if code == 'unfinished_update_requires_review':
        phase, reasons = None, []
        path = Path(home) / 'state/transaction.json'
        try:
            if any(p.is_symlink() for p in (path, *path.parents)) or path.stat().st_size > 1024*1024:
                raise ValueError()
            record = json.loads(path.read_bytes())
            phase = record.get('phase')
            for label, key in [('升级原因', 'cause'), ('回退原因', 'rollbackCause'), ('结果', 'error')]:
                value = record.get(key)
                if value in _REASONS:
                    reasons.append(f'{label}：{_REASONS[value]} [{value}]')
        except (OSError, ValueError, AttributeError, TypeError):
            pass
        phase_label = _PHASES.get(phase, '阶段尚未核实')
        if phase in _PHASES and phase != 'needs_review' and updater_active(home):
            return ('更新正在进行', f'当前阶段：{phase_label}。\n请等待更新器完成，不需要重复打开或重新配对。', 'info')
        detail = '\n'.join(reasons) or '具体原因尚未核实，请保留现场。'
        return ('上次更新未完成', f'阶段：{phase_label}\n{detail}\n请保留 data、state 和原身份，由维护者核对。此提示不代表版本文件已损坏。', 'error')
    if code in ('installed_version_changed', 'linked_installation', 'invalid_installation'):
        return ('版本文件校验未通过', f'安装文件与受信版本记录不一致 [{code}]。\n请保留 data、state 和原版本目录，勿重复配对。', 'error')
    if isinstance(error, PermissionError):
        return ('安装目录访问被拒绝', '当前用户无法读取安装文件或记录。请核对原用户及文件权限，保留身份和回执。', 'error')
    if isinstance(error, FileNotFoundError):
        return ('安装文件或记录缺失', '启动所需的文件或更新记录不存在。请保留 data 与 state，由维护者核对缺失项。', 'error')
    if isinstance(error, (json.JSONDecodeError, UnicodeError)):
        return ('安装记录格式有误', '安装或更新记录无法解析。请保留原文件供维护者核对，不要删除身份。', 'error')
    if code in _REASONS:
        return ('连接器暂未启动', f'{_REASONS[code]} [{code}]。\n请核对该原因，保留身份与回执。', 'error')
    return ('启动结果尚未核实', '启动过程中出现未分类错误。请保留 data、state，由维护者查看阶段诊断；不会显示可能包含凭据的原始异常。', 'error')


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
    except Exception as error:
        if os.name == 'nt':
            import tkinter as tk
            from tkinter import messagebox
            window=tk.Tk();window.withdraw()
            title, message, kind = describe_problem(Path(__file__).absolute().parent, error)
            (messagebox.showinfo if kind == 'info' else messagebox.showerror)(title, message)
        else:
            print('installation_requires_review',file=sys.stderr)
        sys.exit(1)

