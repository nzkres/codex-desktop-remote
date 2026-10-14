"""Synthetic error classification: no live network, identity or Desktop."""
import io
import json
import socket
import ssl
import sys
import unittest
import urllib.error
from contextlib import nullcontext
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows' / 'vendor'))
from connector_core import RelayClient, Connector, network_error_code

class Store:
    def __init__(self):
        self.state = dict(schema=1, deviceToken='a'*64, pairingId='b'*32, terminalId='c'*32,
                          relay='https://synthetic.example.test', name='fixture', state='connected')
    def read(self): return deepcopy(self.state)
    def write(self, value): self.state = deepcopy(value)
    def lock(self, *args): return nullcontext()

class RelayDiagnosticsTests(unittest.TestCase):
    def test_errors_are_typed_and_never_echo_exception_secret(self):
        expired = ssl.SSLCertVerificationError(1, 'private-token')
        expired.verify_code = 10
        for error, code in [(expired, 'relay_tls_certificate_expired'),
                            (ssl.SSLCertVerificationError(1, 'private-token'), 'relay_tls_untrusted'),
                            (ssl.SSLError('private-token'), 'relay_tls_handshake_failed'),
                            (TimeoutError('private-token'), 'relay_timeout'),
                            (socket.gaierror(-2, 'private-token'), 'relay_dns_failed'),
                            (ConnectionRefusedError('private-token'), 'relay_connection_refused'),
                            (OSError('private-token'), 'relay_network_unavailable')]:
            with self.subTest(code=code):
                client = RelayClient('https://synthetic.example.test')
                with patch.object(client.opener, 'open', side_effect=urllib.error.URLError(error)):
                    status, body = client.post('/connector-api/claim', 'synthetic-token', {})
                self.assertEqual(status, 0)
                self.assertEqual(client.failure_code(status), code)
                self.assertNotIn('private-token', json.dumps(body))

    def test_http_html_error_preserves_status_without_exposing_body(self):
        for status, code in [(401,'relay_http_401'), (403,'relay_http_403'),
                             (404,'relay_http_404'), (429,'relay_http_429'),
                             (503,'relay_http_5xx'), (302,'relay_http_unexpected')]:
            client = RelayClient('https://synthetic.example.test')
            error = urllib.error.HTTPError('https://synthetic.example.test',status,'private-token',{},io.BytesIO(b'<html>private-token</html>'))
            with patch.object(client.opener, 'open', side_effect=error):
                result, body = client.post('/fixture', 'fixture', {})
            self.assertEqual(result, status)
            self.assertEqual(client.failure_code(status),code)
            self.assertEqual(body,{})

    def test_status_retries_original_identity_then_clears_error(self):
        store = Store(); client = RelayClient(store.state['relay'])
        client.network_error = 'relay_tls_certificate_expired'
        with patch('connector_core.RelayClient', return_value=client), patch.object(client,'post',return_value=(0,{})):
            report = Connector(store).tick()
        self.assertEqual(report['lastError'], 'relay_tls_certificate_expired')
        self.assertEqual(report['state'],'reconnecting')
        with patch('connector_core.RelayClient', return_value=client), patch.object(client,'post',side_effect=[
                (200,dict(state='confirmed',terminalId='c'*32,terminalState='active')),(204,{})]) as post:
            report = Connector(store).tick()
        self.assertIsNone(report['lastError'])
        self.assertEqual(report['state'],'connected')
        self.assertTrue(all('/claim' not in call.args[0] for call in post.call_args_list))
        self.assertEqual(store.state['deviceToken'],'a'*64)

    def test_heartbeat_error_cannot_appear_connected_or_trust_server_error_code(self):
        store=Store();client=RelayClient(store.state['relay'])
        with patch('connector_core.RelayClient',return_value=client), patch.object(client,'post',side_effect=[
            (200,dict(state='confirmed',terminalId='c'*32,terminalState='active')),(503,{'error':'private-token'})]):
            report=Connector(store).tick()
        self.assertEqual(report['lastError'],'relay_http_5xx')
        self.assertEqual(report['state'],'reconnecting')

if __name__ == '__main__': unittest.main()
