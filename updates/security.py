# OTA 信任边界：校验发布者签名、产品/平台/渠道/序号、下载大小与哈希、解包文件清单。
# HTTPS 保护传输，发布公钥证明发布者；两者作用不同，不能用同站点 SHA 文件替代签名。

"""Authenticated OTA metadata and inert package staging; never switches or executes code."""

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import stat
import time
from urllib.parse import urlsplit
import urllib.error
import urllib.request
import zipfile

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


# 限制下载、解压和元数据大小，阻断超大包或高压缩率压缩包耗尽磁盘。
LIMIT = 600 * 1024 * 1024
UNPACK_LIMIT = 1500 * 1024 * 1024
METADATA_LIMIT = 64 * 1024
HEX = re.compile(r"[0-9a-f]{64}\Z")
VERSION = re.compile(r"([0-9]+)\.([0-9]+)\.([0-9]+)(?:-alpha\.([0-9]+))?\Z")
TARGETS = {"android": "android", "windows": "windows-x64", "relay": "linux-x64"}
COMPATIBILITY = {"android": "desktop-remote-v1", "windows": "desktop-26.930.7945.0", "relay": "desktop-remote-v1"}
COMMON = {"schema", "product", "component", "platform", "channel", "version", "sequence",
          "issuedAt", "expiresAt", "url", "bytes", "sha256", "notes", "compatibility",
          "dataSchema", "rollbackCompatible"}
ANDROID = {"versionCode", "packageId", "minSdk"}
MANIFEST = {"schema", "product", "component", "platform", "version", "files", "dataSchema"}
FORBIDDEN_NAMES = {"trust.json", "fixture.local.json", "local.properties", ".env",
                   "client-settings.json", "state.json", "identity.json"}
FORBIDDEN_DIRS = {"data", "identity", "credentials", "secrets"}


class UpdateError(Exception):
    """Only a fixed public code; source exceptions and downloaded text stay private."""

    def __init__(self, code):
        super().__init__(code)
        self.code = code


def _fail(code):
    # 安全检查统一抛固定错误码，不把网络响应或本地路径带给调用方。
    raise UpdateError(code)


def _unique(pairs):
    # JSON 中重复字段可能造成“验签所见”和“业务所见”不同，直接拒绝。
    result = {}
    for key, value in pairs:
        if key in result:
            _fail("invalid_json")
        result[key] = value
    return result


def _json(raw):
    # 解析 JSON 并拒绝重复键、NaN 等非标准值；返回解析后的对象。
    try:
        return json.loads(raw, object_pairs_hook=_unique,
                          parse_constant=lambda value: _fail("invalid_json"))
    except UpdateError:
        raise
    except (UnicodeError, ValueError, TypeError):
        _fail("invalid_json")


def _version(value):
    # 把受限格式的版本号转换成可比较元组；正式版排序在同版本 alpha 之后。
    if not isinstance(value, str) or len(value) > 64:
        _fail("invalid_release_metadata")
    match = VERSION.fullmatch(value)
    if not match:
        _fail("invalid_release_metadata")
    major, minor, patch, alpha = match.groups()
    if any(len(part) > 9 for part in (major, minor, patch, alpha or "0")):
        _fail("invalid_release_metadata")
    return int(major), int(minor), int(patch), 1 if alpha is None else 0, int(alpha or 0)


def _sha(value):
    # 校验完整的小写 SHA-256 十六进制文本。
    return isinstance(value, str) and HEX.fullmatch(value) is not None


def _url(value, source=None):
    # 更新地址必须是无用户信息、查询和片段的 HTTPS URL。
    # 提供 source 时，下载地址还必须留在该订阅目录下；返回原 URL。
    try:
        parsed = urlsplit(value)
        if (not isinstance(value, str) or len(value) > 2048 or parsed.scheme != "https" or not parsed.hostname
                or (parsed.port is not None and not 1 <= parsed.port <= 65535)
                or parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment
                or not parsed.path.startswith("/") or "\\" in value or "%" in parsed.path
                or "//" in parsed.path or any(ord(c) < 33 for c in value)):
            _fail("untrusted_update_url")
        # A source's directory is the narrowest allowed publication subtree.
        if source is not None:
            trusted = urlsplit(source)
            _url(source)
            base = trusted.path.rsplit("/", 1)[0] + "/"
            if parsed.netloc != trusted.netloc or not parsed.path.startswith(base):
                _fail("untrusted_update_url")
        if parsed.path.split("/").count("..") or parsed.path.split("/").count("."):
            _fail("untrusted_update_url")
        return value
    except (TypeError, ValueError):
        _fail("untrusted_update_url")


