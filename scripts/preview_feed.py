"""Sign/verify small standalone demo fragments; never publishes APKs or live task data."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import time
from urllib.parse import urlsplit

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

MAX_HTML = 256 * 1024
MAX_ENVELOPE = 512 * 1024
PRODUCT = 'desktop-remote-ui-preview'
FIELDS = {'schema', 'product', 'source', 'renderer', 'sequence', 'id', 'title', 'issuedAt', 'expiresAt', 'htmlSha256', 'html'}

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def _object(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('preview_duplicate_key')
            result[key] = value
        return result
    result = json.loads(raw.decode('utf8'), object_pairs_hook=pairs)
    if not isinstance(result, dict):
        raise ValueError('preview_fields_invalid')
    return result

def _decode(text):
    if not isinstance(text, str):
        raise ValueError('preview_encoding_invalid')
    raw = base64.b64decode(text, validate=True)
    if base64.b64encode(raw).decode() != text:
        raise ValueError('preview_encoding_invalid')
    return raw

def checked_source(source):
    uri = urlsplit(source)
    if (uri.scheme != 'https' or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment
            or not re.fullmatch(r'/desktop-remote-previews/(test|stable)/latest\.json', uri.path)):
        raise ValueError('preview_source_invalid')
    return source

def sign_fragment(html, *, key, key_id, source, sequence, title, preview_id='quota-motion', now=None):
    checked_source(source)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 3072 or type(sequence) is not int or sequence < 1:
        raise ValueError('preview_signing_invalid')
    if not 0 < len(html) <= MAX_HTML or not isinstance(title, str) or not 0 < len(title.strip()) <= 100:
        raise ValueError('preview_size_invalid')
    html.decode('utf8')
    if not re.fullmatch(r'[a-z0-9-]{1,64}', preview_id):
        raise ValueError('preview_id_invalid')
    now = int(time.time()) if now is None else now
    payload = dict(schema=1, product=PRODUCT, source=source, renderer=1, sequence=sequence, id=preview_id,
                   title=title, issuedAt=now, expiresAt=now + 7 * 86400, htmlSha256=sha(html), html=base64.b64encode(html).decode())
    raw = json.dumps(payload, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()
    envelope = dict(schema=1, keyId=key_id, payload=base64.b64encode(raw).decode(),
                    signature=base64.b64encode(key.sign(raw, padding.PKCS1v15(), hashes.SHA256())).decode())
    result = json.dumps(envelope, sort_keys=True, separators=(',', ':')).encode()
    if len(result) > MAX_ENVELOPE:
        raise ValueError('preview_size_invalid')
    return result

def verify_fragment(raw, *, keys, source, sequence=0, binding='', now=None):
    checked_source(source)
    if not 0 < len(raw) <= MAX_ENVELOPE:
        raise ValueError('preview_size_invalid')
    envelope = _object(raw)
    if set(envelope) != {'schema', 'keyId', 'payload', 'signature'} or type(envelope['schema']) is not int or envelope['schema'] != 1:
        raise ValueError('preview_fields_invalid')
    public = serialization.load_pem_public_key(keys[envelope['keyId']].encode())
    if not isinstance(public, rsa.RSAPublicKey) or public.key_size < 3072:
        raise ValueError('preview_key_invalid')
    payload = _decode(envelope['payload'])
    public.verify(_decode(envelope['signature']), payload, padding.PKCS1v15(), hashes.SHA256())
    p = _object(payload)
    if set(p) != FIELDS or p['product'] != PRODUCT or p['source'] != source:
        raise ValueError('preview_target_invalid')
    if any(type(p[name]) is not int for name in ('schema', 'renderer', 'sequence', 'issuedAt', 'expiresAt')) or p['schema'] != 1 or p['renderer'] != 1:
        raise ValueError('preview_renderer_unsupported')
    now = int(time.time()) if now is None else now
    if p['issuedAt'] > now + 300 or not p['issuedAt'] < p['expiresAt'] or p['expiresAt'] <= now or p['expiresAt'] - p['issuedAt'] > 31 * 86400:
        raise ValueError('preview_expired')
    if p['sequence'] < max(1, sequence) or (p['sequence'] == sequence and sha(payload) != binding):
        raise ValueError('preview_replayed')
    if not isinstance(p['id'], str) or not re.fullmatch(r'[a-z0-9-]{1,64}', p['id']) or not isinstance(p['title'], str) or not 0 < len(p['title'].strip()) <= 100:
        raise ValueError('preview_fields_invalid')
    html = _decode(p['html'])
    if not 0 < len(html) <= MAX_HTML or sha(html) != p['htmlSha256']:
        raise ValueError('preview_hash_invalid')
    html.decode('utf8')
    return p, sha(payload)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--html', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--sequence', type=int, required=True)
    parser.add_argument('--title', required=True)
    parser.add_argument('--signing-home', type=Path, required=True)
    parser.add_argument('--key-id', required=True)
    args = parser.parse_args()
    from updates.signing import load_key
    key = load_key(args.signing_home, key_id=args.key_id)
    raw = sign_fragment(args.html.read_bytes(), key=key, key_id=args.key_id, source=args.source,
                        sequence=args.sequence, title=args.title)
    public = key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    payload, binding = verify_fragment(raw, keys={args.key_id: public}, source=args.source)
    with args.output.open('xb') as stream:
        stream.write(raw)
    print(json.dumps({'sequence':payload['sequence'], 'title':payload['title'], 'bytes':len(raw), 'sha256':sha(raw),
                      'binding':binding, 'htmlSha256':payload['htmlSha256'], 'signatureVerified':True}))

if __name__ == '__main__':
    main()
