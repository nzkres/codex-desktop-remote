"""Read local Desktop conversation metadata only, using the live SQLite WAL view.

No prompts, rollouts, credentials, permission records or database writes. This
index discovers IDs; only a fresh native Desktop snapshot authorizes operation.
"""
import json
import os
from contextlib import closing
import sqlite3
import sys
from pathlib import Path


def read_catalog(home, created_ids=(), linked=()):
    import re
    if len(created_ids)>1000 or any(not isinstance(i,str) or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',i) for i in created_ids):
        raise ValueError('catalog_unavailable')
    fields={'threadId','projectPath','title','originator','threadSource','allowEmptyHistory'}
    if not isinstance(linked,(list,tuple)) or len(linked)>100:
        raise ValueError('catalog_unavailable')
    admissions={}
    for item in linked:
        if (not isinstance(item,dict) or set(item)!=fields
                or any(not isinstance(item[k],str) or not item[k].strip() for k in fields-{'allowEmptyHistory'})
                or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',item['threadId'])
                or item['threadId'] in admissions or not Path(item['projectPath']).is_absolute()
                or not isinstance(item['allowEmptyHistory'],bool)
                or len(item['title'])>100 or len(item['originator'])>100 or len(item['threadSource'])>100
                or item['threadSource'] in ('guardian_review','subagent')):
            raise ValueError('catalog_unavailable')
        admissions[item['threadId']]=item
    database = Path(home).resolve(strict=True) / 'state_5.sqlite'
    if database.is_symlink() or not database.is_file():
        raise ValueError('catalog_unavailable')
    # Never immutable=1: it hides new conversations still in the WAL.
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True, timeout=2)) as db:
        db.execute('PRAGMA query_only=ON')
        rows = db.execute("""SELECT id, cwd, title, updated_at, archived, originator, thread_source
            FROM threads WHERE source = 'vscode'
            AND (originator IN ('Codex Desktop', 'codex_work_desktop') OR originator IS NULL
                 OR (originator='desktop_remote_lite_create' AND id IN (SELECT value FROM json_each(?)))
                 OR id IN (SELECT value FROM json_each(?)))
            AND (thread_source IS NULL OR thread_source NOT IN ('guardian_review', 'subagent'))
            ORDER BY updated_at DESC, id LIMIT 10001""",(json.dumps(list(created_ids)),json.dumps(list(admissions)))).fetchall()
    if len(rows) > 10000:
        raise ValueError('catalog_too_large')
    tasks=[]
    for row in rows:
        item=dict(zip(('threadId','projectPath','title','updatedAt','archived'),row[:5]))
        approved=admissions.get(item['threadId'])
        if approved:
            # A local operator admission binds the existing identity, directory
            # and exact provenance. It is not a fabricated creation receipt.
            if row[5:]!=(approved['originator'],approved['threadSource']):
                continue
            try:
                if os.path.normcase(str(Path(item['projectPath']).resolve(strict=True)))!=os.path.normcase(str(Path(approved['projectPath']).resolve(strict=True))):
                    continue
            except OSError:
                continue
            item['linkedEmptyHistory']=approved['allowEmptyHistory']
            if not item['title'] or not item['title'].strip():
                item['title']=approved['title']
        tasks.append(item)
    return {'schema':1,'tasks':tasks}


if __name__ == '__main__':
    try:
        if len(sys.argv) not in (2,3,4):
            raise ValueError('catalog_unavailable')
        print(json.dumps(read_catalog(sys.argv[1],json.loads(sys.argv[2]) if len(sys.argv)>=3 else (),
                                      json.loads(sys.argv[3]) if len(sys.argv)==4 else ()), ensure_ascii=True))
    except Exception:
        print('{"error":"catalog_unavailable"}')
        sys.exit(1)
