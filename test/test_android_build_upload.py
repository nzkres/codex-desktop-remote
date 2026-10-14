"""Isolated dispatch and SSH receipt tests for the Android build uploader."""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from scripts import upload_android_build as uploader


class UploadAndroidBuildTests(unittest.TestCase):
    def setUp(self):
        self.old_umask = os.umask(0o077)
        os.umask(self.old_umask)
        self.addCleanup(os.umask, self.old_umask)
        self.temporary = tempfile.TemporaryDirectory(prefix='test-android-upload-', dir=Path.cwd())
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.old_cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.old_cwd)
        self.runner_temp = self.root / 'runner-temp'
        self.runner_temp.mkdir()
        output = self.root / 'android-native/app/build/outputs/apk/debug'
        output.mkdir(parents=True)
        (output / 'app-debug.apk').write_bytes(b'synthetic signed APK')
        (output / 'output-metadata.json').write_bytes(b'{"fixture":true}')
        (self.root / 'android-native/app/build/reports').mkdir(parents=True)
        self.summary = self.root / 'summary.md'
        self.summary.write_text('', encoding='utf-8')
        self.env = mock.patch.dict(os.environ, {
            'GITHUB_REPOSITORY': 'nzkres/codex-desktop-remote',
            'GITHUB_REF': 'refs/heads/main',
            'GITHUB_EVENT_NAME': 'workflow_dispatch',
            'GITHUB_SHA': 'a' * 40,
            'GITHUB_RUN_ID': '123',
            'GITHUB_RUN_ATTEMPT': '1',
            'GITHUB_STEP_SUMMARY': str(self.summary),
            'RUNNER_TEMP': str(self.runner_temp),
            'EXPECTED_CERT_SHA256': 'b' * 64,
            'OTA_UPLOAD_SSH_KEY': 'synthetic-private-key',
            'OTA_UPLOAD_KNOWN_HOSTS': 'example.test ssh-ed25519 synthetic-host-key',
        })
        self.env.start()
        self.addCleanup(self.env.stop)
        self.verify = mock.patch.object(uploader, 'verify_apk', return_value={
            'version': '0.1.0-alpha.1', 'versionCode': 1,
            'bytes': len(b'synthetic signed APK'), 'sha256': 'c' * 64,
            'bundledNotesMatchSource': True,
        })
        self.verify_mock = self.verify.start()
        self.addCleanup(self.verify.stop)

    def assert_clean_failure(self):
        self.assertEqual(self.summary.read_text(encoding='utf-8'), '')
        self.assertEqual(list(self.runner_temp.iterdir()), [])

    def test_non_main_is_rejected_before_verification_or_ssh(self):
        with mock.patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/feature'}):
            with mock.patch.object(uploader.subprocess, 'run') as ssh:
                with self.assertRaisesRegex(ValueError, 'controlled_main_build_required'):
                    uploader.main()
        self.assert_clean_failure()
        self.assertFalse(ssh.called)
        self.verify_mock.assert_not_called()

    def test_ssh_failure_has_no_success_summary_and_cleans_private_files(self):
        def reject(command, **kwargs):
            self.assertEqual(command[0], 'ssh')
            self.assertNotIn('synthetic-private-key', ' '.join(command))
            key = Path(command[command.index('-i') + 1])
            self.assertEqual(key.read_bytes(), b'synthetic-private-key\n')
            return subprocess.CompletedProcess(command, 1, stdout=b'', stderr=b'synthetic failure')

        with mock.patch.object(uploader.subprocess, 'run', side_effect=reject):
            with self.assertRaisesRegex(ValueError, 'server_build_upload_failed'):
                uploader.main()
        self.assert_clean_failure()

    def test_secret_newlines_are_restored_before_ssh(self):
        # gh secret set strips final newlines; CRLF input also occurs on Windows.
        for value in ('line-one\nline-two', 'line-one\r\nline-two\r\n'):
            with self.subTest(value=value):
                def inspect(command, **kwargs):
                    key = Path(command[command.index('-i') + 1])
                    hosts = Path(next(x.split('=', 1)[1] for x in command if x.startswith('UserKnownHostsFile=')))
                    self.assertEqual(key.read_bytes(), b'line-one\nline-two\n')
                    self.assertTrue(hosts.read_bytes().endswith(b'\n'))
                    return subprocess.CompletedProcess(command, 1, stdout=b'', stderr=b'fixture stop')

                with mock.patch.dict(os.environ, {'OTA_UPLOAD_SSH_KEY': value}):
                    with mock.patch.object(uploader.subprocess, 'run', side_effect=inspect):
                        with self.assertRaisesRegex(ValueError, 'server_build_upload_failed'):
                            uploader.main()
                self.assert_clean_failure()

    def test_wrong_receipt_byte_count_has_no_success_summary(self):
        def wrong_receipt(command, **kwargs):
            header = json.loads(kwargs['input'].split(b'\n', 1)[0])
            receipt = {k: header[k] for k in ('runId', 'attempt', 'commit', 'version')}
            receipt.update(stored=True, bytes=0)
            return subprocess.CompletedProcess(command, 0, stdout=json.dumps(receipt).encode(), stderr=b'')

        with mock.patch.object(uploader.subprocess, 'run', side_effect=wrong_receipt):
            with self.assertRaisesRegex(ValueError, 'server_receipt_mismatch'):
                uploader.main()
        self.assert_clean_failure()

    def test_matching_receipt_writes_success_summary_after_ssh(self):
        def matching_receipt(command, **kwargs):
            self.assertTrue(kwargs['capture_output'])
            self.assertEqual(kwargs['timeout'], 210)
            header, body = kwargs['input'].split(b'\n', 1)
            header = json.loads(header)
            self.assertEqual([row['name'] for row in header['files']],
                             ['app.apk', 'output-metadata.json', 'reports.zip', 'verification.json'])
            receipt = {k: header[k] for k in ('runId', 'attempt', 'commit', 'version')}
            receipt.update(stored=True, bytes=len(body))
            return subprocess.CompletedProcess(command, 0, stdout=json.dumps(receipt).encode(), stderr=b'')

        with mock.patch.object(uploader.subprocess, 'run', side_effect=matching_receipt):
            with mock.patch('sys.stdout', new_callable=io.StringIO):
                uploader.main()
        self.assertIn('Android build saved to the private server staging area',
                      self.summary.read_text(encoding='utf-8'))
        self.assertEqual(list(self.runner_temp.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
