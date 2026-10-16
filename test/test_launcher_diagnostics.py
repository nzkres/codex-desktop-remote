import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from updates.launch import describe_problem


class LauncherDiagnostics(unittest.TestCase):
    def test_running_update_is_info_but_stale_phase_requires_review(self):
        with TemporaryDirectory() as folder:
            home=Path(folder);(home/'state').mkdir()
            (home/'state/transaction.json').write_text(json.dumps({'phase':'rollback_restoring'}))
            with patch('updates.launch.updater_active',return_value=True):
                title,message,kind=describe_problem(home,ValueError('unfinished_update_requires_review'))
                self.assertEqual(kind,'info');self.assertIn('恢复原版本',message)
            with patch('updates.launch.updater_active',return_value=False):
                title,message,kind=describe_problem(home,ValueError('unfinished_update_requires_review'))
                self.assertEqual(title,'上次更新未完成');self.assertEqual(kind,'error')

    def test_candidate_and_rollback_reasons_are_distinct_without_private_text(self):
        with TemporaryDirectory() as folder:
            home=Path(folder);(home/'state').mkdir()
            (home/'state/transaction.json').write_text(json.dumps({'phase':'needs_review',
                'cause':'candidate_transport_unverified','rollbackCause':'permissions_unverified',
                'error':'https://secret.example/token'}))
            title,message,kind=describe_problem(home,ValueError('unfinished_update_requires_review'))
            self.assertIn('升级原因',message);self.assertIn('回退原因',message)
            self.assertNotIn('secret',message);self.assertNotIn('版本文件校验未通过',title)

    def test_missing_access_json_and_integrity_failures_are_not_conflated(self):
        cases=[(FileNotFoundError(),'安装文件或记录缺失'),(PermissionError(),'安装目录访问被拒绝'),
               (json.JSONDecodeError('private','x',0),'安装记录格式有误'),
               (ValueError('installed_version_changed'),'版本文件校验未通过')]
        for error,expected in cases:
            with self.subTest(expected=expected):self.assertEqual(describe_problem(Path('unused'),error)[0],expected)
        self.assertNotIn('private',describe_problem(Path('unused'),RuntimeError('private'))[1])
