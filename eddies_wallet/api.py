from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, unquote, urlsplit

from .app import EddiesWalletApp
from .service import Problem

MAX_BODY_BYTES = 1_048_576


def create_server(app: EddiesWalletApp, host: str = "127.0.0.1", port: int = 8080) -> ThreadingHTTPServer:
    application = app

    class Handler(BaseHTTPRequestHandler):
        server_version = "EddiesWalletLocal/0.1"
        app = application

        def log_message(self, fmt: str, *args: Any) -> None:
            # Keep request logs free of bodies, bearer tokens, notes, and pairing codes.
            super().log_message("%s", fmt % args)

        def _send(self, status: int, body: dict[str, Any]) -> None:
            data = json.dumps(body, sort_keys=True, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def _problem(self, problem: Problem) -> None:
            self._send(problem.status, {"error": problem.as_dict()})

        def _json_body(self) -> dict[str, Any]:
            content_length = self.headers.get("Content-Length")
            try:
                size = int(content_length or "0")
            except ValueError as exc:
                raise Problem("INVALID_REQUEST", "Content-Length must be valid", 400) from exc
            if size <= 0 or size > MAX_BODY_BYTES:
                raise Problem("INVALID_REQUEST", "a JSON request body is required", 400)
            try:
                raw = self.rfile.read(size)
                value = json.loads(raw.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise Problem("INVALID_REQUEST", "request body must be valid JSON", 400) from exc
            if not isinstance(value, dict):
                raise Problem("INVALID_REQUEST", "request body must be a JSON object", 422)
            return value

        def _token(self) -> str:
            header = self.headers.get("Authorization", "")
            scheme, _, token = header.partition(" ")
            if scheme.lower() != "bearer" or not token.strip():
                raise Problem("AUTH_REQUIRED", "a bearer session is required", 401)
            return token.strip()

        def _context(self, allow_revoked: bool = False):
            return self.app.service.authenticate(self._token(), allow_revoked=allow_revoked)

        def _query_int(self, query: dict[str, list[str]], name: str, default: int) -> int:
            value = query.get(name, [str(default)])[0]
            try:
                return int(value)
            except ValueError as exc:
                raise Problem("INVALID_REQUEST", f"{name} must be an integer", 422) from exc

        def do_GET(self) -> None:
            try:
                parsed = urlsplit(self.path)
                path = parsed.path.rstrip("/") or "/"
                query = parse_qs(parsed.query, keep_blank_values=True)
                if path == "/healthz":
                    self._send(200, self.app.health())
                    return
                if path == "/readyz":
                    readiness = self.app.service.readiness()
                    self._send(200 if readiness["status"] == "ready" else 503, readiness)
                    return
                if path == "/v1/capabilities":
                    self._send(200, self.app.service.capabilities())
                    return
                if path == "/v1/me":
                    self._send(200, self.app.service.me(self._context()))
                    return
                if path == "/v1/sync":
                    context = self._context(allow_revoked=True)
                    self._send(200, self.app.service.sync(
                        context,
                        after=self._query_int(query, "after", 0),
                        limit=self._query_int(query, "limit", 200),
                    ))
                    return
                if path == "/v1/projection":
                    member_id = query.get("member_id", [None])[0]
                    self._send(200, self.app.service.projection(self._context(), member_id))
                    return
                if path == "/v1/devices":
                    self._send(200, {"devices": self.app.service.list_devices(self._context())})
                    return
                if path.startswith("/v1/commands/"):
                    command_id = unquote(path.removeprefix("/v1/commands/"))
                    self._send(200, self.app.service.get_command(self._context(), command_id))
                    return
                raise Problem("NOT_FOUND", "endpoint not found", 404)
            except Problem as problem:
                self._problem(problem)
            except Exception:
                self._problem(Problem("INTERNAL_ERROR", "the local service could not complete the request", 500))

        def do_POST(self) -> None:
            try:
                parsed = urlsplit(self.path)
                path = parsed.path.rstrip("/") or "/"
                if path == "/v1/auth/fixture":
                    body = self._json_body()
                    self._send(200, self.app.service.fixture_login(body.get("identity")))
                    return
                if path == "/v1/pairing/redeem":
                    body = self._json_body()
                    self._send(200, self.app.service.redeem_pairing(
                        body.get("pairing_code"),
                        body.get("device_label"),
                        limiter_key=self.client_address[0],
                    ))
                    return
                context = self._context()
                if path == "/v1/commands":
                    self._send(200, self.app.service.submit_command(context, self._json_body()))
                    return
                if path == "/v1/pairing-codes":
                    body = self._json_body()
                    self._send(201, self.app.service.create_pairing_code(
                        context, body.get("member_id"), body.get("device_label", "Eddie device")
                    ))
                    return
                if path.startswith("/v1/devices/") and path.endswith("/revoke"):
                    device_id = unquote(path.removeprefix("/v1/devices/").removesuffix("/revoke").strip("/"))
                    self._send(200, self.app.service.revoke_device(context, device_id))
                    return
                raise Problem("NOT_FOUND", "endpoint not found", 404)
            except Problem as problem:
                self._problem(problem)
            except Exception:
                self._problem(Problem("INTERNAL_ERROR", "the local service could not complete the request", 500))

    return ThreadingHTTPServer((host, port), Handler)
