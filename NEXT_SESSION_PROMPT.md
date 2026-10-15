# NEXT SESSION PROMPT — Velune / mavins-web

Copy everything between the lines into the next session.

---

You are resuming a multi-repo project (Velune + mavins-web + B-Pay-backend).
The previous session pushed all previously-stranded commits, closed TODO §H
with a live migration, and left both repos clean on `main`. **There is
nothing to recover and nothing to un-strand.** Do not go looking for a
handover tarball, a bundle, a patch, or a stranded branch — those were
consumed on 2026-10-15 and their contents are now in `main`.

## STEP 0 — read the current state, do not replay history

```bash
cd /workspace
git -C Velune log --oneline -8
git -C mavins-web log --oneline -8
sed -n '1,200p' Velune/TODO.md     # the resume box and §H outcome are at the top
```

`Velune/TODO.md` and `mavins-web/TODO.md` are identical and are the
authoritative state. Read the box at the top of §H before touching anything
genre-related.

## STEP 1 — push with the right token

`GITHUB_TOKEN` is a **read-only** GitHub App user-to-server token (`ghu_`,
empty `x-oauth-scopes`). Reads work; every write returns 403. This is not a
broken secret — it is the wrong secret for pushing.

**Push with `GH_AUTH_TOKEN`**, which is a writable classic PAT (`ghp_`).
Probe it before relying on it:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  -H "Authorization: Bearer $GH_AUTH_TOKEN" \
  https://api.github.com/repos/Zapier-codes/Velune/git/blobs \
  -d '{"content":"probe","encoding":"utf-8"}'
# 201 → writable, proceed.  403 → stop and ask the user to re-register it.
```

Then, per repo:

```bash
cd /workspace/Velune
git config user.name openhands && git config user.email openhands@all-hands.dev
GIT_REMOTE=$(git remote get-url origin)          # intentionally tokenless
git remote set-url origin "https://x-access-token:${GH_AUTH_TOKEN}@github.com/Zapier-codes/Velune.git"
GIT_TERMINAL_PROMPT=0 git push origin main
git remote set-url origin "$GIT_REMOTE"          # restore the tokenless remote
```

Same for `mavins-web`. **Do not leave a token in `.git/config`** — restore
the tokenless remote immediately after pushing, as shown.

Repo convention, granted in writing by the product owner: **push directly to
`main`; no branches and no PRs.** Never force-push. (Note the one existing
exception: Velune PR #1, `fix/build-errors` → `main`, is still open and
predates that instruction — leave it alone unless the user asks.)

## STEP 2 — the database work is DONE. Do not redo it.

Applied and verified, all idempotent. **Do not re-apply any of these:**

* Migrations **025 / 026 / 034** — applied and verified in an earlier session.
* Migration **045** (`points_history`) — applied and verified. Task 7b is done.
* Migration **046** (`genre_taxonomy`) — applied and verified 2026-10-15.
  This is the §H fix. 381/381 campaigns mapped, 0 unresolved terms, both RPCs
  verified live through PostgREST.

Migrations **023** and **024** are **deliberately not applied**. 023's
genre-locked RPC was superseded by the later §35/§36 rewrite; 024 is
orthogonal to the §H leak now that the resolver maps tiles by title. Both
are recorded in §H5 — do not apply them without a reason.

Use the **`POSTGRESQL`** secret (full `postgresql://` URI) for database work.
`pip install pg8000`; `psql` and `psycopg2` are absent. `Velune/tools/db.py`
wraps this. Two gotchas that have already cost time:

* `PGRST202` does **not** mean "function absent" — PostgREST also returns it
  for a call missing a required argument. **Read the error body.**
* In Postgres, `x = any((select arr))` is parsed as the **subquery** form and
  compares `x` to the array's rows, not its elements. Use
  `any(arr_column)` in a join, or `= any(array[...])`.

## STEP 3 — §H is CLOSED. The next work is elsewhere.

Do not re-open §H or re-litigate the genre design. It is done, and the
product decision it needed was made: strict by default (fail closed), with a
bounded `genre_adjacency` table declaring nearest-genre exceptions, shipped
empty for R&B so "never show Hip-Hop to an R&B listener" holds literally.
Widening a pair is a data INSERT, not a code change.

Pick up the next open item in `TODO.md`'s ordered work queue. Known open
threads, in the order the queue implies:

1. **§F — Spotify link semantics and seed-data repair.** Open product
   questions. Note the handover's own finding: the remaining unresolved
   campaign rows are partly **fabricated seed metadata**, not a resolver
   weakness — for artists such as Anirudh Ravichander and Arthur Nery the
   artist id is correct but the seed's `top_tracks` titles do not exist in
   that artist's real discography. Do not lower the agreement threshold to
   make these pass.
2. **Optional, not blocking (H5e):** seed `campaign_genre_tile_mapping`
   (migration 024's table, currently absent) with real YouTube Music
   mood/genre tile titles, if a curated override is wanted. Verified
   behaviour without it: genre tiles resolve and serve; mood tiles fail
   closed rather than serving an arbitrary genre.
3. **§H5d is now ongoing curation, not a code task.** New genre terms that
   will not resolve land in `genre_resolution_queue`; `python
   Velune/tools/genre_report.py` prints the worklist and the reviewed tile
   titles. Clearing an entry is an INSERT into `genre_aliases` or
   `public.genres`.

## Do not lose

* Migrations 025/026/034/045/046 are applied and verified. Do not re-apply.
* `Queue.genre` is `null` for queues with **no genre context** (search,
  album, playlist, artist radio, autoplay) and that is **correct** — the RPC
  reads null as "any eligible campaign", because campaign injection is a
  monetization slot that belongs on every such queue (§33). It is **not** a
  fail-closed signal. `GenreTileMappingCache.resolveTile` is the function
  that keeps that distinction; an unresolved tile returns its raw title so
  the server can resolve or fail closed.
* The genre vocabulary is **data**. `genre_aliases`, `genre_adjacency`, and
  `public.genres` are the extension points. Do not add a `case`/`when` or a
  hardcoded list to resolve a genre term.
* Push with `GH_AUTH_TOKEN`. `GITHUB_TOKEN` is read-only.
* Remotes are intentionally tokenless; restore them after pushing. Do not
  re-embed a token.
* Repo convention: push directly to `main`; no branches or PRs. Never
  force-push.
* `Velune/tools/genre_report.py` and `Velune/tools/sqlsplit.py` are new
  this session and are the supported way to inspect the taxonomy and to
  split a migration file into statements for transactional application.
