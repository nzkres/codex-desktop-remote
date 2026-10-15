"""Credential-free checks for the Windows error handbook."""

import ast
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'windows'))
from error_catalog import CATALOG, _COMMON, _SOURCE_FILES, _SPECIFIC_REASONS, describe, report_codes, safe_code


def source_literals():
    codes = set()
    for relative in _SOURCE_FILES:
        tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Name):
                continue
            if node.func.id not in ('ConnectorError', 'UpdateError', '_fail') or not node.args:
                continue
            argument = node.args[0]
            values = (argument.body, argument.orelse) if isinstance(argument, ast.IfExp) else (argument,)
            codes.update(value.value for value in values
                         if isinstance(value, ast.Constant) and isinstance(value.value, str))
    return codes


class ErrorCatalogTests(unittest.TestCase):
    def test_all_declared_literal_codes_are_documented(self):
        self.assertFalse(source_literals() - CATALOG.keys())
        self.assertFalse(CATALOG.keys() - _COMMON.keys() - _SPECIFIC_REASONS.keys())
        self.assertIn('credential_decryption_failed', CATALOG)
        self.assertIn('credential_encryption_failed', CATALOG)

    def test_every_entry_has_safe_chinese_guidance_and_real_source(self):
        for code, entry in CATALOG.items():
            with self.subTest(code=code):
                self.assertEqual(safe_code(code), code)
                self.assertTrue(entry['title'])
                self.assertTrue(entry['category'])
                self.assertTrue(entry['reason'])
                self.assertTrue(entry['action'])
                self.assertIn('错误码：' + code, describe(code))
                source = entry['source']
                if source:
                    self.assertIn(source['path'], (*_SOURCE_FILES, 'windows/error_catalog.py', 'src/desktop-open.js', 'src/service.js', 'src/questions.js'))
                    lines = (ROOT / source['path']).read_text(encoding='utf-8').splitlines()
                    self.assertIsInstance(source['line'], int)
                    self.assertGreaterEqual(source['line'], 1)
                    self.assertLessEqual(source['line'], len(lines))
                    self.assertTrue(source['symbol'])
                    self.assertIn(source['kind'], ('raise', 'classification'))

    def test_common_and_network_codes_have_specific_guidance(self):
        for code in ('invalid_pairing_code', 'connection_unavailable',
                     'previous_runtime_requires_review', 'identity_changed',
                     'candidate_health_unverified', 'update_needs_review', 'update_rolled_back',
                     'relay_tls_certificate_expired', 'relay_tls_handshake_failed', 'relay_http_429'):
            with self.subTest(code=code):
                entry = CATALOG[code]
                self.assertNotIn('现有信息不足', entry['reason'])
                self.assertNotIn('此代码表示对应模块', entry['reason'])
        self.assertIn('重载', CATALOG['relay_tls_certificate_expired']['action'])
        self.assertIn('不要关闭证书校验', CATALOG['relay_tls_certificate_expired']['action'])
        self.assertEqual(CATALOG['relay_http_401']['source']['symbol'], 'RelayClient.failure_code')
        for code in ('relay_tls_certificate_expired', 'relay_tls_untrusted',
                     'relay_tls_handshake_failed'):
            self.assertEqual(CATALOG[code]['source']['path'], 'windows/vendor/connector_core.py')
            self.assertEqual(CATALOG[code]['source']['symbol'], 'network_error_code')
        self.assertEqual(CATALOG['gateway_failed']['source']['symbol'], 'report_codes')
        self.assertIn('分类/展示入口', describe('gateway_failed'))

    def test_unknown_data_cannot_be_echoed(self):
        private = 'Bearer secret-token C:\\Users\\private\\identity.dpapi'
        for value in (private, None, 17, {'code': 'invalid_pairing_code'},
                      'unknown_unreviewed_code'):
            with self.subTest(value=type(value).__name__):
                self.assertEqual(safe_code(value), 'operation_failed')
                self.assertNotIn(private, describe(value))
        self.assertNotIn(private, describe('operation_failed'))

    def test_observed_codes_do_not_guess_from_unverified_status(self):
        self.assertEqual(report_codes({'state': 'connected', 'dataChannel': 'unverified',
                                       'gateway': 'starting', 'transport': 'unhealthy'}), [])
        self.assertEqual(report_codes({'state': 'reconnecting', 'lastError': 'relay_timeout',
                                       'gateway': 'failed', 'transport': 'invalid_binary'}, live=True),
                         ['relay_timeout', 'gateway_failed', 'transport_failed'])
        self.assertEqual(report_codes({'gateway': 'failed', 'transport': 'invalid_binary'}), [])
        self.assertEqual(report_codes({'runtimeNeedsReview': True}, live=False),
                         ['previous_runtime_requires_review'])
        self.assertEqual(report_codes({'runtimeNeedsReview': True}, live=True), [])
        self.assertEqual(report_codes({'lastError': 'secret\nbody'}), ['operation_failed'])
        self.assertEqual(report_codes(None), [])


if __name__ == '__main__':
    unittest.main()
