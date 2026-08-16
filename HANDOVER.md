# Velune EQ/DSP Handover (v3)

You're picking up work on **Velune**, an Android music app (fork, package
`com.nikhil.yt`), repo: `github.com/Zapier-codes/Velune`. This document is
written by the previous Claude session so you don't have to reconstruct
context from scratch. Read it fully before touching anything.

**This file lives in the repo now** (`HANDOVER.md` at repo root) — v1 and
v2 of this document only ever existed as text pasted into chat by the
user, which meant a fresh session cloning the repo saw none of it. If a
future session is reading this from a fresh clone, that problem is fixed;
if you're instead seeing this pasted into chat with no repo access yet,
apply the outstanding patch(es) below first, or ask the user to, so the
file and the code it describes stay in sync.

## 0. Read this first — what you can and can't do

- **You cannot push to GitHub.** No credentials for this repo. Your job is
  to produce `.patch` files via `git format-patch`, verify them with
  `git am` against a fresh clone, hand them to the user, and **tell them
  explicitly, as a complete, copy-pasteable command**, how to apply and
  push it. `git am` accepts an absolute path directly — there is no need
  to `mv`/copy the patch into the repo first, and no need to guess a
  download directory. **Look at the user's own terminal output in this
  conversation before guessing a path.** This user is on Termux on
  Android (prompt `~/Velune $`), and their downloads land at
  `/sdcard/Download/`, confirmed repeatedly across sessions:
  ```
  cd ~/Velune
  git am /sdcard/Download/<patch-file> && git push origin main
  ```
  If a future session has no prior terminal output to go on (a fresh
  conversation, no evidence of the user's platform), don't silently
  assume any specific path — ask, or give the command with a clearly
  marked placeholder and say plainly that the download location depends
  on their device/browser. Guessing a plausible-sounding path and
  presenting it as correct is worse than asking, because it produces a
  confident, complete-looking command that still fails.
- **You cannot build or run the Android app.** No Android SDK in the
  sandbox, and the network allowlist doesn't include Google's Maven repo.
  What you *can* do: `apt-get install kotlin` and JVM-test pure
  logic/DSP/parsing code in isolation, stubbing out Android-only types.
  Keep doing this for anything math/logic-heavy — see §3 for what's been
  verified this way so far. Note: the sandbox's `apt-get` kotlinc is an
  *old* 1.3.31 build (no trailing commas in some positions, no
  `String.lowercase()`, `continue`/`break` inside `when` inside `while`
  needs labeled loops). When this bites, keep the **committed** source
  using modern idioms (the real Gradle build uses a modern Kotlin) and
  only adapt a throwaway harness copy — don't downgrade the actual patch
  to work around the sandbox's old compiler.
- **The GitHub REST API (`api.github.com`) is unauthenticated from this
  sandbox and rate-limits fast** (60 req/hr/IP, shared with whatever else
  hits that IP). If you need real API response shapes for a test fixture
  and get rate-limited, either wait it out or hand-build a fixture that
  mirrors a real response you *did* manage to fetch earlier in the
  session — don't invent a plausible-looking JSON shape from memory
  without ever having seen the real thing at least once.
- **The user pastes AI-generated technical writeups into chat and treats
  them as a spec.** Not an audio engineer — relaying another model's
  competitive analysis of Poweramp/Neutron/UAPP/Wavelet. Treat pasted
  blocks as a wish list to triage, not a literal spec. Break asks into
  real pieces, let the user pick priority (`ask_user_input_v0` works well
  for this).
- **Watch for terminology confusion, gently.** A past session hit one
  worth knowing about: the user initially read "load impulse response"
  as "pick which song to process," and pushed back saying they didn't
  want a document picker, they wanted "the already playing song" wired
  in. That's not a picker problem — it was a mixup between two different
  things: an *impulse response* (a small correction-curve file, not a
  song) versus the actual track being played (which already flows
  through the entire DSP chain automatically, always has, no picker
  involved). If IR/convolution/"which audio does this apply to" comes up
  again, don't assume the confusion is resolved for good — a plain
  one-line reminder heads it off cheaply.
- **Be honest about scope.** Some asks aren't a patch, they're a
  different subsystem (see §4). Don't pretend otherwise.
- **When a user request implies "use a real external source" (e.g. "use
  an open source library" rather than "build one"), actually go find the
  real thing** — check its real file format/bit-depth/schema against what
  your own code currently assumes, rather than assuming compatibility.
  The 24-bit PCM fix in §2 exists because a previous session checked a
  real downloaded file instead of assuming the existing WAV parser
  already covered whatever format a "real" library would ship in.

## 1. How to get set up

```bash
cd ~
rm -rf Velune
git clone https://github.com/Zapier-codes/Velune.git
cd Velune
git log --oneline -6
cat HANDOVER.md   # this file, if it's landed on main by the time you read this
```

As of this handover (v3), the **real GitHub `main`** was last confirmed
at:

```
aa79252 feat(eq): add Convolution UI — load/enable/clear impulse response in Master tab
```

One patch was built after that commit in the v3 session — `0012` — but
**you have no way to know from here whether the user has applied it
yet.** Check the log after cloning:

- If `main` still tops out at `aa79252` → `0012` not applied yet. Ask if
  the user still has the file, or regenerate it from §2 below if needed.
- If `main` already has a commit titled `feat(eq): connect preset IR
  library to ASH-IR-Dataset on GitHub` → applied, build on top of that
  history directly. This file (`HANDOVER.md`) is included in that same
  patch, so if that commit landed, this file is already on `main` too —
  which is also how you'd know a fresh clone will show it to whoever
  picks this up next.

Number your next patch accordingly.

## 2. What's been done so far (in order)

All of this lives under `app/src/main/kotlin/com/nikhil/yt/eq/` and
`app/src/main/kotlin/com/nikhil/yt/ui/screens/equalizer/`.

1. **Biquad parametric EQ** (pre-existing) — solid, standard. Untouched.
2. **Master bus controls** (pre-existing) — balance, bass boost, stereo
   width, all on `CustomEqualizerAudioProcessor`.
3. **Lookahead limiter** (patch `0009`, merged) —
   `audio/LookaheadLimiter.kt`. ~5ms delay line, sliding-window-minimum
   gain envelope, L/R-linked gain reduction.
4. **Convolution engine** (patch `0010`, merged) — real IR-based tone
   shaping. `audio/Fft.kt`, `audio/PartitionedConvolver.kt`,
   `audio/ConvolutionAudioProcessor.kt`, `data/ImpulseResponse.kt`. Wired
   into `CustomEqualizerAudioProcessor` as the first stage, ahead of the
   biquad bands.
5. **Convolution UI** (patch `0011`, merged) — SAF file picker, load/
   enable/clear controls in the Master tab, DataStore persistence,
   `AxionEqViewModel.importImpulseResponse` / `setConvolutionEnabled` /
   `clearImpulseResponse`.
6. **Preset IR library** (patch `0012`, this session, pending user
   apply) — closes the "no preset library" gap from v2's §3 by
   connecting to a real open source dataset instead of hand-building
   presets:
   - **`eq/data/PresetIrRepository.kt`**: browses/downloads from
     `github.com/ShanonPearce/ASH-IR-Dataset` — a manufacturer-organized
     set of real measured single-channel headphone correction filters
     (HpCFs). Lists manufacturers and models via GitHub's contents API
     (`api.github.com/repos/.../contents/HpCFs[/manufacturer]`), with
     on-disk caching and stale-cache fallback if the API rate-limits.
     Downloads a selected filter's raw WAV bytes as-is from
     `raw.githubusercontent.com` — nothing redistributed through any
     server Velune controls.
   - **`eq/data/MiniJson.kt`**: small dependency-free JSON parser backing
     the above — same reasoning as `ImpulseResponse.kt`'s hand-rolled WAV
     parser: avoids pulling in org.json/Gson/kotlinx.serialization for
     one call site.
   - **`eq/data/ImpulseResponse.kt` — real bug fix**: the dataset's
     filters are **24-bit PCM**; the existing parser only accepted 16-bit
     and 32-bit, so *every single download would have failed
     validation*. Added 24-bit decode (manual 3-byte read, sign-extend).
     Found by actually downloading a real file and checking its header
     instead of assuming compatibility — see §0's last bullet.
   - **`AxionEqViewModel.kt`**: refactored so the SAF-picker import path
     (`importImpulseResponse`) and the new
     `importFromPresetLibrary(model)` share one
     `validateIrFile`/`adoptImportResult` pair — a downloaded preset goes
     through the *exact same* validate-then-adopt logic as a manually
     picked file, nothing preset-specific skips validation. New state:
     `presetManufacturers`, `presetModels`,
     `presetSelectedManufacturer`, `presetBrowserLoading`,
     `presetBrowserError`, `presetDownloadingName`.
   - **`AxionEqScreen.kt`**: "Browse preset library" button in
     `ConvolutionSection`, opening a new `PresetLibrarySheet` composable
     — manufacturer list, drill into models, tap to download+apply.
     Attribution text shown in the sheet (see licensing note below).
   - **Licensing — read this before shipping**: the dataset is
     **CC BY-NC-SA 4.0** (attribution, non-commercial, share-alike).
     That's the *dataset's* license, not Velune's, but it means this
     integration is only appropriate if Velune's own distribution stays
     non-commercial (no ads, no paid tier gating this feature). This
     wasn't fully resolved with the user in the v3 session — **worth
     confirming Velune's monetization status before this ships**, and if
     it's ever monetized, this specific integration needs to be swapped
     for a permissively-licensed source instead.

### How the DSP/logic was verified (and how you should verify yours)

Same approach every session: `apt-get install -y kotlin`, extract the
pure-Kotlin logic, stub out Android types, run standalone `main()`s with
assertions.

For patch `0012` specifically:
1. **24-bit PCM decode** — verified against a real HpCF file downloaded
   from the dataset (1024-tap mono FIR, 44.1kHz, values in a sane
   normalized range) *and* hand-built WAVs with known exact 24-bit values
   (max positive/negative, zero, half-scale) to pin down sign/scale/byte
   order precisely, not just "doesn't crash." Confirmed no regression to
   the existing 16-bit path, and that genuinely bad bit-depths (e.g.
   corrupted-to-20-bit) are still rejected.
2. **MiniJson parser** — round-tripped against hand-crafted tricky input
   (escapes, nesting, numbers, booleans, null) and confirmed it throws
   (rather than silently misparsing) on malformed input.
3. **Catalog parsing** (`parseManufacturers`/`parseModels`/
   `prettifyFileName`) — tested against fixtures reconstructed from real
   GitHub API responses fetched earlier in the same session (the API
   itself got rate-limited mid-session, so fixtures were hand-built to
   mirror an actually-observed response rather than a guessed shape — see
   §0). Covers: loose top-level files correctly excluded from the
   manufacturer list, `&`-in-name folders (GitHub's own URL-encoded
   `url` field used as-is rather than re-encoded), `null` `download_url`
   and non-`.wav` entries skipped, sorting, filename prettification
   edge cases.

**What this did NOT verify** (say so explicitly if asked, same as
always): the SAF picker itself, the new bottom sheet's actual Compose
rendering/scroll behavior on a real device, DataStore persistence across
a real process death, Hilt injection, GitHub API rate-limit behavior
under real multi-user load (each user's own device IP, so probably fine,
but unconfirmed), on-device MIME-type behavior for real file providers,
or download reliability on-device (flaky connection mid-download, etc —
`PresetIrRepository.download` doesn't currently retry or resume).

## 3. The end goal — what we're actually trying to reach

Neutron/Poweramp/UAPP/Wavelet feature parity, with the same
explicitly-out-of-scope list from v1/v2: tempo/pitch, bit-perfect/USB-DAC
output, decoder-level gapless, "a decade of tuning."

**Still open, now that convolution + its preset library both exist:**
- **Never tested on-device**, at all, across three patches now (`0010`,
  `0011`, `0012`): the ~23ms convolution latency's interaction with
  `flush()` on seek/track-change, actual CPU cost for realistic IR
  lengths, whether the SAF picker actually works across different file
  managers/providers, and now also whether the preset browser sheet
  renders/scrolls sensibly and whether downloads actually complete
  reliably on a real network. This is arguably the single biggest gap
  left in the convolution feature as a whole — a lot has been built and
  JVM-verified, nothing has been touched on a real phone.
- **Preset library's licensing status** — see §2's licensing note. Ask
  the user directly if this hasn't come up: is Velune ever going to be
  monetized in a way that conflicts with CC BY-NC-SA 4.0?

**Still-live, not-yet-started options** (same as v1/v2, offer if asked
"what's next"):
- Spectrum analyzer feeding the EQ UI (`Fft.kt` already exists from the
  convolution engine, reuse it).
- Tempo/pitch engine (bigger, separate subsystem).

## 4. Suggested next step

Ask the user directly, `ask_user_input_v0` style:

1. **On-device verification pass** — nothing convolution-related has
   ever run on an actual phone; this is arguably overdue given how much
   has been built on top of it across three patches.
2. **Spectrum analyzer** — FFT-driven visualizer feeding the EQ UI.
3. **Tempo/pitch engine** — separate, larger DSP subsystem.
4. Something else the user names.

Whichever you pick: scope it honestly, build it for real, verify what you
can standalone with a JVM-compiled test harness, generate a numbered
`.patch` file with `git format-patch`, verify it applies cleanly with
`git am` against a fresh clone before handing it over, and **explicitly
give the user the complete apply command using their actual, already-
demonstrated download path** (see §0). If you make any nontrivial
decision or discover any nontrivial gotcha, **update this file
(`HANDOVER.md`) in the same patch** so the next session — whether that's
you later, or a fresh Claude with no memory of this conversation — picks
it up automatically on clone instead of depending on this exact chat
transcript still being around.
