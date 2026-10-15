# AGENTS.md — Velune

Repository knowledge for agents working in this repo. See `TODO.md` for the
current task queue and the authoritative state; see `NEXT_SESSION_PROMPT.md`
for the handover between sessions.

## Repos

Multi-repo project: `Velune` (Android app), `mavins-web` (Next.js admin +
Supabase migrations), `B-Pay-backend`. Migrations live in **mavins-web**,
`supabase_migration_NNN_*.sql`. `TODO.md` and `NEXT_SESSION_PROMPT.md` are
kept **byte-identical** across Velune and mavins-web — update both.

## Git conventions

* Push directly to `main`. **No branches, no PRs** (granted in writing by the
  product owner). Never force-push.
* `GITHUB_TOKEN` is **read-only** (a `ghu_` GitHub App user-to-server token,
  empty `x-oauth-scopes`): reads 200, writes 403. **Push with
  `GH_AUTH_TOKEN`**, a writable classic PAT (`ghp_`).
* Remotes are intentionally **tokenless**. Temporarily add a token to push,
  then restore the tokenless URL in the same command. Never commit a token
  into `.git/config`.
* Commit identity: `openhands` / `openhands@all-hands.dev`, with
  `Co-authored-by: openhands <openhands@all-hands.dev>`.

## Database access

* Use the **`POSTGRESQL`** secret (full `postgresql://` URI).
* `pip install pg8000`; **`psql` and `psycopg2` are absent**. `tools/db.py`
  wraps this and connects via the session pooler (the direct
  `db.<ref>.supabase.co` host does not resolve from the sandbox).
* Apply a migration transactionally with `tools/sqlsplit.py`:

  ```python
  from sqlsplit import split_sql
  c.run("begin")
  for s in split_sql(open(path).read()):
      c.run(s)
  c.run("commit")
  ```

  Plain `c.run(whole_file)` fails, and naive `;`-splitting breaks on `$$`
  bodies and `--` comments inside string literals — that is why `sqlsplit`
  exists. `pg8000`'s simple-query protocol does allow multiple statements,
  but the splitter gives per-statement error reporting.

## Gotchas that have already cost time

* **`PGRST202` does not mean "function absent".** PostgREST also returns it
  for a call missing a required argument. Read the error body.
* **`x = any((select arr))` is the subquery form** in Postgres — it compares
  `x` against the array's *rows*, not its elements, and raises
  `operator does not exist: text = text[]`. Use `any(arr_column)` against a
  joined column, or `= any(array[...])`.
* The live queue RPC is the **§35/§36 rewrite** (`p_genre text DEFAULT NULL`,
  returns `id/source_url/resolved_song_id`, rotates on `last_served_at`).
  Migration 023's genre-locked version is **not** what is live.
* `genres.id` and `genres.label` are both `text`; `track_campaigns.id` and
  `users.id` are `uuid`; `target_genres` is `text[]`.

## Genre handling — read before touching anything genre-related

The genre vocabulary is **data, not code**. Do not add a `case`/`when`, a
Kotlin list, or a TypeScript map to resolve a genre term.

* `public.genres` — the 14-row canonical dimension, also drives the browse UI.
  Keep it stable; do not grow it to fit granular tags.
* `public.genre_aliases` — free-text term → canonical genre. Composite PK, so
  a hybrid such as `'gospel pop'` maps to both Gospel and Pop.
* `public.genre_adjacency` — the **bounded** nearest-genre fallback. Empty for
  a genre means strict. R&B has no neighbours, deliberately, so "never show
  Hip-Hop to an R&B listener" holds literally.
* `public.campaign_genres` / `public.user_genres` — trigger-maintained from
  `target_genres` / `primary_genre`. Query the queue through these, never by
  string equality.
* `public.genre_resolution_queue` — every term that will not resolve. Run
  `python tools/genre_report.py` for the worklist; clearing an entry is an
  INSERT into `genre_aliases` or `genres`.

`resolve_genre_terms()` / `resolve_genre_set()` read those tables at query
time. Three tiers: exact canonical match → explicit alias → whole-word
containment (so `'Indie Pop'`, `'Dance Pop'`, `'K-Pop'` all reach Pop with no
row each). Matching is on a **slug** (`genre_slug`: non-alphanumerics → one
space), so `hip hop` matches `hip-hop` and no regex escaping is needed.

### The `null` genre contract — do not "fix" this

`Queue.genre` is `null` for queues with **no genre context** (search, album,
playlist, artist radio, autoplay), and the RPC reads null as "match any
eligible campaign". That is **correct**: campaign injection is a monetization
slot that belongs on every such queue (§33). Null is **not** a fail-closed
signal.

What must never happen is a **genre-scoped** queue degrading into that
branch. `GenreTileMappingCache.resolveTile` exists to prevent it: an
unresolved tile returns its **raw title** so the server can resolve it or
fail closed, and only a tile an admin has explicitly reviewed as a mood
returns null.

## Migrations — do not re-apply

Applied and verified: **025, 026, 034, 045** (points_history), **046**
(genre taxonomy). Deliberately **not** applied: **023** (superseded) and
**024** (orthogonal since tiles resolve by title).

## Environment notes

* No Android SDK / Gradle build is available in this sandbox — Kotlin changes
  are reviewed by hand, not compiled. Verify SQL against the live database
  inside a rolled-back transaction before committing.
* `tools/genre_report.py` and `tools/sqlsplit.py` are supported utilities.
