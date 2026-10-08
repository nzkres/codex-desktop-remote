"""Real TLS sockets and bounded downloads, using a temporary synthetic CA only."""
from datetime import datetime, timedelta, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import ssl
import tempfile
import threading
import unittest
from unittest.mock import patch

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from updates.security import fetch, download, UpdateError


class HttpsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        key = rsa.generate_private_key(public_exponent=65537,key_size=3072)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'localhost')])
        now=datetime.now(timezone.utc)
        certificate=(x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=1))
            .not_valid_after(now+timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost')]),critical=False)
            .add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(key,hashes.SHA256()))
        (cls.root/'cert.pem').write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
        (cls.root/'key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        cls.body=b'verified synthetic package bytes'
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_GET(self):
                if self.path=='/redirect':
                    self.send_response(302);self.send_header('Location','/package');self.end_headers();return
                if self.path=='/manifest':
                    body=b'{"schema":1}'
                elif self.path=='/large':
                    body=b'X'*70000
                else: body=cls.body
                self.send_response(200)
                self.send_header('Content-Length',str(len(body)+(3 if self.path=='/size' else 0)))
                self.end_headers()
                self.wfile.write(body)
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        server_context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(cls.root/'cert.pem',cls.root/'key.pem')
        cls.server.socket=server_context.wrap_socket(cls.server.socket,server_side=True)
        cls.worker=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.worker.start()
        cls.url=f'https://localhost:{cls.server.server_port}'
        cls.context=ssl.create_default_context(cafile=str(cls.root/'cert.pem'))

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.worker.join();cls.temp.cleanup()

    def payload(self,path='/package'):
        return {'url':self.url+path,'bytes':len(self.body),'sha256':hashlib.sha256(self.body).hexdigest()}

    def test_trusted_https_manifest_and_package(self):
        self.assertEqual(fetch(self.url+'/manifest',context=self.context),{'schema':1})
        with tempfile.TemporaryDirectory() as directory:
            result=download(self.payload(),Path(directory)/'package',context=self.context)
            self.assertEqual(result.read_bytes(),self.body)

    def test_tls_unknown_ca_and_redirect_are_rejected(self):
        for path,context in [('/manifest',None),('/redirect',self.context),('/large',self.context)]:
            with self.assertRaises(UpdateError): fetch(self.url+path,context=context)

    def test_size_hash_disk_and_disabled_tls_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            for i,payload in enumerate([self.payload('/size'),{**self.payload(),'sha256':'0'*64}]):
                with self.assertRaises(UpdateError):
                    download(payload,Path(directory)/str(i),context=self.context)
            with patch('updates.security.shutil.disk_usage') as usage:
                usage.return_value.free=0
                with self.assertRaisesRegex(UpdateError,'insufficient_disk_space'):
                    download(self.payload(),Path(directory)/'disk',context=self.context)
            unsafe=ssl._create_unverified_context()
            with self.assertRaisesRegex(UpdateError,'invalid_tls_context'):
                download(self.payload(),Path(directory)/'tls',context=unsafe)

