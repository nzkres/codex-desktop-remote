"""Isolated tests for the review-only Certbot deploy hook."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


HOOK = Path(__file__).with_name("renew-caddy.sh.review")
LINEAGE = "/etc/letsencrypt/live/codex-remote-ip"
NAME = "codex-desktop-remote-test-caddy"
CONTAINER_ID = "a" * 64
IMAGE = "caddy@sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d"


@unittest.skipUnless(sys.platform.startswith("linux"), "requires Linux /bin/sh")
class RenewCaddyHookTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.calls = self.directory / "docker-calls.jsonl"
        docker = self.directory / "docker"
        docker.write_text(
            f"#!{sys.executable}\n"
            "import json, os, sys\n"
            "from pathlib import Path\n"
            "args = sys.argv[1:]\n"
            "with Path(os.environ['CALLS_FILE']).open('a') as output:\n"
            "    output.write(json.dumps(args) + '\\n')\n"
            "if args[:1] == ['inspect'] and len(args) == 4 and args[1] == '--format':\n"
            "    if args[2] == '{{.Id}}':\n"
            "        if os.environ.get('FAIL_FIRST_INSPECT') == '1': sys.exit(2)\n"
            "        value = os.environ['FAKE_ID']\n"
            "    else:\n"
            "        values = {\n"
            "          '{{ index .Config.Labels \"com.docker.compose.service\" }}': 'FAKE_SERVICE',\n"
            "          '{{ index .Config.Labels \"com.docker.compose.project\" }}': 'FAKE_PROJECT',\n"
            "          '{{.Config.Image}}': 'FAKE_IMAGE',\n"
            "          '{{.State.Running}}': 'FAKE_RUNNING',\n"
            "        }\n"
            "        if args[3] != os.environ['FAKE_ID'] or args[2] not in values: sys.exit(3)\n"
            "        value = os.environ[values[args[2]]]\n"
            "    print(value)\n"
            "elif args[:1] != ['exec']:\n"
            "    sys.exit(4)\n",
            encoding="utf-8",
        )
        docker.chmod(0o700)
        self.environment = {
            **os.environ,
            "PATH": str(self.directory) + os.pathsep + os.environ.get("PATH", ""),
            "CALLS_FILE": str(self.calls),
            "FAKE_ID": CONTAINER_ID,
            "FAKE_SERVICE": "cdrtest-caddy",
            "FAKE_PROJECT": "codex-desktop-remote-test",
            "FAKE_IMAGE": IMAGE,
            "FAKE_RUNNING": "true",
        }

    def run_hook(self, lineage=LINEAGE, **overrides):
        environment = {**self.environment, **overrides}
        if lineage is None:
            environment.pop("RENEWED_LINEAGE", None)
        else:
            environment["RENEWED_LINEAGE"] = lineage
        return subprocess.run(["/bin/sh", str(HOOK)], env=environment,
                              capture_output=True, text=True, timeout=5, check=False)

    def docker_calls(self):
        if not self.calls.exists():
            return []
        return [json.loads(line) for line in self.calls.read_text(encoding="utf-8").splitlines()]

    def test_missing_empty_or_unrelated_lineage_never_calls_docker(self):
        for lineage in (None, "", "/etc/letsencrypt/live/other"):
            with self.subTest(lineage=lineage):
                self.calls.unlink(missing_ok=True)
                result = self.run_hook(lineage)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.docker_calls(), [])

    def test_invalid_container_identity_never_executes(self):
        cases = (
            {"FAKE_ID": "A" * 64},
            {"FAKE_ID": "a" * 63},
            {"FAKE_ID": "a" * 63 + "/"},
            {"FAKE_SERVICE": "other"},
            {"FAKE_PROJECT": "other"},
            {"FAKE_IMAGE": "caddy:latest"},
            {"FAKE_RUNNING": "false"},
            {"FAIL_FIRST_INSPECT": "1"},
        )
        for overrides in cases:
            with self.subTest(overrides=overrides):
                self.calls.unlink(missing_ok=True)
                result = self.run_hook(**overrides)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(any(call[0] == "exec" for call in self.docker_calls()))

    def test_success_inspects_exact_identity_and_executes_only_fixed_id(self):
        result = self.run_hook()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.docker_calls()
        self.assertEqual(calls[0], ["inspect", "--format", "{{.Id}}", NAME])
        self.assertEqual(len(calls), 6)
        self.assertTrue(all(call[0] == "inspect" and call[-1] == CONTAINER_ID
                            for call in calls[1:5]))
        self.assertEqual(calls[5], [
            "exec", CONTAINER_ID, "caddy", "reload", "--force", "--config", "/etc/caddy/Caddyfile",
            "--adapter", "caddyfile", "--address", "127.0.0.1:22019",
        ])


if __name__ == "__main__":
    unittest.main()
