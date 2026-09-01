from __future__ import annotations

import argparse
import os

from .api import create_server
from .app import EddiesWalletApp, default_database_path


def main() -> None:
    parser = argparse.ArgumentParser(description="EddiesWallet local synthetic backend")
    subparsers = parser.add_subparsers(dest="command", required=True)
    serve = subparsers.add_parser("serve", help="start the local HTTP API")
    serve.add_argument("--db", default=default_database_path(), help="SQLite path (development substitute)")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8080)
    serve.add_argument("--reset", action="store_true", help="delete and recreate the local synthetic database")
    serve.add_argument("--no-seed", action="store_true", help="do not install synthetic fixture data")
    args = parser.parse_args()

    if args.command == "serve":
        mode = os.environ.get("EDDIES_ENV", "local")
        if mode == "production" and args.reset:
            parser.error("--reset is disabled in production mode")
        app = EddiesWalletApp(args.db, mode=mode, reset=args.reset, seed=not args.no_seed and mode == "local")
        server = create_server(app, args.host, args.port)
        print(f"EddiesWallet API listening on http://{args.host}:{args.port}", flush=True)
        print(f"database: {args.db} (SQLite development substitute)", flush=True)
        if mode == "local":
            print("local fixture identities: parent-a, child-a, parent-b, child-b; POST /v1/auth/fixture", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()


if __name__ == "__main__":
    main()
