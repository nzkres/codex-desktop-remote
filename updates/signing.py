"""Publisher-only Windows RSA vault; private PEM is never written to disk.

This is not shipped in application update packages. Creation is explicit, only
in a new external directory. A failed creation preserves its directory for
review; neither creation nor signing repairs or replaces an existing vault.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from updates.security import UpdateError, _json, _safe_local


ROOT = Path(__file__).resolve().parents[1]
PRODUCT = 'codex-desktop-remote'
KEY_ID = re.compile(r'[A-Za-z0-9._-]{1,64}\Z')
MAX_FILE = 64 * 1024


def _windows():
    if os.name != 'nt':
        raise UpdateError('windows_signing_required')


def _directory(directory, key_id, root):
    _windows()
    if not isinstance(key_id, str) or not KEY_ID.fullmatch(key_id):
        raise UpdateError('invalid_signing_key_id')
    if not Path(directory).is_absolute():
        raise UpdateError('external_signing_directory_required')
    path = _safe_local(directory).resolve()
    if any(path.is_relative_to(Path(repo).resolve()) for repo in (ROOT, root)):
        raise UpdateError('external_signing_directory_required')
    return path


def _public(key, key_id):
    public = key.public_key()
    pem = public.public_bytes(serialization.Encoding.PEM,
                              serialization.PublicFormat.SubjectPublicKeyInfo).decode('ascii')
    der = public.public_bytes(serialization.Encoding.DER,
                              serialization.PublicFormat.SubjectPublicKeyInfo)
    return {'schema': 1, 'product': PRODUCT, 'keyId': key_id,
            'publicKeyPem': pem, 'publicKeySha256': hashlib.sha256(der).hexdigest()}


def _write_new(path, data):
    path = _safe_local(path)
    with path.open('xb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def _read_file(path):
    path = _safe_local(path)
    with path.open('rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or not 0 < info.st_size <= MAX_FILE:
            raise UpdateError('invalid_signing_vault')
        data = stream.read(MAX_FILE + 1)
        if len(data) != info.st_size:
            raise UpdateError('invalid_signing_vault')
        return data


def create_vault(directory, *, key_id, root=ROOT):
    """Create a fresh CurrentUser-DPAPI vault, returning public information only."""
    path = _directory(directory, key_id, root)
    if path.exists() or not path.parent.is_dir():
        raise UpdateError('new_signing_directory_required')
    from windows.vendor.connector_core import dpapi, private_directory
    try:
        path.mkdir()  # Exclusive; no recursive creation or changes to parent ACLs.
        private_directory(path)
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        public = _public(key, key_id)
        record = {'schema': 1, 'product': PRODUCT, 'keyId': key_id,
                  'publicKeySha256': public['publicKeySha256'],
                  'privateKeyPem': key.private_bytes(serialization.Encoding.PEM,
                      serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode('ascii')}
        protected = dpapi(json.dumps(record, sort_keys=True).encode('ascii'))
        _write_new(path / 'publisher.dpapi', protected)
        # Public record is the last file, so incomplete creation never looks ready.
        _write_new(path / 'public.json', json.dumps(public, sort_keys=True).encode('ascii'))
        restored = load_key(path, key_id=key_id, root=root)
        if _public(restored, key_id) != public:
            raise UpdateError('invalid_signing_vault')
        return public
    except Exception:
        # Keep any partial, encrypted files; never remove a racing/existing path.
        raise UpdateError('signing_vault_create_failed') from None


def load_key(directory, *, key_id, root=ROOT):
    """Unseal only in process memory; fail closed on corruption or binding changes."""
    path = _directory(directory, key_id, root)
    from windows.vendor.connector_core import dpapi
    try:
        public = _json(_read_file(path / 'public.json'))
        record = _json(dpapi(_read_file(path / 'publisher.dpapi'), decrypt=True))
        if (not isinstance(record, dict)
                or set(record) != {'schema', 'product', 'keyId', 'publicKeySha256', 'privateKeyPem'}
                or type(record['schema']) is not int or record['schema'] != 1
                or record['product'] != PRODUCT or record['keyId'] != key_id
                or not isinstance(record['privateKeyPem'], str)):
            raise UpdateError('invalid_signing_vault')
        key = serialization.load_pem_private_key(record['privateKeyPem'].encode('ascii'), password=None)
        if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 3072:
            raise UpdateError('invalid_signing_vault')
        expected = _public(key, key_id)
        if (not isinstance(public, dict) or type(public.get('schema')) is not int
                or public != expected or record['publicKeySha256'] != expected['publicKeySha256']):
            raise UpdateError('invalid_signing_vault')
        return key
    except Exception:
        raise UpdateError('invalid_signing_vault') from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('create', 'inspect'))
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--key-id', required=True)
    args = parser.parse_args(argv)
    try:
        public = (create_vault(args.directory, key_id=args.key_id) if args.command == 'create'
                  else _public(load_key(args.directory, key_id=args.key_id), args.key_id))
    except Exception:
        print('signing_vault_failed', file=sys.stderr)
        return 1
    # No private PEM, protected blob or local path in command output.
    print(json.dumps(public, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
