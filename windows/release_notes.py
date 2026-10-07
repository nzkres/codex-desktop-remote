"""Bundled, offline release history, loaded once for this process."""
import json
from pathlib import Path

CATALOG = json.loads(Path(__file__).with_name('releases.json').read_text(encoding='utf-8'))
VERSION = CATALOG['releases'][0]['version']


def format_notes():
    lines = [CATALOG['name'] + ' · 当前运行版本 ' + VERSION,
             '说明随本版本提供；查看说明不会连接服务器或更改任务。']
    for release in CATALOG['releases']:
        lines.extend(['', release['version'] + ' · ' + release['date'], release['summary']])
        for change in release['changes']:
            lines.extend(['', change['title'], '体验：' + change['experience'], '验证：' + change['verify']])
        if release['limitations']:
            lines.extend(['', '当前限制：', *('• ' + item for item in release['limitations'])])
    return '\n'.join(lines)
