import importlib.util,json,sqlite3,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('desktop_usage',Path(__file__).resolve().parents[1]/'scripts/desktop_usage.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
ID='11111111-1111-4111-8111-111111111111'

class DesktopUsageTests(unittest.TestCase):
    def fixture(self,root):
        sessions=root/'sessions';sessions.mkdir();path=sessions/'fixture.jsonl'
        path.write_text(json.dumps({'type':'session_meta','payload':{'id':ID}})+'\n',encoding='utf8')
        db=sqlite3.connect(root/'state_5.sqlite')
        db.execute('CREATE TABLE threads(id,rollout_path,source,originator,thread_source)')
        db.execute('INSERT INTO threads VALUES (?,?,?,?,?)',(ID,str(path),'vscode','Codex Desktop',None));db.commit();db.close()
        return path
    def append(self,path,used=80,limit='codex',plan=None):
        with path.open('a',encoding='utf8') as f:f.write(json.dumps({'type':'event_msg','timestamp':'2026-10-15T00:00:00Z','payload':{'type':'token_count','rate_limits':{'limit_id':limit,'plan_type':plan,'primary':{'used_percent':used,'window_minutes':10080,'resets_at':1792474764},'credits':{'balance':'PRIVATE_VALUE'}}}})+'\n')
    def test_plan_is_whitelisted_from_same_event_and_never_borrowed_from_older_snapshot(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);path=self.fixture(root)
            for plan in ('plus','PRO','PRIVATE_TOKEN',None,{'token':'PRIVATE_VALUE'}):
                self.append(path,plan=plan)
                row=module.read_usage(root,ID)['limits'][0]
                self.assertEqual(row.get('planType'),plan.lower() if isinstance(plan,str) and plan.lower() in ('plus','pro') else None)
                self.assertNotIn('PRIVATE',json.dumps(row))
    def test_recent_events_only_public_fields_multiple_buckets_and_wal(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);path=self.fixture(root)
            self.append(path,20);self.append(path,60,'review');self.append(path,89)
            value=module.read_usage(root,ID)
            self.assertEqual(value['status'],'snapshot');self.assertEqual(len(value['limits']),2)
            self.assertEqual(value['limits'][0]['windows'][0]['usedPercent'],89)
            self.assertNotIn('PRIVATE_VALUE',json.dumps(value));self.assertNotIn('rollout',json.dumps(value))
    def test_wrong_task_and_outside_path_rejected_and_missing_not_created(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);path=self.fixture(root);self.append(path)
            path.write_text(json.dumps({'type':'session_meta','payload':{'id':'another'}})+'\n',encoding='utf8')
            with self.assertRaises(ValueError):module.read_usage(root,ID)
            db=sqlite3.connect(root/'state_5.sqlite');db.execute('UPDATE threads SET rollout_path=?',(str(root/'outside.jsonl'),));db.commit();db.close()
            (root/'outside.jsonl').write_text('{}',encoding='utf8')
            with self.assertRaises(ValueError):module.read_usage(root,ID)
            self.assertEqual(module.read_usage(root,'22222222-2222-4222-8222-222222222222')['status'],'unavailable')
    def test_bad_values_and_large_incomplete_lines_never_become_fake_allowance(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);path=self.fixture(root)
            self.append(path,-5);self.assertEqual(module.read_usage(root,ID)['limits'],[])
            with path.open('a',encoding='utf8') as f:f.write('x'*(2*1024*1024+10)+'\n')
            self.append(path,99);value=module.read_usage(root,ID)
            self.assertEqual(value['limits'][0]['windows'][0]['usedPercent'],99)
    @unittest.skipUnless(__import__('os').name=='nt','Windows extended drive paths')
    def test_extended_windows_path_stays_inside_the_same_sessions_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);path=self.fixture(root);self.append(path,89)
            db=sqlite3.connect(root/'state_5.sqlite');db.execute('UPDATE threads SET rollout_path=?',('\\\\?\\'+str(path),));db.commit();db.close()
            self.assertEqual(module.read_usage(root,ID)['limits'][0]['windows'][0]['usedPercent'],89)

if __name__=='__main__':unittest.main()
