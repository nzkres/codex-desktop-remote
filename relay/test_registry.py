import concurrent.futures
import json
import secrets
import tempfile
import unittest
from pathlib import Path

from frp_policy import OPS, evaluate
from registry import ControlError, Registry


class RegistryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.now = 1000
        self.path = Path(self.temp.name) / "registry.sqlite3"
        self.registry = Registry(self.path, clock=lambda: self.now)

    def enroll(self, owner="owner", name="Windows"):
        pair = self.registry.create_pairing(owner)
        token = secrets.token_hex(32)
        self.registry.claim(pair["code"], token, name, "windows", "127.0.0.1")
        terminal = self.registry.confirm(owner, pair["id"], name)
        return terminal, token, pair

    def error(self, status, call):
        with self.assertRaises(ControlError) as caught:
            call()
        self.assertEqual(caught.exception.status, status)

    def test_requires_confirmation_and_keeps_only_hashes(self):
        pair = self.registry.create_pairing("owner")
        token = secrets.token_hex(32)
        self.registry.claim(pair["code"], token, "Home", "windows", "local")
        self.assertEqual(self.registry.terminals("owner"), [])
        self.assertEqual(self.registry.pairing_status(pair["id"], token)["state"], "claimed")
        self.error(404, lambda: self.registry.confirm("other", pair["id"], "stolen"))
        terminal = self.registry.confirm("owner", pair["id"], "Home")
        self.assertEqual(self.registry.confirm("owner", pair["id"], "Home")["id"], terminal["id"])
        self.assertFalse(terminal["online"])
        self.assertNotIn(token, json.dumps(self.registry.terminals("owner")))
        with self.registry.transaction() as db:
            dump = "\n".join(db.iterdump())
        self.assertNotIn(token, dump)
        self.assertNotIn(pair["code"], dump)

    def test_lost_claim_reply_is_recoverable_only_with_same_device_credential(self):
        pair = self.registry.create_pairing("owner")
        token = secrets.token_hex(32)
        first = self.registry.claim(pair["code"], token, "PC", "windows", "fixture")
        recovered = self.registry.claim(pair["code"], token, "PC", "windows", "fixture")
        self.assertEqual(first["id"], recovered["id"])
        self.error(401, lambda: self.registry.claim(pair["code"], secrets.token_hex(32), "Other", "windows", "fixture"))
        device = self.registry.confirm("owner", pair["id"], "PC")
        self.now += Registry.PAIR_SECONDS
        self.assertEqual(self.registry.claim(pair["code"], token, "PC", "windows", "fixture")["id"], pair["id"])
        self.assertEqual(len(self.registry.terminals("owner")), 1)
        self.assertEqual(self.registry.device(device["id"], token)["id"], device["id"])

    def test_expiry_cancel_and_single_use(self):
        pair = self.registry.create_pairing("owner")
        self.now += Registry.PAIR_SECONDS
        self.error(401, lambda: self.registry.claim(pair["code"], secrets.token_hex(32), "PC", "windows", "local"))
        _, token, pair = self.enroll()
        self.error(401, lambda: self.registry.claim(pair["code"], secrets.token_hex(32), "PC", "windows", "local"))
        self.error(409, lambda: self.registry.cancel_pairing("owner", pair["id"]))
        next_pair = self.registry.create_pairing("owner")
        next_token = secrets.token_hex(32)
        self.registry.claim(next_pair["code"], next_token, "PC", "windows", "local")
        self.registry.cancel_pairing("owner", next_pair["id"])
        self.error(409, lambda: self.registry.confirm("owner", next_pair["id"], "PC"))
        self.assertEqual(self.registry.pairing_status(next_pair["id"], next_token)["state"], "rejected")

    def test_concurrent_claim_has_exactly_one_winner(self):
        pair = self.registry.create_pairing("owner")
        def attempt(_):
            try:
                self.registry.claim(pair["code"], secrets.token_hex(32), "PC", "windows", "local")
                return True
            except ControlError:
                return False
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            self.assertEqual(sum(pool.map(attempt, range(8))), 1)

    def test_throttle_survives_failed_transaction_and_malformed_codes(self):
        token = secrets.token_hex(32)
        for _ in range(Registry.MAX_FAILURES):
            self.error(401, lambda: self.registry.claim("wrong", token, "PC", "windows", "same-peer"))
        pair = self.registry.create_pairing("owner")
        self.error(429, lambda: self.registry.claim(pair["code"], token, "PC", "windows", "same-peer"))
        self.now += Registry.FAILURE_SECONDS
        new_pair = self.registry.create_pairing("owner")
        self.registry.claim(new_pair["code"], token, "PC", "windows", "same-peer")

    def test_owner_isolation_and_device_token_cannot_impersonate(self):
        a, ta, _ = self.enroll("alice")
        b, tb, _ = self.enroll("bob")
        self.assertEqual(len(self.registry.terminals("alice")), 1)
        self.error(404, lambda: self.registry.change("alice", b["id"], "remove"))
        self.error(404, lambda: self.registry.route("alice", b["id"]))
        self.error(403, lambda: self.registry.heartbeat(b["id"], ta, "ready", "test"))
        self.registry.heartbeat(b["id"], tb, "login_required", "test")
        self.assertTrue(self.registry.terminals("bob")[0]["online"])
        self.assertEqual(self.registry.terminals("bob")[0]["executor"], "login_required")

    def test_pause_resume_remove_and_reenrollment(self):
        a, ta, _ = self.enroll()
        b, tb, _ = self.enroll()
        self.registry.heartbeat(a["id"], ta, "ready", "test")
        self.registry.heartbeat(b["id"], tb, "ready", "test")
        old_port = self.registry.route("owner", a["id"])["port"]
        self.registry.change("owner", a["id"], "rename", "Changed")
        self.assertEqual(self.registry.device(a["id"], ta)["port"], old_port)
        self.registry.change("owner", a["id"], "pause")
        self.error(403, lambda: self.registry.route("owner", a["id"]))
        self.error(403, lambda: self.registry.device(a["id"], ta))
        self.registry.change("owner", a["id"], "resume")
        self.error(503, lambda: self.registry.route("owner", a["id"]))
        self.registry.heartbeat(a["id"], ta, "ready", "test")
        self.registry.change("owner", a["id"], "remove")
        self.error(403, lambda: self.registry.device(a["id"], ta))
        self.error(404, lambda: self.registry.change("owner", a["id"], "resume"))
        self.assertEqual(self.registry.route("owner", b["id"])["terminalId"], b["id"])
        c, tc, _ = self.enroll(name="Changed")
        self.assertNotEqual(a["id"], c["id"])
        self.assertNotEqual(old_port, self.registry.device(c["id"], tc)["port"])

    def test_stale_heartbeat_and_persistence(self):
        a, token, _ = self.enroll()
        self.registry.heartbeat(a["id"], token, "ready", "test")
        other = Registry(self.path, clock=lambda: self.now)
        self.assertEqual(len(other.terminals("owner")), 1)
        self.now += Registry.ONLINE_SECONDS
        self.error(503, lambda: other.route("owner", a["id"]))
        self.assertEqual(other.terminals("owner")[0]["executor"], "unknown")

    def test_stage_one_database_upgrade_preserves_device_identity(self):
        device, token, _ = self.enroll()
        self.registry.heartbeat(device["id"], token, "ready", "test")
        original_port = self.registry.device(device["id"], token)["port"]
        # Temporary fixture only: recreate the prior release's table shape.
        with self.registry.transaction() as db:
            db.execute("ALTER TABLE terminals DROP COLUMN access_epoch")
        upgraded = Registry(self.path, clock=lambda: self.now)
        self.assertEqual(upgraded.device(device["id"], token)["port"], original_port)
        self.assertEqual(upgraded.route("owner", device["id"])["epoch"], 0)
        upgraded.change("owner", device["id"], "pause")
        upgraded.change("owner", device["id"], "resume")
        upgraded.heartbeat(device["id"], token, "ready", "test")
        self.assertEqual(upgraded.route("owner", device["id"])["epoch"], 2)

    def test_validation_and_capacity(self):
        for _ in range(5):
            self.registry.create_pairing("owner")
        self.error(429, lambda: self.registry.create_pairing("owner"))
        self.error(400, lambda: self.registry.claim("0" * 12, secrets.token_hex(32), "PC", "ubuntu", "local"))
        self.error(400, lambda: self.registry.claim("0" * 12, secrets.token_hex(32), "PC\nspoof", "windows", "local"))

    def test_credential_cannot_be_reused_and_port_pool_is_bounded(self):
        self.registry = Registry(self.path, port_start=22000, port_end=22000, clock=lambda: self.now)
        _, token, _ = self.enroll()
        pair = self.registry.create_pairing("owner")
        self.error(409, lambda: self.registry.claim(pair["code"], token, "PC", "windows", "local"))
        self.registry.claim(pair["code"], secrets.token_hex(32), "PC", "windows", "local")
        self.error(409, lambda: self.registry.confirm("owner", pair["id"], "PC"))

    def test_expired_claim_cannot_be_confirmed_and_wrong_token_cannot_poll(self):
        pair = self.registry.create_pairing("owner")
        token = secrets.token_hex(32)
        self.registry.claim(pair["code"], token, "PC", "windows", "local")
        self.error(401, lambda: self.registry.pairing_status(pair["id"], secrets.token_hex(32)))
        self.now += Registry.PAIR_SECONDS
        self.error(409, lambda: self.registry.confirm("owner", pair["id"], "PC"))
        self.assertEqual(self.registry.pairing_status(pair["id"], token)["state"], "expired")

    def test_frp_policy_rejects_other_device_port_proxy_and_protocol(self):
        a, token, _ = self.enroll()
        b, _, _ = self.enroll()
        device = self.registry.device(a["id"], token)
        user = {"user": a["id"], "metas": {"device_token": token}}
        proxy = {"user": user, "proxy_name": device["proxyName"], "proxy_type": "tcp", "remote_port": device["port"]}
        rewritten = evaluate(self.registry, "Login", {**user, "run_id": b["id"]})
        self.assertFalse(rewritten["unchange"])
        self.assertEqual(rewritten["content"]["run_id"], a["id"])
        for op in OPS:
            content = user if op == "Login" else proxy
            self.assertFalse(evaluate(self.registry, op, content)["reject"], op)
        for change in ({"remote_port": device["port"] + 1}, {"remote_port": 0}, {"proxy_name": b["id"] + ".gateway"}, {"proxy_type": "http"}, {"group": "shared"}, {"custom_domains": ["example.com"]}):
            self.assertTrue(evaluate(self.registry, "NewProxy", {**proxy, **change})["reject"])
        self.assertTrue(evaluate(self.registry, "Login", {"user": b["id"], "metas": user["metas"]})["reject"])
        for invalid in (None, [], "bad", {"user": []}):
            self.assertTrue(evaluate(self.registry, "NewProxy", invalid)["reject"])
        self.registry.change("owner", a["id"], "remove")
        for op in OPS:
            self.assertTrue(evaluate(self.registry, op, user if op == "Login" else proxy)["reject"], op)


if __name__ == "__main__":
    unittest.main()
