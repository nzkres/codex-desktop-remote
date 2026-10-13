# 服务器返回的通道参数不能直接拿来执行：先核对设备、主机、端口及证书。
# 目前只支持 FRP 与已配对中继同主机的部署，不能自动信任任意新主机。

import hashlib
import os
import re
import ssl
from urllib.parse import urlsplit
from connector_core import no_links
# 计算文件 SHA-256，供同名公开运行资源的内容一致性检查。
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

# 输入服务端 JSON 和本机身份状态，严格核对字段、设备绑定、同主机和 CA。
# 返回通过验证的原对象；任何不符都抛错，不猜测缺失参数。
def validate_connection(body, state):
    fields = {"schema", "terminalId", "remotePort", "proxyName", "server", "serverPort", "serverName", "caPem", "caSha256", "transportToken"}
    if not isinstance(body, dict) or set(body) != fields or type(body["schema"]) is not int or body["schema"] != 1:
        raise ValueError("invalid_connection_settings")
    if body["terminalId"] != state["terminalId"] or body["proxyName"] != state["terminalId"] + ".gateway":
        raise ValueError("connection_identity_mismatch")
    # This phase deliberately supports the existing one-host relay deployment.
    # A future split-host deployment needs an explicit local trust configuration.
    if body["server"] != urlsplit(state["relay"]).hostname:
        raise ValueError("connection_host_mismatch")
    for key in ("server", "serverName"):
        if not isinstance(body[key], str) or not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?", body[key]):
            raise ValueError("invalid_connection_host")
    for key in ("serverPort", "remotePort"):
        if type(body[key]) is not int or not 1024 <= body[key] <= 65535:
            raise ValueError("invalid_connection_port")
    for key in ("transportToken", "caSha256"):
        if not isinstance(body[key], str) or not re.fullmatch(r"[0-9a-f]{64}", body[key]):
            raise ValueError("invalid_connection_credential")
    pem = body["caPem"]
    if not isinstance(pem, str) or not 1 <= len(pem.encode()) <= 16384 or "PRIVATE KEY" in pem:
        raise ValueError("invalid_connection_ca")
    if hashlib.sha256(pem.encode()).hexdigest() != body["caSha256"]:
        raise ValueError("connection_ca_mismatch")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    try:
        context.load_verify_locations(cadata=pem)
    except (ValueError, ssl.SSLError):
        raise ValueError("invalid_connection_ca") from None
    return body


# 按内容哈希命名公开证书/模板文件，独占创建并刷盘；已有文件必须内容相同。
# 只适用于公开材料，令牌由其他通道传入，不在此持久化明文秘密。
def public_asset(root, suffix, content):
    """Content-addressed, exclusive creation; never overwrite an existing file."""
    path = root / ("runtime-" + hashlib.sha256(content).hexdigest() + suffix)
    no_links(path)
    try:
        with path.open("xb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError:
        if digest(path) != hashlib.sha256(content).hexdigest():
            raise ValueError("runtime_asset_changed") from None
    return path
