# Velune Campaign Card Handover (v1)

> **▶ START HERE — read this box only, then go to §8 below for the
> real next work. Skip the rest unless you get stuck.**
>
> **Next task in THIS repo/file: no numbered queue here** (different
> convention from the other two repos in this project — established
> intentionally, don't force one on). Work any item in **"8. Not done
> / open"** below; the real current blocker is **no live Supabase
> credentials wired in**, so nothing built here is testable end-to-end
> until that's supplied.
>
> **Full cross-repo status, as of this note:**
> - **Velune** (this repo, this file) — next: see §8 below
> - **Mavins-web** — next: **Task 28** (`Zapier-codes/Mavins-web`,
>   local folder `mavins-web` lowercase)
> - **B-Pay-backend** — next: **none currently unblocked** (Korapay-
>   only focus active; `Zapier-codes/B-Pay-backend`, fork of
>   `Phoenix-Boss/B-PAY-backend`)
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
- **No real Supabase project wired in.** The user hadn't provided a URL/
  anon key as of this session — `local.properties`/CI secrets are unset,
  so `BuildConfig.SUPABASE_URL`/`SUPABASE_ANON_KEY` build empty and
  nothing will show on Home until real credentials are supplied (see §5)
  and at least one campaign row exists and is inside its date window.
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
