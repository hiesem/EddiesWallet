from __future__ import annotations

import hashlib
from dataclasses import dataclass


# Deliberately public, synthetic fixture values.  They are only accepted when
# EDDIES_ENV=local (the default for tests/development); they are not Google
# credentials and are never a production authentication mechanism.
FIXTURE_TOKENS: dict[str, str] = {
    "parent-a": "local-fixture-parent-a-v1",
    "child-a": "local-fixture-child-a-v1",
    "parent-b": "local-fixture-parent-b-v1",
    "child-b": "local-fixture-child-b-v1",
}


@dataclass(frozen=True)
class FixtureIdentity:
    identity: str
    role: str
    family_id: str
    member_id: str
    device_id: str | None


FIXTURE_IDENTITIES = {
    "parent-a": FixtureIdentity("parent-a", "parent", "family-a", "parent-a", None),
    "child-a": FixtureIdentity("child-a", "child", "family-a", "eddie-a", "device-a"),
    "parent-b": FixtureIdentity("parent-b", "parent", "family-b", "parent-b", None),
    "child-b": FixtureIdentity("child-b", "child", "family-b", "eddie-b", "device-b"),
}


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class FixtureAuthAdapter:
    """Explicit local identity adapter; intentionally unavailable in production."""

    def __init__(self, enabled: bool):
        self.enabled = enabled

    def token_for(self, identity: str) -> str:
        if not self.enabled:
            raise ValueError("LOCAL_AUTH_DISABLED")
        try:
            return FIXTURE_TOKENS[identity]
        except KeyError as exc:
            raise ValueError("UNKNOWN_FIXTURE_IDENTITY") from exc


class GoogleTokenProvider:
    """Production seam, intentionally unconfigured in this local slice.

    A later setup task must verify Google signatures, issuer, audience, expiry,
    nonce where applicable, and stable subject before issuing an app session.
    No token inspection or fake verification is performed here.
    """

    def verify(self, _id_token: str, _nonce: str | None = None) -> None:
        raise RuntimeError("GOOGLE_AUTH_NOT_CONFIGURED")
