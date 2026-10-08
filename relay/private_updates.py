"""Authenticated, bounded private OTA files for the explicitly managed relay."""

import os
from pathlib import Path
import re
import stat

from registry import ControlError


METADATA_LIMIT = 64 * 1024
PACKAGE_LIMIT = 600 * 1024 * 1024
PREFIX = "/desktop-remote-updates/"
ROUTE = re.compile(
    r"/desktop-remote-updates/(test|stable)/(android|windows|relay)/"
    r"(latest\.json|(?:android|windows|relay)-[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}"
    r"(?:-alpha\.[0-9]{1,9})?\.(?:apk|zip))\Z"
)
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
HEX32 = re.compile(r"[0-9a-f]{32}\Z")


def _safe_directory(path):
    try:
        info = path.lstat()
    except OSError:
        raise ControlError(404, "not_found") from None
    if (not stat.S_ISDIR(info.st_mode) or path.is_symlink()
            or (hasattr(path, "is_junction") and path.is_junction())
            or (os.name == "posix" and (info.st_uid != os.geteuid() or info.st_mode & 0o077))):
        raise ControlError(404, "not_found")


class PrivateUpdates:
    def __init__(self, data_root: Path, registry, account):
        self.data_root = Path(data_root).absolute()
        self.feed_root = self.data_root / "update-feed"
        self.registry = registry
        self.account = account

    @staticmethod
    def _single_header(handler, name):
        values = handler.headers.get_all(name, [])
        if len(values) != 1:
            raise ControlError(401, "credential_required")
        return values[0]

    def _authorize(self, handler, component):
        authorization = self._single_header(handler, "Authorization")
        principal = self._single_header(handler, "X-Desktop-Remote-Principal")
        if not authorization.startswith("Bearer ") or not HEX64.fullmatch(authorization[7:]):
            raise ControlError(401, "credential_required")
        token = authorization[7:]
        if principal == "account":
            if handler.headers.get_all("X-Desktop-Remote-Device", []):
                raise ControlError(401, "credential_required")
            self.account(authorization)
            return
        if principal == "device":
            terminal = self._single_header(handler, "X-Desktop-Remote-Device")
            if not HEX32.fullmatch(terminal):
                raise ControlError(401, "credential_required")
            self.registry.device(terminal, token)
            if component != "windows":
                raise ControlError(403, "update_component_forbidden")
            return
        raise ControlError(401, "credential_required")

    @staticmethod
    def _route(raw_path):
        # Match the raw request target: no query, fragments, URL encoding,
        # normalization, extra separators, or directory endpoint.
        if not isinstance(raw_path, str) or not raw_path.isascii() or any(
                marker in raw_path for marker in ("?", "#", "%", "\\", "//")):
            raise ControlError(404, "not_found")
        match = ROUTE.fullmatch(raw_path)
        if match is None:
            raise ControlError(404, "not_found")
        channel, component, filename = match.groups()
        if filename != "latest.json":
            prefix = component + "-"
            suffix = ".apk" if component == "android" else ".zip"
            if not filename.startswith(prefix) or not filename.endswith(suffix):
                raise ControlError(404, "not_found")
        return channel, component, filename

    def _open(self, channel, component, filename):
        # The managed home/data root is owner-private. Recheck every feed
        # component on each request, including the final file descriptor.
        for ancestor in (self.data_root, *self.data_root.parents):
            if ancestor.is_symlink() or (hasattr(ancestor, "is_junction") and ancestor.is_junction()):
                raise ControlError(404, "not_found")
        for directory in (self.data_root.parent, self.data_root, self.feed_root,
                          self.feed_root / channel, self.feed_root / channel / component):
            _safe_directory(directory)
        path = self.feed_root / channel / component / filename
        descriptor = None
        try:
            descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
                                 | getattr(os, "O_CLOEXEC", 0))
            info = os.fstat(descriptor)
            limit = METADATA_LIMIT if filename == "latest.json" else PACKAGE_LIMIT
            if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or not 0 < info.st_size <= limit
                    or path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction())
                    or (os.name == "posix" and (info.st_uid != os.geteuid() or info.st_mode & 0o077))):
                raise ControlError(404, "not_found")
            stream = os.fdopen(descriptor, "rb")
            descriptor = None
            return stream, info.st_size
        except ControlError:
            raise
        except OSError:
            raise ControlError(404, "not_found") from None
        finally:
            if descriptor is not None:
                os.close(descriptor)

    @staticmethod
    def _stream(handler, stream, length):
        remaining = length
        while remaining:
            chunk = stream.read(min(128 * 1024, remaining))
            if not chunk:
                handler.close_connection = True
                return
            handler.wfile.write(chunk)
            remaining -= len(chunk)

    def handle(self, handler):
        channel, component, filename = self._route(handler.path)
        if handler.command != "GET":
            raise ControlError(405, "method_not_allowed")
        self._authorize(handler, component)
        stream, length = self._open(channel, component, filename)
        with stream:
            content_type = ("application/json" if filename == "latest.json" else
                            "application/vnd.android.package-archive" if component == "android" else
                            "application/zip")
            handler.send_response(200)
            handler.send_header("Content-Type", content_type)
            handler.send_header("Content-Length", str(length))
            handler.send_header("Cache-Control", "private, no-store")
            handler.send_header("X-Content-Type-Options", "nosniff")
            handler.send_header("Connection", "close")
            handler.end_headers()
            handler.close_connection = True
            self._stream(handler, stream, length)
