"""Reviewed operator action for the existing test :9443 proxy, not a new service.

Run on the reviewed host as root with a public DATA plan prefixed on stdin.
prepare/activate are one-time bootstrap; publish only swaps signed demo JSON.
"""
import base64
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

SERVER_HOME = Path('/srv/codex-desktop-remote-test')
OPERATOR = Path('/opt/codex-desktop-remote-test/operator')
STAGE = OPERATOR / 'preview-feed-bootstrap-20261015'
CONFIG = Path('/etc/codex-desktop-remote-test/Caddyfile')
MOUNT = Path('/var/lib/codex-desktop-remote-test-caddy/config')
FEED = MOUNT / 'ui-preview-test'
CANDIDATE = MOUNT / 'ui-preview-bootstrap18'
CONTAINER = 'codex-desktop-remote-test-caddy'
PIN = 'sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d'
SOURCE = 'https://106.53.126.69:9443/desktop-remote-previews/test/latest.json'
KEY_PIN = 'c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d'

def safe(path):
    for p in (path, *path.parents):
        assert not p.is_symlink(), 'preview_link_rejected'

def read(path):
    safe(path)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as f:
        info = os.fstat(f.fileno())
        assert stat.S_ISREG(info.st_mode) and info.st_nlink == 1
        return f.read()

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def write(path, raw):
    safe(path)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(raw); f.flush(); os.fsync(f.fileno())

def syncdir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(fd)
    finally: os.close(fd)

def snapshot():
    c = json.loads(subprocess.check_output(['docker', 'inspect', CONTAINER]))[0]
    assert c['State']['Running'] and c['Image'] == PIN
    assert c['Config']['Labels']['com.docker.compose.project'] == 'codex-desktop-remote-test'
    mounts = {m['Destination']: m['Source'] for m in c['Mounts']}
    assert mounts['/config'] == str(MOUNT) and mounts['/etc/caddy/Caddyfile'] == str(CONFIG)
    return {'containerId': c['Id'], 'pid': c['State']['Pid'], 'startedAt': c['State']['StartedAt'],
            'services': {name: subprocess.check_output(['systemctl','show',name,'--property=MainPID,ActiveEnterTimestampMonotonic,ActiveState'],text=True).strip()
                         for name in ('codex-desktop-remote-test.service','codex-desktop-remote-test-frps.service')},
            'feeds': {p.parent.name:digest(read(p)) for p in (SERVER_HOME/'data/update-feed/test').glob('*/latest.json')},
            'relayProgram': digest(read(SERVER_HOME/'current.json')),
            'old443Config': digest(read(Path('/opt/codex-remote-caddy/Caddyfile')))}

def verify(raw, public):
    assert 0 < len(raw) <= 512*1024
    envelope = json.loads(raw)
    assert set(envelope) == {'schema','keyId','payload','signature'} and envelope['schema'] == 1
    assert envelope['keyId'] == 'cdr-ota-test-20261008-v1'
    key = serialization.load_pem_public_key(public.encode())
    assert key.key_size >= 3072
    assert digest(key.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)) == KEY_PIN
    payload = base64.b64decode(envelope['payload'],validate=True)
    key.verify(base64.b64decode(envelope['signature'],validate=True),payload,padding.PKCS1v15(),hashes.SHA256())
    p = json.loads(payload)
    assert set(p) == {'schema','product','source','renderer','sequence','id','title','issuedAt','expiresAt','htmlSha256','html'}
    assert (p['schema'],p['product'],p['source'],p['renderer']) == (1,'desktop-remote-ui-preview',SOURCE,1)
    assert type(p['sequence']) is int and p['sequence'] > 0
    assert p['issuedAt'] <= time.time()+300 < p['expiresAt'] and p['issuedAt'] < p['expiresAt'] <= p['issuedAt']+31*86400
    html = base64.b64decode(p['html'],validate=True)
    assert 0 < len(html) <= 256*1024 and digest(html) == p['htmlSha256']
    html.decode('utf8')
    return p

