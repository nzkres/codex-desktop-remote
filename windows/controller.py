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


class LiteRuntime:
    """These are disposable adapter/tunnel children; Desktop owns every turn."""
    def __init__(self, adapter, transport, drain=lambda: False):
        self.adapter, self.transport = adapter, transport
        self.allowed = False
        self.drain = drain
        self.disconnected = False
        self.clean = False
        self.faulted = False

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

    def resume_existing(self, state):
        return False  # A crash stays reviewable. No flag deletion/adoption shortcut.

    def quiescent(self):
        return self.clean


class Controller:
    def __init__(self, config_file):
        self.config_file = Path(config_file).resolve()
        no_links(self.config_file)
        self.config = json.loads(self.config_file.read_text(encoding='utf-8'))
        # Only a new application-local identity. Never accept a legacy state path.
        self.store = CredentialStore(ROOT / 'data' / 'identity')

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

    def pair(self, relay, code, name):
        return Connector(self.store).begin(relay, code, name)

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
                    'controlToken': secrets.token_hex(32)}
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
