PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS families (
    id TEXT PRIMARY KEY,
    unit_label TEXT NOT NULL CHECK (length(unit_label) BETWEEN 1 AND 32),
    timezone TEXT NOT NULL,
    ledger_version INTEGER NOT NULL DEFAULT 0 CHECK (ledger_version >= 0),
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS family_members (
    id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('owner', 'parent', 'child')),
    display_name TEXT NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    auth_subject TEXT UNIQUE,
    created_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    UNIQUE (family_id, id)
);

CREATE TABLE IF NOT EXISTS child_devices (
    id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    auth_subject TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL CHECK (length(label) BETWEEN 1 AND 80),
    paired_at TEXT NOT NULL,
    last_seen_at TEXT,
    revoked_at TEXT,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    UNIQUE (family_id, id)
);

CREATE TABLE IF NOT EXISTS pairing_codes (
    id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    consumed_at TEXT,
    revoked_at TEXT,
    created_at TEXT NOT NULL,
    created_by TEXT NOT NULL,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, created_by) REFERENCES family_members(family_id, id)
);

CREATE TABLE IF NOT EXISTS ledger_accounts (
    id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('spending', 'savings', 'owed')),
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    UNIQUE (family_id, member_id, kind),
    UNIQUE (family_id, id)
);

CREATE TABLE IF NOT EXISTS commands (
    row_id INTEGER PRIMARY KEY AUTOINCREMENT,
    command_id TEXT NOT NULL,
    family_id TEXT NOT NULL,
    actor_member_id TEXT NOT NULL,
    device_id TEXT,
    client_seq INTEGER NOT NULL CHECK (client_seq > 0),
    base_position INTEGER NOT NULL CHECK (base_position >= 0),
    kind TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    effective_on TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('accepted', 'rejected')),
    rejection_code TEXT,
    rejection_detail TEXT,
    event_id TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (family_id, actor_member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, device_id) REFERENCES child_devices(family_id, id),
    UNIQUE (family_id, command_id)
);

CREATE TABLE IF NOT EXISTS ledger_events (
    event_id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('deposit', 'withdrawal', 'spending', 'save', 'reversal')),
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    actor_member_id TEXT NOT NULL,
    effective_on TEXT NOT NULL,
    recorded_at TEXT NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('manual', 'synthetic_seed', 'reversal')),
    idempotency_key TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    payload_json TEXT NOT NULL,
    reversal_of TEXT,
    server_position INTEGER NOT NULL CHECK (server_position > 0),
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, actor_member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, reversal_of) REFERENCES ledger_events(family_id, event_id),
    UNIQUE (family_id, event_id),
    UNIQUE (family_id, server_position),
    UNIQUE (family_id, idempotency_key),
    UNIQUE (family_id, reversal_of)
);

CREATE TABLE IF NOT EXISTS ledger_postings (
    posting_id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL,
    family_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    delta_units INTEGER NOT NULL CHECK (delta_units <> 0),
    FOREIGN KEY (event_id) REFERENCES ledger_events(event_id),
    FOREIGN KEY (family_id, event_id) REFERENCES ledger_events(family_id, event_id),
    FOREIGN KEY (family_id, account_id) REFERENCES ledger_accounts(family_id, id),
    UNIQUE (event_id, account_id)
);

CREATE TABLE IF NOT EXISTS app_sessions (
    id TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK (role IN ('parent', 'child')),
    family_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    device_id TEXT,
    issued_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked_at TEXT,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, device_id) REFERENCES child_devices(family_id, id)
);

