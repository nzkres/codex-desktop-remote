"""Create a new ignored, nonsecret test config; never copy a paired identity.

The following probe only reads the explicitly selected Desktop test task and
records its effective permission digest. It does not send model input.
"""
import argparse
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--thread-id', required=True, type=uuid.UUID)
    parser.add_argument('--project', required=True, type=Path)
    parser.add_argument('--desktop-root', required=True, type=Path)
    parser.add_argument('--node', required=True, type=Path)
    parser.add_argument('--frpc', required=True, type=Path)
    args = parser.parse_args()
    output = ROOT / 'fixture.local.json'
    if output.exists():
        raise ValueError('configuration_already_exists')
    paths = {key: getattr(args, key).resolve(strict=True) for key in ('project', 'desktop_root', 'node', 'frpc')}
    if not paths['project'].is_dir() or not paths['desktop_root'].is_dir():
        raise ValueError('directory_required')
    for key in ('node', 'frpc'):
        if not paths[key].is_file():
            raise ValueError('runtime_file_required')
    # Reserve no fixed service port. The actual start rechecks availability.
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    data = ROOT / 'data' / ('fixture-' + str(args.thread_id))
    cfg = {'schema': 1, 'threadId': str(args.thread_id), 'projectPath': str(paths['project']),
           'desktopRoot': str(paths['desktop_root']), 'title': 'Desktop 手机测试',
           'permissionsHash': '', 'port': port, 'dataDir': str(data)}
    for key in ('node', 'frpc'):
        with paths[key].open('rb') as stream:
            cfg[key] = {'path': str(paths[key]), 'sha256': hashlib.file_digest(stream, 'sha256').hexdigest()}
    with output.open('x', encoding='utf-8') as stream:
        json.dump(cfg, stream, ensure_ascii=False, indent=2)
    # The probe validates the strict Desktop pin and DESKTOP_REMOTE_READY marker
    # before setting the permission digest. No relay access or --send-literal.
    result = subprocess.run([str(paths['node']), str(ROOT / 'scripts' / 'probe.mjs'), str(output)],
        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0, timeout=45)
    if result.returncode != 0:
        raise ValueError('desktop_fixture_unverified_config_retained')
    print('Test configuration prepared. No model input sent and no device paired.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Do not echo private Desktop output, paths, contents or exceptions.
        code = str(error) if isinstance(error, ValueError) and str(error) in (
            'configuration_already_exists', 'directory_required', 'runtime_file_required',
            'desktop_fixture_unverified_config_retained') else 'configuration_failed'
        print(code, file=sys.stderr)
        sys.exit(1)
