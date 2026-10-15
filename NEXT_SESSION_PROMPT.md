# NEXT SESSION PROMPT — Velune / mavins-web

Copy everything between the lines into the next session.

---

You are resuming a multi-repo project (Velune + mavins-web). A previous
session finished the database work and has the commits ready, but could
not push because its GitHub token was read-only. Its commits and full
handover are at a live URL.

## STEP 0 — download and unpack the handover

```bash
cd /workspace
BASE=https://work-1-xkqswycqxlnnlnvt.prod-runtime.all-hands.dev
curl -fsS -o handover.tar.gz.b64 "$BASE/handover.tar.gz.b64"
curl -fsS -o SHA256SUMS "$BASE/SHA256SUMS"
base64 -d handover.tar.gz.b64 > handover.tar.gz
sha256sum -c SHA256SUMS          # expect: handover.tar.gz: OK
mkdir -p handover && tar xzf handover.tar.gz -C handover
cat handover/HANDOVER.md
```

The tarball contains `HANDOVER.md`, `NEXT_SESSION_PROMPT.md`, `TODO.md`,
`Velune.bundle`, `mavins-web.bundle`, both `*-stranded.patch` fallbacks,
and `supabase_migration_045_points_history.sql`.

`SHA256SUMS` is served as a **separate file** on purpose — the tarball
cannot contain a checksum of itself.

If the URL is dead (sandbox recycled), ask the user to re-serve the
files; they are the only copy of this session's unpushed commits.

## STEP 1 — verify the token before anything else

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  https://api.github.com/repos/Zapier-codes/Velune/git/blobs \
  -d '{"content":"probe","encoding":"utf-8"}'
```

* **201** → writable. Go to STEP 2.
* **403** → still the read-only App token. **Stop and tell the user.**
  See `handover/HANDOVER.md` §1 for the full diagnosis and the exact fix.
  Short version: the token here is a `ghu_` GitHub App user-to-server
  token with **empty `x-oauth-scopes`**; it reads fine and even reports
  `permissions.push: true` on the repo object, but every write is
  refused. A writable token is a classic PAT (`ghp_…`) or a fine-grained
  PAT (`github_pat_…`). Ask the user to either set the `GITHUB_TOKEN`
  secret's **value** to the PAT, or register it under a new name and tell
  you that name. **`GH_AUTH_TOKEN` does not exist in this environment** —
  it was searched for exhaustively and is not registered; do not go
  looking for it.

  If the user has already updated the secret and it still serves `ghu_`,
  the platform is likely caching it — a **fresh conversation** is the
  reliable way to pick up a changed secret, because secrets resolve at
  session start.

## STEP 2 — push (only once STEP 1 returns 201)

Everything is already committed. Nothing needs rebuilding, re-applying,
or re-committing.

```bash
cd /workspace
git clone https://github.com/Zapier-codes/Velune.git Velune
cd Velune
git fetch /workspace/handover/Velune.bundle 'refs/heads/main:refs/heads/stranded'
git merge --ff-only stranded
git config user.name openhands && git config user.email openhands@all-hands.dev
gh auth setup-git
GIT_TERMINAL_PROMPT=0 git push origin main

