"""Synthetic ZIP fixtures catch missing/mismatched packaged release notes."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile
from scripts.check_android_apk import verify_apk


class ApkReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / 'fixture.apk'
        self.metadata = self.root / 'output-metadata.json'
        self.releases = self.root / 'releases.json'
        self.release = {'version': '0.1.0-alpha.2', 'versionCode': 2}
        self.raw = json.dumps({'releases': [self.release]}).encode()
        self.releases.write_bytes(self.raw)
        self.build = {'applicationId': 'dev.codex.desktopremote.lite', 'elements': [dict(self.release, versionName=self.release['version'])]}
        self.metadata.write_text(json.dumps(self.build), encoding='utf-8')

    def zip(self, notes):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'synthetic manifest')
            archive.writestr('classes.dex', b'synthetic dex')
            if notes is not None:
                archive.writestr('assets/releases.json', notes)

    def test_packaged_notes_and_build_version_must_match(self):
        self.zip(self.raw)
        result = verify_apk(self.apk, self.metadata, self.releases)
        self.assertEqual(result['version'], self.release['version'])
        self.assertTrue(result['bundledNotesMatchSource'])

    def test_missing_asset_is_rejected_even_with_correct_build_metadata(self):
        self.zip(None)
        with self.assertRaisesRegex(ValueError, 'missing'):
            verify_apk(self.apk, self.metadata, self.releases)

    def test_stale_asset_is_rejected(self):
        self.zip(self.raw.replace(b'alpha.2', b'alpha.1'))
        with self.assertRaisesRegex(ValueError, 'notes_mismatch'):
            verify_apk(self.apk, self.metadata, self.releases)

    def test_different_apk_version_is_rejected(self):
        self.zip(self.raw)
        self.build['elements'][0]['versionCode'] = 1
        self.metadata.write_text(json.dumps(self.build), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'version_mismatch'):
            verify_apk(self.apk, self.metadata, self.releases)
