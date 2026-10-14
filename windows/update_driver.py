# 把共享的更新事务翻译成 Windows 操作：预检、启动候选、验收、激活、清理。
# engine.Transaction 控制顺序，本文件实现平台细节；数据目录独立于版本目录。

"""Windows update lifecycle. Only a normally exited GUI can hand off its installation."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from updates.processes import OwnedChild
from updates.security import UpdateError
from updates.storage import digest, lock, no_links, read


# 从稳定 data 目录创建控制器；任务数据必须位于该安装 data 的子目录。
def controller_for(home, quarantine=False):
    from controller import Controller
    home = no_links(home)
    cfg = read(home / 'data' / 'fixture.local.json')
    data = no_links(cfg['dataDir'])
    if not data.is_relative_to(home / 'data') or data == home / 'data':
        raise UpdateError('managed_data_directory_required')
    return Controller(home / 'data' / 'fixture.local.json', home / 'data', quarantine)


# 运行所选版本的只读任务探针，核对空闲、权限和发送回执；返回通过的结果。
# 无论成功或失败都清理探针进程树，探针不会发送模型输入。
def probe(root, controller):
    cfg = controller.config
    binary = no_links(cfg['node']['path'])
    with binary.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != cfg['node']['sha256']:
            raise UpdateError('runtime_binary_changed')
    child = OwnedChild([binary, Path(root) / 'scripts' / 'update_probe.mjs', controller.config_file], cwd=root,
                       environment={**os.environ, 'CDR_DESKTOP_PYTHON': sys.executable})
    try:
        answer = child.read(timeout=50)
        if (set(answer) != {'idle', 'version', 'permissionsVerified', 'receiptsCertain'}
                or answer['idle'] is not True or answer['permissionsVerified'] is not True
                or answer['receiptsCertain'] is not True):
            raise UpdateError('active_or_unverified_desktop_task')
        return answer
    finally:
        child.stop_tree()


# 生成身份、配置和本机任务数据的摘要，用于比较更新前后是否改变。
# 摘要不是数据库备份，不能拿它恢复或覆盖用户数据；仅排除明确可丢弃的运行字段。
def snapshot(controller):
    with controller.store.lock():
        state = controller.store.read()
    if state and (state.get('runtimeNeedsReview') or state.get('state') != 'connected'):
        raise UpdateError('identity_or_runtime_requires_review')
    # Full decrypted identity CAS apart from explicit disposable runtime fields.
    stable = None if state is None else {k:v for k,v in state.items() if k not in (
        'adapterBinding', 'transportCrashContained', 'lastError')}
    files = {}
    directory = no_links(controller.config['dataDir'])
    for path in sorted(directory.rglob('*')):
        no_links(path)
        if path.is_file():
            with path.open('rb') as stream:
                files[path.relative_to(directory).as_posix()] = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'identity': digest(stable), 'configuration': digest(controller.config), 'taskData': files}


# 共享事务依赖的 Windows 平台适配器，保存候选进程对象和应用锁。
class WindowsDriver:
    # 定位安装与旧版本，初始化未启动的候选进程和未取得的锁。
    def __init__(self, home, old_slot):
        self.home, self.old_slot = Path(home), Path(old_slot)
        self.controller = controller_for(home)
        self.application_lock = None
        self.target = None
        self.activation_started = False

    # 先占住应用锁阻止并发启动，再检查运行锁、身份及原任务状态。失败时释放已取得的锁。
    def preflight(self):
        self.application_lock = lock(self.home / 'state' / 'application.lock')
        self.application_lock.__enter__()
        try:
            with self.controller.store.lock('runtime.lock'):
                snapshot(self.controller)
                probe(self.old_slot, self.controller)
        except BaseException:
            self.release('preflight_failed')
            raise

    # 委托模块函数，供 engine 在切换前后比较完整保全证据。
    def snapshot(self):
        return snapshot(self.controller)

    # 助手已经等待旧 GUI 退出；这里再次核对运行锁可用和状态，不接管遗留进程。
    def stop_old(self):
        # The helper pinned and waited on the ORIGINAL GUI handle before apply.
        # No runtime is adopted; the exclusive runtime lock must still be free.
        with self.controller.store.lock('runtime.lock'):
            snapshot(self.controller)

    # 隔离启动候选版本，手机写操作尚未开放；有未清理候选时拒绝再启动一个。
    def start(self, root):
        if self.target is not None:
            raise UpdateError('candidate_tree_exit_unconfirmed')
        self.target = OwnedChild([sys.executable, '-B', Path(root) / 'windows' / 'ota_entry.py',
                                  '--home', self.home, '--quarantine'], cwd=root)

    # 等待候选明确回报版本和健康验证完成；进程存在或窗口出现都不够。
    def verify(self, root):
        message = self.target.read(timeout=100)
        expected = read(Path(root) / 'windows' / 'releases.json')['releases'][0]['version']
        if message != {'schema': 1, 'ready': True, 'version': expected, 'probe': 'verified'}:
            raise UpdateError('candidate_health_unverified')

    # 把候选从隔离验证转成可用 GUI，释放应用锁并发送 activate。
    # 一旦开始激活就可能接收用户操作，确认丢失时不能盲目杀掉或回退它。
    def activate(self):
        self.target.allow_survive_owner_exit()
        self.activation_started = True
        self.application_lock.__exit__(None, None, None)
        self.application_lock = None
        self.target.write({'schema': 1, 'op': 'activate'})
        if self.target.read(timeout=15) != {'schema': 1, 'active': True}:
            raise UpdateError('candidate_activation_unverified')

    # 仅在候选尚未激活时清理其整棵树，为安全回退提供退出证据。
    def cleanup_target(self):
        if self.activation_started:
            raise UpdateError('activation_outcome_requires_review')
        if self.target is not None:
            self.target.stop_tree()
            self.target = None

    # 释放事务资源；已可能激活的窗口只分离管理句柄，避免误杀正在使用的新版本。
    def release(self, phase):
        if self.application_lock is not None:
            self.application_lock.__exit__(None, None, None)
            self.application_lock = None
        if self.target is not None:
            if self.activation_started:
                # Never kill a potentially activated GUI on ambiguous acknowledgement.
                self.target.detach_windows()
            else:
                self.target.stop_tree()