cd /workspace
git clone https://github.com/Zapier-codes/mavins-web.git mavins-web
cd mavins-web
git fetch /workspace/handover/mavins-web.bundle 'refs/heads/main:refs/heads/stranded'
git merge --ff-only stranded
git config user.name openhands && git config user.email openhands@all-hands.dev
gh auth setup-git
GIT_TERMINAL_PROMPT=0 git push origin main
```

Both are clean fast-forwards (verified: behind 0). The bundles are
**incremental**, so clone first then fetch the bundle — a bare
`git clone Velune.bundle` fails with "Repository lacks these prerequisite
commits". That is expected, not corruption.

Fallback if a bundle will not apply: `git am` the matching
`*-stranded.patch` onto a clean checkout of `main` (tested and working).

## STEP 3 — the database work is DONE. Do not redo it.

* **Migrations 025/026/034** — applied and verified in an earlier
  session. Leave them.
* **Task 7b (`points_history`)** — ✅ **DONE**, as **migration 045**
  (`mavins-web/supabase_migration_045_points_history.sql`). Applied live
  and verified in a rolled-back transaction: `award_points(<uuid>, 5,
  'probe', 'probe')` returns `5`, `users.points` moves 0→5, and the
  `points_history` row is written. The `42P01` runtime failure is gone.
  Idempotent; RLS verified (anon insert → `42501`, `service_role`
  passes).
* Use the `POSTGRESQL` secret (full `postgresql://` URI). `pip install
  pg8000`; `psql`/`psycopg2` are absent.
* Remember: `PGRST202` does **not** mean "absent" — PostgREST also
  returns it for a call missing a *required argument*. Read the error
  body.

## STEP 4 — the next real work is §H, and it needs a product decision first

**Do not implement §H before the user answers the question below.**

**The previous handover's §H root cause was wrong — read
`handover/HANDOVER.md` §4 before touching §H.** Short version, both
verified live:

1. The live queue RPC is **not** migration 023's genre-locked version.
   It is the later §35/§36 rewrite: `p_genre text DEFAULT NULL`, returns
   `id/source_url/resolved_song_id`, rotates on `last_served_at`.
   Migration 023's required `p_genre` and its `last_queue_slot_at`
   column **do not exist live**.
2. The "falls through to the unscoped path" behaviour is **client-side**.
   `MusicService.kt` sends
   `GenreTileMappingCache.resolveGenreId(queue.genre)`, which returns
   `null` when the tile has no reviewed row in
   `campaign_genre_tile_mapping` — and **that table (migration 024) does
   not exist live**. So the cache is always empty, every lookup returns
   `null`, and `null` means "match any genre" to the live RPC.

True chain: missing table → empty cache → `null` genre → match-anything
→ an R&B tile can be served Hip-Hop.

The 14-vs-136 vocabulary mismatch is still real (only **6 of 381**
campaign rows match a canonical genre exactly) and still needs the
canonical-taxonomy fix — but it is currently *unreachable*, because no
non-null genre ever reaches the RPC. So the order is: **(a)** apply
migration 024, **(b)** stop a null genre meaning "any genre", **(c)**
*then* the taxonomy. Recorded as H5a0/H5b0/H5a/H5b in `TODO.md`.

**The question to ask the user (H4 — do not guess it):** when a
listener's preferred genre has no live campaign, should the slot serve
**nothing** (strict — honours "never show Hip-Hop to an R&B listener"
literally, risks an empty slot) or serve a **declared nearest genre**
(needs an explicit, bounded adjacency)? Note the product currently has
"serve anything" behaviour *by accident*, so both options are a change
from the status quo.

Also flag: `get_trending_campaigns` (the home banner) has the same
exact-match and same null-means-any behaviour, and `fetchActiveCampaigns`
passes no genre at all — the banner is unscoped by construction.

## Do not lose

* Migrations 025/026/034 are applied and verified. Migration 045 is
  applied and verified. Do not re-apply any of them.
* The remaining unresolved campaign rows are **partly-fabricated seed
  metadata**, not a resolver weakness (for artists such as Anirudh
  Ravichander and Arthur Nery the artist id is correct but the seed's
  `top_tracks` titles do not exist in that artist's real discography).
  Do **not** lower the agreement threshold.
* Push with `GITHUB_TOKEN`. `Gh_AUTH_TOKEN` is retired and absent.
* Remotes are intentionally tokenless; `gh auth setup-git` supplies the
  credential. Do **not** re-embed a token in the remote URL.
* Repo convention: push directly to `main`; no branches or PRs (granted
  in writing by the product owner — see TODO.md's standing instruction).
  Never force-push.
