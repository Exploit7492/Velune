# Consolidated Cross-Repo Todo — Velune / mavins-web / B-Pay-backend

> **Companion to, not a replacement for, the per-repo handover files.**
> The handover files (`HANDOVER.md`, `HANDOVER_CAMPAIGN.md`,
> mavins-web's `handover.md`) remain the authoritative narrative record.
> This file is the single consolidated work list across all three repos,
> so a fresh session can see what is open without reading ~200KB of
> handover prose first.
>
> Repos in scope:
> - `github.com/Zapier-codes/Velune` — Android app, package `com.nikhil.yt`
> - `github.com/Zapier-codes/mavins-web` — Next.js web app (fork of
>   `Phoenix-Boss/mavins-web`; do not modify the upstream)
> - `github.com/Zapier-codes/SpotiFLAC-Mobile` — **reference only, do not
>   modify.** Cloned 2026-10-15 as the proposed source of Spotify-link
>   resolution. Flutter/Dart + Go; nothing portable to Kotlin, and its
>   own resolution hop (`songlink.go` → `api.song.link`) is now dead
>   (HTTP 401 `PUBLIC_API_ACCESS_DEPRECATED`). See §B3. Registered here
>   so future sessions do not re-clone and re-litigate it.
> - `B-Pay-backend` — payments backend (referenced throughout; not
>   cloned this session)
>
> Conventions that apply to every task below, copied verbatim in spirit
> from mavins-web's own handover: split every task into at most 5 parts
> (a–e) and build **one** part per session; end every session with a
> single `&&`-chained copy-pasteable apply+push command covering every
> repo touched.
>
> **STANDING INSTRUCTION (2026-10-15, direct from the product owner) —
> overrides the older "one part per session" pace and the usual
> branch-and-PR rule:**
>
> 1. **The agent owns both frontend and backend, end to end.** No
>    splitting a task into "app side" and "DB side" and stopping at the
>    handover between them. If a task needs a migration, apply it; if it
>    needs app code, write it; if it needs a deploy, do it.
> 2. **Push directly to the default branch** (`main`). The product owner
>    explicitly does not want to review branches or PRs — the only
>    artifact they want to see is a push to `main`. This is a deliberate
>    override of the default "never push to main" rule, granted in
>    writing. Still: never force-push, and never rewrite published
>    history.
> 3. Credentials live in the registered-secret store, never pasted into
>    the chat transcript.

Last updated: 2026-10-15 (session that began Phase 1 and found §A1's
diagnosis to be largely **incorrect** — see the correction box under
§A1. Phase 1 shrank from "apply 025→044" to "apply a handful of
genuinely-missing migrations".)

---

## NEXT 40 TASKS — ordered work queue

Ordered by dependency, not by appeal. Phases 1 and 2 unblock everything
else; do not start Phase 3+ while Phase 1 is open, because a 500 caused
by a missing RPC is indistinguishable from a 500 caused by bad code.
Per the repo convention: one part-letter (a–e) per session.

**Four decisions are needed before several tasks can be scoped — see §F.**
They are listed there, not here, so this list stays a work queue.

---

### ▶ RESUME HERE — state at end of the 2026-10-15 session

**Everything below is verified, not assumed. Read this block, then start
at task 1.**

**Pushed and live:**
- `Zapier-codes/Velune` — commit `1f4a7d8` on `main`.
- `Zapier-codes/mavins-web` — commit `66a4cec` on `main`.
- `TODO.md` is byte-identical in both (git blob
  `25ba6212ebf7800d389822ded84374ff2124246c`). Edit one, copy to the
  other, push both — that is the convention.

**The one thing blocking Phase 1:** there is no route to run DDL against
the live DB. PostgREST cannot, under any key. A direct Postgres
connection **is** reachable — session pooler,
`aws-0-eu-west-1.pooler.supabase.com`, user
`postgres.atojskxrxfsbpeefigtm` — but the **database password** was
missing from the secret store. The product owner has now added a
PostgreSQL secret; **the first job of the next session is to find which
env-var name it is registered under and confirm it connects.** See §A4
for the exact matrix of what was tried and what each failure means.

**Secret names — resolved, §A3 has the full table.** The mismatches were
a typo plus a naming-convention clash, not a code fault. Register:
`SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_URL` (same value),
`SUPABASE_ANON_KEY`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` (same value),
`SUPABASE_SERVICE_ROLE_KEY`, `NEXT_PUBLIC_APP_URL`. The existing
`SUPABASE_SECRETE_KEY` is misspelled *and* unread by any code — it never
worked except for manual probing. Add `SUPABASE_DB_PASSWORD` for the
migrations. Do not commit any of it; `.env*` is gitignored.

**Tooling:** `tools/probe_rpc_existence.py` is committed in mavins-web.
Run it before believing any "this RPC is missing" claim.

**Then, in order, for Phase 1:**
1. Connect. If the password works, everything below is quick.
2. Apply exactly **three** migrations — not 025→044. The live DB is
   close to current; only these are genuinely absent:
   - `supabase_migration_025_live_campaigns_banner.sql`
     (defines `get_live_campaigns_for_banner` — the `/earn` 500 cause)
   - `supabase_migration_026_award_points.sql` (defines `award_points`)
   - `supabase_migration_034_bpay_tag.sql` (adds `users.bpay_tag`)
   All three are idempotent (`CREATE OR REPLACE` /
   `ADD COLUMN IF NOT EXISTS`), so re-running them is safe.
3. Re-probe: both RPCs must stop returning `PGRST202`; `users.bpay_tag`
   must exist. Then `GET /api/listener/campaigns` should return 200.
4. Tasks 3–4 (env var names), then Phase 2.

**Correction to carry forward — do not re-derive this:** the previous
handover's claim that "live Supabase is stuck at migration ~023, nine
RPCs missing" was **wrong**, and §A1 now carries a correction box
explaining why. The probe called parameterised functions with zero
arguments; PostgREST answers that with `PGRST202`, which looks exactly
like non-existence. **If you probe an RPC, pass a deliberately malformed
argument** — an existing function replies with a cast error
(`22P02`/`22007`), a missing one with `PGRST202`. 31 RPCs are live.

**Known trap when pushing:** the git remotes may carry a stale token.
`$GITHUB_TOKEN` is a GitHub **App** installation token and gets `403` on
push. **Use `$Gh_AUTH_TOKEN`** (a PAT with `repo` + `workflow` scope) —
`git remote set-url origin "https://x-access-token:${Gh_AUTH_TOKEN}@github.com/Zapier-codes/<repo>.git"`.
Set `git config user.name openhands` / `user.email
openhands@all-hands.dev` in a fresh clone; it is not preconfigured.

**Also known:** `/tmp` is wiped between sessions. Anything not committed
is gone. The Spotify repair scripts were lost that way (task 8).

---

### Phase 1 — Unblock the database (nothing is verifiable until this clears)

> **SCOPE CORRECTED 2026-10-15.** The original plan was "apply 025→044".
> That was based on a faulty probe (see the correction box under §A1).
> The live DB is close to current; only **three** artifacts are actually
> missing: `get_live_campaigns_for_banner` (025),
> `award_points` (026), and `users.bpay_tag` (034). Tasks 1–6 below are
> therefore collapsed into one job.

1. **A1b (revised)** — Apply exactly three migrations to live:
   `supabase_migration_025_live_campaigns_banner.sql`,
   `supabase_migration_026_award_points.sql`,
   `supabase_migration_034_bpay_tag.sql`. All three are written as
   idempotent `CREATE OR REPLACE` / `ADD COLUMN IF NOT EXISTS`.
   *Blocked on: a Postgres connection (see §A4).*
2. **A1c (revised)** — Re-probe: `get_live_campaigns_for_banner` and
   `award_points` must stop returning `PGRST202`; `users.bpay_tag` must
   appear. Then confirm `/earn` returns 200 instead of 500.
3. **A3a** — Enumerate every `process.env.*SUPABASE*` read in
   mavins-web and the Velune equivalent; compare to actual secret names.
4. **A3b** — Align the names; verify one authenticated request end-to-end.
5. **A2a/A2b** — Diff the two migration sets file-by-file and
   consolidate. **Lower priority than originally thought** — the live DB
   does not need it. It matters for reproducibility, not for unblocking.
6. **A1a** — Decide the canonical migration set. Also lower priority;
   same reasoning.
7. **A5 (new)** — Investigate the 12 live functions with no defining
   migration, and the 027/029 numbering gaps. *Done when:* either every
   live function has a committed definition, or the gap is documented as
   accepted. This is what makes the DB reproducible at all.


### Phase 2 — Finish the Spotify campaign work

8. **B5a** — Persist the repair tooling **into the repo**, not `/tmp`.
    The 2026-10-15 session lost `resolve_tracks.py`,
    `update_tracks.py`, `resolution.json` and the backup when `/tmp` was
    cleared between turns; only the DB writes survived. *Done when:*
    the scripts + a backup export are committed under a `tools/` path.
9. **B4c** — Relax the queue-slot RPC filter to gate on
    `resolved_song_id is not null` rather than on URL shape, preserving
    §36's intent (never hand the app something unplayable) while removing
    the blanket Spotify exclusion. *Note:* the current filter already
    accepts `resolved_song_id`, so this is hardening, not a blocker.
10. **B5b** — Harden the seed migration: `20260831000024_seed_user_campaigns.sql:91`
    still fabricates `'https://open.spotify.com/artist/' || id::TEXT` as
    its last-resort `COALESCE` fallback. Make it fail loudly instead of
    inventing a URL.
11. **B5c** — Close the recurrence hole: `ensure_seed_campaigns()` skips
    a seed only while it has an **active** campaign. If a repaired
    campaign is ever deactivated or completed and the function re-runs, a
    fresh row is created with the fabricated URL. Guard against it.
12. **B5d** — Repair the remaining **127 rows**, pass 1: artists kworb
    covers. Re-run the pipeline and re-apply.
13. **B5e** — Repair remaining rows, pass 2: MusicBrainz-only artists.
14. **B5f** — Repair remaining rows, pass 3: the ~53 hard artists
    (non-Latin scripts, common-word names). Expect manual review; do not
    loosen the artist-agreement rule to raise the count.
15. **B5g** — Add a DB-level guard/test asserting no row has an
    artist-level `source_url` with a null `resolved_song_id`.
16. **B4b** — Add a Spotify branch to `CampaignUrlResolver` so the app
    can resolve a Spotify link at runtime, not just pre-resolved data.
    *Only if §F3 is answered "yes".*
17. **B4b-2** — Wire `SpotifyMapper` into the campaign path. It is
    currently reachable only from `spotifyimport/`, never from
    `campaign/`.
18. **B4e** — On-device check that a repaired campaign actually plays.
    **Blocked:** no Android SDK, emulator, or device in this sandbox.

### Phase 3 — mavins-web wiring

19. **C1a** — Wire the header search box (`Header.tsx` renders a
    placeholder input with no handler at all).
20. **C1b** — Add platform validation to `/promote` and
    `/api/campaigns/create`. This omission is exactly how the fabricated
    Spotify rows entered the table.
21. **C1c** — Remove the dead `/share/:path*` matcher entry, or add the
    missing route. Decide which.
22. **C3a** — Delete-vs-keep for `currency.service.ts`,
    `purchaseMetrics.ts`, `shareUtils.ts`. Check `purchaseMetrics.ts`
    against `handover.md` ~12139 first — it may be a deliberate stub.
23. **C1d** — Fix `/earn` 500. **Depends on Phase 1.**
24. **C1e** — Fix `/api/listener/balance` 500. **Depends on Phase 1.**

### Phase 4 — Pawns SDK

25. **D1a** — Rotate the Pawns key exposed in this public repo's git
    history. *Needs the product owner; cannot be verified from here.*
26. **D1b** — Decide git-history rewrite vs accept-and-move-on. Rotating
    is load-bearing; rewriting is optional hardening.
27. **D2a** — Surface `getStatus()` / `getLastError()` in the UI; today a
    user cannot tell whether sharing is running.
28. **D2b** — Wire `PawnsManager.clearAllData()` to account deletion. The
    consent copy implies the user can clear their data; nothing calls it.
29. **D2c** — Confirm the AAR versions are current and that the AARs are
    legitimately redistributable in a public repo.
30. **D2d** — Confirm a tenant built with an empty `PAWNS_API_KEY`
    degrades gracefully rather than silently attempting to start.

### Phase 5 — Inherited handover work

31. **mavins 46e** — Audit trail + safety rails across all admin work.
32. **mavins 46f** — Admin roles/permissions + user-management mutating
    actions + starting-capital grant.
33. **mavins 48d** — Finish the gamification wiring (Part 1 done).
34. **mavins Task 70** — Fork B-Pay into `Zapier-codes`, reconfigure onto
    mavins-web's own Supabase project as a dedicated disbursement app.
35. **mavins Task 16** — Resolve the `role DEFAULT 'artist'` contradiction
    against Task 48's finding of zero `'artist'` rows in live data. Check
    the live column default directly.
36. **Velune** — Build the flanger effect for Simple. Confirmed zero
    flanger implementation exists anywhere; genuine from-scratch DSP.
37. **Velune** — Resolve the hide-vs-grey discrepancy on the local-track
    video toggle. Confirm intended treatment before changing anything.
38. **Velune** — On-device EQ/DSP verification pass. The largest standing
    gap: nothing EQ/DSP-related has ever run on a real phone.
39. **A5** — Reproducibility: give every out-of-band live function a
    committed migration, and resolve the 027/029 numbering gaps.
40. **A6** — After 025/026/034 land: re-verify the whole listener payout
    chain end-to-end (stream → earnings → request → disburse → B-Pay
    ledger), since it has never been confirmed working as a whole.

---

## A. Blockers — nothing else on this list is trustworthy until these clear

### A1. Live Supabase is stuck at migration ~023; the app's later RPCs do not exist
**Verified this session, not inferred.** Direct REST probes against the
live project (`atojskxrxfsbpeefigtm`) returned `PGRST202` ("no matches
were found in the schema cache") for:

> ## ⛔ CORRECTION (2026-10-15) — THE TABLE BELOW IS LARGELY WRONG
>
> **The original probe called parameterised functions with zero
> arguments.** PostgREST answers that with `PGRST202` — *"Searched for
> the function … without parameters … but no matches were found"* —
> which is indistinguishable, at a glance, from "the function does not
> exist". It was read as the latter. It is not the latter.
>
> Re-probed properly, by calling each function with a **deliberately
> malformed argument**: an existing function answers with a *cast
> error* (`22P02` for a bad uuid, `22007` for a bad date); a genuinely
> absent one answers with `PGRST202`. Results:
>
> | RPC | Original claim | Actual |
> |---|---|---|
> | `ensure_device_listener` | MISSING | **EXISTS** (`22P02`) |
> | `compute_daily_payout_pool` | MISSING | **EXISTS** (`22007`) |
> | `request_listener_withdrawal` | MISSING | **EXISTS** (`22P02`) |
> | `disburse_listener_withdrawal` | MISSING | **EXISTS** (`22P02`) |
> | `credit_listener_earnings` | MISSING | **EXISTS** as `credit_listener_earnings_for_date` (`22007`) |
> | `sweep_claimable_withdrawals` | MISSING | **EXISTS** as `sweep_claimable_withdrawals_for_disbursement` (called 200) |
> | `get_live_campaigns_for_banner` | MISSING | **genuinely ABSENT** |
> | `award_points` | MISSING | **genuinely ABSENT** |
>
> The live schema cache exposes **31 RPCs**. Migrations 028, 030, 031,
> 032, 035, 036, 039, 040, 041 and 044 — all of which the original
> table listed as "MISSING" or implied unapplied — are **defined in the
> repo and present live**. `track_campaigns.campaign_name` (033) is
> present too.
>
> **So the live DB is not "stuck at ~023".** It is close to current,
> with two specific gaps. Phase 1 shrinks from "apply 025→044" to
> "apply the two genuinely-missing pieces", and the `/earn` 500 has one
> precise cause rather than a vague migration backlog.
>
> **Genuinely missing live, with a defining migration that exists and
> simply needs applying:**
> - `get_live_campaigns_for_banner` — `supabase_migration_025_live_campaigns_banner.sql:40`
>   defines it (`CREATE OR REPLACE FUNCTION public.get_live_campaigns_for_banner()`).
>   Absent live. **This is the single cause of the `/earn` 500.**
> - `award_points` — `supabase_migration_026_award_points.sql:51` defines it.
>   Absent live; `api/gamification/streak/update` already calls it.
> - `users.bpay_tag` — `supabase_migration_034_bpay_tag.sql:43` adds it
>   (`ALTER TABLE public.users ADD COLUMN IF NOT EXISTS bpay_tag TEXT`).
>   **Note the original §A1 table said `track_campaigns.bpay_tag` — that
>   was wrong twice over: the column belongs to `users`, not
>   `track_campaigns`, and 034 does exist.**
>
> *(A first pass at this correction wrongly claimed 025/026/034 had no
> defining file at all. They do. That pass had only grepped for
> `create function` and missed the `alter table` in 034 and mis-scoped
> the search. Corrected here.)*
>
> **Genuine numbering gaps:** migrations **027** and **029** exist in
> neither the root set nor the CLI set. They may be harmless numbering
> gaps or deleted migrations; unresolved, and worth a look before
> treating the root set as complete.
>
> **12 further live functions have no defining migration in the repo**
> (`balance_culture`, `cleanup_old_shares`, `expire_stale_replies`,
> `increment`, `increment_batch`, `is_admin`,
> `process_daily_growth_purchases`, `record_campaign_stream`,
> `refresh_memory_digests_staggered`, `shuffle_daily_services`,
> `update_dilation_and_spawn`, `update_slang_trends`). They exist live
> but were created out-of-band. **There is no migration set that would
> recreate this database from scratch** — that is a real, separate
> finding, and it means "apply all the migrations" was never going to be
> sufficient regardless.
>
> **Revised Phase 1 scope: apply 025, 026, 034 — not 025→044.**

The original (now-corrected) probe output, kept for the record:

| RPC | Live status | Defined in |
|---|---|---|
| `get_next_campaign_for_queue_slot` | **exists** | `supabase_migration_023_fair_rotation_queue_slot.sql` |
| `get_live_campaigns_for_banner` | **MISSING** | `supabase_migration_025_live_campaigns_banner.sql` |
| `award_points` | **MISSING** | `supabase_migration_026_award_points.sql` |
| `ensure_device_listener` | **MISSING** | `supabase_migration_028_ensure_device_listener.sql` |
| `compute_daily_payout_pool` | **MISSING** | `supabase_migration_030_...` |
| `credit_listener_earnings` | **MISSING** | `supabase_migration_031_...` |
| `request_listener_withdrawal` | **MISSING** | `supabase_migration_032_...` |
| `disburse_listener_withdrawal` | **MISSING** | `supabase_migration_039_...` |
| `sweep_claimable_withdrawals` | **MISSING** | `supabase_migration_041_...` |

Columns/tables show the same watermark: `track_campaigns.campaign_name`
exists (033) but `track_campaigns.bpay_tag` does not (034);
`platform_fee_settings` and `payment_sessions` exist but
`admin_actions` (015) and `admin_roles` (016) return 404.

**This is the single highest-value item on the list.** It is the direct
cause of the mavins-web `/earn` 500 and of the missing listener-payout
machinery, and it makes every "is feature X working?" question
unanswerable until it is resolved.

- **A1a** — Determine the intended apply order and ownership of the 42
  root-level `supabase_migration_*.sql` files vs. the 30
  `supabase/migrations/` CLI-managed files (see A2). Decide which set is
  canonical **before** applying anything.
- **A1b** — Apply the missing migrations (025 → 044) to the live project,
  in numeric order, from the canonical set chosen in A1a.
- **A1c** — Re-run the probe table above and confirm every RPC now
  returns a non-`PGRST202` response. Re-check `/api/listener/campaigns`
  returns 200 rather than 500.

### A2. Migration directories have diverged — two competing sources of truth
`supabase_migration_NNN_*.sql` (42 files, repo root) and
`supabase/migrations/<timestamp>_*.sql` (30 files, Supabase CLI layout)
are **not** mirrors of each other. The CLI folder is missing 023 and
025–034 entirely; it has its own extra files (`services.sql`,
`create_services.sql`, `seed_user_campaigns.sql`, `add_coverart.sql`,
`enhanced_leaderboard.sql`, `deduct_growth_budget.sql`) that do not
exist at root.

- **A2a** — Diff the two sets file-by-file and classify each as
  (i) same migration, different name, (ii) only-at-root, (iii)
  only-in-CLI-folder.
- **A2b** — Consolidate onto one directory with one naming scheme; record
  the decision in mavins-web's `handover.md`. Do **not** delete either
  set until A1b has been applied and verified — the root set is the only
  copy of 025–034.

### A3. Supabase env var **names** are wrong in both repos
The credential **values** are correct (project ref
`atojskxrxfsbpeefigtm`; the anon key decodes to `role:anon` for that
same ref and its sha256 matches the stored secret). But the names under
which they are stored do not match what the code reads, in both repos.
Verify and align the names before debugging any auth/RLS symptom, or a
correct key will keep looking like a broken one.

- **A3a** — Enumerate every `process.env.*SUPABASE*` reference in
  mavins-web and every equivalent in Velune, and compare against the
  actual secret names.
- **A3b** — Rename/re-register so names match reads; re-verify an
  authenticated request end-to-end.

#### A3 RESOLVED (2026-10-15) — the exact names to register

**Root cause of the mismatch: the registered names do not match what
either codebase reads.** There are two independent problems — a typo,
and a naming convention clash — and neither codebase is at fault.

What the code actually reads (verified by grepping both repos, not from
memory):

| Read by | Variable | Count |
|---|---|---|
| Velune (`app/build.gradle.kts:121-129`) | `SUPABASE_URL` | 7 |
| Velune | `SUPABASE_ANON_KEY` | 5 |
| mavins-web | `NEXT_PUBLIC_SUPABASE_URL` | 26 |
| mavins-web | `NEXT_PUBLIC_SUPABASE_ANON_KEY` | 21 |
| mavins-web | `SUPABASE_SERVICE_ROLE_KEY` | 7 |

What is currently registered, and why it fails:

| Registered | Problem |
|---|---|
| `SUPABASE_URL` | **Correct.** Matches Velune. mavins-web still needs the `NEXT_PUBLIC_` twin. |
| `SUPABASE_PUBLIC_KEY` | **No code reads this.** Velune wants `SUPABASE_ANON_KEY`; mavins-web wants `NEXT_PUBLIC_SUPABASE_ANON_KEY`. |
| `SUPABASE_SECRETE_KEY` | **Two problems.** Misspelled (`SECRETE` → `SECRET`), *and* no code reads this name — mavins-web wants `SUPABASE_SERVICE_ROLE_KEY`. |

**Register these six names.** The values are the same two keys repeated
under each name the code expects — that redundancy is what makes both
repos work without editing either one:

| Name to register | Value | Needed by |
|---|---|---|
| `SUPABASE_URL` | project URL | Velune |
| `NEXT_PUBLIC_SUPABASE_URL` | **same** project URL | mavins-web |
| `SUPABASE_ANON_KEY` | anon / publishable key | Velune |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | **same** anon key | mavins-web |
| `SUPABASE_SERVICE_ROLE_KEY` | service_role / secret key | mavins-web (server only) |
| `NEXT_PUBLIC_APP_URL` | site origin, e.g. `http://localhost:3000` | mavins-web |

Plus, to unblock §A4 (migrations) — **one** of these, whichever you
prefer; the next session will look for both:

| Name to register | Value |
|---|---|
| `SUPABASE_DB_PASSWORD` | the Postgres password, or a full `postgresql://` URI |

**Notes that matter:**
- `SUPABASE_SECRETE_KEY` is misspelled. Fix it to `SUPABASE_SECRET_KEY`
  *or* delete it — but note that **neither spelling is read by any
  code**. It only ever worked for manual REST probing. Renaming it to
  `SUPABASE_SERVICE_ROLE_KEY` is the change that actually matters.
- The anon key is **safe** to expose to the browser and is expected in a
  `NEXT_PUBLIC_` variable. The service_role key is **not** — it bypasses
  RLS. Keep it server-side only. All 7 current uses in mavins-web are in
  `src/app/api/**` route handlers or `src/lib/supabase/admin.ts`, which
  is correct; do not add `NEXT_PUBLIC_` to it.
- `.env*` is gitignored in mavins-web (`.gitignore:34`), so none of this
  belongs in the repo. Velune reads the same values from
  `local.properties` (also untracked) or the environment.
- **Do not rename to match the Supabase dashboard's newer vocabulary.**
  The dashboard may now label these "publishable key" and "secret key";
  the code still reads the names above, so register under the names
  above.

- **A3a** — *done* — enumeration complete, table above.
- **A3b** — Register the six names above; then load `/earn` and confirm
  it no longer fails on a missing key. (It will still 500 until §A1's
  migration 025 is applied — those are two separate faults, and fixing
  the names will not fix the 500 on its own.)

### A4. There is no working path to run DDL against the live database
**Established this session by direct test, not assumption.** Applying a
migration needs `CREATE OR REPLACE FUNCTION` / `ALTER TABLE`. The
credentials currently registered cannot do that:

| Route | Result |
|---|---|
| `SUPABASE_PUBLIC_KEY` (anon) | PostgREST only; no DDL |
| `SUPABASE_SECRETE_KEY` (service_role) | PostgREST only; **no DDL** |
| Generic `exec_sql`-style RPC | **does not exist** (probed ~10 names) |
| Direct Postgres, `db.<ref>.supabase.co:5432` | **DNS does not resolve** (IPv6-only) |
| Direct Postgres, pooler | **reachable**, tenant located in `eu-west-1` |
| Pooler with service_role JWT as password | `28P01 password authentication failed` |
| `psql` / `supabase` CLI | **not installed** |
| `.pgpass`, `supabase/config.toml`, committed `.env` | **no DB password present** |

**The finding:** PostgREST is a data API, not a SQL console. It can
`SELECT`/`INSERT`/`UPDATE` and call `CREATE`-less RPCs, but it cannot
execute DDL under *any* key. The service_role key is not the missing
piece — a **Postgres password** is, and a direct connection is genuinely
reachable, so that is the whole gap.

**`A4a` — register the DB password as a secret.** The exact value is in
the Supabase dashboard under *Project Settings → Database → Connection
string* (the password shown there, or a newly reset one). A full
`postgresql://` connection URI works too and is less error-prone. Use the
**session pooler** form — the host is
`aws-0-eu-west-1.pooler.supabase.com`, user `postgres.<project-ref>`.
Do not paste it into the chat; put it in the secret store.

**`A4b`** — with that secret, apply 025, 026 and 034, then re-probe.

**Why not just paste it in the chat:** a chat transcript is retained and
is the wrong place for a database superuser password. The product owner
offered to do exactly that — the offer is appreciated but declining it is
the correct call, and rotating afterwards would not undo the exposure.
A registered secret is injected into the command environment on demand
and is masked in output, which is what is needed here.

---

## B. Spotify links on the campaign card — feasibility + the real fix

**Short answer: the capability already exists in Velune. The blocker is
data quality plus one deliberate SQL filter, not missing code.**

### B1. What is already there (verified by reading the code)
Velune already contains a complete Spotify subsystem:

- `spotify/SpotifyAuth.kt` — obtains tokens via `sp_dc` cookie +
  TOTP (no OAuth app registration needed).
- `spotify/SpotifyMapper.kt` — `mapPlaylistTracks()` searches YouTube
  per Spotify track (`YouTube.search(query, SearchFilter.FILTER_SONG)`)
  and returns a playable `SongItem`. This is exactly the
  Spotify→playable-audio capability being asked for.
- `spotifyimport/SpotifyImport{Screen,ViewModel,Repository,Models}.kt` —
  the UI and orchestration around it.

**Velune has no Spotify playback SDK.** Playback is YouTube-backed plus
local media. So "the song can be played in the app" necessarily means
"resolve the Spotify link to a YouTube video", which `SpotifyMapper`
already does for playlists.

### B2. Why Spotify campaigns still do not play — three independent causes

1. **The queue-slot RPC deliberately excludes them.** Velune's current
   `main` (`b453a02`) added an `ilike`-based YouTube-shape filter to
   `get_next_campaign_for_queue_slot`; its message is "Restrict
   queue-slot RPC to YouTube-playable campaigns only". A Spotify
   `source_url` is filtered out by design. `HANDOVER_CAMPAIGN.md` §36
   documents this as an intentional fix for a slot that kept returning
   an unplayable Spotify row.
2. **`CampaignUrlResolver.extractVideoId()` only parses YouTube URL
   shapes** and falls back to treating the input as a bare 11-char
   video id. A Spotify URL yields `null`, so
   `CampaignRepository.fetchNextCampaignForQueueSlot` logs "could not
   extract a video id" and returns `null`.
3. **The Spotify links in the data are mostly fabricated.** Of 380
   `track_campaigns` rows, **379 are `open.spotify.com/artist/...` and
   only 1 is YouTube-shaped**. The artist IDs were generated by
   `supabase/migrations/20260831000024_seed_user_campaigns.sql` as
   `'https://open.spotify.com/artist/' || seed_record.id::TEXT`, i.e.
   the artist's **internal UUID**, not a real Spotify ID. Spot-checked
   against Spotify's own oEmbed: Central Cee's stored
   `1Cd373x9fTEKCKjpKK0JXZ` → **404**, while the real
   `5H4yInM5zmHqpKIoMNAx4r` → 200. Of 20 distinct sampled IDs,
   **7 resolved and 12 did not** (1 inconclusive). Note the seed row
   prefers `COALESCE(spotify_url, youtube_url, ...)`, so it picks the
   broken Spotify value even though a real `youtube_url` exists on the
   same user row.

### B3. Assessment of `Zapier-codes/SpotiFLAC-Mobile` as the source
Cloned and read. It is a **Flutter/Dart** app with a **Go** backend —
none of it is directly reusable in a Kotlin app. More importantly, its
actual resolution mechanism does **not** transfer:

- Its Spotify→YouTube hop is `songlink.go` → `api.song.link`.
  **Tested live: `api.song.link` now returns HTTP 401 without an API
  key**, and the public `song.link` page for a track returns **no
  YouTube URL at all** (only Deezer, Amazon, TIDAL, Napster, Yandex,
  Audius, Boomplay, Anghami, Pandora). So the technique SpotiFLAC relies
  on is no longer usable unauthenticated.
- What **does** work unauthenticated and is worth borrowing: Spotify's
  **oEmbed** endpoint (`https://open.spotify.com/oembed?url=...`)
  returns title + thumbnail (200 for tracks and artists; artist
  embeds also expose top tracks). This is a free metadata source with
  no key.

**Conclusion:** do not port SpotiFLAC. Use Velune's own existing
`SpotifyMapper` path, fed by oEmbed for the artist-link case.

### B4. The fix, split into parts

- **B4a** — Repair the seed data. Replace fabricated
  `open.spotify.com/artist/<uuid>` values with real IDs, or reorder the
  seed `COALESCE` to prefer the already-present valid `youtube_url`.
  Confirm which of the two the product actually wants before editing
  (see Open Questions).
- **B4b** — Extend `CampaignUrlResolver` with a Spotify branch: detect
  `open.spotify.com/{track,album,artist}` and resolve to a YouTube
  video id. For a track link, use oEmbed for title/artist then the
  existing `SpotifyMapper` search. For an artist link, resolve the
  artist's top track via the artist embed page, then the same search.
- **B4c** — Relax the queue-slot RPC's YouTube-only `ilike` filter so a
  Spotify-linked campaign is eligible **once it is resolvable** — i.e.
  gate on `resolved_song_id is not null` rather than on the URL shape,
  so unresolved rows are still excluded. Keep §36's original intent
  (never hand the app something it cannot play) while removing the
  blanket Spotify exclusion.
- **B4d** — Backfill `resolved_song_id` for existing Spotify campaigns
  via the B4b resolver, so they become eligible under B4c without
  waiting to be re-seeded.
- **B4e** — On-device verification. **Not possible in this sandbox** (no
  Android SDK, no emulator, no phone). This is the same standing
  limitation every recent Velune session has recorded.

### B5. ✅ DONE 2026-10-15 — the data repair (B4a/B4d) is applied

The chosen approach needs **no app change at all**, because
`CampaignRepository` already prefers `resolved_song_id` over
`source_url` (`CampaignRepository.kt:260`), and the queue-slot RPC's
eligibility filter already accepts `resolved_song_id is not null`
(`track_campaigns_queue_slot_rpc.sql:74`). So the fix was to stop
storing an unplayable artist link and store a real track link plus a
resolved YouTube id.

What was run (scripts kept in the session, not committed):

1. For each seed artist, resolve the **real** Spotify artist id —
   kworb's index (103 artists), else MusicBrainz `url-rels` (32), else
   Spotify's own search page (0; it is client-rendered and exposes no
   server-side artist entities).
2. Read the artist's real track titles from the Spotify **artist embed**
   plus kworb's full `_songs.html` page (44–262 titles per artist).
3. Match `users.metadata.top_tracks[].name` against those titles to get
   a genuine **track-level** Spotify id.
4. Resolve that track to a **YouTube video id** by searching YouTube and
   scoring candidates, then verifying the pick with YouTube **oEmbed**.
5. Write `track_campaigns.source_url` (real track URL) +
   `track_campaigns.resolved_song_id` (video id), and repair
   `users.spotify_url` / `users.spotify_id`.

Result: **252 of 381 campaign rows** repaired (98 artists, 154 verified
tracks). Those rows were previously **all** `resolved_song_id = NULL`
with a fabricated artist URL. Verified after the write:

- `get_next_campaign_for_queue_slot(null)` now returns a row whose
  `source_url` is a real `open.spotify.com/track/...` and whose
  `resolved_song_id` (`yEgilud6tRU`) passes YouTube oEmbed 200
  ("Fousheé - Deep End").
- Backup of every prior value: `backup_before_update.json`.

> **⚠️ The tooling and the backup did not survive.** `/tmp` was cleared
> between sessions, taking `resolve_tracks.py`, `update_tracks.py`,
> `resolution.json` and `backup_before_update.json`. Only the **DB
> writes** persisted (re-verified: 252 track URLs, 252 video ids, 151/151
> real artist ids). There is currently **no rollback artifact** and no
> committed script to re-run the repair. Rebuilding the tooling into the
> repo is task 10. Until then, treat the applied values as
> unreproducible.

Precision was deliberately favoured over recall. The scorer requires
whole-word artist agreement **and** that the artist owns the upload or is
named in the video title; single-token artist names (e.g. "Mora") need
the name in the title, an exact channel match, or a `- Topic` channel.
This drops ~53 artists rather than risk pointing a paid campaign at a
stranger's video — a wrong pick is worse than an unresolved row, since
the app already drops unresolved rows cleanly.

Two real bugs found and fixed while building this, both worth keeping in
mind for any similar work:

- **Accents were being replaced by a space**, not stripped: NFKD
  decomposition left combining marks that a `[^a-z0-9]` class turned
  into separators, so `"Me Rehúso"` normalised to `"me rehu so"` and
  `"Tiësto"` to `"tie sto"`. This silently broke both artist-id lookup
  and title matching. Fix: drop combining marks before the character
  class.
- **Bracketed suffixes were stripped before variant detection**, so
  `norm()` erased `"[Acoustic Version]"` and the "is this a variant"
  penalty could never fire. Variant detection must run on the raw title.

Still open: the ~127 rows not covered (53 artists with no verified
track) remain `resolved_song_id = NULL` and are correctly excluded by
the RPC. `B4b`/`B4c` are **not** needed for the Spotify-link case now
that the data is real, but `B4b` is still the right change if the
product wants the app to accept a Spotify URL pasted at runtime rather
than resolved ahead of time. `B4e` (on-device playback check) is still
unverified in this sandbox.

---

## C. mavins-web — wiring audit results

### C1. Confirmed broken / unwired
- **`/earn` returns HTTP 500.** Root cause is A1: the route calls
  `admin.rpc('get_live_campaigns_for_banner')`, which does not exist
  live. Verified directly (`/api/listener/campaigns` → 500
  `{"success":false,"error":"Failed to load campaigns"}`).
- **`/earn` balance panel is also dead** — `/api/listener/balance` → 500,
  same class of cause (`ensure_device_listener` missing).
- **Header search box is completely unwired.** `Header.tsx` renders
  `<input placeholder="Search campaigns, tracks..." />` with no
  `onChange`, `onSubmit`, `value`, or handler of any kind. It is a
  decorative input; typing and pressing Enter does nothing.
- **`middleware.ts` matches `/share/:path*`, but no `/share` route
  exists.** Dead matcher entry; the matcher also lists
  `/api/share/:path*` separately, so the `/share` line is redundant even
  if a page were added.
- **`promote` performs no platform validation.** `page.tsx:743` only
  rejects an empty string (`'Please enter a YouTube URL'`), and
  `/api/campaigns/create` never inspects `sourceUrl` for a platform at
  all — it stores whatever it is given. This is precisely how the
  Spotify rows in B2.3 got into the table from the web side.

### C2. Corrected findings — **not** dead, contrary to the first pass
Recording these so the next session does not re-flag them:
- `src/components/promote/PublicAnalyticsShowcase.tsx` **is used** —
  dynamically imported and rendered by `src/app/promote/page.tsx`.
- `src/middleware.ts` **is active** — it is the Next.js root middleware.
- `src/services/freshconnect/freshconnect.service.ts` and
  `src/lib/auth/guestCheckout.ts` are **orphaned, not deleted** — their
  only remaining references are explanatory comments in
  `campaignCancellation.service.ts` and
  `api/payments/initialize/route.ts`. They carry no runtime effect.

### C3. Genuinely unreferenced (zero importers)
- `src/services/currency/currency.service.ts`
- `src/lib/growth/purchaseMetrics.ts`
- `src/lib/utils/shareUtils.ts`

- **C3a** — For each of the three, decide delete-vs-keep-and-document.
  `purchaseMetrics.ts` is worth a second look before deleting: it is
  specified as pseudocode in mavins-web's own `handover.md` (~line
  12139) as part of the planned growth-service purchase flow, so it may
  be a deliberate stub rather than leftover debris.

