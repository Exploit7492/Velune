#!/usr/bin/env python3
"""Report how many campaign rows still resolve to a playable video id.

Task 8 (B5a) + the guard for task 15 (B5g). Read-only; safe to run any
time, and the natural "did the repair work" check after update_tracks.py.

Exit code is 1 when any artist-level `source_url` still has a null
`resolved_song_id`, so it can be used as a CI-style assertion later.

Usage
-----
    export POSTGRESQL=...
    python3 tools/verify_resolution.py
"""

from __future__ import annotations

import sys

import db

SUMMARY = """
select
  count(*)                                                    as total,
  count(resolved_song_id)                                     as resolved,
  count(*) filter (where resolved_song_id is null)             as unresolved,
  count(*) filter (where source_url like '%/artist/%'
                     and resolved_song_id is null)             as unplayable_artist_rows,
  count(*) filter (where source_url like '%/track/%')          as track_urls,
  count(distinct artist_id) filter (where resolved_song_id is null) as artists_open
from public.track_campaigns
"""

BY_ARTIST = """
select u.artist_name, count(*) as rows
from public.track_campaigns tc
join public.users u on u.id = tc.artist_id
where tc.resolved_song_id is null
group by u.artist_name
order by rows desc, u.artist_name
"""


def main():
    conn = db.connect()
    (total, resolved, unresolved, unplayable,
     track_urls, artists_open) = conn.run(SUMMARY)[0]

    print(f"campaign rows           : {total}")
    print(f"resolved (playable)     : {resolved}")
    print(f"unresolved              : {unresolved}")
    print(f"artist-url + unresolved : {unplayable}")
    print(f"track-level source_url  : {track_urls}")
    print(f"artists still open      : {artists_open}")

    if unresolved:
        print("\nstill unresolved, by artist:")
        for artist_name, count in conn.run(BY_ARTIST):
            print(f"  {count:>4}  {artist_name}")

    if unplayable:
        print(f"\nFAIL: {unplayable} artist-level rows have no resolved_song_id")
        return 1
    print("\nOK: no artist-level row lacks a resolved_song_id")
    return 0


if __name__ == "__main__":
    sys.exit(main())
