-- PostgreSQL migration boundary for the self-hosted prototype direction.
-- This file is intentionally not applied by the local SQLite runner. Keep table
-- names/semantics aligned with eddies_wallet/schema.sql until a PostgreSQL
-- adapter and restricted API role are added.

CREATE TABLE families (
    id uuid PRIMARY KEY,
    unit_label text NOT NULL CHECK (length(unit_label) BETWEEN 1 AND 32),
    timezone text NOT NULL,
    ledger_version bigint NOT NULL DEFAULT 0 CHECK (ledger_version >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE family_members (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL REFERENCES families(id),
    role text NOT NULL CHECK (role IN ('owner', 'parent', 'child')),
    display_name text NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    auth_subject text UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (family_id, id)
);

CREATE TABLE child_devices (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    member_id uuid NOT NULL,
    auth_subject text NOT NULL UNIQUE,
    label text NOT NULL,
    paired_at timestamptz NOT NULL,
    last_seen_at timestamptz,
    revoked_at timestamptz,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    UNIQUE (family_id, id)
);

CREATE TABLE pairing_codes (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    member_id uuid NOT NULL,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, created_by) REFERENCES family_members(family_id, id)
);

CREATE TABLE ledger_accounts (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    member_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('spending', 'savings', 'owed')),
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    UNIQUE (family_id, member_id, kind),
    UNIQUE (family_id, id)
);

CREATE TABLE commands (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL REFERENCES families(id),
    actor_member_id uuid NOT NULL,
    client_device_id uuid,
    client_seq bigint NOT NULL CHECK (client_seq > 0),
    base_position bigint NOT NULL CHECK (base_position >= 0),
    kind text NOT NULL,
    payload jsonb NOT NULL,
    payload_hash text NOT NULL,
    effective_on date NOT NULL,
    status text NOT NULL CHECK (status IN ('accepted', 'rejected')),
    rejection_code text,
    rejection_detail text,
    event_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (family_id, actor_member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, client_device_id) REFERENCES child_devices(family_id, id),
    UNIQUE (family_id, id),
    UNIQUE (family_id, event_id)
);

CREATE TABLE ledger_events (
    event_id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    member_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('deposit', 'withdrawal', 'spending', 'save', 'reversal')),
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    actor_member_id uuid NOT NULL,
    effective_on date NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    source text NOT NULL CHECK (source IN ('manual', 'synthetic_seed', 'reversal')),
    idempotency_key uuid NOT NULL,
    note text NOT NULL DEFAULT '',
    payload jsonb NOT NULL,
    reversal_of uuid,
    server_position bigint NOT NULL CHECK (server_position > 0),
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, actor_member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, reversal_of) REFERENCES ledger_events(family_id, event_id),
    UNIQUE (family_id, event_id),
    UNIQUE (family_id, server_position),
    UNIQUE (family_id, idempotency_key),
    UNIQUE (family_id, reversal_of)
);

CREATE TABLE ledger_postings (
    posting_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL,
    family_id uuid NOT NULL,
    account_id uuid NOT NULL,
    delta_units bigint NOT NULL CHECK (delta_units <> 0),
    FOREIGN KEY (family_id, event_id) REFERENCES ledger_events(family_id, event_id),
    FOREIGN KEY (family_id, account_id) REFERENCES ledger_accounts(family_id, id),
    UNIQUE (event_id, account_id)
);

CREATE TABLE app_sessions (
    id uuid PRIMARY KEY,
    token_hash text NOT NULL UNIQUE,
    role text NOT NULL CHECK (role IN ('parent', 'child')),
    family_id uuid NOT NULL,
    member_id uuid NOT NULL,
    device_id uuid,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    FOREIGN KEY (family_id, member_id) REFERENCES family_members(family_id, id),
    FOREIGN KEY (family_id, device_id) REFERENCES child_devices(family_id, id)
);

-- Defense-in-depth policy starting point. The API must set a trusted,
-- transaction-local app.family_id/app.member_id context after validating its
-- session. The client never connects with the owner, superuser, or BYPASSRLS.
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE child_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE pairing_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_postings ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_sessions ENABLE ROW LEVEL SECURITY;

-- The eventual API role receives only allow-listed read views and a narrowly
-- scoped append function. No generic client INSERT/UPDATE/DELETE grants are
-- included in this migration. Concrete policies belong with the PostgreSQL
-- adapter once its session-context contract is implemented and tested.