### C4. Pages verified working (guest state renders correctly)
Probed live, all HTTP 200 and rendering their intended signed-out
content: `/`, `/analytics`, `/campaign-live`, `/leaderboard`,
`/notifications`, `/settings`, `/security`, `/appearance`,
`/complete-profile`, `/auth/confirmed`, `/fund-wallet`, `/promote`,
`/earnings`. **`/notifications` and `/settings` were flagged as broken
in an earlier pass and are not** — they render fine in a real browser;
the earlier read was an artifact of inspecting pre-hydration HTML.

---

## D. Pawns SDK — findings and follow-up

**Correction to an earlier note: the Pawns SDK lives in Velune, not
mavins-web.** 76 references across the app.

Present in Velune:
- `app/libs/pawns-ndk-1.8.1.aar`, `app/libs/internet-sharing-1.8.1.aar`
- `app/src/main/java/com/nikhil/yt/PawnsManager.kt` (singleton;
  `initialize` / `optIn` / `optOut` / `start` / `stop` / `getStatus` /
  `clearAllData`)
- `ConsentModal.kt` (consent copy, links to Pawns privacy + acceptable-use
  policies, 18+/account-holder attestation)
- `PawnsBootReceiver` (declared in `AndroidManifest.xml`)
- `MusicService.kt` (starts the SDK on service start)
- `app/build.gradle.kts` — `PAWNS_API_KEY` build field
- `scripts/brand.sh`, `configs/sample.json`, and
  `.github/workflows/build-tenant.yml`

