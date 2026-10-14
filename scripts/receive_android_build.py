"""SSH forced command: receive immutable Android builds, never publish an OTA feed.

Install this file root-owned. The restricted upload account owns only INCOMING.
Input is one bounded JSON line followed by the exact declared file bytes and EOF.
No shell commands, archive extraction, caller paths or credentials are accepted.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import sys
import tempfile

INCOMING = Path('/srv/codex-desktop-remote-incoming/android')
FILES = {'app.apk': 48 * 1024**2, 'output-metadata.json': 65536,
         'reports.zip': 16 * 1024**2, 'verification.json': 65536}
QUOTA = 1024**3


def require(value):
    if not value:
        raise ValueError('build_upload_rejected')


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def safe(path):
    for part in (path, *path.parents):
        require(not part.is_symlink())


def syncdir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def receive(stream, root=INCOMING):
    root = Path(root).absolute()
    safe(root)
    require(root.is_dir())
    raw = stream.readline(8193)
    require(len(raw) <= 8192 and raw.endswith(b'\n'))
    header = json.loads(raw, object_pairs_hook=unique)
    require(set(header) == {'schema', 'runId', 'attempt', 'commit', 'version', 'files'})
    require(type(header['schema']) is int and header['schema'] == 1)
    for field, pattern in [('runId', r'[1-9][0-9]{0,19}'), ('attempt', r'[1-9][0-9]{0,3}'),
                           ('commit', r'[0-9a-f]{40}'), ('version', r'0\.1\.0-alpha\.[1-9][0-9]{0,4}')]:
        require(isinstance(header[field], str) and re.fullmatch(pattern, header[field]))
    rows = header['files']
    require(isinstance(rows, list) and len(rows) == len(FILES))
    names = set()
    for row in rows:
        require(isinstance(row, dict) and set(row) == {'name', 'bytes', 'sha256'})
        name = row['name']
        require(isinstance(name, str) and name in FILES and name not in names)
        names.add(name)
        require(type(row['bytes']) is int and 0 < row['bytes'] <= FILES[name])
        require(isinstance(row['sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', row['sha256']))
    total = sum(row['bytes'] for row in rows)
    target = root / (header['runId'] + '-' + header['attempt'])
    require(not target.exists())
    safe(target)
    used = 0
    for folder, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            p = Path(folder) / name
            require(not p.is_symlink())
            if p.is_file():
                require(p.stat().st_nlink == 1)
                used += p.stat().st_size
    require(used + total + len(raw) < QUOTA)
    require(shutil.disk_usage(root).free > total + 64 * 1024**2)
    temp = Path(tempfile.mkdtemp(prefix='.partial-', dir=root))
    try:
        for row in rows:
            digest = hashlib.sha256()
            with (temp / row['name']).open('xb') as out:
                remaining = row['bytes']
                while remaining:
                    chunk = stream.read(min(remaining, 128 * 1024))
                    require(bool(chunk))
                    remaining -= len(chunk)
                    out.write(chunk)
                    digest.update(chunk)
                out.flush()
                os.fsync(out.fileno())
            require(digest.hexdigest() == row['sha256'])
        require(stream.read(1) == b'')
        with (temp / 'receipt.json').open('xb') as out:
            out.write(raw)
            out.flush()
            os.fsync(out.fileno())
        syncdir(temp)
        require(not target.exists())
        os.rename(temp, target)
        syncdir(root)
        return {'stored': True, 'runId': header['runId'], 'attempt': header['attempt'],
                'commit': header['commit'], 'version': header['version'], 'bytes': total}
    finally:
        if temp.exists():
            shutil.rmtree(temp)


def main():
    import fcntl
    os.umask(0o077)
    try:
        require(os.environ.get('SSH_ORIGINAL_COMMAND') == 'receive-android-build')
        signal.signal(signal.SIGALRM, lambda *_: (_ for _ in ()).throw(TimeoutError()))
        signal.alarm(180)
        safe(INCOMING)
        fd = os.open(INCOMING / '.upload.lock', os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = receive(sys.stdin.buffer)
        print(json.dumps(result))
        return 0
    except Exception:
        print('build_upload_rejected', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
