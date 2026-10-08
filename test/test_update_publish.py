"""Publisher contracts use only synthetic source trees, APKs and RSA keys."""

import base64
import json
from pathlib import Path
import tempfile
import time
import unittest
import zipfile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from updates import publish
from updates.security import COMPATIBILITY, TARGETS, UpdateError, unpack, verify, verify_tree


SOURCE = "https://updates.example.test/desktop-remote-updates/test/windows/latest.json"


def _catalog(component):
    release = {"version": "0.1.0-alpha.3", "summary": "Synthetic release",
               "changes": [{"title": "Synthetic change", "experience": "Local fixture",
                            "verify": "Inspect fixture"}], "limitations": ["Not installed"]}
    if component == "android":
        release["versionCode"] = 3
    return {"schema": 1, "component": component, "releases": [release]}


class PublishTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = rsa.generate_private_key(public_exponent=65537, key_size=3072)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ota-publisher-test-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "repo"
        self.root.mkdir()
        self.key_path = self.base / "publisher.pem"
        self.key_path.write_bytes(self.key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption()))

    def put(self, name, data=b"synthetic source\n"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def make_source(self, component):
        for name in publish.UPDATE_REQUIRED:
            self.put(name)
        self.put(f"{component if component != 'android' else 'android-native'}/releases.json",
                 json.dumps(_catalog(component)).encode())
        if component == "windows":
            for name in publish.WINDOWS_REQUIRED:
                if name != "windows/releases.json":
                    self.put(name, b'{"type":"module"}' if name == "src/package.json" else b"source\n")
        elif component == "relay":
            for name in publish.RELAY_REQUIRED:
                if name != "relay/releases.json":
                    self.put(name)

    def source(self, component):
        return f"https://updates.example.test/desktop-remote-updates/test/{component}/latest.json"

    def artifact_url(self, component, extension="zip"):
        return f"https://updates.example.test/desktop-remote-updates/test/{component}/{component}-0.1.0-alpha.3.{extension}"

    def sign(self, component, artifact, output, **options):
        channel = options.get("channel", "test")
        return publish.sign(component, artifact, output, private_key=self.key_path,
                            key_id="synthetic-1", source=self.source(component).replace("/test/", f"/{channel}/"),
                            url=self.artifact_url(component, "apk" if component == "android" else "zip").replace("/test/", f"/{channel}/"),
                            sequence=3, root=self.root, **options)

    def verify_signed(self, envelope, component):
        public = self.key.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo).decode()
        trust = {"schema": 1, "product": publish.PRODUCT, "channel": "test",
                 "keys": {"synthetic-1": public}, "sources": {component: self.source(component)}}
        return verify(envelope, trust, component, TARGETS[component],
                      COMPATIBILITY[component], "0.1.0-alpha.2", now=int(time.time()))[0]

    def test_windows_pack_manifest_and_signed_wire_roundtrip(self):
        self.make_source("windows")
        artifact = self.base / "windows.zip"
        result = publish.pack("windows", artifact, root=self.root)
        with zipfile.ZipFile(artifact) as bundle:
            names = set(bundle.namelist())
            manifest = json.loads(bundle.read("package.json"))
            self.assertEqual(set(manifest["files"]), names - {"package.json"})
            self.assertEqual(manifest["dataSchema"], 1)
            self.assertIn("src/package.json", names)
            self.assertNotIn("updates/publish.py", names)
        with tempfile.TemporaryDirectory() as stage_dir:
            stage = unpack(artifact, Path(stage_dir) / "new", result)
            self.assertEqual(verify_tree(stage, "windows", "0.1.0-alpha.3"), manifest)
        metadata = self.base / "latest.json"
        envelope = self.sign("windows", artifact, metadata)
        payload = self.verify_signed(envelope, "windows")
        self.assertEqual(payload["sha256"], result["sha256"])
        self.assertEqual(payload["version"], "0.1.0-alpha.3")
        self.assertIn("Synthetic change", payload["notes"])
        self.assertEqual(json.loads(metadata.read_text()), envelope)
        self.assertNotIn("BEGIN PRIVATE KEY", metadata.read_text())
        self.assertEqual(base64.b64decode(envelope["payload"])[0:1], b"{")

    def test_relay_pack_is_source_only_and_signs(self):
        self.make_source("relay")
        artifact = self.base / "relay.zip"
        publish.pack("relay", artifact, root=self.root)
        with zipfile.ZipFile(artifact) as bundle:
            self.assertIn("updates/relay_host.py", bundle.namelist())
            self.assertNotIn("relay/test_http.py", bundle.namelist())
        payload = self.verify_signed(self.sign("relay", artifact, self.base / "relay-latest.json"), "relay")
        self.assertEqual(payload["platform"], "linux-x64")

    def test_existing_artifact_and_metadata_are_immutable(self):
        self.make_source("windows")
        artifact = self.base / "windows.zip"
        publish.pack("windows", artifact, root=self.root)
        original = artifact.read_bytes()
        with self.assertRaises(UpdateError):
            publish.pack("windows", artifact, root=self.root)
        self.assertEqual(artifact.read_bytes(), original)
        metadata = self.base / "latest.json"
        self.sign("windows", artifact, metadata)
        original = metadata.read_bytes()
        with self.assertRaises(UpdateError):
            self.sign("windows", artifact, metadata)
        self.assertEqual(metadata.read_bytes(), original)

    def test_rejects_bad_source_missing_file_and_tampered_archive_without_metadata(self):
        self.make_source("windows")
        missing = self.root / "src/main.js"
        missing.unlink()
        artifact = self.base / "windows.zip"
        with self.assertRaises(UpdateError):
            publish.pack("windows", artifact, root=self.root)
        self.assertFalse(artifact.exists())
        self.put("src/main.js")
        publish.pack("windows", artifact, root=self.root)
        with zipfile.ZipFile(artifact) as bundle:
            contents = {name: bundle.read(name) for name in bundle.namelist()}
        contents["src/main.js"] = b"tampered source"
        with zipfile.ZipFile(artifact, "w") as bundle:
            for name, data in contents.items():
                bundle.writestr(name, data)
        metadata = self.base / "latest.json"
        with self.assertRaises(UpdateError):
            self.sign("windows", artifact, metadata)
        self.assertFalse(metadata.exists())
        valid = self.base / "valid.zip"
        publish.pack("windows", valid, root=self.root)
        with self.assertRaises(UpdateError):
            publish.sign("windows", valid, metadata, private_key=self.key_path,
                         key_id="synthetic-1", source=SOURCE,
                         url="https://other.example.test/test/windows/x.zip", sequence=3, root=self.root)
        with self.assertRaises(UpdateError):
            publish.sign("windows", valid, metadata, private_key=self.key_path,
                         key_id="synthetic-1", source=SOURCE,
                         url="https://updates.example.test/test/windows/nested/x.zip",
                         sequence=3, root=self.root)
        self.assertFalse(metadata.exists())

    def test_android_existing_apk_needs_explicit_publisher_check(self):
        self.make_source("android")
        artifact = self.base / "existing.apk"
        with zipfile.ZipFile(artifact, "w") as apk:
            apk.writestr("AndroidManifest.xml", b"synthetic invalid manifest")
        metadata = self.base / "android-latest.json"
        fields = {"apk_version_code": 3, "apk_package_id": "dev.codex.desktopremote.lite", "min_sdk": 26}
        with self.assertRaises(UpdateError):
            self.sign("android", artifact, metadata, **fields)
        with self.assertRaises(UpdateError):
            self.sign("android", artifact, metadata, apk_verified=True,
                      apk_version_code=2, apk_package_id=fields["apk_package_id"], min_sdk=26)
        self.assertFalse(metadata.exists())
        envelope = self.sign("android", artifact, metadata, apk_verified=True, **fields)
        payload = self.verify_signed(envelope, "android")
        self.assertEqual(payload["versionCode"], 3)
        self.assertEqual(payload["packageId"], "dev.codex.desktopremote.lite")
        self.assertEqual(payload["minSdk"], 26)

    def test_key_must_be_external_and_strong_and_stable_rejects_prerelease(self):
        self.make_source("windows")
        artifact = self.base / "windows.zip"
        publish.pack("windows", artifact, root=self.root)
        metadata = self.base / "latest.json"
        self.put("inside.pem", self.key_path.read_bytes())
        with self.assertRaises(UpdateError):
            publish.sign("windows", artifact, metadata, private_key=self.root / "inside.pem",
                         key_id="synthetic-1", source=SOURCE, url=self.artifact_url("windows"),
                         sequence=3, root=self.root)
        with self.assertRaises(UpdateError):
            self.sign("windows", artifact, metadata, channel="stable")
        self.assertFalse(metadata.exists())

    def test_rejects_unservable_feed_paths_before_creating_metadata(self):
        self.make_source("windows")
        artifact = self.base / "windows.zip"
        publish.pack("windows", artifact, root=self.root)
        valid_source = self.source("windows")
        valid_url = self.artifact_url("windows")
        cases = (
            (valid_source.replace("desktop-remote-updates", "public-updates"),
             valid_url.replace("desktop-remote-updates", "public-updates")),
            (valid_source.replace("latest.json", "windows.json"), valid_url),
            (valid_source.replace("/test/", "/stable/"), valid_url.replace("/test/", "/stable/")),
            (valid_source.replace("/windows/", "/relay/"), valid_url.replace("/windows/", "/relay/")),
            (valid_source, valid_url.replace("windows-0.1", "relay-0.1")),
            (valid_source, valid_url.replace("alpha.3", "alpha.2")),
            (valid_source, valid_url.replace(".zip", ".apk")),
        )
        for source, url in cases:
            with self.subTest(source=source, url=url):
                metadata = self.base / "latest.json"
                with self.assertRaisesRegex(UpdateError, "private_feed_path_required"):
                    publish.sign("windows", artifact, metadata, private_key=self.key_path,
                                 key_id="synthetic-1", source=source, url=url,
                                 sequence=3, root=self.root)
                self.assertFalse(metadata.exists())

    def test_relay_custom_https_port_uses_exact_private_route(self):
        self.make_source("relay")
        artifact = self.base / "relay.zip"
        publish.pack("relay", artifact, root=self.root)
        source = self.source("relay").replace(".test/", ".test:9443/")
        url = self.artifact_url("relay").replace(".test/", ".test:9443/")
        envelope = publish.sign("relay", artifact, self.base / "latest.json",
                                private_key=self.key_path, key_id="synthetic-1",
                                source=source, url=url, sequence=3, root=self.root)
        self.assertEqual(json.loads(base64.b64decode(envelope["payload"]))["url"], url)


if __name__ == "__main__":
    unittest.main()
