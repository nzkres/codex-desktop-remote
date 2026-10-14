"""Offline source-package publisher. Never deploys, builds APKs or ships private keys.

Windows/relay ZIPs contain only allowlisted repository source. Their consumers
still need separately installed Python; Windows also needs Node, FRP and the
pinned native Desktop. Android sign takes an already built, independently
certificate-checked APK. The publisher must confirm its package/versionCode/
minSdk and signing certificate out of band before using --apk-verified.
"""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from urllib.parse import urlsplit
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from updates.security import (COMPATIBILITY, LIMIT, TARGETS, UpdateError, _safe_local, _url,
                              unpack, verify, verify_tree)
from updates.authorization import SOURCE_PATH


ROOT = Path(__file__).resolve().parents[1]
PRODUCT = "codex-desktop-remote"
KEY_ID = re.compile(r"[A-Za-z0-9._-]{1,64}\Z")
WINDOWS_REQUIRED = (
    "windows/controller.py", "windows/gui.py", "windows/error_catalog.py", "windows/demo.py", "windows/release_notes.py",
    "windows/releases.json", "windows/ota_entry.py", "windows/update_driver.py",
    "windows/update_helper.py", "windows/update_ui.py",
    "scripts/configure.py", "scripts/probe.mjs", "scripts/update_probe.mjs", "scripts/diagnose_desktop.mjs",
    "src/package.json", "src/compatibility.js", "src/follower.js", "src/main.js",
    "src/activity.js", "src/catalog.js", "src/desktop-open.js", "scripts/desktop_catalog.py", "src/receipts.js", "src/service.js", "src/tasks.js", "src/version.js",
    "src/vendor/conversation-timing.js", "src/vendor/desktop-ipc.js",
    "src/vendor/desktop-state.js", "src/vendor/mobile-sync.js",
    "windows/vendor/connection_settings.py", "windows/vendor/connector_core.py",
    "windows/vendor/frp_config.py", "windows/vendor/runtime_core.py",
    "windows/vendor/transport_worker.py", "windows/vendor/windows_job.py",
)
RELAY_REQUIRED = (
    "relay/releases.json", "relay/auth/auth_server.py", "relay/configure_managed.py",
    "relay/control_server.py",
    "relay/forwarder.py", "relay/frp_policy.py", "relay/gateway_link.py",
    "relay/maintenance.py", "relay/managed.py", "relay/registry.py", "relay/private_updates.py",
    "relay/release_notes.py", "relay/transport_settings.py", "relay/update_driver.py",
    "updates/relay_host.py",
)
UPDATE_REQUIRED = (
    "updates/__init__.py", "updates/security.py", "updates/storage.py", "updates/engine.py",
    "updates/authorization.py", "updates/credentials.py",
    "updates/processes.py", "updates/child_gate.py", "updates/launch.py",
    "updates/bootstrap.py", "updates/cli.py", "updates/requirements.txt",
    "third-party/licenses/AGPL-3.0.txt", "third-party/licenses/HAPI-NOTICE.md",
)


def _fail(code):
    raise UpdateError(code)


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            _fail("invalid_release_catalog")
        result[key] = value
    return result


