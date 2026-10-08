"""Private OTA requests with synthetic tokens and a local trusted HTTPS fixture."""

from datetime import datetime, timedelta, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
from pathlib import Path
import ssl
import tempfile
import threading
import unittest
from unittest.mock import patch
import urllib.request

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from updates.authorization import UpdateAuthorization
from updates.security import UpdateError, download, fetch


TOKEN = "a" * 64
DEVICE = "b" * 32
BODY = b"synthetic signed-package bytes"


class LocalHTTPS:
    def __init__(self, certificate, key):
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                fixture.requests.append((self.path, dict(self.headers.items())))
                if fixture.redirect:
                    self.send_response(302)
                    self.send_header("Location", fixture.redirect)
                    self.end_headers()
                    return
                status = fixture.status
                if status == 200 and self.headers.get("Authorization") != "Bearer " + TOKEN:
                    status = 401
                if status != 200:
                    self.send_response(status)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                data = b'{"schema":1}' if self.path.endswith("latest.json") else BODY
                self.send_response(200)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *_):
                pass

        self.requests = []
        self.status = 200
        self.redirect = None
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(str(certificate), str(key))
        self.server.socket = tls.wrap_socket(self.server.socket, server_side=True)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = f"https://127.0.0.1:{self.server.server_port}"

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)


class AuthorizationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ota-auth-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "127.0.0.1")])
        certificate = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
                       .public_key(key.public_key()).serial_number(x509.random_serial_number())
                       .not_valid_before(datetime.now(timezone.utc) - timedelta(days=1))
                       .not_valid_after(datetime.now(timezone.utc) + timedelta(days=1))
                       .add_extension(x509.SubjectAlternativeName(
                           [x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]), critical=False)
                       .sign(key, hashes.SHA256()))
        cert_path, key_path = self.root / "ca.pem", self.root / "tls-key.pem"
        cert_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                             serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        self.context = ssl.create_default_context(cafile=str(cert_path))
        self.primary = LocalHTTPS(cert_path, key_path)
        self.addCleanup(self.primary.close)
        self.source = self.primary.origin + "/desktop-remote-updates/test/windows/latest.json"
        self.artifact = self.primary.origin + "/desktop-remote-updates/test/windows/windows-0.1.0-alpha.3.zip"
        self.auth = UpdateAuthorization(self.primary.origin + "/", self.source, TOKEN)

    def payload(self, url=None):
        return {"url": self.artifact if url is None else url,
                "bytes": len(BODY), "sha256": hashlib.sha256(BODY).hexdigest()}

    def code(self, expected, action):
        with self.assertRaises(UpdateError) as raised:
            action()
        self.assertEqual(raised.exception.code, expected)

    def test_feed_and_package_send_headers_over_verified_https(self):
        self.assertEqual(fetch(self.source, context=self.context, authorization=self.auth), {"schema": 1})
        destination = self.root / "download.zip"
        self.assertEqual(download(self.payload(), destination, context=self.context,
                                  authorization=self.auth), destination.absolute())
        self.assertEqual(destination.read_bytes(), BODY)
        self.assertEqual([path for path, _ in self.primary.requests],
                         ["/desktop-remote-updates/test/windows/latest.json",
                          "/desktop-remote-updates/test/windows/windows-0.1.0-alpha.3.zip"])
        for _, headers in self.primary.requests:
            self.assertEqual(headers["Authorization"], "Bearer " + TOKEN)
            self.assertEqual(headers["X-Desktop-Remote-Principal"], "account")
            self.assertNotIn("X-Desktop-Remote-Device", headers)

    def test_unauthenticated_denied_and_http_errors_are_fixed(self):
        self.code("update_authentication_required", lambda: fetch(self.source, context=self.context))
        self.code("update_authentication_required", lambda: download(
            self.payload(), self.root / "no-auth.zip", context=self.context))
        self.assertFalse((self.root / "no-auth.zip").exists())
        self.primary.status = 403
        self.code("update_access_denied", lambda: fetch(
            self.source, context=self.context, authorization=self.auth))
        self.code("update_access_denied", lambda: download(
            self.payload(), self.root / "denied.zip", context=self.context, authorization=self.auth))
        self.assertFalse((self.root / "denied.zip").exists())

    def test_installed_global_opener_cannot_add_authorization(self):
        class ForbiddenGlobalOpener:
            addheaders = [("Authorization", "Bearer " + TOKEN)]

            def open(self, *_args, **_kwargs):
                raise AssertionError("global opener must not be used")

        with patch.object(urllib.request, "_opener", ForbiddenGlobalOpener()):
            self.code("update_authentication_required", lambda: fetch(self.source, context=self.context))
        self.assertNotIn("Authorization", self.primary.requests[-1][1])

    def test_wrong_origin_and_redirect_never_forward_token(self):
        second = LocalHTTPS(self.root / "ca.pem", self.root / "tls-key.pem")
        self.addCleanup(second.close)
        wrong = second.origin + "/desktop-remote-updates/test/windows/windows-0.1.0-alpha.3.zip"
        self.code("untrusted_update_url", lambda: download(
            self.payload(wrong), self.root / "cross.zip", context=self.context, authorization=self.auth))
        self.assertEqual(second.requests, [])
        self.primary.redirect = wrong
        self.code("update_redirect_rejected", lambda: fetch(
            self.source, context=self.context, authorization=self.auth))
        self.assertEqual(second.requests, [])
        self.assertFalse((self.root / "cross.zip").exists())

    def test_bad_binding_device_scope_and_redacted_repr(self):
        self.assertNotIn(TOKEN, repr(self.auth))
        self.assertNotIn(TOKEN, str(self.auth))
        for relay in ("http://127.0.0.1", self.primary.origin + "/api", self.primary.origin + "/?x=1",
                      "https://user@127.0.0.1/", "https://127.0.0.1:0/"):
            with self.subTest(relay=relay):
                self.code("untrusted_update_url", lambda: UpdateAuthorization(relay, self.source, TOKEN))
        self.code("untrusted_update_url", lambda: UpdateAuthorization(
            self.primary.origin, self.source.replace("/windows/", "/relay/"), TOKEN).headers_for(self.artifact))
        self.code("update_authentication_required", lambda: UpdateAuthorization(
            self.primary.origin, self.source, "not-a-token"))
        self.code("update_authentication_required", lambda: UpdateAuthorization(
            self.primary.origin, self.source, TOKEN, principal="device"))
        device = UpdateAuthorization(self.primary.origin, self.source, TOKEN,
                                     principal="device", device_id=DEVICE)
        self.assertEqual(device.headers_for(self.artifact)["X-Desktop-Remote-Device"], DEVICE)
        self.code("untrusted_update_url", lambda: device.headers_for(
            self.primary.origin + "/desktop-remote-updates/test/windows/nested/windows-0.1.0.zip"))
        self.code("untrusted_update_url", lambda: device.headers_for(
            self.primary.origin + "/desktop-remote-updates/test/windows/relay-0.1.0.zip"))

    def test_tls_certificate_is_still_required(self):
        self.code("update_fetch_failed", lambda: fetch(
            self.source, context=ssl.create_default_context(), authorization=self.auth))


if __name__ == "__main__":
    unittest.main()
