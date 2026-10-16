import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('desktop_catalog', Path(__file__).resolve().parents[1] / 'scripts/desktop_catalog.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CatalogTests(unittest.TestCase):
    def test_live_wal_new_conversation_and_filtering_without_private_content(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'state_5.sqlite'
            db = sqlite3.connect(p)
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('CREATE TABLE threads(id TEXT, cwd TEXT, title TEXT, updated_at INTEGER, archived INTEGER, source TEXT, originator TEXT, thread_source TEXT, first_user_message TEXT)')
            def add(id, source='vscode', originator='Codex Desktop', kind='user'):
                db.execute('INSERT INTO threads VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)',
                           (id, directory, id, source, originator, kind, 'PRIVATE_CONTENT'))
                db.commit()
            add('first')
            self.assertEqual(len(module.read_catalog(directory)['tasks']), 1)
            add('second', originator='codex_work_desktop')
            add('agent', source='{"subagent":{}}', kind='subagent')
            add('guardian', kind='guardian_review')
            add('other-client', originator='Other editor')
            value = module.read_catalog(directory)
            self.assertEqual({r['threadId'] for r in value['tasks']}, {'first', 'second'})
            self.assertNotIn('PRIVATE_CONTENT', json.dumps(value))
            self.assertEqual(db.execute('SELECT count(*) FROM threads').fetchone()[0], 5)
            db.close()
    def test_missing_database_never_created(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError): module.read_catalog(directory)
            self.assertFalse((Path(directory) / 'state_5.sqlite').exists())
    def test_created_originator_requires_exact_creation_receipt_id(self):
        with tempfile.TemporaryDirectory() as directory:
            db=sqlite3.connect(Path(directory)/'state_5.sqlite')
            db.execute('CREATE TABLE threads(id TEXT,cwd TEXT,title TEXT,updated_at INTEGER,archived INTEGER,source TEXT,originator TEXT,thread_source TEXT)')
            first='00000000-1111-2222-3333-444444444444'
            other='00000000-1111-2222-3333-555555555555'
            for i in (first,other):
                db.execute('INSERT INTO threads VALUES (?,?,?,1,0,?,?,NULL)',(i,directory,'Empty','vscode','desktop_remote_lite_create'))
            db.commit()
            self.assertEqual(module.read_catalog(directory)['tasks'],[])
            self.assertEqual([x['threadId'] for x in module.read_catalog(directory,[first])['tasks']],[first])
            with self.assertRaises(ValueError):module.read_catalog(directory,["invalid') OR 1=1"])
            db.close()
    def test_unknown_schema_is_failure_not_empty_list(self):
        with tempfile.TemporaryDirectory() as directory:
            db=sqlite3.connect(Path(directory)/'state_5.sqlite');db.close()
            with self.assertRaises(sqlite3.Error):module.read_catalog(directory)

    def test_admission_binds_existing_id_directory_and_provenance_without_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            db=sqlite3.connect(Path(directory)/'state_5.sqlite')
            db.execute('CREATE TABLE threads(id TEXT,cwd TEXT,title TEXT,updated_at INTEGER,archived INTEGER,source TEXT,originator TEXT,thread_source TEXT)')
            first='00000000-1111-2222-3333-444444444444'
            other='00000000-1111-2222-3333-555555555555'
            origin='desktop_remote_create_probe'
            for id in (first,other):
                db.execute('INSERT INTO threads VALUES (?,?,?,1,0,?,?,?)',(id,directory,'','vscode',origin,origin))
            db.commit()
            approved={'threadId':first,'projectPath':directory,'title':'Existing empty conversation',
                      'originator':origin,'threadSource':origin,'allowEmptyHistory':True}
            self.assertEqual(module.read_catalog(directory)['tasks'],[])
            rows=module.read_catalog(directory,linked=[approved])['tasks']
            self.assertEqual(len(rows),1)
            self.assertEqual(rows[0]['threadId'],first)
            self.assertEqual(rows[0]['title'],approved['title'])
            self.assertTrue(rows[0]['linkedEmptyHistory'])
            self.assertEqual(db.execute('SELECT count(*) FROM threads').fetchone()[0],2)
            self.assertEqual(db.execute('SELECT title FROM threads WHERE id=?',(first,)).fetchone()[0],'')
            for field,value in [('originator','other'),('threadSource','other'),('projectPath',str(Path(directory).parent))]:
                with self.subTest(field=field):
                    self.assertEqual(module.read_catalog(directory,linked=[{**approved,field:value}])['tasks'],[])
            db.execute('UPDATE threads SET source=? WHERE id=?',('cli',first));db.commit()
            self.assertEqual(module.read_catalog(directory,linked=[approved])['tasks'],[])
            db.execute('UPDATE threads SET source=?,thread_source=? WHERE id=?',('vscode','subagent',first));db.commit()
            self.assertEqual(module.read_catalog(directory,linked=[approved])['tasks'],[])
            with self.assertRaises(ValueError):module.read_catalog(directory,linked=[approved,approved])
            with self.assertRaises(ValueError):module.read_catalog(directory,linked=[{**approved,'allowEmptyHistory':'true'}])
            with self.assertRaises(ValueError):module.read_catalog(directory,linked=[{**approved,'threadId':"invalid') OR 1=1"}])
            db.close()

if __name__ == '__main__': unittest.main()
