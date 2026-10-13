# 全新受管安装引导：建立安装结构、可信基线与启动入口。
# 不是把别的电脑身份复制到本机的迁移工具；首次安装与后续更新是不同路径。

"""Verify a signed first package, then create a NEW private installation. No migration."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# 新安装入口只从当前源码树加载更新模块；尚未碰触现有安装现场。
sys.path.insert(0,str(ROOT))
from updates.security import verify, unpack, UpdateError, COMPATIBILITY, TARGETS
from updates.storage import read, atomic, no_links


def install(home, trust_path, envelope_path, archive, component):
    # home 必须是全新目录；trust/envelope/archive 分别是信任根、签名元数据和安装包。
    # 验签、解包、复制并写入安装指针后，返回新版本目录；任何检查失败都报错。
    home = no_links(home)
    if home.exists() or not home.parent.is_dir() or component not in ('windows','relay'):
        raise UpdateError('new_installation_home_required')
    if (component == 'windows' and os.name != 'nt') or (component == 'relay' and not sys.platform.startswith('linux')):
        raise UpdateError('wrong_installation_platform')
    trust, envelope = read(trust_path), read(envelope_path)
    release, ledger = verify(envelope, trust, component, TARGETS[component], COMPATIBILITY[component], None)
    # 在临时目录中先完成包检查；只有验证成功才创建看起来可启动的安装目录。
    # Failed verification/extraction never creates an apparently launchable home.
    with tempfile.TemporaryDirectory(prefix='desktop-remote-verified-') as temporary:
        extracted = unpack(archive, Path(temporary)/'package', release)
        home.mkdir(mode=0o700)
        if os.name == 'nt':
            sys.path.insert(0,str(ROOT/'windows/vendor'))
            from connector_core import private_directory
            private_directory(home)
        for name in ('data','state','versions','staging'):
            (home/name).mkdir(mode=0o700)
        name = release['version']+'-'+release['sha256'][:16]
        destination = home/'versions'/name
        shutil.copytree(extracted,destination)
        # Code comes from the authenticated package, including the bootstrap launcher.
        launcher = destination/'updates/launch.py'
        if not launcher.is_file():
            raise UpdateError('bootstrap_launcher_missing')
        shutil.copyfile(launcher,home/('Start.pyw' if component=='windows' else 'start.py'))
        atomic(home/'trust.json',trust)
        atomic(home/'state/ledger.json',ledger)
        atomic(home/'install.json',{'schema':1,'component':component,'platform':TARGETS[component],
                                   'compatibility':COMPATIBILITY[component]})
        # current.json 最后记录已认证版本，供稳定启动器核对完整文件树。
        atomic(home/'current.json',{'path':'versions/'+name,'version':release['version'],
                                   'manifestSha256':hashlib.sha256((destination/'package.json').read_bytes()).hexdigest()})
    return destination


if __name__=='__main__':
    # 命令行只允许 Windows/Relay 新装；失败时输出固定错误码并保留现场。
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',required=True,type=Path)
    parser.add_argument('--trust',required=True,type=Path)
    parser.add_argument('--metadata',required=True,type=Path)
    parser.add_argument('--package',required=True,type=Path)
    parser.add_argument('--component',required=True,choices=['windows','relay'])
    args=parser.parse_args()
    try:
        install(args.home,args.trust,args.metadata,args.package,args.component)
        print('Verified fresh installation created. Configure this new instance before starting it.')
    except Exception as error:
        print(error.code if isinstance(error,UpdateError) else 'installation_failed_preserve_evidence',file=sys.stderr)
        sys.exit(1)

