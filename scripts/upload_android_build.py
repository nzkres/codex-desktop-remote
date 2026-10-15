"""Send a checked, main-only APK to the private server through a restricted SSH key."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from scripts.check_android_apk import verify_apk


def main():
    if (os.environ.get('GITHUB_REPOSITORY') != 'nzkres/codex-desktop-remote'
            or os.environ.get('GITHUB_REF') != 'refs/heads/main'
            or os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch'):
        raise ValueError('controlled_main_build_required')
    os.umask(0o077)
    output = Path('android-native/app/build/outputs/apk/debug')
    apk = output / 'app-debug.apk'
    metadata = output / 'output-metadata.json'
    report = verify_apk(apk, metadata, 'android-native/releases.json')
    # These values are public fingerprints, never signing material.
    report['certificateSha256'] = os.environ['EXPECTED_CERT_SHA256'].lower()
    report['commit'] = os.environ['GITHUB_SHA']
    with tempfile.TemporaryDirectory(prefix='cdr-upload-', dir=os.environ['RUNNER_TEMP']) as temporary:
        folder = Path(temporary)
        key, hosts = folder / 'key', folder / 'known_hosts'
        # gh secret set trims trailing CR/LF. Linux OpenSSH rejects a private
        # key without its final LF; restore text framing, never change key data.
        key.write_bytes((os.environ['OTA_UPLOAD_SSH_KEY'].replace('\r\n', '\n').rstrip('\n') + '\n').encode('ascii'))
        hosts.write_bytes((os.environ['OTA_UPLOAD_KNOWN_HOSTS'].replace('\r\n', '\n').rstrip('\n') + '\n').encode('ascii'))
        os.chmod(key, 0o600)
        reports = folder / 'reports.zip'
        source = Path('android-native/app/build/reports')
        with zipfile.ZipFile(reports, 'x', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(source.rglob('*')):
                if path.is_symlink():
                    raise ValueError('report_link_rejected')
                if path.is_file() and path.suffix in ('.xml', '.html', '.json', '.txt', '.css', '.js'):
                    archive.write(path, path.relative_to(source).as_posix())
        files = {'app.apk': apk.read_bytes(), 'output-metadata.json': metadata.read_bytes(),
                 'reports.zip': reports.read_bytes(), 'verification.json': json.dumps(report).encode()}
        header = {'schema': 1, 'runId': os.environ['GITHUB_RUN_ID'],
                  'attempt': os.environ['GITHUB_RUN_ATTEMPT'], 'commit': os.environ['GITHUB_SHA'],
                  'version': report['version'], 'files': [
                      {'name': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                      for name, data in files.items()]}
        body = json.dumps(header).encode() + b'\n' + b''.join(files.values())
        command = ['ssh', '-T', '-i', str(key), '-o', 'IdentitiesOnly=yes',
                   '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
                   '-o', 'UserKnownHostsFile=' + str(hosts), '-o', 'ConnectTimeout=15',
                   '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=3',
                   'cdr-ota-upload@106.53.126.69', 'receive-android-build']
        result = subprocess.run(command, input=body, capture_output=True, timeout=630)
        if result.returncode:
            raise ValueError('server_build_upload_failed')
        receipt = json.loads(result.stdout)
        if (receipt.get('stored') is not True or receipt.get('bytes') != sum(len(x) for x in files.values())
                or any(receipt.get(k) != header[k]
                for k in ('runId', 'attempt', 'commit', 'version'))):
            raise ValueError('server_receipt_mismatch')
        print(json.dumps(receipt))
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write('Android build saved to the private server staging area. '
                          'Not yet published to the phone OTA feed.\n\n' +
                          '```json\n' + json.dumps({**report, 'receipt': receipt}, indent=2) + '\n```\n')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('android_server_upload_failed') from None
