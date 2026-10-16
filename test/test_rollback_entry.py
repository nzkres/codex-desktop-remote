"""Binding and admission gates for the distinct restoration host."""
import hashlib
from pathlib import Path
import sys
import unittest
from tempfile import TemporaryDirectory
from unittest.mock import Mock, patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'windows'))
import rollback_entry as entry
from updates.storage import atomic
from updates.security import UpdateError


class RestoreTests(unittest.TestCase):
    def test_maintenance_host_requires_exact_signed_archive_binding(self):
        with TemporaryDirectory() as folder:
            home=Path(folder);old=home/'old';old.mkdir();new=home/'candidate';new.mkdir()
            (home/'state').mkdir();(home/'staging'/'one').mkdir(parents=True)
            archive=home/'staging/one/package.zip';manifest=b'{"synthetic":true}'
            with zipfile.ZipFile(archive,'w') as bundle:bundle.writestr('package.json',manifest)
            pointer={'version':'0.0.1','path':'versions/old'}
            candidate={'version':'0.0.2','manifestSha256':hashlib.sha256(manifest).hexdigest()}
            atomic(home/'state/transaction.json',{'phase':'rollback_restoring','old':pointer,'candidate':candidate})
            atomic(home/'state/staged.json',{'old':pointer,'candidate':candidate,'envelope':{'synthetic':True}})
            atomic(home/'state/ledger.json',{});atomic(home/'trust.json',{})
            release={'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}
            config={'component':'windows','platform':'windows-x64','compatibility':'synthetic'}
            with patch.multiple(entry, ROOT=new, installed=Mock(return_value=(config,pointer,old)),
                                verify=Mock(return_value=(release,{})),slot=Mock(return_value=(new,{}))):
                self.assertEqual(entry.restore_context(home),(pointer,old))
                # A verified manifest alone cannot bind a host to the archive.
                archive.write_bytes(b'replaced')
                with self.assertRaisesRegex(UpdateError,'staged_package_changed'):
                    entry.restore_context(home)
                atomic(home/'state/transaction.json',{'phase':'needs_review','old':pointer,'candidate':candidate})
                with self.assertRaisesRegex(UpdateError,'rollback_context_unverified'):
                    entry.restore_context(home)

    def test_restoration_waits_for_durable_rollback_and_never_connects_unknown_state(self):
        window=Mock();gui=Mock(closing=False,update_blocked=True,update_phase='rollback_restoring')
        entry.connect_restored(window,gui);gui.connect.assert_not_called()
        callback=window.after.call_args.args[1]
        gui.update_phase='rolled_back';gui.update_blocked=False;callback();gui.connect.assert_called_once()
        for phase in ('needs_review','switched','unreadable'):
            gui=Mock(closing=False,update_blocked=True,update_phase=phase);window=Mock()
            entry.connect_restored(window,gui);gui.connect.assert_not_called();window.after.assert_not_called()
