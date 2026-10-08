"""Local durable update state; never restore a user's data from a snapshot."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import tempfile

from updates.security import UpdateError


def no_links(path):
    path = Path(path).absolute()
    for item in (path, *path.parents):
        if item.is_symlink() or (hasattr(item, 'is_junction') and item.is_junction()):
            raise UpdateError('update_path_link_rejected')
    return path


def read(path):
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
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True,
                                     separators=(',', ':'), allow_nan=False).encode()).hexdigest()


@contextmanager
def lock(path):
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
    journal = Path(home) / 'state' / 'transaction.json'
    if journal.exists() and read(journal).get('phase') not in ('committed', 'rolled_back'):
        raise UpdateError('interrupted_update_requires_review')
