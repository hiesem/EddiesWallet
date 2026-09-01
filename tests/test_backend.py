from __future__ import annotations

import json
import sqlite3
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from eddies_wallet.api import create_server
from eddies_wallet.app import EddiesWalletApp
from eddies_wallet.service import Problem


class BackendTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.db_path = str(Path(self.tempdir.name) / "wallet.sqlite3")
        self.app = EddiesWalletApp(self.db_path, reset=True)
        self.parent_a = self.app.service.authenticate("local-fixture-parent-a-v1")
        self.parent_b = self.app.service.authenticate("local-fixture-parent-b-v1")
        self.child_a = self.app.service.authenticate("local-fixture-child-a-v1")

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def command(self, kind: str, payload: dict, *, parent=None, base_position: int = 0, note: str = "") -> dict:
        return {
            "command_id": str(uuid.uuid4()),
            "client_seq": 1,
            "base_position": base_position,
            "kind": kind,
            "payload": payload,
            "effective_on": "2026-02-01",
            "note": note,
        }

    def test_health_readiness_and_fixture_identity(self) -> None:
        self.assertEqual(self.app.health()["status"], "ok")
        self.assertEqual(self.app.service.readiness()["status"], "ready")
        self.assertEqual(self.app.service.me(self.parent_a)["family_id"], "family-a")
        self.assertEqual(self.app.service.me(self.child_a)["role"], "child")
        self.assertFalse(self.app.service.capabilities()["google_auth"])
        self.assertTrue(self.app.service.capabilities()["fixture_auth"])

    def test_seed_families_are_isolated(self) -> None:
        self.assertEqual(self.app.service.projection(self.parent_a)["projection"]["spending"], 24)
        self.assertEqual(self.app.service.projection(self.parent_b)["projection"]["spending"], 12)
        self.assertEqual(len(self.app.service.sync(self.parent_a)["events"]), 1)
        self.assertEqual(self.app.service.sync(self.parent_a)["events"][0]["family_id"], "family-a")
        with self.assertRaises(Problem) as error:
            self.app.service.projection(self.parent_a, "eddie-b")
        self.assertEqual(error.exception.code, "CROSS_FAMILY_REFERENCE")

    def test_parent_deposit_withdrawal_and_save_are_server_derived(self) -> None:
        deposit = self.app.service.submit_command(
            self.parent_a,
            self.command("deposit", {"member_id": "eddie-a", "amount_units": 6}),
        )
        self.assertEqual(deposit["status"], "accepted")
        self.assertEqual(deposit["sync_state"], "confirmed")
        self.assertEqual(deposit["projection"]["spending"], 30)
        self.assertEqual(deposit["event"]["postings"], [{"account": "spending", "delta_units": 6}])

        withdrawal = self.app.service.submit_command(
            self.parent_a,
            self.command("withdrawal", {"member_id": "eddie-a", "amount_units": 4}, note="virtual snack record"),
        )
        self.assertEqual(withdrawal["projection"]["spending"], 26)
        saving = self.app.service.submit_command(
            self.parent_a,
            self.command("save", {"member_id": "eddie-a", "amount_units": 10}),
        )
        self.assertEqual(saving["projection"]["spending"], 16)
        self.assertEqual(saving["projection"]["savings"], 10)
        self.assertEqual(saving["projection"]["held"], 26)
        self.assertTrue(all("balance" not in item for item in saving["event"]["postings"]))

    def test_idempotency_returns_original_and_mismatch_is_rejected(self) -> None:
        request = self.command("deposit", {"member_id": "eddie-a", "amount_units": 5})
        first = self.app.service.submit_command(self.parent_a, request)
        retry = self.app.service.submit_command(self.parent_a, dict(request))
        self.assertEqual(retry["event_id"], first["event_id"])
        self.assertEqual(retry["server_position"], first["server_position"])
        with self.app.database.read() as connection:
            self.assertEqual(connection.execute("SELECT COUNT(*) FROM ledger_events WHERE family_id = 'family-a'").fetchone()[0], 2)
            self.assertEqual(connection.execute("SELECT COUNT(*) FROM ledger_postings WHERE family_id = 'family-a'").fetchone()[0], 2)
        mismatch = dict(request, payload={"member_id": "eddie-a", "amount_units": 7})
        with self.assertRaises(Problem) as error:
            self.app.service.submit_command(self.parent_a, mismatch)
        self.assertEqual(error.exception.code, "DUPLICATE_PAYLOAD_MISMATCH")

    def test_rejected_command_has_outcome_but_no_event_or_posting(self) -> None:
        before = self.app.service.sync(self.parent_a)["server_position"]
        request = self.command(
            "spending", {"member_id": "eddie-a", "amount_units": 999}, note="too much virtual spending"
        )
        outcome = self.app.service.submit_command(self.parent_a, request)
        self.assertEqual(outcome["status"], "rejected")
        self.assertEqual(outcome["sync_state"], "rejected")
        self.assertEqual(outcome["rejection"]["code"], "INSUFFICIENT_SPENDING")
        self.assertEqual(outcome["server_position"], before)
        self.assertEqual(len(self.app.service.sync(self.parent_a)["events"]), 1)
        stored = self.app.service.get_command(self.parent_a, request["command_id"])
        self.assertEqual(stored["rejection"]["code"], "INSUFFICIENT_SPENDING")

    def test_policy_dependent_commands_are_explicitly_gated(self) -> None:
        request = self.command("loan_issue", {"member_id": "eddie-a", "amount_units": 2})
        outcome = self.app.service.submit_command(self.parent_a, request)
        self.assertEqual(outcome["status"], "rejected")
        self.assertEqual(outcome["rejection"]["code"], "FEATURE_GATED_POLICY")
        self.assertIn("loan", outcome["rejection"]["message"])
        self.assertEqual(len(self.app.service.sync(self.parent_a)["events"]), 1)

    def test_child_cannot_write_or_forge_role_or_family(self) -> None:
        request = self.command("deposit", {"member_id": "eddie-a", "amount_units": 2})
        with self.assertRaises(Problem) as error:
            self.app.service.submit_command(self.child_a, request)
        self.assertEqual(error.exception.code, "CHILD_WRITE_FORBIDDEN")
        with self.assertRaises(Problem) as error:
            self.app.service.submit_command(self.parent_a, dict(request, role="child"))
        self.assertEqual(error.exception.code, "FORGED_ROLE")
        with self.assertRaises(Problem) as error:
            self.app.service.submit_command(self.parent_a, dict(request, family_id="family-b"))
        self.assertEqual(error.exception.code, "FORGED_SCOPE")
        with self.assertRaises(Problem) as error:
            self.app.service.submit_command(self.parent_a, dict(request, balance=999))
        self.assertEqual(error.exception.code, "CLIENT_DERIVED_STATE_FORBIDDEN")
        cross = self.app.service.submit_command(
            self.parent_a, self.command("deposit", {"member_id": "eddie-b", "amount_units": 2})
        )
        self.assertEqual(cross["rejection"]["code"], "CROSS_FAMILY_REFERENCE")

    def test_child_database_connection_cannot_insert_and_ledger_is_immutable(self) -> None:
        with self.assertRaises(sqlite3.IntegrityError):
            with self.app.database.transaction("child") as connection:
                connection.execute(
                    """INSERT INTO ledger_events
                       (event_id, family_id, member_id, kind, actor_member_id, effective_on,
                        recorded_at, source, idempotency_key, payload_json, server_position)
                       VALUES ('forged', 'family-a', 'eddie-a', 'deposit', 'parent-a', '2026-01-01',
                               '2026-01-01T00:00:00Z', 'manual', 'forged', '{}', 99)"""
                )
        with self.assertRaises(sqlite3.IntegrityError):
            with self.app.database.transaction("child") as connection:
                connection.execute("UPDATE families SET unit_label = 'forged' WHERE id = 'family-a'")
        with self.assertRaises(sqlite3.IntegrityError):
            with self.app.database.transaction("system") as connection:
                connection.execute("UPDATE ledger_events SET note = 'edited' WHERE event_id = 'seed-event-a'")
        with self.assertRaises(sqlite3.IntegrityError):
            with self.app.database.transaction("system") as connection:
                connection.execute("DELETE FROM ledger_events WHERE event_id = 'seed-event-a'")

    def test_reversal_is_new_event_and_replay_matches_projection(self) -> None:
        deposit = self.app.service.submit_command(
            self.parent_a, self.command("deposit", {"member_id": "eddie-a", "amount_units": 3})
        )
        reversal = self.app.service.submit_command(
            self.parent_a,
            self.command("reversal", {"member_id": "eddie-a", "event_id": deposit["event_id"]}),
        )
        self.assertEqual(reversal["status"], "accepted")
        self.assertEqual(reversal["event"]["reversal_of"], deposit["event_id"])
        self.assertEqual(reversal["event"]["postings"], [{"account": "spending", "delta_units": -3}])
        self.assertNotEqual(reversal["event_id"], deposit["event_id"])
        with self.app.database.read() as connection:
            replay = {"spending": 0, "savings": 0, "owed": 0}
            for row in connection.execute(
                """SELECT a.kind, p.delta_units FROM ledger_events e
                   JOIN ledger_postings p ON p.event_id = e.event_id
                   JOIN ledger_accounts a ON a.id = p.account_id
                   WHERE e.family_id = 'family-a' AND e.member_id = 'eddie-a'
                   ORDER BY e.server_position, p.posting_id"""
            ):
                replay[row["kind"]] += row["delta_units"]
        projection = self.app.service.projection(self.parent_a)["projection"]
        replayed = self.app.service.replay_projection(self.parent_a)["projection"]
        self.assertEqual({key: projection[key] for key in replay}, replay)
        self.assertEqual(replayed, projection)
        self.assertEqual(projection["spending"], 24)

    def test_pairing_is_scoped_one_use_expiring_and_revocation_requires_repair(self) -> None:
        pairing = self.app.service.create_pairing_code(self.parent_a, "eddie-a", "new synthetic tablet")
        child = self.app.service.redeem_pairing(pairing["pairing_code"], "new synthetic tablet", "test-client")
        self.assertEqual(child["family_id"], "family-a")
        self.assertEqual(child["member_id"], "eddie-a")
        with self.assertRaises(Problem) as error:
            self.app.service.redeem_pairing(pairing["pairing_code"], "replay", "test-client-2")
        self.assertEqual(error.exception.code, "PAIRING_INVALID_OR_EXPIRED")
        new_child = self.app.service.authenticate(child["access_token"])
        self.assertEqual(self.app.service.sync(new_child)["state"], "confirmed")
        revoked = self.app.service.revoke_device(self.parent_a, child["device_id"])
        self.assertTrue(revoked["clear_local_data"])
        revoked_context = self.app.service.authenticate(child["access_token"], allow_revoked=True)
        with self.assertRaises(Problem) as error:
            self.app.service.sync(revoked_context)
        self.assertEqual(error.exception.code, "DEVICE_REVOKED")
        repair_code = self.app.service.create_pairing_code(self.parent_a, "eddie-a")["pairing_code"]
        repaired = self.app.service.redeem_pairing(repair_code, "re-paired tablet", "repair-client")
        self.assertNotEqual(repaired["device_id"], child["device_id"])

        expired = self.app.service.create_pairing_code(self.parent_a, "eddie-a")["pairing_code"]
        with self.app.database.transaction("system") as connection:
            connection.execute("UPDATE pairing_codes SET expires_at = '2000-01-01T00:00:00Z' WHERE consumed_at IS NULL")
        with self.assertRaises(Problem) as error:
            self.app.service.redeem_pairing(expired, "expired", "expiry-client")
        self.assertEqual(error.exception.code, "PAIRING_INVALID_OR_EXPIRED")

    def test_child_sync_is_confirmed_and_member_scoped(self) -> None:
        result = self.app.service.sync(self.child_a, after=0, limit=1)
        self.assertEqual(result["state"], "confirmed")
        self.assertTrue(result["confirmed"])
        self.assertEqual(result["projections"][0]["member_id"], "eddie-a")
        self.assertTrue(all(event["member_id"] == "eddie-a" for event in result["events"]))
        with self.assertRaises(Problem) as error:
            self.app.service.projection(self.child_a, "eddie-b")
        self.assertEqual(error.exception.code, "FORGED_SCOPE")

    def test_production_mode_has_no_fixture_authentication(self) -> None:
        production_path = Path(self.tempdir.name) / "production.sqlite3"
        production = EddiesWalletApp(production_path, mode="production", reset=True, seed=False)
        with self.assertRaises(Problem) as error:
            production.service.fixture_login("parent-a")
        self.assertEqual(error.exception.code, "LOCAL_AUTH_DISABLED")
        self.assertFalse(production.service.capabilities()["fixture_auth"])


class HttpTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        app = EddiesWalletApp(Path(self.tempdir.name) / "wallet.sqlite3", reset=True)
        self.server = create_server(app, "127.0.0.1", 0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.tempdir.cleanup()

    def request(self, method: str, path: str, body: dict | None = None, token: str | None = None):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Content-Type": "application/json"} if body is not None else {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as error:
            payload = error.read()
            error.close()
            return error.code, json.loads(payload)

    def test_health_fixture_http_command_and_sync(self) -> None:
        status, health = self.request("GET", "/healthz")
        self.assertEqual(status, 200)
        self.assertEqual(health["status"], "ok")
        status, fixture = self.request("POST", "/v1/auth/fixture", {"identity": "parent-a"})
        self.assertEqual(status, 200)
        token = fixture["access_token"]
        status, response = self.request("POST", "/v1/commands", {
            "command_id": str(uuid.uuid4()),
            "client_seq": 1,
            "base_position": 1,
            "kind": "deposit",
            "payload": {"member_id": "eddie-a", "amount_units": 1},
            "effective_on": "2026-02-01",
        }, token)
        self.assertEqual(status, 200)
        self.assertEqual(response["status"], "accepted")
        status, sync = self.request("GET", "/v1/sync?after=1", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(sync["events"][0]["kind"], "deposit")
        status, denied = self.request("POST", "/v1/commands", {
            "command_id": str(uuid.uuid4()),
            "client_seq": 1,
            "base_position": 0,
            "kind": "deposit",
            "payload": {"member_id": "eddie-a", "amount_units": 1},
            "effective_on": "2026-02-01",
        }, "local-fixture-child-a-v1")
        self.assertEqual(status, 403)
        self.assertEqual(denied["error"]["code"], "CHILD_WRITE_FORBIDDEN")


if __name__ == "__main__":
    unittest.main()
