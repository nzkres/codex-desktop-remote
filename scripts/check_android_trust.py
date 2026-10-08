"""Preflight controlled Android OTA trust and its exact APK asset bytes."""

import argparse
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit
import zipfile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from updates.authorization import SOURCE_PATH
from updates.security import _json, _url


LIMIT = 64 * 1024
ASSET = "assets/update-trust.json"
KEY_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}\Z")


class TrustCheckError(ValueError):
    def __init__(self):
        super().__init__("android_trust_check_failed")


def _load(path):
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(LIMIT + 1)
        if not 0 < len(raw) <= LIMIT:
            raise TrustCheckError()
        value = _json(raw.decode("utf-8", errors="strict"))
        if (not isinstance(value, dict) or set(value) != {"schema", "product", "channel", "keys", "sources"}
                or type(value["schema"]) is not int or value["schema"] != 1
                or value["product"] != "codex-desktop-remote" or value["channel"] != "test"
                or not isinstance(value["keys"], dict) or len(value["keys"]) != 1
                or not isinstance(value["sources"], dict) or set(value["sources"]) != {"android"}):
            raise TrustCheckError()
        key_id, pem = next(iter(value["keys"].items()))
        if not isinstance(key_id, str) or not KEY_ID.fullmatch(key_id) or not isinstance(pem, str):
            raise TrustCheckError()
        public_key = serialization.load_pem_public_key(pem.encode("ascii"))
        if not isinstance(public_key, rsa.RSAPublicKey) or public_key.key_size < 3072:
            raise TrustCheckError()
        canonical = public_key.public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        ).decode("ascii").strip()
        if pem.strip().replace("\r\n", "\n") != canonical:
            raise TrustCheckError()
        source = value["sources"]["android"]
        if not isinstance(source, str) or "?" in source or "#" in source or "%" in source:
            raise TrustCheckError()
        _url(source)
        match = SOURCE_PATH.fullmatch(urlsplit(source).path)
        if match is None or match.groups() != ("test", "android"):
            raise TrustCheckError()
        return value, raw
    except Exception as error:
        if isinstance(error, TrustCheckError):
            raise
        raise TrustCheckError() from None


def validate_trust(path) -> dict:
    return _load(path)[0]


def check_apk_trust(apk, trust_path):
    _, expected = _load(trust_path)
    try:
        with zipfile.ZipFile(apk) as archive:
            matching = [item for item in archive.infolist() if item.filename == ASSET]
            if len(matching) != 1 or matching[0].is_dir() or matching[0].file_size > LIMIT:
                raise TrustCheckError()
            with archive.open(matching[0]) as stream:
                actual = stream.read(LIMIT + 1)
            if actual != expected:
                raise TrustCheckError()
    except Exception as error:
        if isinstance(error, TrustCheckError):
            raise
        raise TrustCheckError() from None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trust", type=Path, required=True)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.apk is None:
            validate_trust(args.trust)
        else:
            check_apk_trust(args.apk, args.trust)
    except TrustCheckError:
        print("Android OTA trust verification failed.", file=sys.stderr)
        return 1
    print("Android OTA trust verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
