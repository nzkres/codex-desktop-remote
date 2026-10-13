# 连接器的总调度层：GUI → Controller → 配对/心跳核心 → Node 本机接口 + FRP 隧道。
# 这里不执行模型、不创建 Desktop 任务；Desktop 始终拥有原任务。
# 阅读顺序：connect → _runtime → LiteRuntime.reconcile → LiteRuntime.disconnect。

"""Isolated pairing/transport assembly. Never adopts an existing Gateway/FRP."""
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import socket
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).with_name('vendor')))
from connector_core import Connector, ConnectorError, CredentialStore, RelayClient, no_links
from connection_settings import validate_connection, public_asset
from runtime_core import OwnedProcess, LoopbackHealthProbe, tunnel_probe
from frp_config import launch_material
from windows_job import transport_process, spawn_guarded, helper_pins


# 向本机接口发出“停止接收新操作并检查已有操作”的请求。
# port 为本机端口，control_token 为仅本机使用的控制凭据，instance 标识本次实例。
# 只有响应明确来自本实例且 drained=True 才返回 True；超时、畸形响应都返回 False。
def prepare_disconnect(port, control_token, instance):
    """Local-only lifecycle capability, never sent to the relay or stored."""
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=7)
    try:
        connection.request('POST', '/api/connector/prepare-disconnect', body=b'',
                           headers={'Authorization': 'Bearer ' + control_token, 'Connection': 'close'})
        response = connection.getresponse()
        size = int(response.getheader('Content-Length', '0'))
        if response.status != 200 or not 0 < size <= 2048:
            return False
        body = json.loads(response.read(size))
        return body == {'instance': instance, 'drained': True}
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        connection.close()


# 同时持有两个自有进程对象。adapter 是 Node 任务接口，transport 是 FRP。
# drain 回调负责检查未完成操作；clean 是退出证据，不能仅凭进程状态猜测。
class LiteRuntime:
    """These are disposable adapter/tunnel children; Desktop owns every turn."""
    # 只保存对象和初始标志，此时还没有启动任何进程。
    # faulted 一旦置位，会阻止把发生过适配器故障的本轮运行当作普通断开。
    def __init__(self, adapter, transport, drain=lambda: False):
        self.adapter, self.transport = adapter, transport
        self.allowed = False
        self.drain = drain
        self.disconnected = False
        self.clean = False
        self.faulted = False

    # 每个连接循环调用一次，根据服务器授权和本机接口健康状态推进传输。
    # allowed=False 时停止隧道；不会因此直接停止 Desktop 的模型任务。
    # 返回给 GUI 的报告；probe_verified 只证明探针链路，不证明一次手机任务完成。
    def reconcile(self, allowed):
        self.allowed = allowed
        if allowed and self.adapter.tick() == 'healthy':
            self.transport.tick()
        else:
            self.transport.stop()
        if self.adapter.state in ('failed', 'invalid_binary'):
            self.faulted = True
        return {'gateway': self.adapter.state, 'transport': self.transport.state,
                'executor': 'unknown', 'dataChannel': 'probe_verified'
                if allowed and self.adapter.state == self.transport.state == 'healthy' else 'unavailable'}

    # 只执行一次的清理过程。先尝试 drain，再分别停止隧道和本机接口。
    # 即使第一个子进程清理失败，也必须尝试第二个；两个进程树均退出才可能 clean。
    # 当前 normal/allowed/faulted 门槛较保守：断网后退出仍可能要求复核，这是现有行为。
    def disconnect(self, normal=False):
        if self.disconnected:
            return  # An abnormal disconnect cannot be upgraded to a clean one.
        self.disconnected = True
        eligible = normal and self.allowed and not self.faulted
        self.allowed = False
        never_started = not self.adapter.ever_started and not self.transport.ever_started
        drained = False
        errors = False
        try:
            if eligible and self.adapter.ever_started:
                drained = self.drain() is True
        except Exception:
            pass
        # Always attempt BOTH owned trees, even if the first cleanup fails.
        for child in (self.transport, self.adapter):
            try:
                child.stop()
            except Exception:
                errors = True
        trees_empty = all(getattr(child, 'tree_stopped', False) is True
                          for child in (self.adapter, self.transport))
        self.clean = not errors and trees_empty and (never_started or drained)
        if normal and not self.clean:
            raise ConnectorError('runtime_cleanup_requires_review')

    # 当前实现故意不接管崩溃前遗留的运行现场，固定返回 False。
    # 这里没有自动冷恢复；不能把删除 runtimeNeedsReview 当作实现了恢复。
    def resume_existing(self, state):
        return False  # A crash stays reviewable. No flag deletion/adoption shortcut.

    # 返回清理是否有完整证据，供身份核心决定能否清除运行保护标志。
    def quiescent(self):
        return self.clean


