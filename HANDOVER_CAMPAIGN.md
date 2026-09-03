# Velune Campaign Card Handover (v1)

> **▶ START HERE — read this box only, then go to §8 below for the
> real next work. Skip the rest unless you get stuck.**
>
> **Newest note (2026-09-02, latest of all) — §20: Task 59 fully
> done, all 16 rounds. Part b-b-b-b (final piece):
> `OnlinePlaylistScreen.kt`'s 4 `YouTubeQueue(...)` sites now carry
> `genre = viewModel.genreTileTitle`.** Genre now survives the entire
> path end to end — home-screen genre-tile tap through every
> song-tap-to-queue site in the app, into `MusicService.kt`'s own
> `campaignSlotProvider`. Canonical write-up in Mavins-web's
> `handover.md`, Task 59's own "Round 16" section. **Two small,
> separate follow-ups remain open, not part of this closure**: a
> grid-tap-to-album/artist/playlist navigation gap, and a pre-existing
> HTTP-status log-escaping bug in `CampaignRepository.kt` — both
> flagged in Mavins-web's own write-up, neither fixed yet.
>
> **Newest note (2026-09-02, previous) — §19: Round 16 B-ii Part
> b-b-b split a/b, Part b-b-b-a done (`AlbumScreen.kt`'s 3 call sites,
> commit `e2ba9ef`); Part b-b-b-b (`OnlinePlaylistScreen.kt`, 4 sites)
> not started.** Canonical write-up in Mavins-web's `handover.md`,
> Task 59's own "Round 16" — see §19 below for the sync-note summary.
> **Next: Part b-b-b-b — the last remaining piece of this entire
> genre-threading chain.**
>
> **Newest note (2026-09-01, latest of all) — §13: cache-lifecycle half
> of the remaining chain built (`GenreTileMappingCache.kt`);
> `MusicService.kt`'s own wiring still open, single call site next.**
> Canonical write-up in Mavins-web's `handover.md`, Task 59's own
> "Round 13" — see §13 below for the sync-note summary.
>
> **Newest note (2026-09-01, latest of all) — Task 59 Part 2b-b's
> first job done: `MAVINS_API_URL` confirmed + `ingestGenreTile()`
> built.** See §11 below for the full write-up, commit `4652493`. Also
> flags (does NOT fix) a real, pre-existing bug found across all six
> HTTP-status log lines in `CampaignRepository.kt` — worth its own
> small later part. **Next: the 6-file UI/nav genre-threading chain**
> (`MoodAndGenresScreen` → `NavigationBuilder` →
> `YouTubeBrowseViewModel`/`Screen` → `PlayerConnection.kt` →
> `MusicService`) — deliberately not started this session, per this
> project's own one-part-per-session rule. See Mavins-web's own
> `handover.md`, Task 59, for that session's actual next step.
>
> **Newest note (2026-08-30, latest of all) — §10 added: Task 59 Part 2
> (queue-slot campaign injection wiring) traced end-to-end in this
> repo's own code, not implemented.** Sync note only — canonical
> write-up is in Mavins-web's `handover.md`, Task 59's own "Round 5"
> entry (full file/line call chain + build plan). Read §10 below first,
> then that entry, before starting Part 2 here.
>
> **Newest note, same session (2026-08-30, latest of all) — §9 CLOSED:
> root cause confirmed by the corrected query, fix written in
> Mavins-web as `supabase_migration_020_trending_campaigns_show_
> planting.sql`.** Query result (product owner ran it directly): the
> reported campaign is sitting at `current_stage = 'planting'`,
> `total_streams = 0`, `is_active = true` — exactly the state
> `get_trending_campaigns`'s old `WHERE` clause excluded. The fix lives
> in Mavins-web (that's where the RPC is defined) — nothing in this
> repo's own code needed to change, matching this diagnosis's own
> earlier finding that `CampaignRepository.kt` was already correct.
> Migration changes the clause from `tc.current_stage NOT IN
> ('planting', 'completed')` to `tc.current_stage != 'completed'` —
> 'planting' campaigns now show, 'completed' ones still don't. **Not
> yet applied to the live DB** — same hand-off as every prior
> migration, no live-DB network path from either sandbox. Full
> write-up in §9's own step 2/3 below. Once applied, the next real
> check is building/running this app on an actual device to confirm
> the campaign renders — still blocked on no Android SDK in this
> sandbox.
>
> **Newest note, same session (2026-08-30, latest of all) — §9's
> step-2 query had a bug (corrected, still needs to be run), and the
> design question it was meant to check is now ANSWERED — changing
> this diagnosis's own conclusion.** `title` doesn't exist on
> `track_campaigns` (it's on the joined `tracks` table) — corrected
> query is in §9. Separately, product owner confirmed directly: new
> campaigns should show immediately, multiple active ones display in
> a shuffled home-page slideshow, and campaigns are queued by genre
> too. **This contradicts `get_trending_campaigns`'s own live filter**
> (`current_stage NOT IN ('planting', 'completed')` — new campaigns
> start at `'planting'`). No longer "working as designed, worth
> confirming" — confirmed to be the opposite of the intended design.
> Still not fixed — documentation only. Full reasoning in §9.
>
> **Newest note, same session (2026-08-30, later than the note below
> it) — §9's leading hypothesis RULED OUT: `get_trending_campaigns`/
> `record_campaign_stream` confirmed live in the database.** Also:
> **live Supabase credentials wired into `local.properties`** this
> session (`SUPABASE_URL`/`SUPABASE_ANON_KEY`, same project Mavins-web
> uses) — closes part of §8's blocker below. App still not built/run —
> no Android SDK in this sandbox.
>
> **Newest note (2026-08-30) — corrects two stale parts of this file
> (§7 and `campaign_schema.sql`'s own description), and documents a
> cross-repo diagnosis for "an admin-published campaign isn't showing
> on Velune."** Neither issue was code-fixed this session —
> documentation only, per explicit instruction. See "§9 — 2026-08-30
> correction + cross-repo diagnosis" near the end of this file for the
> full write-up. Short version: §7 below and `campaign_schema.sql` at
> this repo's root both describe an old, since-removed design (an
> in-app admin screen, a standalone `campaigns` table) — the actual
> current code (`CampaignRepository.kt`) reads live from Mavins-web's
> real `track_campaigns` table via two RPCs instead, and that
> migration was never reflected back into this file until now. The
> "campaign not showing" question itself very likely isn't a bug in
> this repo at all — see §9 for the full disambiguation, tracked in
> full (matching content) as Task 57 in Mavins-web's own `handover.md`.
>
> **Next task in THIS repo/file: no numbered queue here** (different
> convention from the other two repos in this project — established
> intentionally, don't force one on). Work §9's own step 2 (query
> ready, see above), or any other item in **"8. Not done / open"**
> below. Live Supabase credentials are now wired in (see above) —
> the remaining standing blocker for anything requiring an actual
> on-device build/run is **no Android SDK in this sandbox**, not
> credentials anymore.
>
> **Full cross-repo status, as of this note:**
> - **Velune** (this repo, this file) — next: §9 is closed (fix
>   written, needs the migration applied to the live DB, then an
>   on-device build to confirm), or any item in §8 below
> - **Mavins-web** — next: see that repo's own `handover.md` START HERE
>   box directly (this file's own copy of "next task" for that repo
>   goes stale fast — don't trust a hand-copied pointer here over that
>   repo's own live file)
> - **B-Pay-backend** — next: **none currently unblocked** (Korapay-
>   only focus active; `Zapier-codes/B-Pay-backend`, fork of
>   `Phoenix-Boss/B-PAY-backend`) — not independently re-verified this
>   session, carried forward from the previous note
>
> **A session does not need to ask permission before cloning another
> repo or switching context between the three** — if the true next
> task lives elsewhere, just clone it and go.
>
> **This repo also has an unrelated `HANDOVER.md`** (EQ/DSP subsystem,
> same Android app, nothing to do with the Mavins payments/campaign
> project) — don't confuse the two files' scopes.
>
> **Every session must update this box before ending** — whatever you
> just finished, update the pointers above (and the matching box in
> whichever other repo's file needs it) so the next session, in any of
> the three repos, orients in one glance.

---

## Unified hand-off command format — MANDATORY, every session, all three repos

**Kept identical across all three repos' handover files — this file's
copy, Mavins-web's `handover.md`, and B-Pay-backend's own
`handover.md` should all read the same here. If you edit this section,
copy the same edit into the other two in the same session** (same rule
this project already applies to the "Full cross-repo status" box
above).

Whenever a session finishes work — in this repo alone, or this one
plus another — the final message must end with **one single,
copy-pasteable, `&&`-chained command line** covering every repo
touched this session, nothing else. Never separate blocks per repo,
never prose interleaved between repos, never a bare `git am` without
its `git push` right after it:

```
cd ~/<repo-1-local-dir> && git am ~/storage/downloads/<repo-1-slug>-<description>.patch && git push origin main && cd ~/<repo-2-local-dir> && git am ~/storage/downloads/<repo-2-slug>-<description>.patch && git push origin main
```

Extend with more `&& cd ~/<repo> && git am ... && git push ...`
segments for however many repos were actually touched. A single-repo
session still uses this exact shape — just a one-segment chain, not a
different/shorter format.

**Fixed rules:**
1. Patch filenames: always `<repo-slug>-<short-description>.patch`,
   lowercase-hyphenated. Fixed slugs: `mavins-web`, `b-pay-backend`,
   `velune`.
2. `cd` targets use each repo's **real local folder name/casing**,
   which is NOT always the slug or the GitHub name:
   - Mavins-web → `cd ~/mavins-web` (lowercase — GitHub repo is
     capitalized `Zapier-codes/Mavins-web`, the local clone is not)
   - B-Pay-backend → `cd ~/B-PAY-backend` (matches GitHub casing)
   - Velune → `cd ~/Velune` (matches GitHub casing) — this repo pushes
     directly to `main`, no fork/PR step, confirmed by a successful
     `git am` + `git push origin main` run in this project.
3. Every repo segment gets its own `git push origin main` right after
   its own `git am` — never batch every `git am` first and push once
   at the end.
4. All three currently push the same way (`git push origin main`) —
   B-Pay-backend's still auto-joins its open upstream PR on push, no
   extra command; Mavins-web and Velune push straight to `main` with
   no PR step at all. If any repo's push mechanics ever change, update
   this section (in all three files) and that repo's cross-repo status
   note together.
5. Nothing between or after the chain — explanatory prose goes before
   this command block, never interleaved with or appended after it.

See B-Pay-backend's own `handover.md` → "Unified hand-off command
format" for the full original write-up with complete rationale for
each rule — this is the same content, kept in sync.

## Build-focus + mandatory task-splitting — MANDATORY, every session, all three repos

**Added to all three repos' handover files this session (2026-08-30),
kept identical the same way the section above it is — if you edit
this section, copy the same edit into the other two in the same
session.**

**Direct product-owner instruction, two parts:**

1. **All sessions should focus on building the code now, fully** — the
   discovery/diagnosis-heavy phase this project spent a lot of recent
   sessions in (schema queries, cross-repo diagnoses, architecture
   proposals) should give way to actually implementing what's already
   been decided. A task that's still genuinely blocked on a real open
   product question stays blocked — don't force an answer that isn't
   there — but a task sitting on a *resolved* decision with nothing
   left but to write the code is exactly what a session should pick
   next, in preference to opening a new discovery thread.
2. **Every session must split whatever task it picks into parts, and
   build only one of those parts** — never the whole task in one go,
   regardless of how small the task looks at a glance. This formalizes,
   as a standing rule rather than an occasional judgment call, the
   pattern this project has already used successfully several times
   (Mavins-web's Task 33 Part 2's a/b/c/d split, Task 46's a/b/c/d/e
   split, Task 48-b/48-c/48-d's own lettered sub-splits) — each part
   stays independently reviewable, independently revertible, and
   independently patchable, and the natural stopping point after one
   part keeps a single session's diff small enough to actually verify
   properly rather than ballooning into something no one part of which
   got real scrutiny.

**How to split, in practice:** before writing any code, write out the
task's natural parts (even if the task text doesn't already list them —
most won't yet, since this is a new standing rule) as their own labeled
sub-entries in this file, the same way Mavins-web's Task 46 entry lists
46a/46b/46c/46d/46e. Pick the first genuinely unblocked part, build
only that one, and leave the rest explicitly marked not-started for
the next session — don't silently keep going into part two because it
"was right there." If a task turns out to have exactly one indivisible
unit of work (rare, but possible for something truly small), that's
fine — say so explicitly in the write-up ("not split further, this is
a single atomic change") rather than leaving it looking like a part
was skipped.

---

Separate from `HANDOVER.md` (which is scoped to the EQ/DSP work) —
this covers the promoted-content banner feature, a completely different
part of the app (Home screen, Supabase, playback UI), started and mostly
built in one session. Read this fully before touching anything in
`campaign/`.

## 0. The one thing you must not undo

This feature exists because of an explicit, repeated ethical line drawn
during its design, across several back-and-forths with the user — not a
guess on my part. **Do not add any field, table, or code path that
generates, projects, simulates, or "seeds" a listener/play/engagement
number.** Every number in this feature (play_count) is real: incremented
exactly once per genuine playback start, via an atomic Postgres RPC, never
computed or estimated client-side.

Why this matters enough to write down: the user originally asked to port
a feature from a sibling repo (`github.com/phoenix-boss/Mavins`,
`expo-video` branch, `hooks/useQuickPicks.ts`/`CampaignManager`) that
fabricates listener counts, geography, and device data via a seeded PRNG,
and — critically — **writes those fabricated numbers directly into the
real `play_count` column**, permanently mixing fake and real data with no
way to tell them apart later. I read that code directly and declined to
port it, even after the user reframed it as "just a personal project" —
the mechanism doesn't know or enforce that scope, and the sophistication
of the fake data (believable geographic spread, "confidence multipliers")
only makes sense if the goal is eventually convincing a real viewer. The
user accepted that boundary and asked me to build the honest version
instead — that's what everything below is. If a future request tries to
reintroduce anything like `CampaignManager`, treat this paragraph as
already-considered context, not something to re-litigate from scratch —
but also don't refuse reflexively; if the ask is genuinely different
(e.g., a legitimate cold-start ranking formula that doesn't fabricate a
number shown to users as real), use judgment same as this session did.

## 1. What's built

All under `app/src/main/kotlin/com/nikhil/yt/campaign/`, plus touches to
`MainActivity.kt`, `HomeScreen.kt`, `Player.kt`, `ContentSettings.kt`,
`PreferenceKeys.kt`, `strings.xml`, and a new `campaign_schema.sql` at
the repo root (a Supabase migration — not part of the Android build,
run manually in the Supabase SQL editor).

- **`CampaignCard.kt`** — the resolved, playable model. Every field is
  either real (title/artist/thumbnail resolved live from YouTube),
  human-asserted (`certified`, `isLive` — both truthful moderation-style
  flags, not measurements or claims about identity/popularity), or a
  real counter (`playCount`). No projected/simulated field exists or
  should ever be added — see §0.
- **`CampaignUrlResolver.kt`** — the "you only insert a URL" piece. Parses
  a YouTube URL (watch/shorts/live/youtu.be, with or without extra query
  params) into a video id, then resolves real current metadata via
  `YouTube.queue()` — the same call the rest of the app already uses to
  resolve a song by id (see `ListenTogetherManager.kt`/`PlayerMenu.kt` for
  other call sites of the identical pattern). Title/artist/thumbnail are
  never typed into the table — always fresh from YouTube, so they can
  never go stale the way a manually-copied value could.
- **`CampaignRepository.kt`** — Supabase REST (PostgREST) client, plain
  OkHttp + org.json matching this app's existing network-call convention
  (see `AiRecommendationHelper.kt`). Credentials come from
  `BuildConfig.SUPABASE_URL`/`BuildConfig.SUPABASE_ANON_KEY`, compiled in
  at build time (see §5 — this changed from an earlier runtime-Settings
  design in the same session, at the user's explicit correction).
  `fetchActiveCampaigns()` fetches raw rows and resolves each via
  `CampaignUrlResolver`; `recordPlay()` calls the atomic
  `increment_campaign_play` RPC. Both no-op gracefully (return empty / do
  nothing) if Supabase isn't configured yet or any call fails — a broken
  promo fetch must never block the rest of Home, and a failed play-count
  increment must never interrupt actual playback.
- **`CampaignPlaybackTracker.kt`** — small app-scoped singleton
  (`StateFlow<CampaignCard?>`), not DI-wired, deliberately: the setter
  (`HomeScreen`'s campaign tap handler) and the reader (`Player.kt`'s
  badge) live in genuinely separate parts of the composition with no
  natural shared ViewModel. Self-clearing: `Player.kt` calls
  `clearIfNot(currentlyPlayingId)` on every media-item change, so the
  "Promoted" badge can never stick to a track the campaign didn't
  actually point at (user skips, queue advances, etc.).
- **`CampaignCardSection.kt`** — the Home screen UI. A 300×72dp rectangle
  banner (small, per explicit direction — not a tall feature card),
  horizontally swipeable if more than one campaign is live. Shows the
  ported `certified` badge (real asset from Mavins, see §2) only when
  true, a tiered play-count display (real count once ≥10 plays, a plain
  "New" pill below that — see the file's doc comment for why), and a
  pulsing red "LIVE" pill (UI animation only — the underlying claim is
  `CampaignCard.isLive`, a human-set fact, not something inferred from
  the animation). Renders nothing — no placeholder/skeleton — when there
  are no live campaigns.
- **Home screen wiring** (`HomeScreen.kt`) — `CampaignCardSection` is the
  very first item in the LazyColumn, above the category chips and
  everything else. Tap handler: resolves + plays the song via the same
  `YouTube.queue()` → `playerConnection.playQueue()` path everything else
  in this app uses, sets `CampaignPlaybackTracker`, expands straight to
  the full player (`playerBottomSheetState.expandSoft()` — unlike every
  other Home row, a campaign tap is meant to feel like an event), then
  fires `recordPlay()` in the background without awaiting it.
- **Player screen badge** (`Player.kt`, inside `MetroPlayerContent`) —
  shows the certified badge + "Promoted" label above the title, only
  when `CampaignPlaybackTracker.current`'s tracked campaign's `songId`
  still matches what's actually playing.
- **New `LocalPlayerBottomSheetState` CompositionLocal** (`MainActivity.kt`)
  — didn't exist before this session; the player bottom sheet state was
  previously just a local `remember` inside `MainActivity`'s composable
  body, not exposed to descendants. Added following the exact same
  pattern as the existing `LocalPlayerConnection`/`LocalBottomSheetPageState`
  in the same file, so `HomeScreen` (and any future screen) can expand/
  collapse the player without new parameter-threading through
  `NavigationBuilder.kt`.
- **Build-time config** (`app/build.gradle.kts`, `.github/workflows/build-tenant.yml`)
  — `SUPABASE_URL`/`SUPABASE_ANON_KEY` are `buildConfigField`s, sourced
  `local.properties` first (local dev) then `System.getenv(...)` (CI),
  exactly the same priority order and pattern already used for
  `LASTFM_API_KEY`/`ZAI_API_KEY`/`TOGETHER_BEARER_TOKEN`/
  `DISCORD_APPLICATION_ID` in the same file. Wired into the "Build APK
  with custom package name" step's `env:` block from
  `secrets.SUPABASE_URL`/`secrets.SUPABASE_ANON_KEY`, matching the
  `KEYSTORE_*` env-var pattern already used one step later for signing.
  **This is deliberately not a per-user Settings field** — see §5 for
  why, and don't reintroduce one without re-reading that section first.
- **`campaign_schema.sql`** (repo root, not part of the app build) — the
  actual Supabase migration. `campaigns` table: `source_url`,
  `resolved_song_id` (cached extraction, re-derivable), `is_live`,
  `certified`, `active` (manual kill switch), `start_date`/`end_date`
  (the visibility window — `end_date > start_date` enforced by a CHECK
  constraint), `cta_label`, `play_count`, `created_at`. Visibility is
  enforced **twice**: once in the RLS SELECT policy itself (so an
  expired/future/paused campaign literally cannot be returned to any
  client no matter what the app's query does), and again in the app's
  own query as defense in depth. The `increment_campaign_play` RPC
  (SECURITY DEFINER, `grant execute ... to anon` — anon gets zero direct
  table grants) also re-checks the date window before moving the counter,
  so a stale cached campaign id can't record a play against an expired
  campaign either.

## 2. Real assets ported from Mavins (not code, just art)

`assets/images/badge.png`/`badge2.png` from
`github.com/phoenix-boss/Mavins` (`expo-video` branch) — the actual
certified-badge artwork, legitimately reused since the user owns both
repos. Downscaled from 1024px/~1.9MB each (React Native doesn't care,
Android very much does) to 192px/~55-59KB each via `convert -resize`,
copied into `app/src/main/res/drawable-nodpi/campaign_badge_light.png`
and `campaign_badge_dark.png`. Nothing from `CampaignManager` or any
count/projection logic was ported — see §0.

## 3. Deliberate scope-down: no Radio/Podcast/Show types

The user's original ask described a live badge applying to "radios
podcasts shows etc but not normal music and video." **Velune has none of
those content types** — it's a YouTube Music client, songs and videos
only, confirmed by grepping the whole codebase (the one "podcast" hit
that exists is code that *filters podcasts out* of the home feed, not a
podcast player). Building a `content_type: radio/podcast/show` schema and
resolver for capabilities that don't exist would have been dead
scaffolding implying support that isn't there.

Instead: `is_live` is scoped to the one real live-capable thing this app
has — a YouTube video that's a genuine livestream — using the same
`Timeline.Window.isLive` concept the player already relies on elsewhere
(`PlayerConnection.kt`, for skip-button logic). It's a human-set truthful
flag on the campaign row (same honesty bar as `certified`), not
auto-detected — there's no pre-playback "is this live" signal surfaced
anywhere in this app's YouTube data layer currently (checked; not
present), so auto-detection would need real new work in the innertube
layer, not something to fake here. If the user ever wants true auto-
detection, or wants Radio/Podcast as real features, both are legitimate,
separate, larger projects — flag that plainly rather than quietly
building toward it.

## 5. Correction: build-time config, not per-user Settings

The first version of this feature (still visible in patch `0020`'s diff,
if you're reading history rather than current `main`) put the Supabase
URL/anon key in a Settings screen, following the same pattern as the
per-user AI API keys elsewhere in this app (`OpenRouterApiKey` etc.). The
user corrected this immediately: campaigns are a single, app-owned
backend every install talks to, not a credential each user brings their
own copy of — it belongs baked in via CI secrets like `KEYSTORE_BASE64`
already is for signing, not typed into a screen per device.

That correction was right, and it's also more correct Supabase practice,
not just a preference: a Supabase **anon** key is specifically designed
to be embedded in client apps (row level security is what actually gates
access — see `campaign_schema.sql` — not secrecy of this key). A later
patch reverted the Settings UI entirely and moved config to
`BuildConfig.SUPABASE_URL`/`BuildConfig.SUPABASE_ANON_KEY`, sourced the
same `local.properties`-then-CI-env-var way every other secret in
`app/build.gradle.kts` already is. If a future session is tempted to add
a runtime settings field for this again, re-read this paragraph first —
it's not that a settings field is impossible, it's that it's the wrong
model for a backend the app owner controls centrally.

## 7. Admin flow (this session): create/edit/pause/delete from the app

Previously the only way to manage campaigns was the Supabase dashboard
directly. Added:

- **`CampaignAdminRepository.kt`** — deliberately a **separate class**
  from `CampaignRepository.kt`, not an extension of it, so that class's
  doc can keep truthfully saying "anon gets zero direct write grants."
  Signs in via real Supabase Auth (`/auth/v1/token?grant_type=password`),
  stores the access/refresh token pair via DataStore (same "not committed
  to source, not OS-keystore-grade encrypted" caveat as every other
  credential this app stores client-side), and transparently refreshes
  the access token a minute before it expires. Full create/update/delete/
  pause on the `campaigns` table, all using the signed-in session's JWT.

  **Why not just embed the service-role key and skip all this?** The
  service-role key bypasses Row Level Security entirely — baking it into
  the app would mean anyone who decompiles the APK gets unrestricted
  read/write access to the *whole* database, not just campaigns. That's
  a real vulnerability, not a theoretical one, so it was never on the
  table. Supabase Auth + RLS scoped to the `authenticated` role is the
  actual right way to do this.

- **`campaign_schema.sql`** — four new RLS policies (`for select/insert/
  update/delete ... to authenticated`), stated plainly in the file's own
  comment: `auth.role() = 'authenticated'` grants full management to
  *any* signed-in user, which is the right tradeoff for a single-admin
  project but stops being right the moment a second real account exists
  that shouldn't have this access — at that point, tighten to a specific
  `auth.uid()` check instead. No SQL creates a login; that's done once
  from the Supabase dashboard (Authentication → Users), not from this
  file — a credential doesn't belong in a schema migration.

- **`CampaignAdminScreen.kt`** — sign-in form → campaign list (every row,
  every status: LIVE/SCHEDULED/ENDED/PAUSED, computed fresh from
  `active`+the date window, never stored) → a bottom-sheet create/edit
  form with real `DatePickerDialog`s for start/end. Pause/resume and
  delete are one-tap from the list; full edit opens the same sheet the
  create flow uses, prefilled.

- **Settings entry point** (`ContentSettings.kt`) — a "Manage Campaigns"
  item under a restored "Promoted Content" group (the group patch `0021`
  removed when the per-user Supabase key fields moved to build-time
  config — this is a different kind of entry, an app-nav link, not a
  credential field, so bringing the group back for it is correct, not a
  reversion of that patch's actual point). Discoverable the same way any
  other settings item is; the real access control is the sign-in wall,
  not obscurity.

**Known simplification**: the create/edit form doesn't expose
`cta_label` — it's always submitted as `"Play"` from the app. The column
still exists and defaults sensibly; edit it directly in Supabase if a
campaign needs different CTA text. Not fixed here for scope reasons, not
because it's hard.

## 8. Not done / open

- **Committed and pushed** — confirmed on real `main` as of this note
  (`d3151cd`/`4ea693a`). If you're reading this from a fresh clone, the
  campaign feature and this file are already there; ignore any earlier
  version of this bullet that said otherwise.
- **Real Supabase project wired in (2026-08-30)** — `SUPABASE_URL`/
  `SUPABASE_ANON_KEY` were added to `local.properties` this session,
  same project Mavins-web uses (`atojskxrxfsbpeefigtm`). Reported done
  by the person running the command, not independently verified from
  this sandbox — `local.properties` is correctly gitignored (per §5's
  own reasoning) and lives only on the device that ran it, so there's
  nothing in this repo's own git history to check it against. If
  `BuildConfig.SUPABASE_URL`/`SUPABASE_ANON_KEY` still build empty on a
  real build, re-check that file's contents directly rather than
  assuming this note alone guarantees it. At least one campaign row
  still needs to exist and be inside its date window for anything to
  show on Home (see §5 and §9's own open diagnosis for what that
  actually requires right now).
- **Not tested on-device**, same caveat as every UI/integration patch in
  this project's history — reviewed by hand very carefully (every
  extension function, every class field, every composable signature used
  was checked against its real declaration in this codebase, not
  assumed), but never run. Specifically unconfirmed: whether
  `expandSoft()` behaves correctly when called from `HomeScreen` via the
  new CompositionLocal versus its original `MainActivity`-local usage;
  whether the live-badge pulse animation and tiered play-count read well
  at actual banner size; whether a truly empty/misconfigured Supabase
  setup fails as silently as intended (no crash, no visible broken
  state) end to end.
- **No admin/creation UI in the app itself** — by design (see the
  schema's RLS comment): creating a campaign row, setting `certified`/
  `is_live`, is a manual action taken directly in Supabase (dashboard or
  service-role key), not something the app can do with its anon key.
  Worth asking the user whether that's the long-term intent or whether a
  future patch should add an authenticated admin flow.
- **`resolved_song_id` caching isn't backfilled automatically** — a
  campaign row can be created with just `source_url`, and the resolver
  falls back to re-parsing the URL every fetch if that column is empty.
  Fine for the current scale; if this ever needs to avoid repeated
  parsing at volume, something should write the extracted id back once
  resolved (would need a broader anon grant or a server-side trigger —
  not attempted here, matches this feature's "anon gets minimal
  privileges" posture deliberately).

## 9. 2026-08-30 correction + cross-repo diagnosis: "admin-published campaign not showing" — CLOSED, fix written in Mavins-web, not yet applied to live DB

**Trigger:** the product owner reported an admin-published campaign in
Mavins-web wasn't appearing on Velune's Home screen. This section
documents what was found — **nothing below was code-fixed this
session, documentation only, per explicit instruction.** The matching
write-up, same content, lives in Mavins-web's own `handover.md` as
Task 57 — read whichever file you found first, they're kept in sync.

### Correction to §7 and `campaign_schema.sql` above — both describe a superseded design

§7 ("Admin flow: create/edit/pause/delete from the app") and the
`campaign_schema.sql` file at this repo's root both describe this
feature's **v1/v2 design**: a Velune-only `campaigns` table, managed
via an in-app admin screen (`CampaignAdminScreen.kt`/
`CampaignAdminRepository.kt`). That design is no longer what's running:

- `134cb37` ("remove Manage Campaigns entirely") deleted
  `CampaignAdminScreen.kt`/`CampaignAdminRepository.kt` outright,
  removed the Settings entry point, and removed the nav route — this
  app no longer has any in-app campaign management UI. That commit's
  own message states the reasoning plainly: "Campaign management
  belongs in a separate app entirely; this app should only display the
  placed campaign, read from Supabase."
- A later sequence of commits (`fa7d377`, `6dcd1b4`, `444de3f`,
  `28db525`) migrated `CampaignRepository.kt` off the standalone
  `campaigns` table entirely. **It now reads from Mavins-web's own
  `track_campaigns` table**, via two RPCs — `get_trending_campaigns`
  (listing) and `record_campaign_stream` (play recording, replacing
  the old `increment_campaign_play`) — whose response fields match
  `track_campaigns`'s real schema directly (`total_streams`,
  `trending_score`, `geographic_tier`, `current_stage`, with the exact
  same stage-name strings — `planting`/`germination`/`root_system`/
  `branching`/`full_bloom` — Mavins-web's own seed engine uses).
  Confirmed: both RPCs are fully defined in Mavins-web's
  `supabase_schema.sql`, explicitly commented as being for this app
  ("Velune Home screen + Mavins discovery" / "Velune calls this on
  every play").

Neither change was ever reflected back into this file until now. If
you're reading §7 or `campaign_schema.sql` above expecting them to
describe the current code, they don't — this section is the correction.
`campaign_schema.sql` itself has been left as-is (not deleted) since
it's still accurate *history* of the v1/v2 design, same reasoning this
file already applies elsewhere to superseded content — just don't read
it as the current schema `CampaignRepository.kt` actually talks to.

### The actual diagnosis: very likely not a bug in this repo at all

Read `CampaignRepository.kt` end to end this session — its query
construction, header/auth handling, and response parsing
(`parseTrendingRows`) all correctly match the RPC contract
Mavins-web's `supabase_schema.sql` defines. No bug found in this
repo's own code.

**The most likely actual cause: RULED OUT (2026-08-30) — the RPCs
ARE live.** The product owner ran a direct check against the database
(not a re-derivation from app behavior):
```sql
select proname, pg_get_function_identity_arguments(oid) as args
from pg_proc
where proname in ('get_trending_campaigns', 'record_campaign_stream');
```
Both came back present with the expected signatures
(`get_trending_campaigns(p_limit integer, p_country_code text, p_genre
text)`, `record_campaign_stream(p_campaign_id uuid, p_user_id uuid,
p_listen_duration_seconds integer, p_country_code text,
p_is_full_listen boolean)`). **This app is not calling a function that
doesn't exist — that specific failure mode is ruled out. Move to the
next check below, don't re-run this one.**

**Live Supabase credentials for this app's own build were also wired
in this session** — `local.properties` now has real
`SUPABASE_URL`/`SUPABASE_ANON_KEY` values (same project Mavins-web
uses, confirmed shared per this section's own earlier finding) —
closing part of §8's "No real Supabase project wired in" blocker
below. The app itself was **not built or run this session** — no
Android SDK in this sandbox, same limitation as every other on-device
task in this file's history (see §8). Building/installing on a real
device to see the actual UI is a still-separate, not-yet-taken step,
independent of whichever database finding comes next.

**Two more real, secondary factors when this was first written — the
first is now confirmed to be the actual likely root cause, see "What
would resolve this" step 3 below:**
1. `get_trending_campaigns`'s own filter
   (`current_stage NOT IN ('planting', 'completed')`) means a
   brand-new campaign is deliberately excluded until it reaches
   ≥10,000 total streams (the `germination` threshold, per Mavins-
   web's own `record_campaign_stream`). **Originally flagged here as
   "reads like intentional design, worth confirming" — now confirmed
   to be the OPPOSITE of the intended design, see step 3 below.**
2. Mavins-web's seed engine (what grows `total_streams` toward that
   threshold) has a header comment claiming it runs every 15 minutes;
   its actual deployed cron only fires once a day (`vercel.json`). Real
   discrepancy, confirmed by reading both files in that repo directly —
   a slow-down, not an outage.

**What would resolve this, in order** (same list as Mavins-web's own
Task 57, repeated here for a reader who only has this file open):
1. **DONE (2026-08-30) — RPCs confirmed live, see above. Ruled out.**
2. **DONE (2026-08-30) — corrected query run, result confirms the
   reported campaign is sitting exactly where step 3 predicted.**
   Result:
   | id                                   | title | current_stage | total_streams | is_active | is_paused | target_genres   | created_at                    |
   | ------------------------------------- | ----- | -------------- | -------------- | --------- | --------- | ---------------- | ------------------------------ |
   | ff616798-ee70-4488-a37f-a61abd743b92 | null  | planting       | 0              | true      | false     | ["Afrobeats"]     | 2026-08-29 12:21:10.726697+00 |

   `current_stage = 'planting'`, `total_streams = 0`, `is_active =
   true` — a real, live, active campaign, sitting exactly at the stage
   `get_trending_campaigns`'s `WHERE` clause excluded. Combined with
   step 1 (RPCs confirmed live) and step 3's answered design question,
   this is the confirmed root cause, not a suspected one.
3. **CONFIRMED root cause (2026-08-30) — fixed in Mavins-web this
   session, migration written, not yet applied to the live DB.** The
   "should a brand-new campaign show immediately" question is
   answered: yes, multiple active campaigns display in a shuffled
   home-page slideshow, and campaigns are queued by genre too (matches
   `target_genres`, already a parameter on `get_trending_campaigns`).
   Step 2's result above confirms the reported campaign hit exactly
   this. **Fix:** Mavins-web's
   `supabase_migration_020_trending_campaigns_show_planting.sql` —
   `get_trending_campaigns`'s `WHERE` clause changes from
   `tc.current_stage NOT IN ('planting', 'completed')` to
   `tc.current_stage != 'completed'`. Nothing in **this** repo needed
   to change — `CampaignRepository.kt` was already confirmed correct
   earlier in this section, the bug was entirely on the database side.
   The `trending_score` formula is deliberately untouched by that
   migration — a 'planting' campaign is now included but still scores
   low (same `CASE ... ELSE 10` weighting as before), so it may sort
   near the bottom of a strictly-ordered result. Whether this app's
   Home screen renders strictly by `trending_score` or shuffles the
   returned set (the product owner's own word) wasn't diagnosed as
   broken, so it wasn't changed — worth checking once this app is
   actually built and run against the applied migration. **Not yet
   applied to the live DB** — same hand-off as every prior migration,
   neither sandbox has a live-DB network path.
4. Lower priority: reconcile the seed engine's stated vs. actual cron
   cadence.

Beyond the two read-only confirmation queries, the only write this
session was a schema/function migration file in Mavins-web
(`supabase_migration_020_trending_campaigns_show_planting.sql`, see
step 3) — not a live-DB change. No cron config changed, no application
code edited in either repo.

## 10. 2026-08-30 — Task 59 Part 2 (campaign queue-slot RPC wiring) traced end-to-end in this repo's own code, NOT implemented — canonical write-up lives in Mavins-web

**Sync note only — full detail lives in Mavins-web's `handover.md`,
Task 59's own "Round 5" entry.** Kept concise here rather than
duplicated in full, per this project's own cross-repo convention (see
§9 above for the same pattern) — copy drift between two full copies of
the same finding is worse than one canonical copy plus a pointer.

**What was found, reading this repo's own code directly:** wiring
genre-aware campaign injection through to Mavins-web's new
`get_next_campaign_for_queue_slot` RPC (migration 023, that repo)
touches 8 files in **this** repo — `MoodAndGenresScreen.kt` →
`NavigationBuilder.kt` → `YouTubeBrowseViewModel`/`YouTubeBrowseScreen.kt`
→ `PlayerConnection.kt` → `MusicService.kt` →
`CampaignInjectedQueue.kt` → `CampaignRepository.kt` — and surfaces a
real architecture mismatch, not just missing plumbing:
`CampaignInjectedQueue.kt` currently pre-fetches a batch of campaigns
once per queue and rotates them locally (`campaignOrder =
...indices.shuffled()`, `getInitialStatus()`), but the new RPC is
designed for one atomic pick-and-mark call **per slot** — naively
calling it in a batch upfront would mark campaigns as "just served"
before they're actually played, corrupting the platform-wide fairness
rotation for other listeners' queues being built concurrently, not
just missing the intended behavior. Also flagged: `MoodAndGenresScreen`
mixes true genre tiles with mood tiles (e.g. "Chill," "Feel Good") that
aren't genres at all — the only per-tile label available
(`it.title`) hasn't been confirmed to match `track_campaigns.target_genres`'s
stored values. This is a real open question, but confirmed **not** a
safety risk either way (a mismatched string just yields zero eligible
campaigns via the RPC's own filter — the same fail-closed outcome as
passing no genre, never an incorrect cross-genre match) — only a
possible under-delivery of campaign impressions until resolved.

**Not implemented — same reason every Velune Kotlin task in this
project has stayed documentation-only so far:** no Android SDK or
Google Maven access exists in either sandbox this project has used, so
there's no way to compile-verify a change this size before handing it
over. Mavins-web's Task 59 "Round 5" entry has the full file/line
citations for every hop in the chain and a two-part build plan (2a:
`CampaignRepository.kt` + `CampaignInjectedQueue.kt` refactor, safe to
reason about in isolation; 2b: the wider 6-file nav/UI threading pass)
— read that entry in full before starting Part 2 in this repo.

## 11. 2026-08-31 — Part 2a built in this repo: `CampaignRepository.kt` + `CampaignInjectedQueue.kt`, canonical write-up in Mavins-web

**Sync note only — same pattern §10 above already used.** Full detail
(the per-slot refactor, the index-tracking bug found and fixed with 4
verified simulation cases, and two more real pre-existing bugs found
and flagged but NOT fixed — one in `MusicService.kt`'s initial-batch
population, one in `CampaignRepository.kt`'s existing
`fetchActiveCampaigns()`) lives in Mavins-web's `handover.md`, Task 59
"Round 7." Read that entry before starting Part 2b or touching either
flagged bug in this repo. **Not compile-verified here either** — same
structural limitation as everything else in this section.

## 11. Task 59 Part 2b-b, first job done (2026-09-01) — `MAVINS_API_URL` + `ingestGenreTile()`

**MAVINS_API_URL confirmed directly by the product owner this session:
`https://mavins.vercel.app`** — no custom domain, matches Mavins-web's
own `package.json` project name (`"mavins"`), no name collision. Part A
(§10 below / `e82466d`) explicitly declined to guess this value; this
session had a real confirmation to build on instead.

Built, this session, commit `4652493`:
1. `app/build.gradle.kts` — `MAVINS_API_URL` BuildConfig field, same
   `localProperties → env → default` fallback pattern every other host
   value in this file already uses.
2. `CampaignRepository.kt` — `ingestGenreTile(tileTitle)`, a
   fire-and-forget POST to Mavins-web's own already-live
   `/api/campaigns/genre-tile-mapping/ingest` route. Verified the
   request shape against that route's own body-parsing/validation
   logic directly (read the route file, then a throwaway Python
   simulation of the exact JSON payload against it), not just assumed
   from memory of what such a route probably expects.

**Found, NOT fixed — a real, pre-existing bug across this entire
file, worth a dedicated later pass:** every HTTP-status warning log
line in `CampaignRepository.kt` (six instances — `fetchActiveCampaigns`,
`fetchNextCampaignForQueueSlot`, `fetchGenreTileMapping`,
`ingestGenreTile` before this session's own fix,
`recordCampaignStream`, and one `songId` log) writes
`${'$'}{response.code}` (or `${'$'}{campaign.songId}`) — Kotlin's
literal-dollar-sign escape, which only means something inside a KDoc
comment. Inside a real string literal (which all six of these are),
it produces the literal text `${response.code}` in the log instead of
the actual value. Caught this only because copying this file's own
established style for consistency would have carried the exact same
bug into this session's one new log line too — fixed that one line,
left the five pre-existing instances alone (out of this part's own
scope; a drive-by fix across five unrelated call sites isn't this
part's job) and flagged here instead. Purely cosmetic (broken debug
logging, not app-breaking) but worth a dedicated single-part cleanup
later — a good, small, well-scoped "next part" for a future session
with nothing else on file at the moment.

**Deliberately not touched this session** — the 6-file UI/nav
genre-threading chain (`MoodAndGenresScreen` → `NavigationBuilder` →
`YouTubeBrowseViewModel`/`Screen` → `PlayerConnection.kt` →
`MusicService`), per this project's own mandatory one-part-per-session
task-splitting rule. That remains open — see Mavins-web's own
`handover.md`, Task 59, for the next session's actual next step there.

**Not compile-verified — no Android SDK/Gradle in this sandbox**, same
structural limitation every prior Velune code change in this project
has flagged. Verified via a brace/paren balance check on both changed
files and the payload-simulation described above.

## 12. Task 59 Part 2b-b, Round 11 (2026-09-01) — genre-tile title now reaches the ViewModel, canonical write-up in Mavins-web

**Kept concise here rather than duplicated — Mavins-web's own
`handover.md`, Task 59's own "Round 11" entry, has the full write-up.**
Three files changed this round: `MoodAndGenresScreen.kt` (sends the
tapped tile's title as a new, URL-encoded `genreTile` query arg),
`NavigationBuilder.kt` (new nullable `genreTile` nav argument on the
shared `youtube_browse` route — confirmed via grep that route has
exactly 3 callers, only this one has a genre signal to send, so the
other two need zero changes), `YouTubeBrowseViewModel.kt` (reads and
`URLDecoder`-decodes the new value into a new `genreTileTitle: String?`
field, not consumed by anything yet). Verified via brace/paren balance
check on all three files plus a throwaway Python simulation of the
encode/decode round trip for 4 real titles (including one with `&`) —
not compile-verified, no Android SDK in this sandbox.

**Still open, unchanged from §11:** actually consuming
`genreTileTitle` (a real `campaignSlotProvider` calling Part A's
`fetchGenreTileMapping()`, threaded into `PlayerConnection.kt`/
`MusicService.kt`), the pre-existing log-escaping bug, and every other
item §11 already flagged.

## §12 — genre reaches a real `Queue` object (2026-09-01)

**Sync note only — canonical write-up in Mavins-web's `handover.md`,
Task 59's own "Round 12" section, same pattern §10/§11 above already
used.** Full detail there; summary here: `Queue.kt` gets a new
`genre: String? get() = null` default property (Round 5's original
architecture recommendation, built here for the first time);
`YouTubeQueue`'s constructor + `radio()` factory both grow a matching
optional `genre` param (confirmed via grep: all 13 existing call sites
across the app pass exactly one positional arg, unaffected, correctly
defaulting to `null`); `YouTubeBrowseScreen.kt`'s one already-traced
flat-song-list call site now passes `viewModel.genreTileTitle`
through. **Deliberately still only that one call site** — the grid/
album/playlist play-path flagged untraced back in §9/Mavins-web's own
Round 2 is still not covered. Verified via brace/paren balance check
on all three changed files, no Android SDK in this sandbox, same
standing limitation.

**Still open, narrower now:** `MusicService.kt`'s actual
`campaignSlotProvider` construction (read `queue.genre`, cached
`fetchGenreTileMapping()` lookup, `ingestGenreTile()` on a cache miss,
`fetchNextCampaignForQueueSlot()` for a confirmed mapping) is now the
single remaining piece of this whole chain — plus the untraced
grid/album/playlist path, the pre-existing log-escaping bug, and
`MusicService.kt`'s own separate initial-batch bug, all still
outstanding from §11.

## §13 — cache-lifecycle half built; `MusicService.kt` wiring still open (2026-09-01)

**Sync note only — canonical write-up in Mavins-web's `handover.md`,
Task 59's own "Round 13" section, same pattern §10/§11/§12 above
already used.** Full detail there; summary here: new
`GenreTileMappingCache.kt` (`com.nikhil.yt.campaign` package) — a
singleton cache wrapping `CampaignRepository.fetchGenreTileMapping()`
with periodic (15-minute) refresh, exposing one entry point,
`resolveGenreId(tileTitle)`, implementing the three-way behavior
already specified in §12's own "still open" note: a real mapping
resolves to that genre id; an explicitly-reviewed non-genre tile
resolves to `null` with no ingest call; an unknown/unreviewed tile
fires `ingestGenreTile()` and resolves to `null` for that call.

**A real correctness bug caught and fixed before it shipped:**
staleness is judged by a separate `lastFetchedAtMs` timestamp, NOT by
whether the cached map is empty — the live `campaign_genre_tile_mapping`
table is expected to start (and may remain, for a while) genuinely
empty, and judging staleness by emptiness would have meant that exact
expected state causes a full refetch on every single call, forever,
defeating the cache entirely for the real launch condition it's meant
to handle.

Verified via a 12-scenario Python simulation (all passed) — no Android
SDK in this sandbox, same standing limitation as every prior round.

**Still open, narrower now:** `MusicService.kt`'s actual
`campaignSlotProvider` construction — replace `{ null }` with a lambda
reading `queue.genre`, calling `GenreTileMappingCache.resolveGenreId()`,
then `CampaignRepository().fetchNextCampaignForQueueSlot()` for a
resolved genre id. Single call site, everything it needs now built.
Plus the untraced grid/album/playlist path, the pre-existing
log-escaping bug, and `MusicService.kt`'s own separate initial-batch
bug, all still outstanding from §11/§12.

## 14. Task 59 Part 2b-b, Round 14 (2026-09-01) — `MusicService.kt` wiring done, canonical write-up in Mavins-web

**Kept concise here — Mavins-web's `handover.md`, Task 59's own
"Round 14" entry, has the full write-up.** §13's own "still open" item
is now closed: `playQueue()`'s `campaignSlotProvider = { null }`
placeholder is a real lambda — `queue.genre` → `GenreTileMappingCache.resolveGenreId()`
→ `CampaignRepository().fetchNextCampaignForQueueSlot()`, everything
already built across §11-§13. `null` genre short-circuits immediately
(same fail-closed default `CampaignInjectedQueue` already had, now
explicit); an unresolved id still injects nothing for that slot.

Verified via a 5-scenario Python simulation of the branching logic —
all 5 passed. Not compile-verified, no Android SDK in this sandbox.

**Task 59's core genre-locked queue-injection mechanic is now fully
wired end to end, tap to injection.** Still open, unchanged from §13:
the grid/album/playlist play-path gap, `MusicService.kt`'s own
separate initial-batch bug, the pre-existing log-escaping bug, and
confirming the mapping table is actually seeded with real tile
titles.

## 15. 2026-09-02 — Task 60 Part A: double-recording bug fixed in this repo

**Sync note only — canonical write-up in Mavins-web's `handover.md`,
Task 60's own "Split into Part A / Part B" + "Part A — done" entries.**
Every genuine campaign play was being written to the database twice
(`CampaignCardSection.kt`'s and `HomeScreen.kt`'s own immediate
`recordPlay()` calls, both firing from the same tap, on top of
`MusicService.kt`'s own correct, playback-transition-gated call), plus
a third call site (`HomeScreen.kt`'s, via the hardcoded
`userId = "anonymous"`) failed outright on every single attempt — a
Postgres type mismatch (`"anonymous"` can't cast to the RPC's
`p_user_id uuid` column) silently swallowed into a log line.

Fixed: removed both duplicate/broken call sites
(`CampaignCardSection.kt`, `HomeScreen.kt`), added the missing
`countryCode` argument to the one surviving correct call
(`MusicService.kt`). `recordPlay()` itself (the now fully-orphaned
wrapper both removed sites used) was left in place rather than
deleted — flagged as a cleanup candidate, its doc comment corrected to
stop claiming callers it no longer has.

Net result: one write per real play, not two; zero silent failures;
real device-id and country attribution on every write. **Not
compile-verified** — same structural limitation as every prior
Velune task in this project.

## 16. 2026-09-02 — Task 59 Part 3b split into 3b-a/3b-b; 3b-a built (carousel state/timer/lifecycle engine)

**Sync note only — canonical write-up in Mavins-web's `handover.md`,
Task 59's own Round 4 entry (Part 3b's own subsection).** Per the
mandatory build-focus/task-splitting rule, split Part 3b (the banner's
UI rebuild — single card, 30s auto-advance, reshuffle on
background-then-resume) into 3b-a (this session: the state/timer/
lifecycle engine, decoupled from rendering) and 3b-b (not started: the
actual `CampaignCardSection.kt` rebuild consuming it, replacing the
current `LazyRow`).

New `campaign/CampaignCarouselState.kt` — `rememberCampaignCarouselState(campaigns)`
composable, exposing `current: CampaignCard?` only (never the
underlying list or its size, so a 3b-b consumer can't accidentally
leak the true live-campaign count even by mistake). Owns a
30-second auto-advance `LaunchedEffect` loop and a
`LifecycleEventObserver` (same `DisposableEffect`/
`LocalLifecycleOwner` pattern already used in `LibraryScreen.kt`,
mirrored exactly) that reshuffles specifically on `ON_RESUME`
following a real prior `ON_STOP` — NOT on the initial `ON_RESUME`
Compose fires when the screen first appears, which is not "returning
from background."

**Verified with a throwaway Python simulation of the state machine**
(written, run, discarded, not committed — same convention every prior
Velune part in this task has used, no Android SDK in this sandbox): 15
scenarios — initial state, tick-advance-and-wrap, the
no-reshuffle-on-initial-resume distinction (the one real bug this
simulation was written specifically to catch before it became Kotlin),
stop-then-resume triggering exactly one reshuffle with index reset,
double-stop defensiveness, empty-list and single-item edge cases, and
a documented assumption for mid-session data refresh (swaps the
source list without forcing a reshuffle). All 15 passed. Brace/paren
balance also checked on the real file (20/20, 43/43) as a basic
structural sanity check. **Not compile-verified** — same structural
limitation as every prior Velune task in this project.

## 17. Task 59 Round 16, Part B-i (2026-09-02) — genreTile reaches AlbumViewModel/ArtistViewModel/OnlinePlaylistViewModel, canonical write-up in Mavins-web

**Kept concise here — Mavins-web's `handover.md`, Task 59's own
"Round 16" entry, has the full write-up.** Round 15's Part B
(consumption in `AlbumScreen`/`ArtistScreen`/`OnlinePlaylistScreen`)
sub-split into i/ii, mirroring Round 11 → 12's own shape one level up
the chain. This is B-i only: all three ViewModels (traced first, not
assumed identical — all already use the same `SavedStateHandle`
pattern) gained the identical `genreTileTitle: String?` field, same
`URLDecoder` convention as every prior round. Confirmed via grep that
`NavigationBuilder.kt`'s three routes already declare the matching
`navArgument` from Round 15 Part A.

Verified via brace balance check on all three files. Not
compile-verified — no Android SDK in this sandbox.

**B-ii — not started:** thread `genreTileTitle` into each screen's own
song-tap-to-queue-construction call site(s) — not yet traced which
exact call sites those are.

## 18. Task 59 Round 16, B-ii Part b-b split a/b (2026-09-02) — Part b-b-a done, canonical write-up in Mavins-web

Part b-b (13 call-site edits across `AlbumScreen.kt`/`ArtistScreen.kt`/
`OnlinePlaylistScreen.kt`, per Round 16's own trace) split a/b by file,
per explicit instruction, since it hadn't been split yet. **Part
b-b-a = `ArtistScreen.kt` alone (6 sites), done this round, commit
`51df723`.** All 6 verified against the real current code directly
before editing, not assumed from the trace note. **Part b-b-b =
`AlbumScreen.kt` (3) + `OnlinePlaylistScreen.kt` (4) = 7 sites — not
started.** Full write-up (each exact call site, before/after) in
Mavins-web's own `handover.md`, Task 59's "Round 16" section.

Verified via brace/paren balance check (189/189 `{}`, 413/413 `()`).
Not compile-verified — no Android SDK/Gradle in this sandbox, same
standing limitation every round of this chain has flagged.

## 19. Task 59 Round 16, B-ii Part b-b-b split a/b (2026-09-02) — Part b-b-b-a done, canonical write-up in Mavins-web

Part b-b-b (`AlbumScreen.kt` 3 sites + `OnlinePlaylistScreen.kt` 4
sites) split a/b by file, same convention as Part b-b's own split one
level up, since it hadn't been split yet. **Part b-b-b-a =
`AlbumScreen.kt` alone (3 sites), done this round, commit `e2ba9ef`.**
Verified against the real current code directly — confirmed
`LocalAlbumRadio`'s actual constructor order and `AlbumViewModel`'s
`genreTileTitle` field by reading both files, not assumed. **Part
b-b-b-b = `OnlinePlaylistScreen.kt` (4 sites) — not started.** Full
write-up in Mavins-web's own `handover.md`, Task 59's "Round 16"
section.

Verified via brace/paren balance check (163/163 `{}`, 394/394 `()`).
Not compile-verified — no Android SDK/Gradle in this sandbox, same
standing limitation every round of this chain has flagged.

## 20. 2026-09-03 — Task 49 Part b-b-i: `ensureDeviceListener()` Kotlin wrapper, canonical write-up in Mavins-web

**Sync note only — same pattern used throughout this section.**
Unrelated task (Task 49, listener earnings — not Task 59): new
`CampaignRepository.ensureDeviceListener(deviceId: String): String?`,
wrapping migration 028's `ensure_device_listener` RPC (an idempotent
upsert of a minimal `public.users` row for a device-ID-only listener,
`role = 'listener'`). Split into b-b-i (this — the repository
function) / b-b-ii (wiring the actual call site in `MusicService.kt`,
not started) per explicit instruction. Full detail — including a real,
flagged-not-assumed uncertainty about this RPC's scalar (`RETURNS
UUID`) response shape, unlike every other RPC this file's Kotlin code
parses — in Mavins-web's `handover.md`, Task 49's own "Part b-b"
entry. Verified via brace/paren balance check (a first pass came back
unbalanced, isolated to the diff's own added lines to find the exact
line rather than scanning the whole file — a doc-comment parenthetical
left unclosed, fixed before finalizing). Not compile-verified.

## 21. 2026-09-03 — Task 49 Part b-b-ii: `ensureDeviceListener()` wired into the real call site, canonical write-up in Mavins-web

**Kept concise here — Mavins-web's `handover.md`, Task 49's own
"Part b-b" entry, has the full write-up.** Wired
`CampaignRepository().ensureDeviceListener(deviceId)` into
`MusicService.kt`'s one real `getOrCreateCampaignDeviceId()` call
site (confirmed via grep — exactly one), using the same `deviceId`
local variable `recordCampaignStream` already uses. Called
sequentially before `recordCampaignStream`, inside the same existing
`scope.launch(SilentHandler)` block.

Confirmed `recordCampaignStream` doesn't touch `listener_play_events`
at all yet, so this call is purely proactive today — no ordering race
exists, sequenced correctly for whenever a future part starts writing
rows that reference it.

Verified via brace/paren balance check (0/0, balanced). Not
compile-verified. **Part b-b (both b-b-i and b-b-ii) is now fully
done.**
