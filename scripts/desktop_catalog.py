"""Read local Desktop conversation metadata only, using the live SQLite WAL view.

No prompts, rollouts, credentials, permission records or database writes. This
index discovers IDs; only a fresh native Desktop snapshot authorizes operation.
"""
import json
from contextlib import closing
import sqlite3
import sys
from pathlib import Path


def read_catalog(home):
    database = Path(home).resolve(strict=True) / 'state_5.sqlite'
    if database.is_symlink() or not database.is_file():
        raise ValueError('catalog_unavailable')
    # Never immutable=1: it hides new conversations still in the WAL.
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True, timeout=2)) as db:
        db.execute('PRAGMA query_only=ON')
        rows = db.execute("""SELECT id, cwd, title, updated_at, archived
            FROM threads WHERE source = 'vscode'
            AND (originator IN ('Codex Desktop', 'codex_work_desktop') OR originator IS NULL)
            AND (thread_source IS NULL OR thread_source NOT IN ('guardian_review', 'subagent'))
            ORDER BY updated_at DESC, id LIMIT 10001""").fetchall()
    if len(rows) > 10000:
        raise ValueError('catalog_too_large')
    return {'schema': 1, 'tasks': [dict(zip(
        ('threadId', 'projectPath', 'title', 'updatedAt', 'archived'), row)) for row in rows]}


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('catalog_unavailable')
        print(json.dumps(read_catalog(sys.argv[1]), ensure_ascii=True))
    except Exception:
        print('{"error":"catalog_unavailable"}')
        sys.exit(1)
