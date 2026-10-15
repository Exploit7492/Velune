"""Live Postgres access for the campaign repair tools (task 8 / B5a).

Connects with the registered `POSTGRESQL` secret, which holds either a
full `postgresql://` URI or a bare password. A bare password is paired
with the session-pooler coordinates recorded in TODO.md section A4.

`db.<ref>.supabase.co` is deliberately not used: it does not resolve
from this sandbox (IPv6-only). The pooler does.
"""

from __future__ import annotations

import os
import ssl
import urllib.parse

import pg8000.native

POOLER_HOST = "aws-0-eu-west-1.pooler.supabase.com"
POOLER_USER = "postgres.atojskxrxfsbpeefigtm"
POOLER_DB = "postgres"


def connect():
    """Return a pg8000 native connection to the live database."""
    value = None
    for name in ("POSTGRESQL", "SUPABASE_DB_PASSWORD", "DATABASE_URL"):
        if os.environ.get(name):
            value = os.environ[name].strip()
            break
    if not value:
        raise SystemExit(
            "No Postgres secret in the environment. Register POSTGRESQL "
            "(a postgresql:// URI or the DB password).")

    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE

    if "://" in value:
        parsed = urllib.parse.urlparse(value)
        user = urllib.parse.unquote(parsed.username or POOLER_USER)
        password = urllib.parse.unquote(parsed.password or "")
        host = parsed.hostname or POOLER_HOST
        port = parsed.port or 5432
        database = (parsed.path or "/postgres").lstrip("/") or POOLER_DB
    else:
        user, password = POOLER_USER, value
        host, port, database = POOLER_HOST, 5432, POOLER_DB

    return pg8000.native.Connection(
        user=user, password=password, host=host, port=port,
        database=database, ssl_context=context, timeout=30)