# 界面使用的控制器：不直接画窗口，负责调用身份核心并组装运行环境。
class Controller:
    # 读取本地任务配置，定位身份目录。quarantine 由 OTA 指定，用于验证阶段禁止写操作。
    # 配置路径不允许经过符号链接或目录联接，避免意外指向别的安装。
    def __init__(self, config_file, data_root=None, quarantine=False):
        self.config_file = Path(config_file).resolve()
        no_links(self.config_file)
        self.config = json.loads(self.config_file.read_text(encoding='utf-8'))
        # Only a new application-local identity. Never accept a legacy state path.
        self.data_root = Path(data_root) if data_root else ROOT / 'data'
        self.quarantine = quarantine
        self.store = CredentialStore(self.data_root / 'identity')

    # 读取保存的登记快照，并补充运行保护状态。
    # 磁盘上的 adapterBinding 不是实时探针，因此通道只能先报告 unverified。
    def status(self):
        report = Connector(self.store).status()
        # This adapter deliberately has no legacy gatewayBinding. A saved
        # adapterBinding proves configuration, not a live task-channel probe.
        with self.store.lock():
            state = self.store.read()
        if state and state.get('adapterBinding'):
            report['dataChannel'] = 'unverified'
        report['runtimeNeedsReview'] = bool(state and state.get('runtimeNeedsReview'))
        return report

    # 把用户输入的 HTTPS 地址、配对码和设备名交给核心；不在界面层另造配对协议。
    def pair(self, relay, code, name):
        return Connector(self.store).begin(relay, code, name)

    # 由 GUI 工作线程调用。stop 是退出信号，on_status 是把状态送回界面的回调。
    # 第一段只等待登记确认；确认后第二段创建受管运行环境并维持心跳。
    # 被撤销、过期、拒绝等终态直接返回；不是通过重复配对恢复。
    def connect(self, stop, on_status):
        enrollment = Connector(self.store)
        while not stop.is_set():
            report = enrollment.tick()
            on_status(report)
            if report['state'] == 'connected':
                break
            if report['state'] in ('revoked', 'expired', 'rejected', 'claim_failed'):
                return self.status()
            stop.wait(3)
        if stop.is_set():
            return self.status()
        report = Connector(self.store, runtime_factory=self._runtime).run(stop, on_status)
        with self.store.lock():
            report['runtimeNeedsReview'] = self.store.read().get('runtimeNeedsReview') is True
        return report

    # 在独占运行锁已持有的前提下组装本次 Node/FRP 环境。
    # 先拒绝待复核/旧框架状态，再请求服务端分配的连接参数；只使用当前设备身份。
    # 返回 LiteRuntime 对象；真正的进程启动由后续 tick 驱动。
    def _runtime(self, store):
        with store.lock():
            state = store.read()
        if state.get('runtimeNeedsReview'):
            raise ConnectorError('previous_runtime_requires_review')
        if state.get('gatewayBinding') or state.get('runtimeProfile'):
            raise ConnectorError('legacy_identity_rejected')
        cfg = self.config
        if cfg.get('schema') != 1 or type(cfg.get('port')) is not int or not 1024 <= cfg['port'] <= 65535:
            raise ConnectorError('invalid_configuration')
        # Never adopt an existing listener, including another copy of this app.
        with socket.socket() as check:
            check.bind(('127.0.0.1', cfg['port']))
        client = RelayClient(state['relay'])
        status, body = client.post(f"/connector-api/terminals/{state['terminalId']}/connection", state['deviceToken'], {})
        if status != 200:
            raise ConnectorError('task_channel_unavailable')
        settings = validate_connection(body, state)
        ca = public_asset(store.root, '.pem', settings['caPem'].encode())
        template, environment = launch_material(
            server=settings['server'], server_port=settings['serverPort'], server_name=settings['serverName'],
            ca_file=ca, ca_sha256=settings['caSha256'], terminal_id=state['terminalId'],
            local_port=cfg['port'], remote_port=settings['remotePort'],
            device_token=state['deviceToken'], transport_token=settings['transportToken'])
        frp_config = public_asset(store.root, '.toml', template.encode())
        identity = {'schema': 1, 'token': secrets.token_hex(32), 'instance': secrets.token_hex(16),
                    'controlToken': secrets.token_hex(32), 'quarantine': self.quarantine}
        # Secret stays in protected local state and a one-shot inherited pipe.
        with store.lock():
            current = store.read()
            if current != state:
                raise ConnectorError('identity_changed')
            current['adapterBinding'] = {'port': cfg['port'], 'token': identity['token'], 'instance': identity['instance']}
            store.write(current)
        environment_node = {k:v for k,v in os.environ.items() if k.upper() not in ('NODE_OPTIONS','NODE_PATH')
                            and not k.upper().startswith('CONNECTOR_')}
        pins = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'src').rglob('*.js')}
        pins[str(self.config_file)] = hashlib.sha256(self.config_file.read_bytes()).hexdigest()
        pins.update(helper_pins())
        adapter = OwnedProcess(cfg['node']['path'], [str(ROOT/'src'/'main.js'), str(self.config_file)], str(ROOT),
            cfg['node']['sha256'], LoopbackHealthProbe(cfg['port'],identity['token'],identity['instance']),
            bootstrap=lambda: json.dumps(identity).encode(), environment=environment_node,
            pinned_files=pins, max_restarts=0, process_factory=spawn_guarded)
        transport = transport_process(executable=cfg['frpc']['path'], sha256=cfg['frpc']['sha256'],
            config_path=frp_config, template=template, environment=environment, cwd=str(ROOT), ca_file=ca,
            ca_sha256=settings['caSha256'], probe=tunnel_probe(client,state['terminalId'],state['deviceToken'],identity))
        # Retain original handles if cleanup could not be proved. No adoption.
        self._last_runtime = LiteRuntime(adapter, transport,
            lambda: prepare_disconnect(cfg['port'], identity['controlToken'], identity['instance']))
        return self._last_runtime


if __name__ == '__main__':
    from gui import run_gui
    try:
        run_gui(Controller(sys.argv[1]))
    except Exception:
        # Startup diagnostics are fixed and credential-free.
        import tkinter as tk
        from tkinter import messagebox
        root=tk.Tk();root.withdraw()
        messagebox.showerror('Desktop 手机联调', '启动配置无法核对，未接管任何现用服务。')
        root.destroy()
        sys.exit(1)