def _catalog(root, component):
    try:
        data = (Path(root) / ("android-native" if component == "android" else component) / "releases.json").read_bytes()
        catalog = json.loads(data, object_pairs_hook=_unique)
        if (not isinstance(catalog, dict) or catalog.get("schema") != 1 or catalog.get("component") != component
                or not isinstance(catalog.get("releases"), list) or not catalog["releases"]):
            _fail("invalid_release_catalog")
        release = catalog["releases"][0]
        if (not isinstance(release, dict) or not isinstance(release.get("version"), str)
                or not isinstance(release.get("summary"), str) or not isinstance(release.get("changes"), list)
                or not isinstance(release.get("limitations"), list)):
            _fail("invalid_release_catalog")
        return release
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_release_catalog")


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _file_sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        while chunk := stream.read(128 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _files(root, component):
    root = Path(root)
    if component not in ("windows", "relay"):
        _fail("unsupported_package_component")
    names = set(UPDATE_REQUIRED)
    if component == "windows":
        names.update(WINDOWS_REQUIRED)
    else:
        names.update(RELAY_REQUIRED)
    entries = {}
    for name in sorted(names):
        path = root / name
        if (not path.is_file() or path.is_symlink() or any(parent.is_symlink() or
                (hasattr(parent, "is_junction") and parent.is_junction()) for parent in path.parents)):
            _fail("package_source_unavailable")
        entries[name] = path.read_bytes()
    return entries


def pack(component, output, *, root=ROOT):
    """Build a new immutable source ZIP with a complete root package.json."""
    root, output = Path(root).resolve(), _safe_local(output)
    release = _catalog(root, component)
    entries = _files(root, component)
    if not output.parent.is_dir() or output.exists() or output.is_symlink():
        _fail("new_artifact_path_required")
    manifest = {"schema": 1, "product": PRODUCT, "component": component,
                "platform": TARGETS[component], "version": release["version"],
                "files": {name: _sha(data) for name, data in entries.items()}, "dataSchema": 1}
    created = False
    try:
        with output.open("xb") as stream:
            created = True
            with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9,
                                 allowZip64=False) as bundle:
                bundle.writestr("package.json", json.dumps(manifest, sort_keys=True, separators=(",", ":")))
                for name, data in entries.items():
                    bundle.writestr(name, data)
            stream.flush()
            os.fsync(stream.fileno())
        if output.stat().st_size > LIMIT:
            _fail("archive_limits_exceeded")
        payload = {"component": component, "platform": TARGETS[component], "version": release["version"],
                   "bytes": output.stat().st_size, "sha256": _file_sha(output)}
        with tempfile.TemporaryDirectory(prefix="ota-publish-") as directory:
            stage = unpack(output, Path(directory) / "package", payload)
            verify_tree(stage, component, release["version"])
        return payload
    except BaseException:
        if created and output.exists():
            output.unlink()
        raise


def _notes(release):
    lines = [release["summary"]]
    try:
        for change in release["changes"]:
            lines.extend((change["title"], "体验：" + change["experience"], "验证：" + change["verify"]))
        for limitation in release["limitations"]:
            lines.append("限制：" + limitation)
    except (KeyError, TypeError, AttributeError):
        _fail("invalid_release_catalog")
    notes = "\n".join(lines)
    if len(notes) > 8192:
        _fail("invalid_release_catalog")
    return notes


