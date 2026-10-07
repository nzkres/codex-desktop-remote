"""Verify a signed first package, then create a NEW private installation. No migration."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from updates.security import verify, unpack, UpdateError, COMPATIBILITY, TARGETS
from updates.storage import read, atomic, no_links


def install(home, trust_path, envelope_path, archive, component):
    home = no_links(home)
    if home.exists() or not home.parent.is_dir() or component not in ('windows','relay'):
        raise UpdateError('new_installation_home_required')
    if (component == 'windows' and os.name != 'nt') or (component == 'relay' and not sys.platform.startswith('linux')):
        raise UpdateError('wrong_installation_platform')
    trust, envelope = read(trust_path), read(envelope_path)
    release, ledger = verify(envelope, trust, component, TARGETS[component], COMPATIBILITY[component], None)
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
        atomic(home/'current.json',{'path':'versions/'+name,'version':release['version'],
                                   'manifestSha256':hashlib.sha256((destination/'package.json').read_bytes()).hexdigest()})
    return destination


if __name__=='__main__':
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

