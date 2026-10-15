#!/usr/bin/env python3
"""Build a campaign resolution plan, plus a rollback backup.

Task 8 (B5a). Replaces the `/tmp` scripts lost between sessions. This
script only *reads* the database and writes two local JSON files:

  resolution.json            the plan: artist -> track -> video id
  backup_before_update.json  every value the plan would overwrite

`update_tracks.py` refuses to write to the database without the backup
file, so the pair is safe to run against live.

Usage
-----
    export POSTGRESQL=...              # or let the secret store inject it
    python3 tools/resolve_tracks.py --limit 5      # bounded trial run
    python3 tools/resolve_tracks.py                # full run

    --unresolved-only   only artists whose campaigns have a null
                        resolved_song_id (the default and the point)
    --refresh-ids       also re-resolve users.spotify_id / spotify_url
"""

from __future__ import annotations

import argparse
import json
import sys
import time

import db
import spotify_resolver as sr


def fetch_artists(conn, unresolved_only: bool):
    """Artists needing repair, with their top tracks and campaign ids."""
    where = ("where tc.resolved_song_id is null" if unresolved_only else "")
    return conn.run(f"""
        select u.id::text, u.artist_name, u.spotify_id, u.spotify_url,
               u.youtube_url, u.metadata->'top_tracks' as top_tracks,
               array_agg(tc.id::text) as campaign_ids
        from public.track_campaigns tc
        join public.users u on u.id = tc.artist_id
        {where}
        group by u.id, u.artist_name, u.spotify_id, u.spotify_url,
                 u.youtube_url, u.metadata->'top_tracks'
        order by u.artist_name
    """)


def backup_rows(conn):
    """Current values for every row the plan can touch."""
    campaigns = conn.run("""
        select id::text, source_url, resolved_song_id
        from public.track_campaigns
    """)
    users = conn.run("""
        select id::text, spotify_id, spotify_url
        from public.users
        where artist_name is not null
    """)
    return {
        "captured_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "track_campaigns": [
            {"id": r[0], "source_url": r[1], "resolved_song_id": r[2]}
            for r in campaigns],
        "users": [
            {"id": r[0], "spotify_id": r[1], "spotify_url": r[2]}
            for r in users],
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--limit", type=int, default=0,
                    help="max artists to process (0 = all)")
    ap.add_argument("--resolution", default="resolution.json")
    ap.add_argument("--backup", default="backup_before_update.json")
    ap.add_argument("--min-score", type=float, default=0.75)
    ap.add_argument("--refresh-ids", action="store_true",
                    help="also re-resolve spotify_id / spotify_url")
    ap.add_argument("--all", dest="unresolved_only", action="store_false",
                    help="process every artist, not just unresolved ones")
    opts = ap.parse_args()

    conn = db.connect()
    artists = fetch_artists(conn, opts.unresolved_only)
    if opts.limit:
        artists = artists[:opts.limit]
    print(f"{len(artists)} artists to process")

    backup = backup_rows(conn)
    with open(opts.backup, "w", encoding="utf-8") as fh:
        json.dump(backup, fh, indent=1)
    print(f"backup written: {opts.backup} "
          f"({len(backup['track_campaigns'])} campaigns, "
          f"{len(backup['users'])} users)")

    try:
        index = sr.kworb_index()
        print(f"kworb index: {len(index)} artists")
    except Exception as exc:
        print(f"kworb index unavailable ({exc}); MusicBrainz only")
        index = {}

    plan, unresolved = [], []
    for n, row in enumerate(artists, 1):
        (user_id, artist_name, cur_sid, cur_surl,
         yt_url, top_tracks, campaign_ids) = row
        titles = [t.get("name") for t in (top_tracks or []) if t.get("name")]
        if not artist_name or not titles:
            unresolved.append({"artist_name": artist_name,
                               "reason": "no top_tracks metadata"})
            continue

        artist_id, source = sr.resolve_artist_id(artist_name, index, cur_sid)
        if not artist_id:
            unresolved.append({"artist_name": artist_name,
                               "reason": "no spotify artist id"})
            print(f"  [{n}/{len(artists)}] {artist_name}: no artist id")
            continue

        tracks = sr.artist_tracks(artist_id)
        if not tracks:
            unresolved.append({"artist_name": artist_name,
                               "reason": "no tracks for artist id",
                               "spotify_artist_id": artist_id,
                               "artist_id_source": source})
            print(f"  [{n}/{len(artists)}] {artist_name}: no tracks")
            continue
        picked = None
        for title in titles:
            matched = sr.match_track(title, tracks)
            if not matched:
                continue
            resolved = sr.resolve_track(artist_name, matched["title"],
                                        opts.min_score)
            if resolved:
                picked = {"track_title": matched["title"],
                          "spotify_track_id": matched["track_id"],
                          "source_url": ("https://open.spotify.com/track/"
                                         + matched["track_id"]),
                          **resolved}
                break

        if not picked:
            unresolved.append({"artist_name": artist_name,
                               "reason": "no verified youtube match",
                               "tracks_available": len(tracks)})
            print(f"  [{n}/{len(artists)}] {artist_name}: no verified match")
            continue

        entry = {"user_id": user_id, "artist_name": artist_name,
                 "spotify_artist_id": artist_id, "artist_id_source": source,
                 "campaign_ids": campaign_ids,
                 "previous_spotify_id": cur_sid,
                 "previous_spotify_url": cur_surl,
                 **picked}
        plan.append(entry)
        print(f"  [{n}/{len(artists)}] {artist_name}: "
              f"{picked['track_title']!r} -> {picked['video_id']} "
              f"(score {picked['score']})")

    with open(opts.resolution, "w", encoding="utf-8") as fh:
        json.dump({"generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                   "resolved": plan, "unresolved": unresolved},
                  fh, indent=1)

    rows = sum(len(e["campaign_ids"]) for e in plan)
    print(f"\nresolved {len(plan)} artists covering {rows} campaign rows; "
          f"{len(unresolved)} unresolved")
    print(f"plan written: {opts.resolution}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