### D1. Security item — rotate the exposed Pawns key
`app/build.gradle.kts` carries an explicit comment that the Pawns key
was previously hardcoded in source as `MASTER_API_KEY` and **committed
to this public repo's git history**. Moving it to a build-time secret
does **not** un-expose the already-pushed value.

- **D1a** — Rotate the key in the Pawns dashboard. Confirm with the
  product owner that this has happened; it cannot be verified from here.
- **D1b** — Decide whether to rewrite git history (e.g. `filter-repo`)
  or accept the exposure now that the key is rotated. Rotating is the
  load-bearing step; history rewriting is optional hardening.

### D2. Open Pawns work
- **D2a** — `getStatus()` / `getLastError()` are never surfaced in any
  UI. There is no way for a user to tell whether sharing is actually
  running. Add a status surface.
- **D2b** — `PawnsManager.clearAllData()` exists but no call site was
  found; wire it to account deletion / "clear my data", which the
  consent copy implies the user can do.
- **D2c** — Confirm `internet-sharing-1.8.1.aar` (and
  `pawns-ndk-1.8.1.aar`) are still the intended versions and that the
  AARs are legitimately redistributable in a public repo.
- **D2d** — `brand.sh` falls back to an empty `PAWNS_API_KEY` when none
  is supplied. Confirm a tenant built with no key degrades gracefully
  (consent screen should not appear / SDK should not init) rather than
  silently attempting to start.