def sign(component, artifact, output, *, private_key=None, signing_home=None, key_id, source, url, sequence,
         channel="test", issued_at=None, expires_at=None, apk_version_code=None,
         apk_package_id=None, min_sdk=None, apk_verified=False, root=ROOT):
    """Sign metadata for a previously built artifact; never build or deploy it."""
    root, artifact, output = Path(root).resolve(), _safe_local(artifact), _safe_local(output)
    if (private_key is None) == (signing_home is None):
        _fail("one_publisher_key_source_required")
    if private_key is not None:
        key_path = _safe_local(private_key)
        if not Path(private_key).is_absolute() or not key_path.is_file():
            _fail("external_private_key_required")
        if key_path.resolve(strict=True).is_relative_to(root):
            _fail("external_private_key_required")
    if (component not in TARGETS or not artifact.is_file() or artifact.is_symlink()
            or not output.parent.is_dir() or output.exists() or output.is_symlink()):
        _fail("invalid_publish_path")
    if type(sequence) is not int or sequence < 1 or not isinstance(key_id, str) or not KEY_ID.fullmatch(key_id):
        _fail("invalid_publish_metadata")
    _url(source); _url(url, source)
    if urlsplit(url).path.rsplit("/", 1)[0] != urlsplit(source).path.rsplit("/", 1)[0]:
        _fail("untrusted_update_url")
    release = _catalog(root, component)
    # A valid signature must not advertise a path the private server cannot serve.
    match = SOURCE_PATH.fullmatch(urlsplit(source).path)
    extension = "apk" if component == "android" else "zip"
    filename = f"{component}-{release['version']}.{extension}"
    if (match is None or match.groups() != (channel, component)
            or url != source.rsplit("/", 1)[0] + "/" + filename):
        _fail("private_feed_path_required")
    size = artifact.stat().st_size
    if not 0 < size <= LIMIT:
        _fail("archive_limits_exceeded")
    now = int(time.time())
    issued_at = now if issued_at is None else issued_at
    expires_at = issued_at + 7 * 86400 if expires_at is None else expires_at
    payload = {"schema": 1, "product": PRODUCT, "component": component, "platform": TARGETS[component],
               "channel": channel, "version": release["version"], "sequence": sequence,
               "issuedAt": issued_at, "expiresAt": expires_at, "url": url, "bytes": size,
               "sha256": _file_sha(artifact), "notes": _notes(release),
               "compatibility": [COMPATIBILITY[component]], "dataSchema": 1, "rollbackCompatible": True}
    if component == "android":
        if (not apk_verified or type(release.get("versionCode")) is not int
                or apk_version_code != release["versionCode"] or apk_package_id != "dev.codex.desktopremote.lite"
                or type(min_sdk) is not int or min_sdk < 26):
            _fail("apk_publisher_verification_required")
        try:
            with zipfile.ZipFile(artifact) as apk:
                if "AndroidManifest.xml" not in apk.namelist():
                    _fail("invalid_android_artifact")
        except UpdateError:
            raise
        except (OSError, zipfile.BadZipFile):
            _fail("invalid_android_artifact")
        payload.update(versionCode=release["versionCode"], packageId=apk_package_id, minSdk=min_sdk)
    else:
        with tempfile.TemporaryDirectory(prefix="ota-sign-") as directory:
            unpack(artifact, Path(directory) / "package", payload)
    try:
        if signing_home is not None:
            from updates.signing import load_key
            key = load_key(signing_home, key_id=key_id, root=root)
        else:
            key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 3072:
            _fail("invalid_publisher_key")
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_publisher_key")
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")
    signature = key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
    envelope = {"schema": 1, "keyId": key_id, "payload": base64.b64encode(raw).decode(),
                "signature": base64.b64encode(signature).decode()}
    public = key.public_key().public_bytes(serialization.Encoding.PEM,
                                            serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    trust = {"schema": 1, "product": PRODUCT, "channel": channel,
             "keys": {key_id: public}, "sources": {component: source}}
    verify(envelope, trust, component, TARGETS[component], COMPATIBILITY[component], None, now=now)
    with output.open("xb") as stream:
        stream.write(json.dumps(envelope, sort_keys=True, separators=(",", ":")).encode("ascii"))
        stream.flush()
        os.fsync(stream.fileno())
    return envelope


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    package = commands.add_parser("pack", help="Build a new immutable allowlisted source ZIP")
    package.add_argument("component", choices=("windows", "relay"))
    package.add_argument("--output", type=Path, required=True)
    publisher = commands.add_parser("sign", help="Sign metadata for an existing ZIP or checked APK")
    publisher.add_argument("component", choices=tuple(TARGETS))
    for option in ("artifact", "output"):
        publisher.add_argument("--" + option, type=Path, required=True)
    key_source = publisher.add_mutually_exclusive_group(required=True)
    key_source.add_argument("--private-key", type=Path, help="External controlled PEM (legacy/offline tooling)")
    key_source.add_argument("--signing-home", type=Path, help="External Windows CurrentUser-DPAPI publisher vault")
    for option in ("key-id", "source", "url"):
        publisher.add_argument("--" + option, required=True)
    publisher.add_argument("--sequence", type=int, required=True)
    publisher.add_argument("--channel", choices=("test", "stable"), default="test")
    publisher.add_argument("--issued-at", type=int)
    publisher.add_argument("--expires-at", type=int)
    publisher.add_argument("--apk-version-code", type=int)
    publisher.add_argument("--apk-package-id")
    publisher.add_argument("--min-sdk", type=int)
    publisher.add_argument("--apk-verified", action="store_true", help="Publisher checked APK package/version/minSdk/signing certificate out of band")
    args = parser.parse_args(argv)
    try:
        if args.command == "pack":
            pack(args.component, args.output)
        else:
            sign(args.component, args.artifact, args.output, private_key=args.private_key,
                 signing_home=args.signing_home,
                 key_id=args.key_id, source=args.source, url=args.url, sequence=args.sequence,
                 channel=args.channel, issued_at=args.issued_at, expires_at=args.expires_at,
                 apk_version_code=args.apk_version_code, apk_package_id=args.apk_package_id,
                 min_sdk=args.min_sdk, apk_verified=args.apk_verified)
    except Exception:
        # No private-key path, password, artifact bytes or downstream exception text.
        print("publish_failed", file=sys.stderr)
        return 1
    print("Artifact prepared locally; external Python/Node/FRP and deployment remain separate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
