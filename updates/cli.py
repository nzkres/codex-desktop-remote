# 更新命令行入口：把管理员命令分派到检查、暂存、应用或恢复检查。
# 这是维护/自动化接口；用户日常 Windows 更新入口仍是窗口。

"""Local maintenance console; no remote shell, arbitrary URL or process adoption."""
import argparse
import json
from pathlib import Path
import socket
import sys

ROOT=Path(__file__).resolve().parents[1]
# 本地维护命令只导入当前源码树中的更新实现。
sys.path.insert(0,str(ROOT))
from updates.engine import check, stage, installed, Transaction
from updates.storage import no_links, read
from updates.security import UpdateError


def relay_command(home, operation):
    # Relay 的操作交给已运行宿主处理，避免维护 CLI 直接接管其进程。
    from updates.relay_host import request
    return request(home, operation)


def main(home, operation):
    # 根据安装组件分派操作并返回可序列化结果；无效操作抛 UpdateError。
    # Windows 只在这里检查/暂存，实际安装由自己的受控按钮流程启动。
    config, current, _ = installed(home)
    if operation in ('login', 'logout'):
        from updates.credentials import login, logout
        return (login if operation == 'login' else logout)(home)
    if operation == 'recover':
        return Transaction(home,None).recover()
    if config['component']=='relay':
        return relay_command(home,operation)
    if operation in ('check', 'stage'):
        from updates.credentials import for_installation
        return (check if operation == 'check' else stage)(home, authorization=for_installation(home))
    if operation=='status':
        journal=Path(home)/'state/transaction.json'
        return {'version':current['version'], 'transaction':read(journal) if journal.exists() else None}
    raise UpdateError('use_windows_install_button')


if __name__=='__main__':
    # 命令行错误只暴露固定码；不会把异常栈或凭据写到标准输出。
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',required=True,type=Path)
    parser.add_argument('operation',choices=['login','logout','check','stage','apply','status','stop','recover'])
    args=parser.parse_args()
    try:
        print(json.dumps(main(no_links(args.home),args.operation),ensure_ascii=False))
    except Exception as error:
        print(json.dumps({'success':False,'error':error.code if isinstance(error,UpdateError) else 'update_operation_failed'}))
        sys.exit(1)
