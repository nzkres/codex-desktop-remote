"""Read bounded quota events for one authorized Desktop task; never expose log text."""
import datetime
import hashlib
import json
import math
from pathlib import Path
import re
import sqlite3
import sys
from contextlib import closing

def quota_account_key(home, captured_at):
    """Only the account identifier is selected; no token/email leaves this process.

    Do not attach an old quota observation to a newly changed login file.
    """
    try:
        path=home/'auth.json'
        if path.is_symlink() or (hasattr(path,'is_junction') and path.is_junction()): return None
        with path.open('rb') as stream:
            import os,stat
            info=os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_nlink!=1 or not 0<info.st_size<=131072: return None
            if int(info.st_mtime*1000)>captured_at: return None
            value=json.loads(stream.read(131073))
        tokens=value.get('tokens')
        account=tokens.get('account_id') if isinstance(tokens,dict) else None
        if not isinstance(account,str) or not re.fullmatch(r'[A-Za-z0-9_-]{8,160}',account): return None
        return hashlib.sha256(('desktop-remote-quota-account-v1\n'+account).encode()).hexdigest()
    except (OSError,ValueError,TypeError,AttributeError): return None

def window(value):
    if not isinstance(value,dict): return None
    used=value.get('used_percent');minutes=value.get('window_minutes');reset=value.get('resets_at')
    if isinstance(used,bool) or not isinstance(used,(int,float)) or not math.isfinite(used) or not 0<=used<=100: return None
    if isinstance(minutes,bool) or not isinstance(minutes,int) or not 0<minutes<=525600: return None
    if isinstance(reset,bool) or not isinstance(reset,int) or not 0<reset<32503680000: return None
    return {'usedPercent':used,'windowDurationMins':minutes,'resetsAt':reset}

def read_usage(home,thread_id):
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',thread_id): raise ValueError('invalid_task')
    home=Path(home).resolve(strict=True);database=home/'state_5.sqlite'
    if database.is_symlink(): raise ValueError('invalid_database')
    with closing(sqlite3.connect(database.as_uri()+'?mode=ro',uri=True,timeout=2)) as db:
        db.execute('PRAGMA query_only=ON')
        row=db.execute("SELECT rollout_path FROM threads WHERE id=? AND source='vscode' AND (originator IN ('Codex Desktop','codex_work_desktop') OR originator IS NULL) AND (thread_source IS NULL OR thread_source NOT IN ('guardian_review','subagent'))",(thread_id,)).fetchone()
    if not row or not row[0]: return {'status':'unavailable','limits':[]}
    raw_path=row[0]
    # SQLite stores extended drive paths on Windows. Normalize only the local
    # drive prefix; UNC/device paths remain rejected by the sessions boundary.
    if raw_path.startswith('\\\\?\\') and re.match(r'^[A-Za-z]:[\\/]',raw_path[4:]): raw_path=raw_path[4:]
    path=Path(raw_path);sessions=home/'sessions'
    if path.is_symlink() or not path.resolve(strict=True).is_relative_to(sessions.resolve(strict=True)): raise ValueError('invalid_rollout')
    # Reject every reparse component, including junctions. Do not follow a path out of sessions.
    current=path
    while current!=home:
        if current.is_symlink() or (hasattr(current,'is_junction') and current.is_junction()): raise ValueError('invalid_rollout')
        current=current.parent
    with path.open('rb') as stream:
        first=stream.readline(65537)
        metadata=json.loads(first)
        if metadata.get('type')!='session_meta' or metadata.get('payload',{}).get('id')!=thread_id: raise ValueError('wrong_task')
        stream.seek(0,2);size=stream.tell();start=max(0,size-2*1024*1024);stream.seek(start)
        if start: stream.readline(2*1024*1024)
        lines=stream.read(2*1024*1024).splitlines()
    found={}
    for raw in reversed(lines):
        try: event=json.loads(raw)
        except (ValueError,UnicodeError): continue
        payload=event.get('payload',{})
        if event.get('type')!='event_msg' or not isinstance(payload,dict) or payload.get('type')!='token_count': continue
        rate=payload.get('rate_limits')
        if not isinstance(rate,dict): continue
        limit_id=rate.get('limit_id') or 'codex'
        if not isinstance(limit_id,str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}',limit_id) or limit_id in found: continue
        try: captured=int(datetime.datetime.fromisoformat(event['timestamp'].replace('Z','+00:00')).timestamp()*1000)
        except (ValueError,TypeError,KeyError,OverflowError): continue
        windows=[w for w in (window(rate.get('primary')),window(rate.get('secondary'))) if w]
        if windows:
            found[limit_id]={'id':limit_id,'capturedAt':captured,'windows':windows}
            plan=rate.get('plan_type')
            if isinstance(plan,str) and plan.lower() in ('free','go','plus','pro','team','business','enterprise','edu'):
                found[limit_id]['planType']=plan.lower()
        if len(found)>=8: break
    result={'status':'snapshot' if found else 'unavailable','source':'desktop-task-snapshot','limits':list(found.values())}
    if found:
        key=quota_account_key(home,min(row['capturedAt'] for row in found.values()))
        if key: result['accountKey']=key
    return result

if __name__=='__main__':
    try:
        if len(sys.argv)!=3: raise ValueError('invalid_arguments')
        print(json.dumps(read_usage(sys.argv[1],sys.argv[2]),ensure_ascii=True))
    except Exception:
        print('{"status":"unavailable","limits":[]}')
        sys.exit(1)