def _b64(value):
    # 严格解码签名信封中的 Base64 字段，拒绝夹杂无效字符。
    if not isinstance(value, str):
        _fail("invalid_release_envelope")
    try:
        return base64.b64decode(value, validate=True)
    except (ValueError, TypeError):
        _fail("invalid_release_envelope")


def _ledger(value):
    # 将空账本初始化，并校验既有序号、签名字节绑定和版本摘要的结构。
    if value is None or value == {}:
        return {"sequence": 0, "binding": None, "versions": {}}
    if (not isinstance(value, dict) or set(value) != {"sequence", "binding", "versions"}
            or type(value["sequence"]) is not int or value["sequence"] < 0
            or (value["binding"] is not None and not _sha(value["binding"]))
            or not isinstance(value["versions"], dict)
            or any(not isinstance(k, str) or not _sha(v) for k, v in value["versions"].items())):
        _fail("invalid_update_ledger")
    return value


def verify(envelope, trust, component, platform, compatibility, installed_version, ledger=None, now=None):
    """Verify raw signed bytes, strict target and monotonic transition."""
    # envelope 是签名信封，trust 是预置公钥/频道/源；返回发布内容及新防回放账本。
    # 先验证原始 payload 字节的签名，再解释 JSON，防止未认证数据影响更新决策。
    if (not isinstance(trust, dict) or set(trust) != {"schema", "product", "channel", "keys", "sources"}
            or type(trust.get("schema")) is not int or trust["schema"] != 1
            or trust.get("product") != "codex-desktop-remote"
            or trust.get("channel") not in ("test", "stable") or not isinstance(trust.get("keys"), dict)
            or not isinstance(trust.get("sources"), dict) or not trust["keys"] or not trust["sources"]):
        _fail("update_unconfigured")
    if component not in TARGETS or platform != TARGETS[component] or compatibility != COMPATIBILITY[component]:
        _fail("wrong_release_target")
    source = trust["sources"].get(component)
    if not isinstance(source, str):
        _fail("update_unconfigured")
    _url(source)
    if not isinstance(envelope, dict) or set(envelope) != {"schema", "keyId", "payload", "signature"} or type(envelope.get("schema")) is not int or envelope["schema"] != 1:
        _fail("invalid_release_envelope")
    key_id = envelope["keyId"]
    pem = trust["keys"].get(key_id) if isinstance(key_id, str) else None
    if not isinstance(pem, str):
        _fail("untrusted_release_key")
    raw, signature = _b64(envelope["payload"]), _b64(envelope["signature"])
    if not 0 < len(raw) <= METADATA_LIMIT:
        _fail("invalid_release_metadata")
    try:
        key = serialization.load_pem_public_key(pem.encode("ascii"))
        if not isinstance(key, rsa.RSAPublicKey) or key.key_size < 3072:
            _fail("untrusted_release_key")
        key.verify(signature, raw, padding.PKCS1v15(), hashes.SHA256())
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_release_signature")
    try:
        payload = _json(raw.decode("utf-8", errors="strict"))
    except UnicodeError:
        _fail("invalid_release_metadata")
    fields = COMMON | (ANDROID if component == "android" else set())
    if not isinstance(payload, dict) or set(payload) != fields or type(payload.get("schema")) is not int or payload["schema"] != 1:
        _fail("invalid_release_metadata")
    if (payload["product"] != "codex-desktop-remote" or payload["component"] != component
            or payload["platform"] != platform or payload["channel"] != trust["channel"]):
        _fail("wrong_release_target")
    if (type(payload["sequence"]) is not int or payload["sequence"] < 1
            or type(payload["issuedAt"]) is not int or type(payload["expiresAt"]) is not int):
        _fail("invalid_release_metadata")
    clock = int(time.time()) if now is None else now
    if (type(clock) is not int or payload["issuedAt"] > clock + 300 or payload["expiresAt"] <= clock
            or payload["expiresAt"] <= payload["issuedAt"]
            or payload["expiresAt"] - payload["issuedAt"] > 31 * 86400):
        _fail("expired_release")
    order = _version(payload["version"])
    if trust["channel"] == "stable" and order[3] == 0:
        _fail("wrong_release_target")
    if (type(payload["bytes"]) is not int or not 0 < payload["bytes"] <= LIMIT
            or not _sha(payload["sha256"]) or not isinstance(payload["notes"], str)
            or len(payload["notes"]) > 8192 or type(payload["dataSchema"]) is not int
            or payload["dataSchema"] != 1 or payload["rollbackCompatible"] is not True
            or not isinstance(payload["compatibility"], list) or not payload["compatibility"]
            or any(not isinstance(item, str) or not item or len(item) > 128 for item in payload["compatibility"])
            or len(set(payload["compatibility"])) != len(payload["compatibility"])):
        _fail("invalid_release_metadata")
    if compatibility not in payload["compatibility"]:
        _fail("incompatible_release")
    if component == "android" and (type(payload["versionCode"]) is not int or payload["versionCode"] < 1
                                    or payload["packageId"] != "dev.codex.desktopremote.lite"
                                    or type(payload["minSdk"]) is not int or payload["minSdk"] < 26):
        _fail("wrong_release_target")
    _url(payload["url"], source)
    old = _ledger(ledger)
    # 序号不能倒退；同一序号、同一版本也不能静默换包。
    binding = hashlib.sha256(raw).hexdigest()
    if payload["sequence"] < old["sequence"]:
        _fail("release_replay_rejected")
    if payload["sequence"] == old["sequence"] and binding != old["binding"]:
        _fail("same_sequence_replacement_rejected")
    prior = old["versions"].get(payload["version"])
    if prior is not None and prior != payload["sha256"]:
        _fail("same_version_replacement_rejected")
    if installed_version is not None:
        installed = _version(installed_version)
        if order <= installed:
            if order == installed and (prior is None or prior == payload["sha256"]):
                _fail("no_update_available")
            _fail("release_replay_rejected")
    return payload, {"sequence": payload["sequence"], "binding": binding,
                     "versions": {**old["versions"], payload["version"]: payload["sha256"]}}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        # 不跟随跳转，避免认证头被带往未经授权的目标。
        _fail("update_redirect_rejected")