---

## E. Still-open work inherited from the handover files

Not re-derived this session — carried forward verbatim from each repo's
own open checkboxes, listed so they are not lost.

**mavins-web** (`handover.md`, unchecked):
- `46e` — Audit trail + safety rails across all admin work
- `46f` — Admin role/permission system + user-management mutating
  actions + starting-capital grant
- `48-d` — Finish/extend the gamification system ("wired fully") —
  Part 1 done, remainder open
- `Task 70` — Fork B-Pay into `Zapier-codes`, reconfigure onto
  mavins-web's own Supabase project as a dedicated disbursement app
  (`d`: reconfigure Supabase config + fix committed-secret practice;
  `f`: listener must submit their own `@bpay_tag`)
- `Task 71` — Integrate Lizzysub (VTU) and Juicyway into B-Pay-backend;
  canonical write-up now lives in B-Pay-backend's own `handover.md`,
  Task 44
- `Task 16` — unresolved discrepancy: its `role DEFAULT 'artist'` claim
  contradicts Task 48's finding of zero `'artist'` rows in live data.
  Check the column's actual live default directly.

**Velune** (`HANDOVER.md` §4 / §5):
- On-device verification pass — nothing EQ/DSP-related has ever run on a
  real phone; the largest standing gap
- The rest of "and other polices"/"and other polishes" — still
  unitemized; needs specifics from the product owner
