# Campaign resolution tools

Task 8 (B5a). The repair tooling for Spotify-linked campaign rows, kept
in the repo so it cannot be lost the way the `/tmp` originals were.

## The problem these solve

A campaign row needs a **YouTube video id** in `track_campaigns.resolved_song_id`
to be playable — Velune's player is a YouTube player, and
`CampaignUrlResolver` returns null for anything else. A row with only a
Spotify `source_url` and a null `resolved_song_id` is invisible: the
queue-slot RPC filters it out. So a Spotify link must be *resolved* to a
YouTube id ahead of time.

## Pipeline

```
resolve_tracks.py   -> resolution.json + backup_before_update.json
update_tracks.py    -> writes the plan to the DB (or rolls it back)
verify_resolution.py -> reports what is still unplayable
```

`spotify_resolver.py` holds the actual resolution logic; `db.py` is the
connection helper.

## Usage

```bash
export POSTGRESQL=...            # postgresql:// URI or DB password

python3 verify_resolution.py                 # current state
python3 resolve_tracks.py --limit 5          # bounded trial run
python3 resolve_tracks.py                    # full pass
python3 update_tracks.py --dry-run           # show the writes
python3 update_tracks.py                     # apply
python3 update_tracks.py --rollback          # restore from backup
```

`resolution.json` and `backup_before_update.json` are **generated**, not
committed. `update_tracks.py` refuses to write without a backup file, so
a rollback artifact always exists before anything changes.

## Resolution sources

1. **Artist id** — kworb's index, then MusicBrainz `url-rels`, then the
   id already stored on the row (accepted only after confirming it
   exposes tracks; some stored ids are fabricated).
2. **Track titles** — the Spotify artist embed (`__NEXT_DATA__`) plus
   kworb's `<artist_id>_songs.html`; then MusicBrainz recordings, for the
   artists whose Spotify page exposes no track list at all (Dopebwoy's
   embed is `isPlayable` yet its `trackList` is empty, and kworb has no
   songs page for them). MusicBrainz entries carry no Spotify track id,
   so the resolved row stores the verified YouTube link as its
   `source_url` instead.
3. **Match** — `users.metadata.top_tracks[].name` against those titles.
   When the same title resolves to several releases (two Spotify track
   ids, or the same song on an album and a single), the artist's own
   embed list wins over the kworb scrape, and a tie that survives that
   is still refused.
4. **YouTube id** — search, score, then confirm with oEmbed.

Requires `yt-dlp` for the YouTube search step (`pip install yt-dlp`);
without it, step 4 yields nothing rather than guessing.

## Two rules worth not breaking

* **Precision over recall.** A wrong pick points a paid campaign at a
  stranger's video. The scorer requires whole-word artist agreement *and*
  title agreement, and drops an artist entirely rather than guessing.
  Do not loosen it to raise the resolved count.
* **Two normalisation traps.** Accents must be *dropped*, not turned
  into separators (NFKD leaves combining marks that a naive `[^a-z0-9]`
  class converts to word breaks, so "Tiësto" became "tie sto"). And
  bracketed suffixes must be read from the *raw* title, because
  normalisation erases the brackets the variant penalty depends on.

## Known limit

Some remaining rows are unresolvable **because the seed metadata is
wrong**, not because the resolver is weak: several artists' seed
`top_tracks` titles do not exist in that artist's real discography.
Audit `users.metadata.top_tracks` before expecting this pipeline to
improve. See `TODO.md` §B5 and the RESUME block.