def main(data):
    assert os.geteuid() == 0 and data['mode'] in ('prepare','activate','publish')
    os.umask(0o077)
    for path in (OPERATOR, MOUNT, CONFIG): safe(path)
    assert OPERATOR.stat().st_uid == 0 and stat.S_IMODE(OPERATOR.stat().st_mode) == 0o700
    fd = os.open(OPERATOR/'preview-feed-publish.lock', os.O_WRONLY|os.O_CREAT|os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd,'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
        mode = data['mode']
        if mode == 'prepare':
            before = snapshot()
            assert before['containerId'] == data['containerId']
            original = read(CONFIG); candidate = base64.b64decode(data['candidate'],validate=True)
            assert digest(original) == data['oldConfigSha256']
            route = base64.b64decode(data['route'],validate=True)
            assert candidate.count(route) == 1 and candidate.replace(route,b'',1) == original
            assert b'@ui_preview' not in original and b'/config/ui-preview-test' in route
            assert b'uri /control-api/version' in route and b'forward_auth 127.0.0.1:28191' in route
            raw = base64.b64decode(data['envelope'],validate=True); payload = verify(raw,data['publicKey'])
            assert payload['sequence'] == 1
            for directory in (STAGE,CANDIDATE,FEED):
                safe(directory); directory.mkdir(mode=0o700)
            write(STAGE/'original.Caddyfile',original)
            write(STAGE/'candidate.Caddyfile',candidate)
            write(CANDIDATE/'Caddyfile',candidate)
            write(FEED/'latest.json',raw)
            write(STAGE/'r1.json',raw)
            result = subprocess.run(['docker','exec',CONTAINER,'caddy','validate','--config','/config/ui-preview-bootstrap18/Caddyfile','--adapter','caddyfile'],capture_output=True)
            assert result.returncode == 0, 'preview_caddy_validation_failed'
            assert snapshot() == before and read(CONFIG) == original
            write(STAGE/'before.json',json.dumps(before,sort_keys=True).encode())
            syncdir(STAGE); syncdir(FEED); syncdir(CANDIDATE)
            print(json.dumps({'prepared':True,'revision':1,'candidateConfigSha256':digest(candidate),'feedSha256':digest(raw),'validated':True,'servicesUnchanged':True}))
        elif mode == 'activate':
            before = json.loads(read(STAGE/'before.json'))
            assert snapshot() == before
            original = read(STAGE/'original.Caddyfile'); candidate = read(STAGE/'candidate.Caddyfile')
            assert read(CONFIG) == original
            # Preserve the inode: the running container has a single-file bind mount.
            descriptor = os.open(CONFIG,os.O_RDWR|os.O_NOFOLLOW)
            with os.fdopen(descriptor,'r+b') as f:
                assert f.read() == original and os.fstat(f.fileno()).st_nlink == 1
                f.seek(0); f.write(candidate); f.truncate(); f.flush(); os.fsync(f.fileno())
            visible = subprocess.check_output(['docker','exec',CONTAINER,'cat','/etc/caddy/Caddyfile'])
            assert visible == candidate
            result = subprocess.run(['docker','exec',CONTAINER,'caddy','reload','--force','--config','/etc/caddy/Caddyfile','--adapter','caddyfile','--address','127.0.0.1:22019'],capture_output=True)
            if result.returncode:
                write(STAGE/'activation-needs-review.json',json.dumps({'candidate':digest(candidate),'old':digest(original)}).encode())
                raise RuntimeError('preview_reload_requires_state_review_no_blind_retry')
            assert snapshot() == before and read(CONFIG) == candidate
            report={'activated':True,'configSha256':digest(candidate),'servicesUnchanged':True,'otherFeedsUnchanged':True,
                    'old443Unchanged':True,'relayProgramUnchanged':True,'at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
            write(STAGE/'activated.json',json.dumps(report,indent=2).encode());syncdir(STAGE)
            print(json.dumps(report))
        else:
            activated=json.loads(read(STAGE/'activated.json'))
            assert digest(read(CONFIG)) == activated['configSha256']
            before=snapshot(); old=read(FEED/'latest.json')
            assert digest(old) == data['expectedSha256'], 'preview_expected_content_changed'
            raw=base64.b64decode(data['envelope'],validate=True)
            previous=verify(old,data['publicKey']); candidate=verify(raw,data['publicKey'])
            assert candidate['sequence'] > previous['sequence']
            archive=STAGE/f"r{candidate['sequence']}.json"
            write(archive,raw)
            temp=FEED/f".r{candidate['sequence']}-prepared"
            write(temp,raw)
            assert digest(read(FEED/'latest.json')) == data['expectedSha256']
            os.replace(temp,FEED/'latest.json');syncdir(FEED)
            assert read(FEED/'latest.json') == raw and snapshot() == before
            report={'published':True,'revision':candidate['sequence'],'sha256':digest(raw),'servicesUnchanged':True,
                    'appFeedsUnchanged':True,'proxyConfigUnchanged':digest(read(CONFIG))==activated['configSha256'],
                    'at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
            write(STAGE/f"r{candidate['sequence']}-published.json",json.dumps(report,indent=2).encode());syncdir(STAGE)
            print(json.dumps(report))

if __name__ == '__main__':
    main(DATA)
