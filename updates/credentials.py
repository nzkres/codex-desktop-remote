"""Reuse a paired Windows identity or an owner-only relay login for private OTA.

No password/token arguments, embedded credentials, discovery of other installs,
or automatic login. Windows reads its existing user-scoped DPAPI store. The
Linux administrator explicitly logs in to the reviewed HTTPS source's origin;
only an expiring account session is persisted in this installation's private
state directory, never the password.
"""
import getpass
import json
import os
from pathlib import Path
import re
import stat
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

from updates.authorization import UpdateAuthorization
from updates.engine import installed
from updates.security import UpdateError, _json, _opener
from updates.storage import atomic, lock, no_links, read


LOGIN_FILE = 'update-login.local.json'


def _source(home):
    config, _, _ = installed(home)
    trust = read(Path(home) / 'trust.json')
    source = trust.get('sources', {}).get(config['component'])
    if not trust.get('keys') or not isinstance(source, str):
        raise UpdateError('update_trust_not_provisioned')
    return config['component'], source


def _private(home, file_required=False):
    if not sys_linux():
        raise UpdateError('linux_update_login_required')
    home = no_links(home)
    for directory in (home, home / 'state'):
        info = directory.stat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o700:
            raise UpdateError('update_login_permissions_invalid')
    path = no_links(home / 'state' / LOGIN_FILE)
    if path.exists() or file_required:
        info = path.stat()
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                or stat.S_IMODE(info.st_mode) != 0o600 or info.st_nlink != 1):
            raise UpdateError('update_login_permissions_invalid')
    return path


def sys_linux():
    import sys
    return sys.platform.startswith('linux')


def _session(home):
    try:
        value = read(_private(home, file_required=True))
        if (not isinstance(value, dict) or set(value) != {'schema', 'relay', 'token', 'expiresAt'}
                or type(value['schema']) is not int or value['schema'] != 1
                or type(value['expiresAt']) is not int or value['expiresAt'] <= int(time.time())):
            raise UpdateError('update_authentication_required')
        return value
    except UpdateError:
        raise
    except Exception:
        raise UpdateError('update_authentication_required') from None


def for_installation(home):
    component, source = _source(home)
    if component == 'windows':
        try:
            from windows.vendor.connector_core import CredentialStore
            identity = no_links(Path(home) / 'data' / 'identity')
            identity_file = no_links(identity / 'identity.dpapi')
            if not identity_file.is_file():
                raise UpdateError('update_device_not_paired')
            store = CredentialStore(identity)
            with store.lock():
                state = store.read()
            if not state or state.get('state') != 'connected' or not state.get('terminalId'):
                raise UpdateError('update_device_not_paired')
            if state.get('runtimeNeedsReview'):
                raise UpdateError('identity_or_runtime_requires_review')
            return UpdateAuthorization(state['relay'], source, state['deviceToken'],
                                       principal='device', device_id=state['terminalId'])
        except UpdateError:
            raise
        except Exception:
            raise UpdateError('update_device_identity_unavailable') from None
    if component == 'relay':
        value = _session(home)
        return UpdateAuthorization(value['relay'], source, value['token'])
    raise UpdateError('unsupported_update_component')


def _post(relay, operation, body, token=None, context=None):
    # Caller first binds this origin to the provisioned, reviewed update source.
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if token is not None:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(relay + '/auth/' + operation,
        data=json.dumps(body, ensure_ascii=True).encode('ascii'), headers=headers, method='POST')
    try:
        with _opener(context).open(request, timeout=20) as response:
            if operation == 'logout' and response.status == 204:
                return {}
            raw = response.read(16385)
            if response.status != 200 or not 0 < len(raw) <= 16384:
                raise UpdateError('update_login_failed')
            return _json(raw)
    except urllib.error.HTTPError as error:
        code = {401: 'update_authentication_required', 403: 'update_access_denied',
                429: 'update_login_rate_limited'}.get(error.code, 'update_login_failed')
        error.close()
        raise UpdateError(code) from None
    except UpdateError:
        raise
    except Exception:
        raise UpdateError('update_login_failed') from None


def login(home, *, username_provider=input, password_provider=getpass.getpass,
          notice=print, context=None):
    component, source = _source(home)
    if component != 'relay':
        raise UpdateError('use_existing_device_pairing')
    path = _private(home)
    parsed = urlsplit(source)
    relay = parsed.scheme + '://' + parsed.netloc
    # Validate origin and exact private-feed scope BEFORE asking for a password.
    UpdateAuthorization(relay, source, '0' * 64)
    with lock(Path(home) / 'state' / 'update-login.lock'):
        if path.exists():
            raise UpdateError('update_login_exists_logout_first')
        notice('Relay update login server: ' + relay)
        username = username_provider('Relay account: ')
        password = password_provider('Relay password: ')
        if (not isinstance(username, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,64}', username)
                or not isinstance(password, str) or not 1 <= len(password) <= 1024):
            raise UpdateError('update_login_input_invalid')
        value = _post(relay, 'login', {'username': username, 'password': password}, context=context)
        if (not isinstance(value, dict) or set(value) != {'token', 'expiresAt'}
                or type(value['expiresAt']) is not int or value['expiresAt'] <= int(time.time())
                or value['expiresAt'] > int(time.time()) + 31 * 86400):
            raise UpdateError('update_login_response_invalid')
        UpdateAuthorization(relay, source, value['token'])
        atomic(path, dict(value, schema=1, relay=relay))
        _private(home, file_required=True)
        return {'authenticated': True, 'expiresAt': value['expiresAt']}


def logout(home, *, context=None):
    component, source = _source(home)
    if component != 'relay':
        raise UpdateError('use_existing_device_pairing')
    path = _private(home)
    with lock(Path(home) / 'state' / 'update-login.lock'):
        if not path.exists():
            return {'signedOut': True}
        # Expired sessions still permit explicit server-side revoke/removal.
        info = path.stat()
        original_file = (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)
        value = read(path)
        if (not isinstance(value, dict) or set(value) != {'schema', 'relay', 'token', 'expiresAt'}
                or value['schema'] != 1):
            raise UpdateError('update_login_record_invalid')
        UpdateAuthorization(value['relay'], source, value['token'])
        _post(value['relay'], 'logout', {}, token=value['token'], context=context)
        # Do not erase a credential file which changed while the request ran.
        _private(home, file_required=True)
        info = path.stat()
        current_file = (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)
        if current_file != original_file or read(path) != value:
            raise UpdateError('update_login_changed')
        path.unlink()
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
        return {'signedOut': True}