def _opener(context):
    # 可注入 TLS 上下文供测试/部署使用，但证书验证和主机名验证不可关闭。
    if context is not None and (not isinstance(context, ssl.SSLContext)
                                or context.verify_mode != ssl.CERT_REQUIRED or not context.check_hostname):
        _fail("invalid_tls_context")
    return urllib.request.build_opener(_NoRedirect(), urllib.request.HTTPSHandler(context=context))


def _authorization_headers(authorization, url):
    # 由受约束的授权对象决定能否携带 bearer，不能直接传任意头部字典。
    if authorization is None:
        return {}
    from updates.authorization import UpdateAuthorization
    if not isinstance(authorization, UpdateAuthorization):
        _fail("update_authentication_required")
    return authorization.headers_for(url)


def _http_error(error, fallback):
    # 将 HTTP 状态映射为稳定错误码，关闭响应并隐藏服务端私有错误正文。
    status = error.code
    try:
        error.close()
    except Exception:
        pass  # A failed response close must not expose a private network error.
    if status == 401:
        _fail("update_authentication_required")
    if status == 403:
        _fail("update_access_denied")
    _fail(fallback)


def fetch(source, context=None, authorization=None):
    # 只抓取受大小限制的发布信封，不在此处信任其内容；签名由 verify 校验。
    _url(source)
    headers = {"Accept": "application/json", **_authorization_headers(authorization, source)}
    try:
        with _opener(context).open(urllib.request.Request(source, headers=headers), timeout=20) as response:
            raw = response.read(METADATA_LIMIT + 1)
            if response.status != 200 or not 0 < len(raw) <= METADATA_LIMIT:
                _fail("invalid_release_response")
        value = _json(raw.decode("utf-8", errors="strict"))
        if not isinstance(value, dict):
            _fail("invalid_release_envelope")
        return value
    except UpdateError:
        raise
    except urllib.error.HTTPError as error:
        _http_error(error, "update_fetch_failed")
    except (OSError, urllib.error.URLError, ValueError, UnicodeError):
        _fail("update_fetch_failed")


def _safe_local(path):
    # 对本地路径进行绝对化并拒绝链接，避免写入或读取安装范围外的文件。
    try:
        path = Path(path).absolute()
        for part in (path, *path.parents):
            if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
                _fail("unsafe_local_path")
        return path
    except UpdateError:
        raise
    except Exception:
        _fail("unsafe_local_path")


