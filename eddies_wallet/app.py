from __future__ import annotations

import os
from pathlib import Path

from .db import Database
from .service import WalletService


class EddiesWalletApp:
    """Configured local service application."""

    def __init__(
        self,
        db_path: str | os.PathLike[str],
        mode: str | None = None,
        reset: bool = False,
        seed: bool = True,
    ):
        self.mode = mode or os.environ.get("EDDIES_ENV", "local")
        if self.mode not in {"local", "production"}:
            raise ValueError("EDDIES_ENV must be local or production")
        self.database = Database(db_path)
        self.database.initialize(reset=reset)
        self.service = WalletService(self.database, mode=self.mode)
        if seed and self.mode == "local":
            self.service.seed_synthetic()

    def health(self) -> dict[str, str]:
        return {"status": "ok", "service": "eddies-wallet-local-backend", "mode": self.mode}


def default_database_path() -> str:
    return os.environ.get("EDDIES_DB_PATH", str(Path(".data") / "eddies-wallet.sqlite3"))
