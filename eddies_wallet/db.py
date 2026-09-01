from __future__ import annotations

import contextlib
import os
import sqlite3
from pathlib import Path
from typing import Iterator


class Database:
    """Small SQLite development store with an explicit connection actor role.

    SQLite is a local-only development substitute.  The SQL table names and
    constraints intentionally mirror the PostgreSQL migration in
    ``migrations/001_initial.sql``.  Every write made by the service is inside
    a transaction; child-role connections are rejected by schema triggers.
    """

    def __init__(self, path: str | os.PathLike[str]):
        self.path = str(path)
        if self.path != ":memory:":
            Path(self.path).parent.mkdir(parents=True, exist_ok=True)

    def connect(self, actor_role: str = "system") -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA busy_timeout = 10000")
        connection.create_function("actor_role", 0, lambda: actor_role)
        return connection

    def initialize(self, reset: bool = False) -> None:
        if reset and self.path != ":memory:":
            try:
                Path(self.path).unlink()
            except FileNotFoundError:
                pass
        connection = self.connect("system")
        try:
            schema = Path(__file__).with_name("schema.sql").read_text(encoding="utf-8")
            connection.executescript(schema)
        finally:
            connection.close()

    @contextlib.contextmanager
    def transaction(self, actor_role: str = "system") -> Iterator[sqlite3.Connection]:
        connection = self.connect(actor_role)
        try:
            connection.execute("BEGIN IMMEDIATE")
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @contextlib.contextmanager
    def read(self, actor_role: str = "system") -> Iterator[sqlite3.Connection]:
        connection = self.connect(actor_role)
        try:
            yield connection
        finally:
            connection.close()
