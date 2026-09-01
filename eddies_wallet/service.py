from __future__ import annotations

import hashlib
import json
import secrets
import sqlite3
import uuid
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Any, Callable

from .auth import FIXTURE_IDENTITIES, FIXTURE_TOKENS, FixtureAuthAdapter, token_hash
from .db import Database

MAX_UNITS = 9_223_372_036_854_775_807
ALLOWED_KINDS = {"deposit", "withdrawal", "spending", "save", "reversal"}
POLICY_GATED_KINDS = {
    "unsave": "unsave policy is not approved for this slice",
    "loan_issue": "loan and negative-balance policy is not approved for this slice",
    "loan_repayment": "loan and repayment policy is not approved for this slice",
    "interest_reward": "reward timing and automation policy is not approved for this slice",
    "allowance": "allowance amount, cadence, and automation policy is not approved for this slice",
}


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso_now(now: datetime | None = None) -> str:
    value = now or utc_now()
    return value.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def canonical_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def sha256_json(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


class Problem(Exception):
    def __init__(self, code: str, message: str, status: int = 400, state: str | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status
        self.state = state

    def as_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {"code": self.code, "message": self.message}
        if self.state:
            result["state"] = self.state
        return result


@dataclass(frozen=True)
class SessionContext:
    session_id: str
    role: str
    family_id: str
    member_id: str
    device_id: str | None
    revoked: bool = False


@dataclass(frozen=True)
class Rejection:
    code: str
    detail: str


class AttemptLimiter:
    """A small in-process limiter for the local pairing endpoint."""

    def __init__(self, max_attempts: int = 10, window_seconds: int = 300):
        self.max_attempts = max_attempts
        self.window = timedelta(seconds=window_seconds)
        self.attempts: dict[str, deque[datetime]] = defaultdict(deque)

    def allow(self, key: str, now: datetime) -> bool:
        entries = self.attempts[key]
        cutoff = now - self.window
        while entries and entries[0] <= cutoff:
            entries.popleft()
        if len(entries) >= self.max_attempts:
            return False
        entries.append(now)
        return True


class WalletService:
    """Application service for the synthetic local ledger and sync protocol."""

    def __init__(
        self,
        database: Database,
        mode: str = "local",
        now_fn: Callable[[], datetime] | None = None,
    ):
        self.db = database
        self.mode = mode
        self.now_fn = now_fn or utc_now
        self.fixture_auth = FixtureAuthAdapter(enabled=mode == "local")
        self.google_auth_configured = False
        self.pairing_limiter = AttemptLimiter()

    def now(self) -> datetime:
        return self.now_fn().astimezone(timezone.utc)

    def seed_synthetic(self) -> None:
        """Install deterministic families, identities, devices, and seed events."""
        if self.mode != "local":
            raise Problem("SYNTHETIC_SEED_DISABLED", "synthetic data is disabled in production mode", 403)
        with self.db.transaction("system") as connection:
            if connection.execute("SELECT 1 FROM families LIMIT 1").fetchone():
                return
            created = iso_now(self.now())
            families = [
                ("family-a", "credits", "UTC", created),
                ("family-b", "credits", "UTC", created),
            ]
            connection.executemany(
                "INSERT INTO families(id, unit_label, timezone, created_at) VALUES (?, ?, ?, ?)", families
            )
            members = [
                ("parent-a", "family-a", "parent", "Alex (synthetic)", "active", "parent-a", created),
                ("eddie-a", "family-a", "child", "Eddie A (synthetic)", "active", None, created),
                ("parent-b", "family-b", "parent", "Blair (synthetic)", "active", "parent-b", created),
                ("eddie-b", "family-b", "child", "Eddie B (synthetic)", "active", None, created),
            ]
            connection.executemany(
                """INSERT INTO family_members
                   (id, family_id, role, display_name, status, auth_subject, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                members,
            )
            devices = [
                ("device-a", "family-a", "eddie-a", "fixture-child-a", "Synthetic Eddie A tablet", created, created, None),
                ("device-b", "family-b", "eddie-b", "fixture-child-b", "Synthetic Eddie B tablet", created, created, None),
            ]
            connection.executemany(
                """INSERT INTO child_devices
                   (id, family_id, member_id, auth_subject, label, paired_at, last_seen_at, revoked_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                devices,
            )
            accounts = []
            for family_id, member_id in (("family-a", "eddie-a"), ("family-b", "eddie-b")):
                for kind in ("spending", "savings", "owed"):
                    accounts.append((f"account-{family_id}-{kind}", family_id, member_id, kind))
            connection.executemany(
                "INSERT INTO ledger_accounts(id, family_id, member_id, kind) VALUES (?, ?, ?, ?)", accounts
            )
            for identity, token in FIXTURE_TOKENS.items():
                fixture = FIXTURE_IDENTITIES[identity]
                connection.execute(
                    """INSERT INTO app_sessions
                       (id, token_hash, role, family_id, member_id, device_id, issued_at, expires_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        f"session-{identity}",
                        token_hash(token),
                        fixture.role,
                        fixture.family_id,
                        fixture.member_id,
                        fixture.device_id,
                        created,
                        "2099-01-01T00:00:00Z",
                    ),
                )
            self._seed_deposit(connection, "family-a", "parent-a", "eddie-a", 24, "seed-event-a", "seed-command-a")
            self._seed_deposit(connection, "family-b", "parent-b", "eddie-b", 12, "seed-event-b", "seed-command-b")

    def _seed_deposit(
        self,
        connection: sqlite3.Connection,
        family_id: str,
        actor_id: str,
        member_id: str,
        amount: int,
        event_id: str,
        command_id: str,
    ) -> None:
        recorded = iso_now(self.now())
        payload = {"member_id": member_id, "amount_units": amount}
        payload_hash = sha256_json({"kind": "deposit", "payload": payload, "effective_on": "2026-01-01", "note": "synthetic seed"})
        connection.execute(
            """INSERT INTO commands
               (command_id, family_id, actor_member_id, client_seq, base_position, kind,
                payload_json, payload_hash, effective_on, status, event_id, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'accepted', ?, ?)""",
            (command_id, family_id, actor_id, 1, 0, "deposit", canonical_json(payload), payload_hash, "2026-01-01", event_id, recorded),
        )
        position = connection.execute(
            "SELECT ledger_version FROM families WHERE id = ?", (family_id,)
        ).fetchone()[0] + 1
        connection.execute("UPDATE families SET ledger_version = ? WHERE id = ?", (position, family_id))
        connection.execute(
            """INSERT INTO ledger_events
               (event_id, family_id, member_id, kind, actor_member_id, effective_on,
                recorded_at, source, idempotency_key, note, payload_json, server_position)
               VALUES (?, ?, ?, 'deposit', ?, ?, ?, 'synthetic_seed', ?, ?, ?, ?)""",
            (event_id, family_id, member_id, actor_id, "2026-01-01", recorded, command_id, "synthetic seed", canonical_json(payload), position),
        )
        connection.execute(
            """INSERT INTO ledger_postings(event_id, family_id, account_id, delta_units)
               VALUES (?, ?, ?, ?)""",
            (event_id, family_id, f"account-{family_id}-spending", amount),
        )

    def fixture_login(self, identity: str) -> dict[str, Any]:
        try:
            token = self.fixture_auth.token_for(identity)
        except ValueError as exc:
            code = str(exc)
            status = 404 if code == "UNKNOWN_FIXTURE_IDENTITY" else 403
            raise Problem(code, "local fixture authentication is unavailable", status) from exc
        context = self.authenticate(token)
        return {"access_token": token, "token_type": "Bearer", **self.me(context)}

    def authenticate(self, token: str, allow_revoked: bool = False) -> SessionContext:
        if not token:
            raise Problem("AUTH_REQUIRED", "a bearer session is required", 401)
        if self.mode != "local" and token in FIXTURE_TOKENS.values():
            raise Problem("AUTH_INVALID", "local fixture sessions are disabled", 401)
        now = iso_now(self.now())
        with self.db.read("system") as connection:
            row = connection.execute(
                """SELECT s.id, s.role, s.family_id, s.member_id, s.device_id,
                          s.expires_at, s.revoked_at AS session_revoked,
                          d.revoked_at AS device_revoked
                   FROM app_sessions s
                   LEFT JOIN child_devices d ON d.id = s.device_id
                   WHERE s.token_hash = ?""",
                (token_hash(token),),
            ).fetchone()
        if row is None or row["expires_at"] <= now:
            raise Problem("AUTH_INVALID", "the session is invalid or expired", 401)
        revoked = bool(row["session_revoked"] or row["device_revoked"])
        context = SessionContext(row["id"], row["role"], row["family_id"], row["member_id"], row["device_id"], revoked)
        if revoked and not allow_revoked:
            raise Problem("DEVICE_REVOKED" if row["device_revoked"] else "SESSION_REVOKED", "access has been revoked", 403, "revoked")
        return context

    def require_parent(self, context: SessionContext) -> None:
        if context.revoked:
            raise Problem("DEVICE_REVOKED", "access has been revoked", 403, "revoked")
        if context.role != "parent":
            raise Problem("CHILD_WRITE_FORBIDDEN", "child sessions are read-only", 403)

    def require_active(self, context: SessionContext) -> None:
        if context.revoked:
            raise Problem("DEVICE_REVOKED", "access has been revoked", 403, "revoked")

    def me(self, context: SessionContext) -> dict[str, Any]:
        self.require_active(context)
        with self.db.read("system") as connection:
            row = connection.execute(
                """SELECT m.display_name, m.status, f.unit_label, f.timezone, f.ledger_version
                   FROM family_members m JOIN families f ON f.id = m.family_id
                   WHERE m.id = ? AND m.family_id = ?""",
                (context.member_id, context.family_id),
            ).fetchone()
        if row is None:
            raise Problem("AUTH_INVALID", "session membership is no longer active", 401)
        result = {
            "role": context.role,
            "family_id": context.family_id,
            "member_id": context.member_id,
            "display_name": row["display_name"],
            "status": row["status"],
            "unit_label": row["unit_label"],
            "timezone": row["timezone"],
        }
        if context.device_id:
            result["device_id"] = context.device_id
        return result

    def capabilities(self) -> dict[str, Any]:
        return {
            "api_version": "v1",
            "mode": self.mode,
            "storage": "sqlite-development-substitute",
            "fixture_auth": self.mode == "local",
            "google_auth": False,
            "virtual_only": True,
            "accepted_commands": sorted(ALLOWED_KINDS),
            "policy_gated_commands": POLICY_GATED_KINDS,
            "sync_states": ["confirmed", "pending_local", "rejected", "offline", "revoked"],
        }

    def _valid_uuid(self, value: Any, field: str) -> str:
        if not isinstance(value, str):
            raise Problem("INVALID_REQUEST", f"{field} must be a UUID", 422)
        try:
            parsed = uuid.UUID(value)
        except ValueError as exc:
            raise Problem("INVALID_REQUEST", f"{field} must be a UUID", 422) from exc
        if str(parsed) != value.lower():
            raise Problem("INVALID_REQUEST", f"{field} must use canonical UUID form", 422)
        return value

    def _valid_date(self, value: Any) -> str:
        if not isinstance(value, str):
            raise Problem("INVALID_REQUEST", "effective_on must be YYYY-MM-DD", 422)
        try:
            date.fromisoformat(value)
        except ValueError as exc:
            raise Problem("INVALID_REQUEST", "effective_on must be YYYY-MM-DD", 422) from exc
        return value

    def _payload_hash(self, kind: Any, payload: Any, effective_on: Any, note: Any) -> str:
        return sha256_json({"kind": kind, "payload": payload, "effective_on": effective_on, "note": note})

    def _projection(self, connection: sqlite3.Connection, family_id: str, member_id: str | None) -> dict[str, Any]:
        values = {"spending": 0, "savings": 0, "owed": 0}
        if member_id:
            rows = connection.execute(
                """SELECT a.kind, COALESCE(SUM(p.delta_units), 0) AS total
                   FROM ledger_accounts a
                   LEFT JOIN ledger_postings p ON p.account_id = a.id AND p.family_id = a.family_id
                   WHERE a.family_id = ? AND a.member_id = ?
                   GROUP BY a.kind""",
                (family_id, member_id),
            ).fetchall()
            for row in rows:
                values[row["kind"]] = int(row["total"])
        values["held"] = values["spending"] + values["savings"]
        values["checksum"] = sha256_json({"family_id": family_id, "member_id": member_id, **values})
        return values

    def projection(self, context: SessionContext, member_id: str | None = None) -> dict[str, Any]:
        self.require_active(context)
        if context.role == "child":
            if member_id and member_id != context.member_id:
                raise Problem("FORGED_SCOPE", "child scope cannot be changed", 403)
            member_id = context.member_id
        elif member_id is None:
            member_id = self._first_child(context.family_id)
        if not member_id:
            raise Problem("MEMBER_NOT_FOUND", "no child member is available", 404)
        with self.db.read("system") as connection:
            row = connection.execute(
                "SELECT id, role FROM family_members WHERE id = ? AND family_id = ?", (member_id, context.family_id)
            ).fetchone()
            if row is None or row["role"] != "child":
                raise Problem("CROSS_FAMILY_REFERENCE", "member is outside this family", 403)
            return {"family_id": context.family_id, "member_id": member_id, "projection": self._projection(connection, context.family_id, member_id)}

    def replay_projection(self, context: SessionContext, member_id: str | None = None) -> dict[str, Any]:
        """Rebuild a member projection from the immutable event stream in order."""
        self.require_active(context)
        if context.role == "child":
            if member_id and member_id != context.member_id:
                raise Problem("FORGED_SCOPE", "child scope cannot be changed", 403)
            member_id = context.member_id
        elif member_id is None:
            member_id = self._first_child(context.family_id)
        with self.db.read("system") as connection:
            row = connection.execute(
                "SELECT id, role FROM family_members WHERE id = ? AND family_id = ?", (member_id, context.family_id)
            ).fetchone()
            if row is None or row["role"] != "child":
                raise Problem("CROSS_FAMILY_REFERENCE", "member is outside this family", 403)
            values = {"spending": 0, "savings": 0, "owed": 0}
            for posting in connection.execute(
                """SELECT a.kind, p.delta_units FROM ledger_events e
                   JOIN ledger_postings p ON p.event_id = e.event_id
                   JOIN ledger_accounts a ON a.id = p.account_id
                   WHERE e.family_id = ? AND e.member_id = ?
                   ORDER BY e.server_position, p.posting_id""",
                (context.family_id, member_id),
            ):
                values[posting["kind"]] += int(posting["delta_units"])
            values["held"] = values["spending"] + values["savings"]
            values["checksum"] = sha256_json({"family_id": context.family_id, "member_id": member_id, **values})
            return {"family_id": context.family_id, "member_id": member_id, "projection": values}

    def _first_child(self, family_id: str) -> str | None:
        with self.db.read("system") as connection:
            row = connection.execute(
                "SELECT id FROM family_members WHERE family_id = ? AND role = 'child' ORDER BY id LIMIT 1", (family_id,)
            ).fetchone()
        return row[0] if row else None

    def _member_for_command(self, connection: sqlite3.Connection, family_id: str, member_id: Any) -> sqlite3.Row | None:
        if not isinstance(member_id, str):
            return None
        return connection.execute("SELECT * FROM family_members WHERE id = ? AND family_id = ?", (member_id, family_id)).fetchone()

    def _semantic_rejection(
        self,
        connection: sqlite3.Connection,
        context: SessionContext,
        request: dict[str, Any],
        kind: Any,
        payload: Any,
        effective_on: str,
    ) -> Rejection | None:
        if not isinstance(kind, str) or kind not in ALLOWED_KINDS | set(POLICY_GATED_KINDS):
            return Rejection("UNKNOWN_EVENT_KIND", "event kind is not supported")
        if kind in POLICY_GATED_KINDS:
            return Rejection("FEATURE_GATED_POLICY", POLICY_GATED_KINDS[kind])
        if not isinstance(payload, dict):
            return Rejection("INVALID_PAYLOAD", "payload must be an object")
        expected = {"member_id", "amount_units"} if kind != "reversal" else {"member_id", "event_id"}
        if set(payload) != expected:
            return Rejection("INVALID_PAYLOAD", "payload contains missing or unsupported fields")
        member = self._member_for_command(connection, context.family_id, payload.get("member_id"))
        if member is None:
            return Rejection("CROSS_FAMILY_REFERENCE", "target member is outside this family")
        if member["role"] != "child" or member["status"] != "active":
            return Rejection("INVALID_TARGET", "commands target an active child member")
        family_position = connection.execute("SELECT ledger_version FROM families WHERE id = ?", (context.family_id,)).fetchone()[0]
        if request["base_position"] > family_position:
            return Rejection("BASE_POSITION_AHEAD", "base_position is ahead of the server")
        note = request.get("note", "")
        if not isinstance(note, str) or len(note) > 280:
            return Rejection("INVALID_NOTE", "note must be at most 280 characters")
        if kind in {"withdrawal", "spending"} and not note.strip():
            return Rejection("REASON_REQUIRED", "withdrawal and spending records require a short reason")
        if kind != "reversal":
            amount = payload.get("amount_units")
            if isinstance(amount, bool) or not isinstance(amount, int) or amount <= 0:
                return Rejection("INVALID_AMOUNT", "amount_units must be a positive integer")
            if amount > MAX_UNITS:
                return Rejection("AMOUNT_OUT_OF_RANGE", "amount_units is outside BIGINT range")
            balances = self._projection(connection, context.family_id, member["id"])
            if kind == "deposit" and balances["spending"] > MAX_UNITS - amount:
                return Rejection("AMOUNT_OUT_OF_RANGE", "the resulting Spending Jar exceeds BIGINT range")
            if kind in {"withdrawal", "spending", "save"} and balances["spending"] < amount:
                return Rejection("INSUFFICIENT_SPENDING", "the Spending Jar cannot go below zero")
            return None
        original_id = payload.get("event_id")
        if not isinstance(original_id, str):
            return Rejection("INVALID_PAYLOAD", "reversal event_id is required")
        original = connection.execute(
            "SELECT * FROM ledger_events WHERE event_id = ? AND family_id = ?", (original_id, context.family_id)
        ).fetchone()
        if original is None:
            return Rejection("REVERSAL_TARGET_NOT_FOUND", "only an accepted event in this family can be reversed")
        if original["member_id"] != member["id"]:
            return Rejection("CROSS_FAMILY_REFERENCE", "reversal target does not belong to the requested member")
        if original["reversal_of"] is not None or original["kind"] == "reversal":
            return Rejection("REVERSAL_NOT_ALLOWED", "an event can have only one direct reversal")
        if connection.execute(
            "SELECT 1 FROM ledger_events WHERE family_id = ? AND reversal_of = ?", (context.family_id, original_id)
        ).fetchone():
            return Rejection("REVERSAL_ALREADY_EXISTS", "the accepted event has already been reversed")
        balances = self._projection(connection, context.family_id, member["id"])
        for posting in connection.execute(
            """SELECT a.kind, p.delta_units FROM ledger_postings p
               JOIN ledger_accounts a ON a.id = p.account_id
               WHERE p.event_id = ? AND p.family_id = ?""",
            (original_id, context.family_id),
        ):
            if balances[posting["kind"]] - int(posting["delta_units"]) < 0:
                return Rejection("REVERSAL_WOULD_OVERDRAW", "the exact correction would violate a non-negative jar")
        return None

    def _stored_outcome(self, connection: sqlite3.Connection, row: sqlite3.Row) -> dict[str, Any]:
        payload = json.loads(row["payload_json"])
        member_id = payload.get("member_id") if isinstance(payload, dict) else None
        family_position = connection.execute("SELECT ledger_version FROM families WHERE id = ?", (row["family_id"],)).fetchone()[0]
        result: dict[str, Any] = {
            "command_id": row["command_id"],
            "status": row["status"],
            "sync_state": "confirmed" if row["status"] == "accepted" else "rejected",
            "event_id": row["event_id"],
            "server_position": family_position,
            "projection": self._projection(connection, row["family_id"], member_id),
        }
        if row["event_id"]:
            event = connection.execute("SELECT * FROM ledger_events WHERE event_id = ?", (row["event_id"],)).fetchone()
            result["server_position"] = event["server_position"]
            result["event"] = self._event_json(connection, event)
        if row["status"] == "rejected":
            result["rejection"] = {"code": row["rejection_code"], "message": row["rejection_detail"]}
        return result

    def _event_json(self, connection: sqlite3.Connection, row: sqlite3.Row) -> dict[str, Any]:
        payload = json.loads(row["payload_json"])
        amount = payload.get("amount_units") if isinstance(payload, dict) else None
        unit = connection.execute("SELECT unit_label FROM families WHERE id = ?", (row["family_id"],)).fetchone()[0]
        if row["kind"] == "deposit":
            explanation = f"A parent recorded {amount} virtual {unit} in the Spending Jar."
        elif row["kind"] in {"withdrawal", "spending"}:
            explanation = f"A parent recorded a virtual reduction of {amount} {unit} from the Spending Jar."
        elif row["kind"] == "save":
            explanation = f"A parent moved {amount} virtual {unit} from the Spending Jar into the Save Jar."
        else:
            explanation = "A parent reversed an earlier virtual record."
        postings = [
            {"account": posting["kind"], "delta_units": int(posting["delta_units"])}
            for posting in connection.execute(
                """SELECT a.kind, p.delta_units FROM ledger_postings p
                   JOIN ledger_accounts a ON a.id = p.account_id
                   WHERE p.event_id = ? AND p.family_id = ? ORDER BY a.kind""",
                (row["event_id"], row["family_id"]),
            )
        ]
        return {
            "event_id": row["event_id"],
            "family_id": row["family_id"],
            "member_id": row["member_id"],
            "kind": row["kind"],
            "schema_version": row["schema_version"],
            "effective_on": row["effective_on"],
            "recorded_at": row["recorded_at"],
            "server_position": row["server_position"],
            "source": row["source"],
            "note": row["note"],
            "payload": payload,
            "reversal_of": row["reversal_of"],
            "postings": postings,
            "explanation": explanation,
        }

    def submit_command(self, context: SessionContext, request: dict[str, Any]) -> dict[str, Any]:
        self.require_parent(context)
        if not isinstance(request, dict):
            raise Problem("INVALID_REQUEST", "command must be a JSON object", 422)
        command_id = self._valid_uuid(request.get("command_id"), "command_id")
        kind = request.get("kind")
        payload = request.get("payload")
        effective_on = self._valid_date(request.get("effective_on"))
        if isinstance(request.get("client_seq"), bool) or not isinstance(request.get("client_seq"), int) or request["client_seq"] <= 0:
            raise Problem("INVALID_REQUEST", "client_seq must be a positive integer", 422)
        if isinstance(request.get("base_position"), bool) or not isinstance(request.get("base_position"), int) or request["base_position"] < 0:
            raise Problem("INVALID_REQUEST", "base_position must be a non-negative integer", 422)
        if "family_id" in request and request["family_id"] != context.family_id:
            raise Problem("FORGED_SCOPE", "family scope is derived from the session", 403)
        if "role" in request and request["role"] != "parent":
            raise Problem("FORGED_ROLE", "role is derived from the session", 403)
        if "mode" in request and request["mode"] != "parent":
            raise Problem("FORGED_ROLE", "mode is derived from the session", 403)
        if any(field in request for field in ("balance", "projection", "postings", "delta_units")):
            raise Problem("CLIENT_DERIVED_STATE_FORBIDDEN", "balances and postings are server-derived", 422)
        if isinstance(request.get("note", ""), str) and len(request.get("note", "")) > 280:
            raise Problem("INVALID_REQUEST", "note must be at most 280 characters", 422)
        try:
            payload_hash = self._payload_hash(kind, payload, effective_on, request.get("note", ""))
        except (TypeError, ValueError) as exc:
            raise Problem("INVALID_REQUEST", "payload must be JSON-serializable", 422) from exc
        now = iso_now(self.now())
        with self.db.transaction("parent") as connection:
            existing = connection.execute(
                "SELECT * FROM commands WHERE family_id = ? AND command_id = ?", (context.family_id, command_id)
            ).fetchone()
            if existing:
                if existing["payload_hash"] != payload_hash:
                    raise Problem("DUPLICATE_PAYLOAD_MISMATCH", "command_id was already used for a different payload", 409)
                return self._stored_outcome(connection, existing)
            rejection = self._semantic_rejection(connection, context, request, kind, payload, effective_on)
            if rejection:
                connection.execute(
                    """INSERT INTO commands
                       (command_id, family_id, actor_member_id, device_id, client_seq, base_position,
                        kind, payload_json, payload_hash, effective_on, status, rejection_code,
                        rejection_detail, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'rejected', ?, ?, ?)""",
                    (
                        command_id,
                        context.family_id,
                        context.member_id,
                        context.device_id,
                        request["client_seq"],
                        request["base_position"],
                        str(kind),
                        canonical_json(payload),
                        payload_hash,
                        effective_on,
                        rejection.code,
                        rejection.detail,
                        now,
                    ),
                )
                row = connection.execute(
                    "SELECT * FROM commands WHERE family_id = ? AND command_id = ?", (context.family_id, command_id)
                ).fetchone()
                return self._stored_outcome(connection, row)

            member_id = payload["member_id"]
            event_id = str(uuid.uuid4())
            position = connection.execute("SELECT ledger_version FROM families WHERE id = ?", (context.family_id,)).fetchone()[0] + 1
            connection.execute(
                """INSERT INTO commands
                   (command_id, family_id, actor_member_id, device_id, client_seq, base_position,
                    kind, payload_json, payload_hash, effective_on, status, event_id, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'accepted', ?, ?)""",
                (
                    command_id,
                    context.family_id,
                    context.member_id,
                    context.device_id,
                    request["client_seq"],
                    request["base_position"],
                    kind,
                    canonical_json(payload),
                    payload_hash,
                    effective_on,
                    event_id,
                    now,
                ),
            )
            connection.execute("UPDATE families SET ledger_version = ? WHERE id = ?", (position, context.family_id))
            source = "reversal" if kind == "reversal" else "manual"
            reversal_of = payload.get("event_id") if kind == "reversal" else None
            connection.execute(
                """INSERT INTO ledger_events
                   (event_id, family_id, member_id, kind, schema_version, actor_member_id,
                    effective_on, recorded_at, source, idempotency_key, note, payload_json,
                    reversal_of, server_position)
                   VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    event_id,
                    context.family_id,
                    member_id,
                    kind,
                    context.member_id,
                    effective_on,
                    now,
                    source,
                    command_id,
                    request.get("note", ""),
                    canonical_json(payload),
                    reversal_of,
                    position,
                ),
            )
            postings = self._postings_for_accepted(connection, context.family_id, member_id, kind, payload)
            connection.executemany(
                "INSERT INTO ledger_postings(event_id, family_id, account_id, delta_units) VALUES (?, ?, ?, ?)",
                [(event_id, context.family_id, account_id, delta) for account_id, delta in postings],
            )
            row = connection.execute(
                "SELECT * FROM commands WHERE family_id = ? AND command_id = ?", (context.family_id, command_id)
            ).fetchone()
            return self._stored_outcome(connection, row)

    def _postings_for_accepted(
        self,
        connection: sqlite3.Connection,
        family_id: str,
        member_id: str,
        kind: str,
        payload: dict[str, Any],
    ) -> list[tuple[str, int]]:
        accounts = {
            row["kind"]: row["id"]
            for row in connection.execute(
                "SELECT id, kind FROM ledger_accounts WHERE family_id = ? AND member_id = ?", (family_id, member_id)
            )
        }
        if kind == "deposit":
            return [(accounts["spending"], payload["amount_units"])]
        if kind in {"withdrawal", "spending"}:
            return [(accounts["spending"], -payload["amount_units"])]
        if kind == "save":
            return [(accounts["spending"], -payload["amount_units"]), (accounts["savings"], payload["amount_units"])]
        original = connection.execute(
            "SELECT account_id, delta_units FROM ledger_postings WHERE event_id = ? AND family_id = ?",
            (payload["event_id"], family_id),
        ).fetchall()
        return [(row["account_id"], -int(row["delta_units"])) for row in original]

    def get_command(self, context: SessionContext, command_id: str) -> dict[str, Any]:
        self.require_parent(context)
        with self.db.read("system") as connection:
            row = connection.execute(
                "SELECT * FROM commands WHERE family_id = ? AND command_id = ?", (context.family_id, command_id)
            ).fetchone()
            if row is None:
                raise Problem("COMMAND_NOT_FOUND", "command is not in this family", 404)
            return self._stored_outcome(connection, row)

    def sync(self, context: SessionContext, after: int = 0, limit: int = 200) -> dict[str, Any]:
        self.require_active(context)
        if not isinstance(after, int) or after < 0:
            raise Problem("INVALID_CURSOR", "after must be a non-negative integer", 422)
        if not isinstance(limit, int) or not 1 <= limit <= 200:
            raise Problem("INVALID_LIMIT", "limit must be between 1 and 200", 422)
        with self.db.read("system") as connection:
            family_position = connection.execute("SELECT ledger_version FROM families WHERE id = ?", (context.family_id,)).fetchone()[0]
            scope = " AND e.member_id = ?" if context.role == "child" else ""
            params: list[Any] = [context.family_id, after]
            if context.role == "child":
                params.append(context.member_id)
            params.append(limit + 1)
            rows = connection.execute(
                f"""SELECT e.* FROM ledger_events e
                    WHERE e.family_id = ? AND e.server_position > ? {scope}
                    ORDER BY e.server_position LIMIT ?""",
                params,
            ).fetchall()
            has_more = len(rows) > limit
            rows = rows[:limit]
            events = [self._event_json(connection, row) for row in rows]
            next_position = rows[-1]["server_position"] if rows else after
            if context.role == "child":
                projections = [{"member_id": context.member_id, **self._projection(connection, context.family_id, context.member_id)}]
            else:
                members = connection.execute(
                    "SELECT id FROM family_members WHERE family_id = ? AND role = 'child' ORDER BY id", (context.family_id,)
                ).fetchall()
                projections = [
                    {"member_id": member["id"], **self._projection(connection, context.family_id, member["id"])}
                    for member in members
                ]
            checksum = sha256_json({"family_id": context.family_id, "projections": projections, "position": family_position})
        if context.device_id:
            with self.db.transaction("system") as connection:
                connection.execute("UPDATE child_devices SET last_seen_at = ? WHERE id = ?", (iso_now(self.now()), context.device_id))
        return {
            "schema_version": 1,
            "state": "confirmed",
            "confirmed": True,
            "family_id": context.family_id,
            "after": after,
            "next_position": next_position,
            "server_position": family_position,
            "has_more": has_more,
            "events": events,
            "projections": projections,
            "projection_checksum": checksum,
            "pending_local": 0,
            "offline": False,
        }

    def create_pairing_code(self, context: SessionContext, member_id: Any, label: Any = "Eddie device") -> dict[str, Any]:
        self.require_parent(context)
        if not isinstance(member_id, str) or not isinstance(label, str) or not 1 <= len(label) <= 80:
            raise Problem("INVALID_REQUEST", "member_id and a device label are required", 422)
        with self.db.transaction("parent") as connection:
            member = self._member_for_command(connection, context.family_id, member_id)
            if member is None or member["role"] != "child":
                raise Problem("CROSS_FAMILY_REFERENCE", "pairing target is outside this family", 403)
            token = secrets.token_urlsafe(32)
            code_id = str(uuid.uuid4())
            expires = iso_now(self.now() + timedelta(minutes=10))
            connection.execute(
                """INSERT INTO pairing_codes
                   (id, family_id, member_id, token_hash, expires_at, created_at, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (code_id, context.family_id, member_id, token_hash(token), expires, iso_now(self.now()), context.member_id),
            )
        return {"pairing_code": token, "expires_at": expires, "member_id": member_id, "device_label": label}

    def redeem_pairing(self, token: Any, label: Any, limiter_key: str = "local") -> dict[str, Any]:
        now = self.now()
        if not isinstance(token, str) or not token or not isinstance(label, str) or not 1 <= len(label) <= 80:
            raise Problem("INVALID_REQUEST", "pairing code and device label are required", 422)
        if not self.pairing_limiter.allow(limiter_key, now):
            raise Problem("PAIRING_RATE_LIMITED", "too many pairing attempts", 429)
        child_token = secrets.token_urlsafe(32)
        with self.db.transaction("system") as connection:
            row = connection.execute(
                """SELECT * FROM pairing_codes
                   WHERE token_hash = ? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > ?""",
                (token_hash(token), iso_now(now)),
            ).fetchone()
            if row is None:
                raise Problem("PAIRING_INVALID_OR_EXPIRED", "pairing code is invalid, expired, or already used", 400)
            consumed = iso_now(now)
            connection.execute("UPDATE pairing_codes SET consumed_at = ? WHERE id = ?", (consumed, row["id"]))
            device_id = str(uuid.uuid4())
            subject = f"child-device:{device_id}"
            connection.execute(
                """INSERT INTO child_devices
                   (id, family_id, member_id, auth_subject, label, paired_at, last_seen_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (device_id, row["family_id"], row["member_id"], subject, label, consumed, consumed),
            )
            session_id = str(uuid.uuid4())
            connection.execute(
                """INSERT INTO app_sessions
                   (id, token_hash, role, family_id, member_id, device_id, issued_at, expires_at)
                   VALUES (?, ?, 'child', ?, ?, ?, ?, ?)""",
                (
                    session_id,
                    token_hash(child_token),
                    row["family_id"],
                    row["member_id"],
                    device_id,
                    consumed,
                    iso_now(now + timedelta(days=30)),
                ),
            )
        child_context = self.authenticate(child_token)
        return {"access_token": child_token, "token_type": "Bearer", **self.me(child_context)}

    def list_devices(self, context: SessionContext) -> list[dict[str, Any]]:
        self.require_parent(context)
        with self.db.read("system") as connection:
            return [
                dict(row)
                for row in connection.execute(
                    """SELECT id, member_id, label, paired_at, last_seen_at, revoked_at
                       FROM child_devices WHERE family_id = ? ORDER BY paired_at, id""",
                    (context.family_id,),
                )
            ]

    def revoke_device(self, context: SessionContext, device_id: str) -> dict[str, Any]:
        self.require_parent(context)
        with self.db.transaction("system") as connection:
            row = connection.execute(
                "SELECT id, family_id, revoked_at FROM child_devices WHERE id = ? AND family_id = ?",
                (device_id, context.family_id),
            ).fetchone()
            if row is None:
                raise Problem("DEVICE_NOT_FOUND", "device is not in this family", 404)
            revoked = row["revoked_at"] or iso_now(self.now())
            connection.execute("UPDATE child_devices SET revoked_at = ? WHERE id = ?", (revoked, device_id))
            connection.execute(
                "UPDATE app_sessions SET revoked_at = ? WHERE device_id = ? AND revoked_at IS NULL", (revoked, device_id)
            )
        return {"device_id": device_id, "state": "revoked", "revoked_at": revoked, "clear_local_data": True}

    def readiness(self) -> dict[str, Any]:
        try:
            with self.db.read("system") as connection:
                connection.execute("SELECT 1").fetchone()
            return {"status": "ready", "storage": "sqlite-development-substitute", "mode": self.mode}
        except sqlite3.Error:
            return {"status": "not_ready", "storage": "sqlite-development-substitute", "mode": self.mode}
