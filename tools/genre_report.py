"""Report how the live genre taxonomy resolves, and what it cannot.

Run against the live database after migration 046:

    POSTGRESQL=... python tools/genre_report.py

Prints, for the whole live vocabulary:

* how many campaigns and users resolve to at least one canonical genre;
* the distribution of canonical genres across campaigns;
* every term in `genre_resolution_queue` (terms the resolver could not map),
  which is the worklist an admin clears by adding a `genre_aliases` row or a
  new `public.genres` row;
* every term in `campaign_genre_tile_mapping` that is reviewed but still
  unmapped, so a tile title coming from production can be checked the same way.

The last two are the point: they turn "some tile served the wrong genre" into
a concrete row to add, which is a data change rather than a deploy.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import db  # noqa: E402


def main() -> int:
    c = db.connect()
    try:
        print("=== canonical genres ===")
        for gid, label in c.run("select id, label from public.genres order by sort_order"):
            print(f"  {gid:16} {label}")

        total = c.run("select count(*) from public.track_campaigns")[0][0]
        mapped = c.run(
            "select count(distinct campaign_id) from public.campaign_genres")[0][0]
        print(f"\n=== coverage ===")
        print(f"  campaigns mapped : {mapped}/{total}")
        print(f"  users mapped     : "
              f"{c.run('select count(distinct user_id) from public.user_genres')[0][0]}")

        print("\n=== campaigns per canonical genre ===")
        for gid, n in c.run(
                "select genre_id, count(*) from public.campaign_genres "
                "group by 1 order by 2 desc"):
            print(f"  {gid:16} {n}")

        print("\n=== declared adjacency (the bounded fallback) ===")
        rows = c.run(
            "select genre_id, adjacent_genre_id, distance from public.genre_adjacency "
            "order by 1, 2")
        if rows:
            for a, b, d in rows:
                print(f"  {a} -> {b} (d={d})")
        else:
            print("  (none — every genre is strict)")

        unresolved = c.run(
            "select term, occurrences from public.genre_resolution_queue "
            "order by occurrences desc")
        print(f"\n=== unresolved terms ({len(unresolved)}) ===")
        for term, n in unresolved:
            print(f"  w={n:4d}  {term!r}")
        if not unresolved:
            print("  (none — the whole live vocabulary resolves)")

        reviewed = c.run(
            "select tile_title, mapped_genre_id from public.campaign_genre_tile_mapping "
            "where is_reviewed order by tile_title")
        print(f"\n=== reviewed tile titles ({len(reviewed)}) ===")
        for title, mapped in reviewed:
            state = mapped if mapped else "mood/non-genre"
            print(f"  {title!r} -> {state}")
        if not reviewed:
            print("  (none reviewed yet — every genre tile falls through to the")
            print("   server resolver on its raw title, which is correct but")
            print("   means the tile vocabulary is unverified against the taxonomy)")
    finally:
        c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