- Flanger effect for Simple — genuine from-scratch DSP build (confirmed
  zero flanger implementation exists anywhere)
- Hide-vs-grey discrepancy on the local-track video toggle — flagged,
  unresolved; confirm intended treatment first
- Velune's §5 backlog (2026-08, user-reported) and §6 reconnaissance —
  see `HANDOVER.md` directly; not reproduced here

---

## F. Open questions — need a product answer before the work can be scoped

1. **Spotify campaigns: what should a "Spotify link" actually mean?**
   The stored data is **artist** links (379 of 380 rows). An artist has
   many tracks; a campaign card plays one. Should an artist link resolve
   to the artist's top track, to a specific track chosen by the
   advertiser, or should the seed data be switched to track links?
2. **Repair or replace the seed data?** Replace the fabricated artist IDs
   with real ones, or change the seed `COALESCE` to prefer the valid
   `youtube_url` that already exists on the same row? (B4a.)
3. **Should the web promote form accept Spotify links at all?** Today it
   accepts anything and stores it unvalidated (C1). If Velune can now
   resolve Spotify links, the form should say so and validate
   accordingly; if not, it should reject them at the door.
4. **Pawns key rotation (D1a)** — has it been done? Only the product
   owner can confirm.

---

## G. Explicit limitations of this session

