"""Synthetic GUI status and worker contracts; no Tk window or real identity."""

import queue
import sys
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "windows"))
from gui import _Window, status_message


class Value:
    def __init__(self, value=""):
        self.value = value

    def get(self):
        return self.value

    def set(self, value):
        self.value = value


class Button:
    def __init__(self):
        self.options = {}

    def configure(self, **options):
        self.options.update(options)


class Root:
    def after(self, *_args):
        pass


class SyntheticController:
    def __init__(self, report, pair_result=None, pair_error=None):
        self.report = dict(report)
        self.pair_result = pair_result
        self.pair_error = pair_error
        self.calls = []

    def status(self):
        self.calls.append("status")
        return dict(self.report)

    def pair(self, relay, code, name):
        self.calls.append(("pair", relay, code, name))
        if self.pair_error is not None:
            raise self.pair_error
        result = dict(self.pair_result)
        self.report = dict(result)
        return result


def worker_and_drain(controller, command):
    """Exercise the real worker/event drain without creating a Tk interpreter."""
    window = _Window.__new__(_Window)
    window.root = Root()
    window.controller = controller
    window.commands = queue.Queue()
    window.events = queue.Queue()
    window.stop_event = threading.Event()
    window.busy = True
    window.connecting = False
    window.closing = False
    window.update_blocked = False
    window.relay = Value()
    window.status_text = Value()
    window._buttons = lambda: None
    window.commands.put(command)
    window.commands.put(("close",))
    worker = threading.Thread(target=window._work)
    worker.start()
    worker.join(3)
    if worker.is_alive():
        raise AssertionError("synthetic GUI worker did not stop")
    window._drain()
    return window.status_text.get()


