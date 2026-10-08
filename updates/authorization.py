"""Narrow, redacted authorization for the private static OTA directory."""

import re
from urllib.parse import urlsplit

from updates.security import UpdateError


HEX_TOKEN = re.compile(r"[0-9a-f]{64}\Z")
HEX_DEVICE = re.compile(r"[0-9a-f]{32}\Z")
SOURCE_PATH = re.compile(
    r"/desktop-remote-updates/(test|stable)/(android|windows|relay)/latest\.json\Z")
VERSION = r"[0-9]+\.[0-9]+\.[0-9]+(?:-alpha\.[0-9]+)?"


def _fail(code):
    raise UpdateError(code)


def _parsed(value, relay=False):
    try:
        if (not isinstance(value, str) or len(value) > 2048 or "\\" in value or "%" in value
                or "?" in value or "#" in value
                or any(ord(c) < 33 for c in value)):
            _fail("untrusted_update_url")
        result = urlsplit(value)
        port = 443 if result.port is None else result.port
        if (result.scheme != "https" or not result.hostname or result.username is not None
                or result.password is not None or result.query or result.fragment
                or not 1 <= port <= 65535
                or "//" in result.path or any(part in (".", "..") for part in result.path.split("/"))):
            _fail("untrusted_update_url")
        if relay and result.path not in ("", "/"):
            _fail("untrusted_update_url")
        return result
    except (TypeError, ValueError):
        _fail("untrusted_update_url")


def _origin(parsed):
    return parsed.hostname.lower(), 443 if parsed.port is None else parsed.port


class UpdateAuthorization:
    """Bearer capability bound to one relay origin and one component feed."""

    __slots__ = ("_relay", "_source", "_token", "_principal", "_device_id", "_component", "_directory")

    def __init__(self, relay, source, token, principal="account", device_id=None):
        server, feed = _parsed(relay, relay=True), _parsed(source)
        match = SOURCE_PATH.fullmatch(feed.path)
        if _origin(server) != _origin(feed) or not match:
            _fail("untrusted_update_url")
        if not isinstance(token, str) or not HEX_TOKEN.fullmatch(token):
            _fail("update_authentication_required")
        component = match.group(2)
        if principal not in ("account", "device"):
            _fail("update_authentication_required")
        if principal == "device":
            if component != "windows" or not isinstance(device_id, str) or not HEX_DEVICE.fullmatch(device_id):
                _fail("update_authentication_required")
        elif device_id is not None:
            _fail("update_authentication_required")
        self._relay = relay
        self._source = source
        self._token = token
        self._principal = principal
        self._device_id = device_id
        self._component = component
        self._directory = feed.path.rsplit("/", 1)[0] + "/"

    def __repr__(self):
        return "<UpdateAuthorization redacted>"

    __str__ = __repr__

    def headers_for(self, url):
        target = _parsed(url)
        if _origin(target) != _origin(_parsed(self._source)):
            _fail("untrusted_update_url")
        if url != self._source:
            filename = target.path.removeprefix(self._directory)
            extension = "apk" if self._component == "android" else "zip"
            pattern = rf"{self._component}-{VERSION}\.{extension}\Z"
            if not target.path.startswith(self._directory) or "/" in filename or not re.fullmatch(pattern, filename):
                _fail("untrusted_update_url")
        headers = {"Authorization": "Bearer " + self._token,
                   "X-Desktop-Remote-Principal": self._principal}
        if self._device_id is not None:
            headers["X-Desktop-Remote-Device"] = self._device_id
        return headers
