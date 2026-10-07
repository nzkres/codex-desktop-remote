"""Local maintenance console; no remote shell, arbitrary URL or process adoption."""
import argparse
import json
from pathlib import Path
import socket
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from updates.engine import check, stage, installed, Transaction
from updates.storage import no_links, read
from updates.security import UpdateError


def relay_command(home, operation):
    from updates.relay_host import request
    return request(home, operation)


def main(home, operation):
    config, current, _ = installed(home)
    if operation == 'recover':
        return Transaction(home,None).recover()
    if config['component']=='relay':
        return relay_command(home,operation)
    if operation=='check': return check(home)
    if operation=='stage': return stage(home)
    if operation=='status':
        journal=Path(home)/'state/transaction.json'
        return {'version':current['version'], 'transaction':read(journal) if journal.exists() else None}
    raise UpdateError('use_windows_install_button')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',required=True,type=Path)
    parser.add_argument('operation',choices=['check','stage','apply','status','stop','recover'])
    args=parser.parse_args()
    try:
        print(json.dumps(main(no_links(args.home),args.operation),ensure_ascii=False))
    except Exception as error:
        print(json.dumps({'success':False,'error':error.code if isinstance(error,UpdateError) else 'update_operation_failed'}))
        sys.exit(1)
