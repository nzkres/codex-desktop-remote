"""Explicit, publisher-only test APK signer and scoped GitHub provisioning.

Private material stays in the existing Windows DPAPI publisher directory.
GitHub receives only the six Android build inputs, via stdin, never argv.
No server deployment, account login, APK installation or automatic overwrite.
"""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import sys
from urllib.parse import urlsplit

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

from scripts.check_android_trust import validate_trust
from updates import signing
from updates.security import UpdateError, _json, _url
from updates.storage import atomic


REPOSITORY = 'nzkres/codex-desktop-remote'
ALIAS = 'desktop-remote-test-v1'
NAMES = ('ANDROID_UPDATE_TRUST_JSON', 'ANDROID_SIGNING_KEYSTORE_BASE64',
         'ANDROID_SIGNING_STORE_PASSWORD', 'ANDROID_SIGNING_KEY_ALIAS',
         'ANDROID_SIGNING_KEY_PASSWORD', 'ANDROID_SIGNING_CERT_SHA256')


def _trust(home, key_id, origin):
    key = signing.load_key(home, key_id=key_id)
    parsed = urlsplit(origin)
    if parsed.path or origin != 'https://' + parsed.netloc:
        raise UpdateError('invalid_signing_origin')
    source = _url(origin + '/desktop-remote-updates/test/android/latest.json')
    return {'schema': 1, 'product': signing.PRODUCT, 'channel': 'test',
            'keys': {key_id: signing._public(key, key_id)['publicKeyPem']},
            'sources': {'android': source}}


def _encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode('ascii')


def create_android_signer(home, *, key_id, origin):
    trust = _trust(home, key_id, origin)
    home = Path(home)
    files = ('android-signing.dpapi', 'android-public.json', 'android-trust.json')
    if any((home / name).exists() for name in files):
        raise UpdateError('android_signer_already_exists')
    from windows.vendor.connector_core import dpapi
    try:
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Codex Desktop Remote test')])
        now = datetime.now(timezone.utc)
        certificate = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
                       .public_key(key.public_key()).serial_number(x509.random_serial_number())
                       .not_valid_before(now - timedelta(days=1))
                       .not_valid_after(now + timedelta(days=30 * 365))
                       .sign(key, hashes.SHA256()))
        password = secrets.token_urlsafe(48)
        p12 = pkcs12.serialize_key_and_certificates(ALIAS.encode('ascii'), key, certificate, None,
                serialization.BestAvailableEncryption(password.encode('ascii')))
        fingerprint = certificate.fingerprint(hashes.SHA256()).hex()
        public = {'schema': 1, 'product': signing.PRODUCT, 'keyId': key_id, 'origin': origin,
                  'keyAlias': ALIAS, 'certificateSha256': fingerprint,
                  'trustSha256': hashlib.sha256(_encoded(trust)).hexdigest()}
        record = {**public, 'keystoreBase64': base64.b64encode(p12).decode('ascii'), 'password': password}
        signing._write_new(home / files[0], dpapi(_encoded(record)))
        signing._write_new(home / files[1], _encoded(public))
        signing._write_new(home / files[2], _encoded(trust))
        _github_values(home, key_id=key_id, origin=origin)
        return public
    except Exception:
        raise UpdateError('android_signer_create_failed') from None


def _github_values(home, *, key_id, origin):
    """Secret-bearing internal return value; never serialize to logs or stdout."""
    expected_trust = _trust(home, key_id, origin)
    home = Path(home)
    from windows.vendor.connector_core import dpapi
    try:
        public = _json(signing._read_file(home / 'android-public.json'))
        record = _json(dpapi(signing._read_file(home / 'android-signing.dpapi'), decrypt=True))
        trust_raw = signing._read_file(home / 'android-trust.json')
        if (not isinstance(public, dict) or not isinstance(record, dict)
                or set(public) != {'schema', 'product', 'keyId', 'origin', 'keyAlias', 'certificateSha256', 'trustSha256'}
                or set(record) != set(public) | {'keystoreBase64', 'password'}
                or any(record.get(name) != value for name, value in public.items())
                or type(public['schema']) is not int or public['schema'] != 1
                or public['product'] != signing.PRODUCT or public['keyId'] != key_id
                or public['origin'] != origin or public['keyAlias'] != ALIAS
                or trust_raw != _encoded(expected_trust)
                or public['trustSha256'] != hashlib.sha256(trust_raw).hexdigest()):
            raise ValueError()
        validate_trust(home / 'android-trust.json')
        if not isinstance(record['password'], str) or len(record['password']) < 32:
            raise ValueError()
        p12 = base64.b64decode(record['keystoreBase64'], validate=True)
        loaded = pkcs12.load_pkcs12(p12, record['password'].encode('ascii'))
        if (not isinstance(loaded.key, rsa.RSAPrivateKey) or loaded.key.key_size < 3072
                or loaded.cert is None or loaded.additional_certs
                or loaded.cert.friendly_name != ALIAS.encode('ascii')
                or loaded.key.public_key().public_numbers() != loaded.cert.certificate.public_key().public_numbers()
                or loaded.cert.certificate.fingerprint(hashes.SHA256()).hex() != public['certificateSha256']):
            raise ValueError()
        return dict(zip(NAMES, (trust_raw.decode('ascii'), record['keystoreBase64'], record['password'],
                                ALIAS, record['password'], public['certificateSha256'])))
    except Exception:
        raise UpdateError('invalid_android_signer') from None


def provision_github(home, *, key_id, origin):
    values = _github_values(home, key_id=key_id, origin=origin)
    journal_path = Path(home) / 'github-provision.json'
    if journal_path.exists():
        raise UpdateError('signing_provision_record_exists')
    try:
        result = subprocess.run(['gh', 'secret', 'list', '--repo', REPOSITORY, '--json', 'name'],
                                capture_output=True, text=True, check=True, timeout=30)
        existing = {item['name'] for item in json.loads(result.stdout)}
        if existing.intersection(NAMES):
            raise ValueError()
    except Exception:
        raise UpdateError('signing_provision_preflight_failed') from None
    journal = {'schema': 1, 'repository': REPOSITORY, 'phase': 'configuring',
               'completedNames': [], 'activeName': None}
    signing._write_new(journal_path, _encoded(journal))
    try:
        for name in NAMES:
            journal['activeName'] = name
            atomic(journal_path, journal)
            # Secret goes only over stdin; command errors are never echoed.
            subprocess.run(['gh', 'secret', 'set', name, '--repo', REPOSITORY],
                           input=values[name], capture_output=True, text=True, check=True, timeout=30)
            journal['completedNames'].append(name)
            journal['activeName'] = None
            atomic(journal_path, journal)
        journal['phase'] = 'configured'
        atomic(journal_path, journal)
        return {'configured': True, 'repository': REPOSITORY, 'secretCount': len(NAMES)}
    except Exception:
        journal['phase'] = 'needs_review'
        atomic(journal_path, journal)
        raise UpdateError('signing_provision_requires_review') from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('create', 'provision-github'))
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--key-id', required=True)
    parser.add_argument('--origin', required=True)
    args = parser.parse_args(argv)
    try:
        result = (create_android_signer(args.directory, key_id=args.key_id, origin=args.origin)
                  if args.command == 'create' else
                  provision_github(args.directory, key_id=args.key_id, origin=args.origin))
    except Exception:
        print('android_signing_failed', file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