def _file_sha(path):
    # 分块计算文件 SHA-256，避免大安装包一次读入内存。
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        while chunk := stream.read(128 * 1024):
            result.update(chunk)
    return result.hexdigest()


def download(payload, destination, progress=None, context=None, authorization=None):
    # 将已选发布的包下载到全新路径；用字节数和 SHA-256 验证后返回路径。
    # progress 接收(已写入字节, 总字节)，提前检查空间并拒绝响应大小不符。
    _url(payload.get("url") if isinstance(payload, dict) else None)
    headers = _authorization_headers(authorization, payload["url"])
    count = payload.get("bytes")
    expected = payload.get("sha256")
    if type(count) is not int or not 0 < count <= LIMIT or not _sha(expected):
        _fail("invalid_release_metadata")
    target = _safe_local(destination)
    if target.exists() or not target.parent.is_dir():
        _fail("new_download_path_required")
    if shutil.disk_usage(target.parent).free < count * 3 + 64 * 1024 * 1024:
        _fail("insufficient_disk_space")
    written, digest = 0, hashlib.sha256()
    try:
        with _opener(context).open(urllib.request.Request(payload["url"], headers=headers), timeout=20) as response:
            if response.status != 200 or response.headers.get("Content-Length") != str(count):
                _fail("download_size_mismatch")
            with target.open("xb") as output:
                while chunk := response.read(128 * 1024):
                    written += len(chunk)
                    if written > count:
                        _fail("download_size_mismatch")
                    output.write(chunk)
                    digest.update(chunk)
                    if progress is not None:
                        progress(written, count)
                output.flush()
                os.fsync(output.fileno())
        if written != count or digest.hexdigest() != expected:
            _fail("download_integrity_failed")
        return target
    except UpdateError:
        raise
    except urllib.error.HTTPError as error:
        _http_error(error, "update_download_failed")
    except Exception:
        _fail("update_download_failed")


def _parts(name):
    # 验证压缩包内相对路径，拒绝穿越、Windows 保留名及凭据/运行数据文件。
    if (not isinstance(name, str) or not name or name.startswith("/") or name.endswith("//") or "\\" in name
            or ":" in name or "\x00" in name or any(ord(c) < 32 for c in name)):
        _fail("unsafe_archive_path")
    parts = name.rstrip("/").split("/")
    if any(not p or p in (".", "..") or p.endswith((".", " ")) or
           re.match(r"(?i)^(con|prn|aux|nul|conin\$|conout\$|com[1-9]|lpt[1-9])(?:\.|$)", p) for p in parts):
        _fail("unsafe_archive_path")
    lowered = [p.casefold() for p in parts]
    if (lowered[0] in FORBIDDEN_DIRS or any(p in FORBIDDEN_NAMES or p.endswith(
            (".p12", ".pfx", ".key", ".pem", ".db", ".sqlite", ".sqlite3", ".log")) for p in lowered)):
        _fail("forbidden_package_content")
    return parts


def _manifest(raw, expected_component=None, expected_version=None, expected_platform=None):
    # 解读包清单并检查目标、版本与每个文件的摘要格式；返回清单对象。
    value = _json(raw)
    if (not isinstance(value, dict) or set(value) != MANIFEST or type(value["schema"]) is not int
            or value["schema"] != 1 or value["product"] != "codex-desktop-remote"
            or not isinstance(value["component"], str) or value["component"] not in TARGETS
            or value["platform"] != TARGETS[value["component"]]
            or type(value["dataSchema"]) is not int or value["dataSchema"] != 1
            or not isinstance(value["files"], dict)):
        _fail("invalid_package_manifest")
    _version(value["version"])
    if ((expected_component is not None and value["component"] != expected_component)
            or (expected_platform is not None and value["platform"] != expected_platform)
            or (expected_version is not None and value["version"] != expected_version)):
        _fail("wrong_release_target")
    normalized = set()
    for name, digest in value["files"].items():
        _parts(name)
        if name.endswith("/") or name.casefold() == "package.json" or name.casefold() in normalized or not _sha(digest):
            _fail("invalid_package_manifest")
        normalized.add(name.casefold())
    return value


