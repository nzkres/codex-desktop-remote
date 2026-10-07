"""Verify the compiled APK actually includes its matching offline release history."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify_apk(apk, metadata, releases):
    apk = Path(apk)
    raw = Path(releases).read_bytes()
    current = json.loads(raw)['releases'][0]
    build = json.loads(Path(metadata).read_bytes())
    if build['applicationId'] != 'dev.codex.desktopremote.lite' or len(build['elements']) != 1:
        raise ValueError('wrong_apk_identity')
    element = build['elements'][0]
    if (element['versionName'], element['versionCode']) != (current['version'], current['versionCode']):
        raise ValueError('apk_version_mismatch')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError('apk_corrupt')
        if archive.namelist().count('assets/releases.json') != 1:
            raise ValueError('apk_release_notes_missing_or_duplicate')
        if archive.read('assets/releases.json') != raw:
            raise ValueError('apk_release_notes_mismatch')
        if 'AndroidManifest.xml' not in archive.namelist() or 'classes.dex' not in archive.namelist():
            raise ValueError('apk_incomplete')
    return {'version': current['version'], 'versionCode': current['versionCode'],
            'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'bundledNotesMatchSource': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    output = ROOT / 'android-native/app/build/outputs/apk/debug'
    parser.add_argument('--apk', type=Path, default=output / 'app-debug.apk')
    parser.add_argument('--metadata', type=Path, default=output / 'output-metadata.json')
    args = parser.parse_args()
    try:
        report = verify_apk(args.apk, args.metadata, ROOT / 'android-native/releases.json')
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile):
        print('APK release history verification failed; do not publish this artifact.', file=sys.stderr)
        return 1
    print(json.dumps(report))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
