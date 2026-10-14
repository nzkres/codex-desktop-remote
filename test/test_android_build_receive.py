"""Protocol and filesystem boundaries for the restricted Android build receiver."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from scripts import receive_android_build as receiver


class ReceiveAndroidBuildTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.sync = mock.patch.object(receiver, 'syncdir')
        self.sync.start()
        self.addCleanup(self.sync.stop)
        self.payloads = {
            'app.apk': b'apk fixture',
            'output-metadata.json': b'{}',
            'reports.zip': b'zip fixture',
            'verification.json': b'{"verified":true}',
        }

    def packet(self, *, header=None, payloads=None, tail=b''):
        payloads = self.payloads if payloads is None else payloads
        if header is None:
            header = {
                'schema': 1, 'runId': '123', 'attempt': '1',
                'commit': 'a' * 40, 'version': '0.1.0-alpha.1',
                'files': [
                    {'name': name, 'bytes': len(data),
                     'sha256': hashlib.sha256(data).hexdigest()}
                    for name, data in payloads.items()
                ],
            }
        return (json.dumps(header, separators=(',', ':')).encode() + b'\n'
                + b''.join(payloads.values()) + tail)

    def receive(self, packet):
        return receiver.receive(io.BytesIO(packet), self.root)

    def assert_no_partial(self):
        self.assertFalse(list(self.root.glob('.partial-*')))

    def test_success_is_immutable_and_writes_exact_files(self):
        packet = self.packet()
        receipt = self.receive(packet)
        target = self.root / '123-1'
        self.assertEqual(receipt['bytes'], sum(map(len, self.payloads.values())))
        self.assertEqual(set(p.name for p in target.iterdir()),
                         {*self.payloads, 'receipt.json'})
        for name, data in self.payloads.items():
            self.assertEqual((target / name).read_bytes(), data)
        self.assertEqual(json.loads((target / 'receipt.json').read_bytes())['runId'], '123')
        with self.assertRaises(ValueError):
            self.receive(packet)
        self.assertEqual((target / 'app.apk').read_bytes(), self.payloads['app.apk'])
        self.assert_no_partial()

    def test_hash_mismatch_and_truncated_body_leave_no_build(self):
        packet = self.packet()
        for damaged in (packet.replace(b'apk fixture', b'bad fixture'), packet[:-1]):
            with self.subTest(damaged=damaged[-20:]):
                with self.assertRaises(ValueError):
                    self.receive(damaged)
                self.assertFalse((self.root / '123-1').exists())
                self.assert_no_partial()

    def test_trailing_data_is_rejected(self):
        with self.assertRaises(ValueError):
            self.receive(self.packet(tail=b'X'))
        self.assertFalse((self.root / '123-1').exists())
        self.assert_no_partial()

    def test_duplicate_json_keys_are_rejected(self):
        packet = self.packet()
        duplicate = packet.replace(b'"schema":1,', b'"schema":1,"schema":1,', 1)
        with self.assertRaises(ValueError):
            self.receive(duplicate)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_only_the_four_fixed_names_are_accepted(self):
        for name in ('../app.apk', '/tmp/app.apk', 'unexpected.apk'):
            with self.subTest(name=name):
                packet = self.packet()
                header_line, body = packet.split(b'\n', 1)
                header = json.loads(header_line)
                header['files'][0]['name'] = name
                with self.assertRaises(ValueError):
                    self.receive(json.dumps(header).encode() + b'\n' + body)
                self.assertEqual(list(self.root.iterdir()), [])

    def test_oversize_file_and_total_quota_are_rejected(self):
        packet = self.packet()
        header_line, body = packet.split(b'\n', 1)
        header = json.loads(header_line)
        header['files'][0]['bytes'] = receiver.FILES['app.apk'] + 1
        with self.assertRaises(ValueError):
            self.receive(json.dumps(header).encode() + b'\n' + body)
        with mock.patch.object(receiver, 'QUOTA', 1):
            with self.assertRaises(ValueError):
                self.receive(packet)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_insufficient_disk_space_is_rejected_before_writing(self):
        with mock.patch.object(receiver.shutil, 'disk_usage', return_value=mock.Mock(free=0)):
            with self.assertRaises(ValueError):
                self.receive(self.packet())
        self.assertEqual(list(self.root.iterdir()), [])

    def test_existing_build_remains_untouched_after_failed_attempt(self):
        target = self.root / '123-1'
        target.mkdir()
        sentinel = target / 'original'
        sentinel.write_bytes(b'keep')
        with self.assertRaises(ValueError):
            self.receive(self.packet())
        self.assertEqual(sentinel.read_bytes(), b'keep')
        self.assert_no_partial()

    def test_symlink_in_incoming_tree_is_rejected_without_following_it(self):
        outside = self.root.parent / (self.root.name + '-outside')
        outside.mkdir()
        self.addCleanup(outside.rmdir)
        sentinel = outside / 'original'
        sentinel.write_bytes(b'keep')
        self.addCleanup(sentinel.unlink)
        link = self.root / 'linked'
        try:
            link.symlink_to(outside, target_is_directory=True)
        except (OSError, NotImplementedError) as exc:
            self.skipTest(f'symlink unavailable: {exc}')
        with self.assertRaises(ValueError):
            self.receive(self.packet())
        self.assertEqual(sentinel.read_bytes(), b'keep')
        self.assertFalse((self.root / '123-1').exists())
        self.assert_no_partial()

    def test_reported_symlink_is_rejected_before_any_build_write(self):
        sentinel = self.root / 'original'
        sentinel.write_bytes(b'keep')
        original_is_symlink = Path.is_symlink

        def reported_link(path):
            return path == sentinel or original_is_symlink(path)

        with mock.patch.object(Path, 'is_symlink', reported_link):
            with self.assertRaises(ValueError):
                self.receive(self.packet())
        self.assertEqual(sentinel.read_bytes(), b'keep')
        self.assertFalse((self.root / '123-1').exists())
        self.assert_no_partial()


if __name__ == '__main__':
    unittest.main()
