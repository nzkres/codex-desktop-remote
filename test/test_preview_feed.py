import base64
import json
from pathlib import Path
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from scripts.preview_feed import sign_fragment, verify_fragment
from scripts.preview_route import add_preview_route, PREVIEW_ROUTE

class PreviewFeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.keys = {'fixture':cls.key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()}
        cls.source = 'https://example.invalid/desktop-remote-previews/test/latest.json'
        cls.now = 1800000000

    def sign(self, revision=1, html=b'<div>demo</div>'):
        return sign_fragment(html, key=self.key, key_id='fixture', source=self.source, sequence=revision, title='Preview', now=self.now)

    def verify(self, raw, **kwargs):
        return verify_fragment(raw, keys=self.keys, source=self.source, now=self.now, **kwargs)

    def test_two_previews_update_without_app_version(self):
        first, binding = self.verify(self.sign())
        second, _ = self.verify(self.sign(2,b'<div>new design</div>'),sequence=first['sequence'],binding=binding)
        self.assertEqual(second['sequence'],2)
        self.assertNotIn('versionCode', second)

    def test_rollback_and_same_revision_conflict_rejected(self):
        _, binding = self.verify(self.sign(2))
        for raw in (self.sign(1),self.sign(2,b'<div>conflict</div>')):
            with self.assertRaises(ValueError): self.verify(raw,sequence=2,binding=binding)

    def test_mutated_signature_rejected(self):
        raw=json.loads(self.sign());raw['signature']=base64.b64encode(bytes(384)).decode()
        with self.assertRaises(Exception): self.verify(json.dumps(raw).encode())

    def test_cross_origin_source_rejected(self):
        with self.assertRaises(ValueError): verify_fragment(self.sign(),keys=self.keys,source=self.source.replace('example.invalid','other.invalid'),now=self.now)

    def test_expired_preview_rejected(self):
        with self.assertRaises(ValueError): verify_fragment(self.sign(),keys=self.keys,source=self.source,now=self.now+8*86400)

    def test_oversized_html_is_not_signed(self):
        with self.assertRaises(ValueError): self.sign(html=b'x'*(256*1024+1))

    def test_proxy_only_adds_one_authenticated_json_route(self):
        original='https://106.53.126.69:9443 {\n\t@relay path /control-api/* /connector-api/* /terminal-api/* /desktop-remote-updates/*\n}\n'
        candidate=add_preview_route(original)
        self.assertEqual(candidate.replace(PREVIEW_ROUTE,''),original)
        self.assertIn('forward_auth 127.0.0.1:28191',candidate)
        self.assertNotIn('browse',PREVIEW_ROUTE)
        self.assertIn('method GET',PREVIEW_ROUTE)
        self.assertIn('rewrite * /latest.json',PREVIEW_ROUTE)
        with self.assertRaises(ValueError): add_preview_route(candidate)

    def test_unexpected_proxy_baseline_requires_review(self):
        with self.assertRaises(ValueError): add_preview_route('https://other.invalid { }')

if __name__ == '__main__': unittest.main()