CREATE INDEX IF NOT EXISTS idx_events_family_position ON ledger_events(family_id, server_position);
CREATE INDEX IF NOT EXISTS idx_postings_family_account ON ledger_postings(family_id, account_id);
CREATE INDEX IF NOT EXISTS idx_commands_family_command ON commands(family_id, command_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON app_sessions(token_hash);

-- SQLite is the explicitly local development substitute. These triggers provide
-- the persistence-layer read-only boundary for child-role connections. The
-- PostgreSQL migration adds restricted roles/RLS as the production boundary.
CREATE TRIGGER IF NOT EXISTS commands_parent_insert_only
BEFORE INSERT ON commands
WHEN actor_role() NOT IN ('parent', 'system')
BEGIN
    SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN');
END;

CREATE TRIGGER IF NOT EXISTS events_parent_insert_only
BEFORE INSERT ON ledger_events
WHEN actor_role() NOT IN ('parent', 'system')
BEGIN
    SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN');
END;

CREATE TRIGGER IF NOT EXISTS postings_parent_insert_only
BEFORE INSERT ON ledger_postings
WHEN actor_role() NOT IN ('parent', 'system')
BEGIN
    SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN');
END;

CREATE TRIGGER IF NOT EXISTS events_immutable_update
BEFORE UPDATE ON ledger_events
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_LEDGER');
END;

CREATE TRIGGER IF NOT EXISTS events_immutable_delete
BEFORE DELETE ON ledger_events
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_LEDGER');
END;

CREATE TRIGGER IF NOT EXISTS postings_immutable_update
BEFORE UPDATE ON ledger_postings
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_LEDGER');
END;

CREATE TRIGGER IF NOT EXISTS postings_immutable_delete
BEFORE DELETE ON ledger_postings
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_LEDGER');
END;

CREATE TRIGGER IF NOT EXISTS commands_immutable_update
BEFORE UPDATE ON commands
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_COMMAND_OUTCOME');
END;

CREATE TRIGGER IF NOT EXISTS commands_immutable_delete
BEFORE DELETE ON commands
BEGIN
    SELECT RAISE(ABORT, 'IMMUTABLE_COMMAND_OUTCOME');
END;

CREATE TRIGGER IF NOT EXISTS postings_same_family
BEFORE INSERT ON ledger_postings
WHEN (SELECT family_id FROM ledger_events WHERE event_id = NEW.event_id) <> NEW.family_id
  OR (SELECT family_id FROM ledger_accounts WHERE id = NEW.account_id) <> NEW.family_id
BEGIN
    SELECT RAISE(ABORT, 'CROSS_FAMILY_REFERENCE');
END;

-- A child-role persistence connection cannot mutate any table, not only the
-- ledger. The trusted service uses the system role for revocation/last-seen
-- bookkeeping and the parent role for append transactions.
CREATE TRIGGER IF NOT EXISTS families_child_insert
BEFORE INSERT ON families WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS families_child_update
BEFORE UPDATE ON families WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS families_child_delete
BEFORE DELETE ON families WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS members_child_insert
BEFORE INSERT ON family_members WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS members_child_update
BEFORE UPDATE ON family_members WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS members_child_delete
BEFORE DELETE ON family_members WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS devices_child_insert
BEFORE INSERT ON child_devices WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS devices_child_update
BEFORE UPDATE ON child_devices WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS devices_child_delete
BEFORE DELETE ON child_devices WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS pairing_child_insert
BEFORE INSERT ON pairing_codes WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS pairing_child_update
BEFORE UPDATE ON pairing_codes WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS pairing_child_delete
BEFORE DELETE ON pairing_codes WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS accounts_child_insert
BEFORE INSERT ON ledger_accounts WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS accounts_child_update
BEFORE UPDATE ON ledger_accounts WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS accounts_child_delete
BEFORE DELETE ON ledger_accounts WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS sessions_child_insert
BEFORE INSERT ON app_sessions WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS sessions_child_update
BEFORE UPDATE ON app_sessions WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
CREATE TRIGGER IF NOT EXISTS sessions_child_delete
BEFORE DELETE ON app_sessions WHEN actor_role() = 'child' BEGIN SELECT RAISE(ABORT, 'CHILD_WRITE_FORBIDDEN'); END;
