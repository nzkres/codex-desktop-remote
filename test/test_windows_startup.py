"""Synthetic opening/OTA activation lifecycle; no identity or network is used."""
from contextlib import nullcontext
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch
from tempfile import TemporaryDirectory
import json

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows'))
import ota_entry
from updates.security import UpdateError


class StartupTests(unittest.TestCase):
    def run_window(self, quarantine=False, activate=True, eligible=True):
        controller = Mock(quarantine=quarantine)
        constructed = []
        def policy():
            self.assertFalse(constructed, 'identity policy must run before the GUI status worker')
            return eligible and not controller.quarantine
        controller.auto_connect_on_open.side_effect = policy
        window = Mock()
        scheduled = []
        window.after.side_effect = lambda delay, callback: scheduled.append(callback)
        window.mainloop.side_effect = lambda: [callback() for callback in scheduled]
        gui = Mock(update_blocked=False, closing=False)
        def build(*args):
            constructed.append(True)
            return gui
        def health(actual):
            self.assertTrue(actual.quarantine)
            gui.connect.assert_not_called()
            self.assertEqual(scheduled, [])
        stdin = Mock(buffer=io.BytesIO(b'{"schema":1,"op":"activate"}\n' if activate else b'{}\n'))
        with patch.multiple(ota_entry, installed=Mock(return_value=({'component':'windows'}, {}, ota_entry.ROOT)),
                            same_running_slot=Mock(return_value=True), ensure_launchable=Mock(),
                            controller_for=Mock(return_value=controller), enable_dpi=Mock(),
                            lock=Mock(return_value=nullcontext()), health=Mock(side_effect=health),
                            _Window=Mock(side_effect=build)), patch('tkinter.Tk', return_value=window), \
                patch.object(sys, 'stdin', stdin), patch('builtins.print'):
            if quarantine and not activate:
                with self.assertRaises(UpdateError):
                    ota_entry.run(Path('synthetic-home'), quarantine=True)
                self.assertEqual(scheduled, [])
                gui.connect.assert_not_called()
            else:
                ota_entry.run(Path('synthetic-home'), quarantine=quarantine)
                self.assertEqual(gui.connect.call_count, int(eligible))
                self.assertEqual(len(scheduled), int(eligible))

    def test_normal_open_connects_existing_identity_once(self):
        self.run_window()

    def test_update_connects_only_after_activation(self):
        self.run_window(quarantine=True)

    def test_unapproved_candidate_never_connects(self):
        self.run_window(quarantine=True, activate=False)

    def test_unpaired_or_guarded_open_stays_manual(self):
        self.run_window(eligible=False)
        self.run_window(quarantine=True, eligible=False)

    def test_health_requires_transport_normal_exit_and_unchanged_data(self):
        c=Mock();c.store.lock.return_value=nullcontext();c.store.read.return_value={'fixture':True}
        def connect(stop, report):
            report({'state':'connected','dataChannel':'probe_verified'})
            self.assertTrue(stop.is_set())
            return {'state':'connected'}
        c.connect.side_effect=connect
        with patch.object(ota_entry,'snapshot',return_value={'synthetic':'unchanged'}), patch.object(ota_entry,'probe') as probe:
            ota_entry.health(c)
            self.assertEqual(probe.call_count,2)
        with patch.object(ota_entry,'snapshot',side_effect=[{'before':1},{'after':2}]), patch.object(ota_entry,'probe'):
            with self.assertRaisesRegex(UpdateError,'preservation_changed'):
                ota_entry.health(c)

    def test_busy_task_is_refused_before_any_candidate_connection(self):
        c=Mock()
        with patch.object(ota_entry,'snapshot',return_value={}), patch.object(ota_entry,'probe',side_effect=UpdateError('active_or_unverified_desktop_task')):
            with self.assertRaisesRegex(UpdateError,'active_or_unverified_desktop_task'):
                ota_entry.health(c)
        c.connect.assert_not_called()

    def test_transport_failure_reason_is_preserved_without_raw_text(self):
        c=Mock();c.store.lock.return_value=nullcontext();c.store.read.return_value={'fixture':True}
        from controller import ConnectorError
        for supplied,expected in [('previous_runtime_requires_review','previous_runtime_requires_review'),
                                  ('https://synthetic-secret.example/token','operation_failed')]:
            c.connect.side_effect=ConnectorError(supplied)
            with patch.object(ota_entry,'snapshot',return_value={}), patch.object(ota_entry,'probe'):
                with self.assertRaisesRegex(UpdateError,expected):
                    ota_entry.health(c)

    def test_old_helper_failure_keeps_bounded_local_diagnostic(self):
        with TemporaryDirectory() as folder:
            home=Path(folder);c=Mock();window=Mock()
            with patch.multiple(ota_entry, installed=Mock(return_value=({'component':'windows'},{},ota_entry.ROOT)),
                                same_running_slot=Mock(return_value=True),controller_for=Mock(return_value=c),
                                enable_dpi=Mock(),health=Mock(side_effect=UpdateError('active_or_unverified_desktop_task'))), \
                    patch('tkinter.Tk',return_value=window):
                with self.assertRaisesRegex(UpdateError,'active_or_unverified_desktop_task'):
                    ota_entry.run(home,quarantine=True)
            record=json.loads((home/'state/candidate-health.json').read_bytes())
            self.assertEqual(set(record),{'schema','version','ready','code'})
            self.assertIs(record['ready'],False)
            self.assertEqual(record['code'],'active_or_unverified_desktop_task')
            window.mainloop.assert_not_called()

    def test_connect_waits_for_helper_commit_then_runs_once(self):
        window=Mock();gui=Mock(closing=False,update_blocked=True,update_phase='validated')
        ota_entry.connect_after_commit(window,gui)
        gui.connect.assert_not_called()
        callback=window.after.call_args.args[1]
        gui.update_blocked=False;gui.update_phase='committed'
        callback()
        gui.connect.assert_called_once()

    def test_unknown_activation_or_closing_never_connects(self):
        for phase in ('needs_review','unreadable',None):
            window=Mock();gui=Mock(closing=False,update_blocked=True,update_phase=phase)
            ota_entry.connect_after_commit(window,gui)
            gui.connect.assert_not_called();window.after.assert_not_called()
        window=Mock();gui=Mock(closing=True,update_blocked=False)
        ota_entry.connect_after_commit(window,gui)
        gui.connect.assert_not_called()
