"""Trusted pre-exec gate: containment is installed before any candidate code runs."""
import os
import subprocess
import sys

if sys.stdin.buffer.read(1) != b'G':
    raise SystemExit(2)
child = subprocess.Popen(sys.argv[1:], stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr,
                         close_fds=True)
raise SystemExit(child.wait())
