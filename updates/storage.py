# OTA 持久化基础：检查路径、读写 JSON、原子替换、互斥锁和版本槽完整性。
# 版本目录与运行数据分离；本模块不把旧快照恢复成用户的当前数据。

"""Local durable update state; never restore a user's data from a snapshot."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import tempfile

from updates.security import UpdateError


def no_links(path):
    # 拒绝目标及父目录中的符号链接/联接点，防止更新写到预期安装目录之外。
    path = Path(path).absolute()
    for item in (path, *path.parents):
        if item.is_symlink() or (hasattr(item, 'is_junction') and item.is_junction()):
            raise UpdateError('update_path_link_rejected')
    return path


def read(path):
    # 读取体积受限的本地 JSON；重复键直接报错，避免同一状态被不同解析方式解释。
    path = no_links(path)
    if path.stat().st_size > 1024 * 1024:
        raise UpdateError('update_state_too_large')
    def unique(pairs):
        result = {}
        for k, v in pairs:
            if k in result:
                raise UpdateError('duplicate_update_state_key')
            result[k] = v
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def atomic(path, value):
    # 将状态先写入同目录临时文件并落盘，再原子替换目标；返回值为空。
    # 断电或进程中断时，读者应看到完整旧文件或完整新文件。
    path = no_links(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(value, sort_keys=True, ensure_ascii=True, allow_nan=False).encode()
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix='.update-')
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        if os.name != 'nt':
            directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def digest(value):
    # 使用固定 JSON 序列化方式生成摘要，让相同元数据得到相同指纹。
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True,
                                     separators=(',', ':'), allow_nan=False).encode()).hexdigest()


@contextmanager
def lock(path):
    # 非阻塞独占锁：同一安装不能同时运行两个更新交易；离开 with 时释放。
    path = no_links(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    stream = path.open('a+b')
    try:
        if os.name == 'nt':
            import msvcrt
            stream.seek(0, 2)
            if stream.tell() == 0:
                stream.write(b'0'); stream.flush()
            stream.seek(0)
            try:
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError:
                raise UpdateError('update_or_application_already_running') from None
        else:
            import fcntl
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                raise UpdateError('update_or_application_already_running') from None
        yield
    finally:
        stream.close()


def slot(home, pointer):
    # pointer 必须只指向本安装的 versions/子目录；返回已校验的目录和清单。
    # 树内容与清单哈希都要检查，不能只相信 current.json 指向的版本号。
    relative = pointer.get('path', '')
    parts = Path(relative).parts
    if len(parts) != 2 or parts[0] != 'versions' or '\\' in relative or '..' in parts:
        raise UpdateError('invalid_version_pointer')
    root = no_links(Path(home) / relative)
    if not root.is_dir():
        raise UpdateError('installed_version_missing')
    from updates.security import verify_tree
    manifest = verify_tree(root, expected_version=pointer.get('version'))
    if hashlib.sha256((root / 'package.json').read_bytes()).hexdigest() != pointer.get('manifestSha256'):
        raise UpdateError('installed_version_changed')
    return root, manifest


def ensure_launchable(home):
    # 未完成的交易留待人工复核；启动器不能悄悄运行一个切换到一半的版本。
    journal = Path(home) / 'state' / 'transaction.json'
    if journal.exists() and read(journal).get('phase') not in ('committed', 'rolled_back'):
        raise UpdateError('interrupted_update_requires_review')