class GuiStatusTests(unittest.TestCase):
    def test_live_reports_retry_channel_and_reconnect_button(self):
        window = _Window.__new__(_Window)
        window.root = Root()
        window.controller = SyntheticController({"state": "connected"})
        window.commands = queue.Queue()
        window.events = queue.Queue()
        window.stop_event = threading.Event()
        window.busy = True
        window.connecting = True
        window.has_connected = True
        window.closing = False
        window.update_blocked = False
        window.relay = Value()
        window.status_text = Value()
        window.pair_button = Button()
        window.connect_button = Button()
        window.disconnect_button = Button()

        window.events.put(("status", {"state": "reconnecting", "lastError": "connection_unavailable"}))
        window._drain()
        self.assertIn("正在自动重试", window.status_text.get())
        self.assertIn("无需重新配对", window.status_text.get())
        self.assertEqual(window.connect_button.options["state"], "disabled")

        window.events.put(("status", {"state": "connected", "dataChannel": "unverified"}))
        window._drain()
        self.assertIn("服务器登记在线", window.status_text.get())
        self.assertIn("尚未实时检查", window.status_text.get())
        self.assertNotIn("失败", window.status_text.get())

        window.events.put(("status", {"state": "connected", "dataChannel": "probe_verified"}))
        window._drain()
        self.assertIn("手机传输通道已连通", window.status_text.get())
        self.assertIn("手机任务待验证", window.status_text.get())

        window.disconnect()
        window.events.put(("disconnected", {"state": "connected", "runtimeNeedsReview": False}))
        window._drain()
        self.assertIn("已安全断开", window.status_text.get())
        self.assertEqual(window.connect_button.options["text"], "再次连接")
        window.connect()
        window.connect()
        self.assertEqual(window.commands.qsize(), 1)
        self.assertEqual(window.commands.get(), ("connect",))
        self.assertFalse(window.stop_event.is_set())
        self.assertEqual(window.connect_button.options["state"], "disabled")

    def test_terminal_report_does_not_claim_safe_user_disconnect(self):
        window = _Window.__new__(_Window)
        window.root = Root()
        window.events = queue.Queue()
        window.stop_event = threading.Event()
        window.busy = True
        window.connecting = True
        window.has_connected = True
        window.closing = False
        window.relay = Value()
        window.status_text = Value()
        window._buttons = lambda: None
        window.events.put(("disconnected", {"state": "revoked", "runtimeNeedsReview": False}))
        window._drain()
        self.assertIn("设备已移除", window.status_text.get())
        self.assertNotIn("已安全断开", window.status_text.get())

    def test_pending_connection_error_is_actionable_and_survives_reopen(self):
        report = {"state": "claim_pending", "lastError": "connection_unavailable",
                  "relay": "https://relay.example.test"}
        controller = SyntheticController({"state": "not_paired"}, pair_result=report)
        paired = worker_and_drain(controller, ("pair", report["relay"], "012345ABCDEF", "fixture"))
        reopened = worker_and_drain(controller, ("status",))
        self.assertEqual(paired, reopened)
        for phrase in ("未确认", "服务器", "端口", "网络", "证书", "保留", "勿重复配对"):
            self.assertIn(phrase, paired)
        self.assertNotEqual(paired, "正在提交配对")
        self.assertNotIn("012345ABCDEF", paired)

        controller.report = {"state": "awaiting_confirmation", "lastError": None,
                             "relay": report["relay"]}
        accepted = worker_and_drain(controller, ("status",))
        self.assertIn("手机", accepted)
        self.assertIn("确认", accepted)
        self.assertNotIn("网络", accepted)

    def test_status_priority_and_unknown_error_are_bounded(self):
        error = "secret-token-012345ABCDEF"
        guarded = status_message({"state": "claim_pending", "lastError": "connection_unavailable",
                                  "runtimeNeedsReview": True})
        self.assertIn("安全退出", guarded)
        self.assertNotIn("端口", guarded)
        connecting = status_message({"state": "connected", "dataChannel": "probe_verified",
                                     "runtimeNeedsReview": True}, connecting=True)
        self.assertNotIn("安全退出", connecting)
        self.assertIn("手机任务待验证", connecting)
        connecting_pending = status_message({"state": "claim_pending",
                                             "lastError": "connection_unavailable"}, connecting=True)
        self.assertIn("继续查询原申请", connecting_pending)
        self.assertNotIn("点击“连接”", connecting_pending)
        pending_without_error = status_message({"state": "claim_pending", "lastError": None})
        self.assertIn("待确认", pending_without_error)
        self.assertNotIn("正在提交", pending_without_error)

        for report in ({"state": "claim_pending", "lastError": error},
                       {"state": "awaiting_confirmation", "lastError": error},
                       {"state": "connected", "lastError": error}):
            message = status_message(report)
            self.assertNotIn(error, message)
            self.assertNotIn("手机确认配对", message)

        awaiting = status_message({"state": "awaiting_confirmation", "lastError": None})
        self.assertIn("手机", awaiting)
        self.assertIn("确认", awaiting)
        failed_awaiting = status_message({"state": "awaiting_confirmation",
                                          "lastError": "connection_unavailable"}, paired=True)
        self.assertNotIn("等待手机确认配对", failed_awaiting)
        self.assertIn("状态未确认", failed_awaiting)
        unverified = status_message({"state": "connected", "dataChannel": "unverified"})
        self.assertIn("尚未实时检查", unverified)
        self.assertNotIn("失败", unverified)
        probed = status_message({"state": "connected", "dataChannel": "probe_verified"})
        self.assertIn("手机任务待验证", probed)
        self.assertNotIn("任务完成", probed)
        self.assertEqual(status_message(None), "状态待确认")
        self.assertEqual(status_message(["secret-token"]), "状态待确认")
        for channel in ("secret-token", {"secret": "token"}):
            message = status_message({"state": "connected", "dataChannel": channel})
            self.assertIn("转发通道待确认", message)
            self.assertNotIn("secret", message)
            self.assertNotIn("token", message)

    def test_diagnostics_deduplicate_bound_and_preserve_history_on_recovery(self):
        window = _Window.__new__(_Window)
        window.error_text = Value()
        for _ in range(20):
            window._record_errors('连接', ['relay_timeout'])
        self.assertEqual(len(window.error_history), 1)
        window._record_errors('更新操作', ['invalid_release_signature'])
        window._record_errors('连接', [])
        self.assertIn('invalid_release_signature', window.error_text.get())
        self.assertNotIn('relay_timeout', window.error_text.get())
        self.assertEqual(window.error_history[0]['code'], 'relay_timeout')
        window._record_errors('连接', ['Bearer secret-value'])
        self.assertNotIn('secret-value', str(window.error_history))
        for _ in range(110):
            window._record_errors('连接', [])
            window._record_errors('连接', ['relay_timeout'])
        self.assertEqual(len(window.error_history), 100)

    def test_certificate_status_is_actionable_not_a_pairing_failure(self):
        message = status_message({'state':'reconnecting','lastError':'relay_tls_certificate_expired'}, connecting=True)
        for phrase in ('relay_tls_certificate_expired', '重载', '无需重新配对', '正在自动重试'):
            self.assertIn(phrase, message)
        snapshot = status_message({'state':'reconnecting','lastError':'relay_tls_certificate_expired'})
        self.assertIn('最近保存', snapshot)
        self.assertNotIn('正在自动重试', snapshot)

    def test_worker_exception_codes_are_fixed_and_do_not_echo_credential(self):
        for code in ("invalid_relay_url", "https_required", "invalid_device_name",
                     "invalid_relay_response", "relay_response_too_large"):
            with self.subTest(code=code):
                controller = SyntheticController({"state": "not_paired"}, pair_error=ValueError(code))
                message = worker_and_drain(controller, ("pair", "https://relay.example.test",
                                                        "012345ABCDEF", "fixture"))
                self.assertIn(code, message)
                self.assertNotIn("012345ABCDEF", message)
                self.assertNotEqual(message, "操作失败。请检查连接器状态后重试。")

        secret = "bearer fixture-private-value"
        controller = SyntheticController({"state": "not_paired"}, pair_error=RuntimeError(secret))
        message = worker_and_drain(controller, ("pair", "https://relay.example.test",
                                                "012345ABCDEF", "fixture"))
        self.assertNotIn(secret, message)
        self.assertNotIn("012345ABCDEF", message)
        self.assertIn("勿重复配对", message)
        self.assertIn("删除身份", message)


if __name__ == "__main__":
    unittest.main()