Recorded so nothing here is mistaken for a stronger claim than it is:

- **No Android build or on-device run.** No Android SDK, emulator, or
  device in this sandbox. Every Velune runtime claim is from reading
  code, not from execution. B4e is therefore not just undone but
  undoable here.
- **Live-DB findings are probe-based**, via the REST API with the anon
  key, not from `psql` or the Supabase dashboard. `PGRST202` on an RPC
  is conclusive for "not in the schema cache"; it is not a substitute
  for reading `pg_get_functiondef`.
- **The `admin_actions` / `admin_roles` 404s** are recorded as
  "absent from the exposed schema". PostgREST hides tables the anon role
  cannot see, so absence of a 200 is not proof the table was never
  created — it is proof it is not reachable by this key. Confirm with a
  service-role or dashboard check before treating them as unapplied.
- **The Spotify ID validity sample is 20 of 151 distinct IDs.** The
  7-valid/12-invalid split is a sample, not a census. Extrapolating a
  precise failure rate would be overreach; the finding is directional
  and strong, and the specific Central Cee example is conclusive.
- **B-Pay-backend was not cloned**, so its Task 44/71 items are carried
  forward unverified.
- **The repair is durable but not immune to recurrence.** The seed
  migration's `COALESCE(spotify_url, youtube_url, '…/artist/'||id)` is
  now harmless because every seed user has a real `spotify_url`, and
  `ensure_seed_campaigns()` skips any seed that already has an active
  campaign. But that guard only checks for an **active** campaign: if a
  repaired campaign is deactivated or completed and the function runs
  again, a new row is inserted. With real `spotify_url` values present it
  would insert a real artist URL and a **null** `resolved_song_id` — i.e.
  the exact unplayable shape this session removed, though no longer
  fabricated. Tasks 12 and 13 close this properly.
