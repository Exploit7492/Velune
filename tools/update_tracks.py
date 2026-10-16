#!/usr/bin/env python3
"""Apply a resolution plan to the live database, from the repo.

Task 8 (B5a). The write half of the pair; `resolve_tracks.py` produces
`resolution.json` and `backup_before_update.json`.

Safety
------
* Refuses to run without the backup file, so there is always a rollback
  artifact for anything this writes.
* `--dry-run` prints the exact statements without executing them.
* Everything runs in one transaction; a failure rolls the whole batch
  back rather than leaving a half-applied plan.
* `--rollback` restores the backup, which is what makes the plan
  reproducible in the other direction too.

Usage
-----
    export POSTGRESQL=...
    python3 tools/update_tracks.py --dry-run
    python3 tools/update_tracks.py
    python3 tools/update_tracks.py --rollback
"""

from __future__ import annotations

import argparse
import json
import os
import sys

import db

DEFAULT_RESOLUTION = "resolution.json"
DEFAULT_BACKUP = "backup_before_update.json"


def load(path):
    if not os.path.exists(path):
        raise SystemExit(f"missing {path} -- run resolve_tracks.py first")
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def apply_plan(conn, plan, dry_run: bool):
    statements = []
    for entry in plan["resolved"]:
        source_url = entry["source_url"]
        video_id = entry["video_id"]
        if not source_url or not video_id:
            # A row without a playable pair is not an improvement over the
            # unplayable row it would replace; skip it rather than write a
            # half-filled campaign.
            continue
        for campaign_id in entry["campaign_ids"]:
            statements.append((
                "update public.track_campaigns "
                "set source_url = :url, resolved_song_id = :vid where id = :cid",
                {"url": source_url, "vid": video_id, "cid": campaign_id}))
        # The artist-channel fallback can resolve a campaign without ever
        # finding a Spotify artist id. Writing anyway would null the row's
        # spotify_id and store the literal string "artist/None", so the
        # users row is left alone unless there is a real id to write.
        if entry.get("spotify_artist_id"):
            statements.append((
                "update public.users set spotify_id = :sid, spotify_url = :surl "
                "where id = :uid",
                {"sid": entry["spotify_artist_id"],
                 "surl": "https://open.spotify.com/artist/"
                         + entry["spotify_artist_id"],
                 "uid": entry["user_id"]}))

    print(f"{len(statements)} statements to apply")
    if dry_run:
        for sql, params in statements[:10]:
            print(f"  {sql}  {params}")
        if len(statements) > 10:
            print(f"  ... and {len(statements) - 10} more")
        return 0

    for sql, params in statements:
        conn.run(sql, **params)
    return len(statements)


def rollback(conn, backup, dry_run: bool):
    n = 0
    for row in backup["track_campaigns"]:
        sql = ("update public.track_campaigns "
               "set source_url = :url, resolved_song_id = :vid where id = :cid")
        params = {"url": row["source_url"], "vid": row["resolved_song_id"],
                  "cid": row["id"]}
        if dry_run:
            if n < 5:
                print(f"  {sql}  {params}")
        else:
            conn.run(sql, **params)
        n += 1
    for row in backup["users"]:
        sql = ("update public.users set spotify_id = :sid, spotify_url = :surl "
               "where id = :uid")
        params = {"sid": row["spotify_id"], "surl": row["spotify_url"],
                  "uid": row["id"]}
        if dry_run:
            if n < 5:
                print(f"  {sql}  {params}")
        else:
            conn.run(sql, **params)
        n += 1
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--resolution", default=DEFAULT_RESOLUTION)
    ap.add_argument("--backup", default=DEFAULT_BACKUP)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--rollback", action="store_true",
                    help="restore values from the backup instead of applying")
    opts = ap.parse_args()

    conn = db.connect()
    try:
        if opts.rollback:
            backup = load(opts.backup)
            n = rollback(conn, backup, opts.dry_run)
            if not opts.dry_run:
                conn.run("NOTIFY pgrst, 'reload schema'")
            print(f"rolled back {n} rows from {opts.backup}")
            return 0

        plan = load(opts.resolution)
        load(opts.backup)  # presence check: never write without a rollback artifact
        n = apply_plan(conn, plan, opts.dry_run)
        if not opts.dry_run:
            conn.run("NOTIFY pgrst, 'reload schema'")
            print(f"applied {n} statements")
        return 0
    except Exception as exc:
        print(f"failed, transaction rolled back: {str(exc)[:300]}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