def verify_tree(root, expected_component=None, expected_version=None):
    # 遍历解包目录：文件集合、大小、路径和内容摘要必须与清单完全一致。
    # 返回清单；拒绝链接和额外文件，避免启动时执行未列入签名包的内容。
    root = _safe_local(root)
    if not root.is_dir():
        _fail("invalid_package_tree")
    manifest_path = root / "package.json"
    if manifest_path.is_symlink() or not manifest_path.is_file() or manifest_path.stat().st_size > 1024 * 1024:
        _fail("invalid_package_manifest")
    try:
        manifest = _manifest(manifest_path.read_bytes(), expected_component, expected_version)
        actual = {}
        normalized = set()
        total = 0
        for current, dirs, files in os.walk(root, followlinks=False,
                                            onerror=lambda error: _fail("invalid_package_tree")):
            base = Path(current)
            for name in dirs:
                path = base / name
                _parts(path.relative_to(root).as_posix() + "/")
                if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
                    _fail("unsafe_package_tree")
            for name in files:
                path = base / name
                relative = path.relative_to(root).as_posix()
                _parts(relative)
                if path.is_symlink() or not path.is_file():
                    _fail("unsafe_package_tree")
                if relative.casefold() in normalized:
                    _fail("unsafe_package_tree")
                normalized.add(relative.casefold())
                total += path.stat().st_size
                if len(actual) >= 25000 or path.stat().st_size > LIMIT or total > UNPACK_LIMIT:
                    _fail("archive_limits_exceeded")
                actual[relative] = path
        actual.pop("package.json", None)
        if set(actual) != set(manifest["files"]):
            _fail("package_file_set_changed")
        for name, digest in manifest["files"].items():
            if _file_sha(actual[name]) != digest:
                _fail("package_file_changed")
        return manifest
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_package_tree")


def unpack(archive, destination, payload):
    # 校验压缩包摘要后解到全新目录，并再次验证完整文件树；返回目录路径。
    # 先检查所有条目再落盘，避免路径穿越、链接、重复名和压缩炸弹。
    archive, target = _safe_local(archive), _safe_local(destination)
    if not archive.is_file() or target.exists() or not target.parent.is_dir():
        _fail("new_stage_required")
    if (not isinstance(payload, dict) or type(payload.get("bytes")) is not int
            or not 0 < payload["bytes"] <= LIMIT or not _sha(payload.get("sha256"))):
        _fail("invalid_release_metadata")
    try:
        if archive.stat().st_size != payload["bytes"] or _file_sha(archive) != payload["sha256"]:
            _fail("download_integrity_failed")
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_release_archive")
    try:
        with zipfile.ZipFile(archive) as bundle:
            entries = bundle.infolist()
            if not 1 <= len(entries) <= 25000:
                _fail("archive_limits_exceeded")
            names, files, directories, total = set(), set(), set(), 0
            for info in entries:
                parts = _parts(info.filename)
                normalized = "/".join(parts).casefold()
                mode = info.external_attr >> 16
                if (normalized in names or info.flag_bits & 1 or stat.S_ISLNK(mode)
                        or (mode & stat.S_IFMT(mode) and not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)))
                        or (info.external_attr & 0x400)):
                    _fail("unsafe_archive_entry")
                names.add(normalized)
                if info.is_dir():
                    directories.add(normalized)
                else:
                    files.add(normalized)
                    total += info.file_size
                    if (info.file_size > LIMIT or total > UNPACK_LIMIT
                            or info.file_size > max(info.compress_size, 1) * 1000):
                        _fail("archive_limits_exceeded")
            if any(any(name.startswith(file + "/") for name in names) for file in files) or files & directories:
                _fail("unsafe_archive_entry")
            if "package.json" not in files:
                _fail("invalid_package_manifest")
            manifest_info = next(info for info in entries if info.filename.casefold() == "package.json")
            if manifest_info.filename != "package.json":
                _fail("invalid_package_manifest")
            if manifest_info.file_size > 1024 * 1024:
                _fail("invalid_package_manifest")
            manifest = _manifest(bundle.read(manifest_info), payload.get("component"), payload.get("version"), payload.get("platform"))
            if files - {"package.json"} != {name.casefold() for name in manifest["files"]}:
                _fail("package_file_set_changed")
            if shutil.disk_usage(target.parent).free < total + 64 * 1024 * 1024:
                _fail("insufficient_disk_space")
            target.mkdir()
            for info in entries:
                output = target.joinpath(*_parts(info.filename))
                if info.is_dir():
                    output.mkdir(parents=True, exist_ok=True)
                else:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    with bundle.open(info) as source, output.open("xb") as sink:
                        shutil.copyfileobj(source, sink, 128 * 1024)
        verify_tree(target, payload.get("component"), payload.get("version"))
        return target
    except UpdateError:
        raise
    except Exception:
        _fail("invalid_release_archive")
