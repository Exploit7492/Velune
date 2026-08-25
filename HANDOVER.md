# Velune EQ/DSP Handover (v20)

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

As of this handover (v13), the **real GitHub `main`** was last confirmed
at:

```
c4bd784 perf(eq): identity fast path for TempoPitchAudioProcessor — this was the real stutter source
```

That includes the bounded-race startup-restore fix and its
construction-time-guarantee replacement (§2f/§2g), the `App.onCreate()`
SharedPreferences warm-up (§2i), the `isActive()` fix for
`CustomEqualizerAudioProcessor` (§2h), and the `TempoPitchAudioProcessor`
identity fast path that turned out to be the real stutter source (§2j).

**Note on the stale pointer this replaced**: the v11→v12 update (this
session's starting point) pointed at `efaf9e0`/patch `0019` — two real
commits behind by the time this session actually read it, because `main`
moved twice (§2i, §2j) while a *previous* session's own patch was still
sitting unapplied. This is the third time this exact failure mode has
been called out in this file (see the v8/v10 incidents described in the
paragraph below, kept for the pattern, not the specific numbers) — **the
lesson generalizing across all three: don't just check `main` once at the
start of a session and trust that pointer for the rest of it.** This
session specifically got bitten mid-work: drafted this section's own
predecessor against `510522e`, then `main` advanced twice *while that work
was still uncommitted locally* — caught only because of a second
re-clone-and-check right before finalizing, not the first one. If your
session runs long or does substantial work, re-verify against a fresh
`git log` again before you generate your patch, not just when you start.

Also note: some commits land via normal GitHub PRs (`(#144)`, `(#146)`)
rather than this session's `git am`-patch workflow, and some appear to
have been committed directly (author `Pops <pops@velune.dev>`, no
`.patch` file evidence) — the numbered-patch discipline isn't followed
with perfect consistency session to session; don't assume it always
will be.

One more patch was built after that commit in the v13 session — `0020`,
described in §2k below: a stereo output peak/level meter next to the
Preamp knob, plus a first pass at tightening row padding. **You have no
way to know from here whether the user has applied `0020` yet.** Check
the log after cloning:

- If `main` still tops out at `c4bd784` → `0020` not applied yet. Ask if
  the user still has the file, or regenerate it from §2k below if needed.
- If `main` already has a commit titled `feat(eq): output peak/level meter
  next to the Preamp knob` → applied, build on top of that history
  directly. This file is updated in that same patch, so if that commit
  landed, this version of the file is already on `main` too.

Number your next patch `0021`.

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
6. **Preset IR library** (patch `0012`, merged) — closes the "no preset
   library" gap from v2's §3 by connecting to a real open source dataset
   instead of hand-building presets:
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
7. **Spectrum analyzer** (patch `0013`, merged) — closes the "spectrum
   analyzer feeding the EQ UI" item from §3:
   - **`eq/audio/SpectrumAnalyzer.kt`** (new): reuses the existing `Fft`
     class (built for the convolution engine) rather than a second FFT
     implementation. 1024-sample non-overlapping blocks, Hann-windowed,
     magnitude converted to dB and normalized into 28 log-spaced bars
     from 20Hz to min(Nyquist, 20kHz). Publishes each finished block via
     a single `AtomicReference.set` — the audio thread (`accept()`, one
     sample at a time) and the UI thread (`snapshot()`) never share a
     lock, and a reader can never see a half-written array. Off by
     default (`enabled` flag) so there's no per-sample cost paid when no
     spectrum UI is on screen.
   - **`CustomEqualizerAudioProcessor.kt`**: taps the signal at the very
     end of the chain — after convolution, bands, bass boost, width, and
     the limiter — so what it visualizes is exactly what reaches the
     output, not an intermediate stage. Mono downmix `(left+right)*0.5`
     fed in for stereo; the raw sample for mono streams. Reset alongside
     the limiter/convolver in `flush()`.
   - **`EqualizerService.kt`**: `setSpectrumAnalyzerEnabled`/
     `spectrumSnapshot()`, same "pending" pattern as every other control
     here — a toggle flipped before a processor exists is remembered and
     applied once one shows up. `spectrumSnapshot()` reads the first
     active processor (the normal single-player case has exactly one).
   - **`AxionEqViewModel.kt`**: `spectrumBars` StateFlow +
     `setSpectrumVisible(Boolean)`, which starts/stops both the DSP tap
     and a ~20fps (50ms) poll loop together. Deliberately *not*
     persisted like the other master-bus toggles — it's view-visibility,
     always starts off when the screen opens.
   - **`AxionEqScreen.kt`**: new `SpectrumSection` in the Master tab,
     above `MasterBusControls` — a switch plus a `Canvas`-based bar
     graph (`SpectrumBarsCanvas`) with a simple linear peak-decay layered
     on top in the UI layer (the DSP layer itself publishes raw,
     unsmoothed blocks — see its class doc for why that split). Wired to
     `viewModel.setSpectrumVisible` via a `DisposableEffect` scoped to
     the Master tab's own composition, so switching tabs, closing the EQ
     screen, or backgrounding the app all stop it automatically — never
     left polling in the background.

### How the spectrum analyzer specifically was verified

Same JVM-harness approach as every other patch (§2's later paragraph
below describes the general method). For `SpectrumAnalyzer` specifically:
fed a synthetic 1kHz sine wave and confirmed the peak bar lands within one
bar of where the log-frequency mapping predicts (computed independently in
the test, not by re-deriving the analyzer's own formula); confirmed a
quiet bar far from the peak stays well below it; confirmed pure silence
reads near-zero on every bar; confirmed `accept()` never throws when
called disabled and/or unconfigured; confirmed reconfiguring to a
different sample rate mid-session (e.g. a track change) still produces a
correct analysis afterward; confirmed `snapshot()` always returns exactly
`BAR_COUNT` elements, including before the first block completes; confirmed
`reset()` actually clears state. All passed. **Not verified**: the Canvas
composable's actual on-device rendering, frame pacing, or whether 20fps
polling feels smooth — same "nothing touched a real phone yet" caveat as
everything else in this file, see below.

8. **Spectrum analyzer refinements** (patch `0014`, this session, pending
   user apply) — the user explicitly asked for "pro level, like Neutron
   and Poweramp." Doing that properly turned out to require more than the
   three items originally flagged in §3/§4 (overlap, peak-hold, frequency
   labels) — a block-aligned analyzer with no ballistics wouldn't actually
   look professional no matter how you labeled its axis, so bar smoothing
   (ballistics) came along as a necessary fourth piece:
   - **`eq/audio/SpectrumAnalyzer.kt` — restructured**:
     - **75% overlapping analysis** (hop = FFT_SIZE/4 = 256 samples,
       ~5.8ms @44.1kHz, ~172 analyses/sec) instead of one FFT per full
       1024-sample block. `accept()` now just writes into a circular
       history buffer and bumps a counter — cheap, no windowing on the
       per-sample hot path; windowing + FFT only happen once per hop, in
       `analyzeBlock()`, over the last FFT_SIZE samples read out of the
       circular buffer. This is what actually produces continuous-looking
       motion rather than a visible ~23ms step, and catches a transient
       that would've landed on an old block boundary.
     - **Ballistic bar smoothing** — instant attack (a rising level shows
       immediately), rate-limited release (`BAR_RELEASE_DB_PER_SEC = 24.0`
       dB/sec). Without this, 172 analyses/sec of raw magnitude looks like
       static, not a musical display — every real RTA applies some form
       of this, it's not optional polish.
     - **Peak-hold caps** — per-bar peak latches on a new high, holds flat
       for `PEAK_HOLD_MS = 1200.0`ms, then decays at its own slower
       `PEAK_DECAY_DB_PER_SEC = 10.0` dB/sec, trailing visibly above the
       bar. Standard RTA feature (see Neutron/Poweramp's own spectrum
       views).
     - Both ballistics are driven by **elapsed audio-domain time**
       (`hopSize/sampleRate` seconds per block) computed once in
       `configure()`, not wall-clock or UI frame timing — so the decay
       rate is exact regardless of how often (or unevenly) the UI happens
       to poll `snapshot()`.
     - **`snapshot()` now returns `SpectrumSnapshot`** (new data class:
       `levels` + `peaks`, both `FloatArray`), not a bare `FloatArray` —
       published as one atomic pair so a reader never gets a level from
       one block matched with a peak from another.
     - **Frequency labeling**: `barCenterHz` computed per bar in
       `configure()` (geometric mean of its edge frequencies),
       `barIndexForLabel(hz)` resolves a frequency to the nearest bar by
       log-distance, `LABEL_FREQUENCIES_HZ` is the "nice" tick set
       (20/50/100/200/500/1k/2k/5k/10k/20k) a UI is expected to call it
       with. `isConfigured()` added so a caller can tell -1 (not
       configured) apart from a legitimate lookup.
     - **A real bug caught by the JVM harness, not by inspection**:
       `configure()` reset all the internal ballistics arrays on a sample-
       rate change but never re-published to the `AtomicReference` —
       `snapshot()` kept returning the *stale pre-reconfigure* result
       until the next analysis block completed. Invisible in a quick read
       of the code; the reconfigure test in the harness caught it
       immediately (`peaks cleared by reconfigure` failed) because it
       calls `snapshot()` right after `configure()`, before any new
       samples arrive. Fixed by publishing a cleared snapshot inside
       `configure()` itself, same as `reset()` already did.
   - **`EqualizerService.kt`**: `spectrumSnapshot()` now returns
     `SpectrumSnapshot`; added `spectrumBarIndexForLabel(hz)` forwarding
     to the active processor's analyzer.
   - **`AxionEqViewModel.kt`**: `spectrumBars: StateFlow<FloatArray>`
     replaced with `spectrumSnapshot: StateFlow<SpectrumSnapshot>`. New
     `spectrumLabels: StateFlow<List<Pair<String, Int>>>` (label text to
     bar index), computed lazily inside the existing poll loop — retried
     every poll until it resolves, since the very first poll after
     opening the screen can land before the audio processor has a sample
     rate yet — and cleared when the analyzer is hidden so a sample-rate
     change while hidden doesn't leave stale bar indices cached.
   - **`AxionEqScreen.kt`**: `SpectrumBarsCanvas` now draws both the
     smoothed bar and a peak-hold cap (a thin bright line above the bar,
     only drawn once it's visibly separated from the bar itself) —
     no smoothing logic of its own anymore, since ballistics now live
     correctly in the DSP layer against real audio time; the old
     per-recomposition decay hack this replaced was frame-rate-dependent
     (`peaks[i] = max(bars[i], peaks[i] - 0.04f)` — literally wrong,
     would've decayed at different real-world speeds on different
     devices/frame rates) and is gone entirely. New `SpectrumLabelRow`
     renders the axis tick text under an equal-weight `Row` of cells —
     approximates the canvas's own bar-gap geometry rather than sharing
     exact pixel math with it, which is a fine trade-off for label
     alignment (real graphic EQs do this loosely too).

### How the spectrum analyzer refinements were verified

Same JVM-harness method as `SpectrumAnalyzer` v1 (§ above), expanded to
cover what's new: confirmed one hop's worth of samples (not a full block)
is enough to trigger a new analysis, proving overlap actually engages;
confirmed the 1kHz-tone-lands-in-the-right-bar check still holds against
the now-ballistics-smoothed `levels`; confirmed attack is effectively
instant (a strong bar shows after just one hop from cold); confirmed a
peak-hold cap stays flat through a handful of silent hops (well inside the
1200ms hold window) while the bar level itself has already started
falling; confirmed the peak visibly decays once enough silent hops blow
past the hold window, and that the bar itself decays all the way back to
near-silence at its slower, separate rate; confirmed `barIndexForLabel`
is monotonic across low/mid/high test frequencies and resolves every
`LABEL_FREQUENCIES_HZ` entry to a valid bar; confirmed `isConfigured()`
flips correctly; confirmed `reset()` clears both `levels` and `peaks`;
confirmed reconfiguring to a different sample rate mid-session both
re-analyzes correctly *and* (after the bug fix above) immediately
publishes a cleared snapshot rather than a stale one. 30 checks, all
passed after the one real bug fix above. **Not verified, same as v1**:
the Canvas's actual on-device rendering, frame pacing, whether the peak-
hold cap and label row are legible/well-positioned on a real screen, and
whether ~172 analyses/sec (up from ~43) has any noticeable CPU cost on a
low-end device with the analyzer toggled on.

9. **Independent tempo/pitch engine** (patch `0015`, this session) —
   closes the "tempo/pitch engine" item from §3's not-yet-started list.
   User explicitly chose the "real WSOLA/phase-vocoder-family engine"
   option over wiring up Media3's built-in `SonicAudioProcessor` (offered
   both, see the `ask_user_input_v0` at the start of this session) — this
   is the same algorithm family SoundTouch/Neutron/Poweramp use, not the
   cheaper approach stock Android players ship. Also explicitly asked for
   tempo and pitch to be **fully independent** (change one without
   affecting the other), not linked/varispeed.
   - **`eq/audio/SampleQueue.kt`** (new): a small growable/compacting
     `DoubleArray`-backed queue, shared by both classes below since both
     need to buffer input across `queueInput` call boundaries (neither
     Media3 chunk size nor a WSOLA analysis window line up with anything
     predictable).
   - **`eq/audio/WsolaTimeStretcher.kt`** (new): the actual time-stretch
     engine — changes duration without changing pitch. Streaming WSOLA:
     40ms Hann windows at 50% overlap (hop = 20ms), ±10ms similarity
     search per hop against the *previous* chosen segment's tail (cosine
     similarity on a per-hop basis, not a full correlation over the whole
     window) to avoid the audible phase glitching of naive fixed-hop
     overlap-add. Multi-channel aware: the similarity search always runs
     against a channel *mixdown*, and the resulting offset is applied
     identically to every channel, so stereo content can't have its
     channels searched independently and drift out of phase with each
     other (verified — see below). `speed` is a pure time-axis ratio,
     `1.0` = unchanged.
   - **`eq/audio/LinearResampler.kt`** (new): streaming linear-
     interpolation resampler — reading at `rate != 1.0` is classic
     varispeed (pitch and duration change together). Not independent
     pitch control on its own; see below for how it's composed with WSOLA
     to become that.
   - **How independent tempo/pitch is actually achieved**: resample by
     the desired `pitchRatio` first (shifts pitch, incidentally changes
     duration by `1/pitchRatio`), then WSOLA-stretch the result by
     `tempoRatio / pitchRatio` to land on the actually-desired duration.
     This is the standard approach every consumer time-stretch library
     uses (not two independent WSOLA passes, which would compound
     artifacts) — verified directly (see below) that pitch-only, tempo-
     only, and simultaneous-but-different ratios all land on their
     independently-expected duration and pitch, not some coupled/blended
     result.
   - **`eq/audio/TempoPitchAudioProcessor.kt`** (new): the Media3
     `AudioProcessor` wiring the above into the actual playback chain.
     Handles PCM16/PCM_FLOAT, mono/stereo, same as
     `CustomEqualizerAudioProcessor`. Unlike every other processor in
     this package, this one's output frame count is **not** 1:1 with its
     input — that's the whole point of tempo change — so it's a
     standalone `AudioProcessor`, not folded into
     `CustomEqualizerAudioProcessor`'s existing tight 1:1 sample loop.
     `setTempo(ratio)` (coerced 0.25x–3.0x) and `setPitchSemitones(st)`
     (coerced ±12, converted to a ratio via `2^(st/12)`) are independent
     setters — changing one recomputes `wsola.speed =
     tempoRatio/pitchRatio` without touching `resampler.rate`, and vice
     versa. `queueEndOfStream()` pushes a little silence through to flush
     WSOLA's held-back final window rather than losing it — see the
     class doc for the on-device caveat that comes with that. Tracks
     cumulative input/output frame counts for `mediaDurationForPlayoutDuration`
     (see the chain class below).
   - **`eq/audio/TempoPitchAudioProcessorChain.kt`** (new): **the actual
     reason this needed real thought, not just a new `AudioProcessor`.**
     Media3's `DefaultAudioSink` doesn't infer position/seek-bar tracking
     from "audio changed length" automatically — it asks the installed
     `AudioSink.AudioProcessorChain` for `getMediaDuration(playoutDuration)`
     to convert playback time into source-media time, and
     `DefaultAudioProcessorChain`'s own implementation just forwards that
     to its internal `SonicAudioProcessor`. Since this patch's engine
     isn't Sonic, a custom `AudioProcessorChain` was required, routing
     `getMediaDuration` to `TempoPitchAudioProcessor.mediaDurationForPlayoutDuration`
     instead — which itself uses the *observed* cumulative input/output
     frame ratio (not the nominal `tempoRatio`), same reasoning Sonic's
     own implementation uses byte counts rather than trusting the
     parameter, since WSOLA's windowing/rounding means the real ratio
     drifts very slightly from the theoretical one over a long track.
     Confirmed via GitHub source (`androidx/media`, not guessed from
     memory or stale ExoPlayer2 docs) before writing this — see the
     class's own doc comment for the exact reasoning. `SilenceSkippingAudioProcessor`
     (backs the app's existing, unrelated "Skip silence" player setting)
     is preserved exactly as `DefaultAudioProcessorChain` would have
     wired it. `SonicAudioProcessor` is dropped entirely — nothing else
     in the app used it.
   - **`MusicService.kt`**: `tempoPitchAudioProcessor` (lazy, registered
     with `EqualizerService` the same way `customEqAudioProcessor` is),
     `buildAudioSink`'s `setAudioProcessorChain(...)` now builds
     `TempoPitchAudioProcessorChain` instead of
     `DefaultAudioSink.DefaultAudioProcessorChain`, `onDestroy()`
     unregisters it alongside the existing EQ processor.
   - **`EqualizerService.kt`**: `registerTempoPitchProcessor`/
     `unregisterTempoPitchProcessor`, `setTempo`/`setPitchSemitones`,
     `currentTempo`/`currentPitchSemitones` — same "pending state"
     registry pattern as every other control here, a separate list from
     `audioProcessors` since this is a standalone processor type.
   - **`ui/menu/PlayerMenu.kt` — `TempoPitchDialog` rewired, not new**:
     the app already had a tempo/pitch dialog here, driving
     `player.playbackParameters = PlaybackParameters(...)` straight into
     Media3's Sonic processor. Since that processor is gone from the
     chain now, leaving this dialog untouched would've silently broken
     it (sliders that move but do nothing). Same UI/UX, same value
     ranges, now calls `EqualizerService.setTempo`/`setPitchSemitones`
     via the `EqEntryPoint` Hilt entry point (already existed, used
     elsewhere) instead — and persists through the *same*
     `EqTempoKey`/`EqPitchSemitonesKey` DataStore entries the Axion
     screen's Master tab uses (next item), specifically so neither entry
     point's `init`/open logic can silently stomp a value the other one
     just set.
   - **`ui/screens/equalizer/axion/AxionEqViewModel.kt` /
     `AxionEqScreen.kt`**: tempo/pitch also exposed as a second entry
     point — a new "Tempo & Pitch" rotary-knob row in the Master tab's
     `MasterBusControls`, DataStore-backed via the new
     `EqTempoKey`/`EqPitchSemitonesKey` (`constants/PreferenceKeys.kt`),
     restored in `init{}` the same way balance/bass boost/width/limiter
     already are.
10. **Rotary knob / spectrum meter neon restyle** (patch `0016`, this
    session) — UI-only, no DSP/behavior changes, in response to a direct
    "make the knobs and meter slicker/futuristic, glow that follows
    rotation" ask:
    - **`ui/screens/equalizer/axion/RotaryKnob.kt`**: value arc, indicator
      dot, and a new radial under-glow behind the whole knob are each
      drawn as multiple layered passes (wide+dim, then narrower+brighter)
      instead of one flat stroke/dot — the standard fake-glow trick for a
      `Canvas` with no real blur/bloom pass. Under-glow opacity is tied to
      the knob's own value, so it visibly brightens as it's turned up
      (the "light under it that goes with what it's rotating" ask). Value
      display is now `animateFloatAsState`-driven (spring) so external
      value changes sweep into place instead of snapping.
    - **`MasterBusControls` (`AxionEqScreen.kt`)**: knobs now pass
      explicit, varied `size` values instead of all defaulting to the
      same 64dp — Preamp 88dp (largest, primary tone control) down to
      Limiter Ceiling 64dp (smallest, secondary/trim control), Balance
      68dp, Bass Boost/Width 76dp, Tempo/Pitch 80dp each.
    - **`SpectrumBarsCanvas` (`AxionEqScreen.kt`)**: bars are now a
      vertical gradient (dim at baseline, bright at current level)
      instead of a flat fill; the peak-hold cap gets the same
      halo-plus-core treatment as the knob's indicator dot instead of a
      plain line.
    - Deliberately **not** touched: the rest of the screen's background/
      panel chrome, the Simple/Advanced tabs, and the parametric band
      editor. Scoped to exactly what was named (knobs + meter) rather
      than restyling the whole screen unasked.

### How the tempo/pitch engine specifically was verified

Same JVM-harness method as every other patch. For `WsolaTimeStretcher`
specifically (mono, 3 seconds of a 440Hz sine, `speed` unmodified by
pitch): confirmed `speed=1.0` reproduces very close to the original
duration, RMS, and frequency (near-identity, proving the OLA/windowing
machinery itself is correct before testing any actual stretching);
confirmed `speed=2.0` produces ~0.5x duration with pitch unchanged;
confirmed `speed=0.5` produces ~2.0x duration with pitch unchanged;
confirmed stereo input with identical L/R channels stays *exactly*
identical after stretching (proves the mixdown-search-then-apply-to-all-
channels design actually keeps channels coherent, rather than each
channel drifting independently). For the combined resample+WSOLA
pipeline (`LinearResampler` + `WsolaTimeStretcher` together, mirroring
exactly what `TempoPitchAudioProcessor` does): confirmed pitch-only
(`pitchRatio=1.5, tempoRatio=1.0`) preserves duration and shifts pitch by
~1.5x; confirmed tempo-only (`tempoRatio=2.0, pitchRatio=1.0`) halves
duration with pitch unchanged; confirmed a simultaneous, *different*
combination (`pitchRatio=0.8, tempoRatio=1.3`) lands independently on
both the tempo-implied duration and the pitch-implied frequency, not some
coupled/averaged result — this was the actual point of the whole patch
(the user specifically wanted them decoupled) and it's the one test that
would fail if the resample/WSOLA composition math were wrong. All passed
(frequency estimated via zero-crossing rate on a trimmed, edge-padding-
excluded window; length via direct sample count). Re-ran the full suite
against the exact files as committed (not just the scratch harness copies)
immediately before generating the patch, to catch anything that might
have diverged during doc-comment cleanup.

**What this did NOT verify, and can't from a JVM harness**: anything
requiring an actual `ByteBuffer`/Media3 `AudioProcessor` runtime —
`TempoPitchAudioProcessor`'s PCM16/PCM_FLOAT encode/decode correctness,
`queueEndOfStream()`'s flush behavior, and the whole
`TempoPitchAudioProcessorChain`/`getMediaDuration` position-tracking
mechanism have only been checked by reading Media3's real source
(confirmed via GitHub, not assumed) and careful manual review, not by
running any of it. **This is the single biggest unverified risk in this
patch** — if `getMediaDuration` is subtly wrong, or if a real device's
audio thread hits a case the WSOLA algorithm's search/discard logic
doesn't handle cleanly (extremely short buffers, a seek landing mid-
window, a format change mid-track), the failure mode could be anywhere
from "seek bar drifts" to "audio glitches" to a hard crash, and none of
that would show up in a JVM harness that only ever exercised the pure-
math classes with clean synthetic sine waves. Prioritize an on-device
test of this specifically, probably above the already-overdue
convolution/spectrum on-device pass from patch `0010`–`0014`.

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

11. **Muted slave video / master audio: soft-sync speed nudge instead of
    hard-seek resync** (patch `0017`, this session) — `ui/player/Player.kt`,
    not the EQ/DSP package this handover otherwise tracks, but flagged here
    since it's the same "professional feel" bar the rest of this doc holds
    to. User reported the video toggle "keeps hitching / not smooth" and
    correctly self-diagnosed the cause before asking for a fix: the
    pre-existing master/slave video sync (silent second `ExoPlayer`,
    `volume = 0f` + audio track disabled, always following the real audio
    player's position/play-state — this part already existed and is sound
    design, don't rebuild it) had a periodic drift-correction loop that
    hard-`seekTo`'d the slave every 3s if it drifted >700ms. A `seekTo` on a
    network-streamed video forces a re-buffer — a visible freeze — so that
    "fix" was itself the stutter being complained about.
    - Removed the hard-seek loop entirely, then rebuilt drift correction as
      a **soft playback-speed nudge** — the same technique YouTube/Netflix/
      Twitch use for a silent secondary video track glued to a master
      timeline: nudge `PlaybackParameters` speed a few percent instead of
      seeking. No re-buffer, and imperceptible since the slave has no audio
      to reveal a pitch shift. Checked every 400ms; dead zone below 60ms
      drift (no correction — avoids constant micro-jitter); proportional
      nudge clamped to ±6% between 60ms–1500ms drift, ramping back to 1.0x
      once back in the dead zone; only above 1500ms (a real stall, not
      routine clock drift) does it fall back to one hard `seekTo` — that
      threshold should be rare in practice.
    - Initial load-time seek offset (compensates for the async
      resolve-URL-then-prepare latency before the slave's first frame)
      unified to `+500ms` in both places it's used (was `+200ms` in one,
      unset in the other) — explicit user instruction, not a guess.
    - Tuning constants (`SOFT_SYNC_CHECK_INTERVAL_MS`,
      `SOFT_DRIFT_DEAD_ZONE_MS`, `MAX_SOFT_DRIFT_MS`, `SOFT_SYNC_RAMP_MS`,
      `SOFT_SYNC_MAX_RATE`) are file-scope constants at the top of
      `Player.kt`, not magic numbers inline in the loop — retune there if
      a real device still shows drift artifacts.
    - **Explicitly not done, and shouldn't be**: the user's original,
      broader description of this feature included "toggle to unmute the
      video and pause the audio pipeline." Pushed back on that in-chat and
      the user didn't insist — swapping *which* stream provides audio
      would itself introduce an audible gap/glitch at the switch moment.
      The muted-slave-always-follows-master design stays as it is.
    - **Not verified on-device, same caveat as everything else in this
      file**: whether 400ms polling / a ±6% speed range is actually enough
      to keep pace with real-world clock drift on a real phone without the
      slave visibly falling behind between checks, and whether a ±6% speed
      change is genuinely invisible to a user's eye on a real screen (JVM
      harness can't test either — this is Compose+ExoPlayer runtime
      behavior, no math to unit-test in isolation the way the DSP classes
      above are). Worth an on-device pass specifically watching the video
      toggle for a few minutes on a track long enough to accumulate drift.
    - **Still outstanding from the same conversation**: the user's full ask
      this session was five pieces — this video fix was #1, tempo/pitch was
      #2, done as patch `0018` (see item 12 below). Still open: (3) EQ
      screen restructure — collapse Simple/Advanced/Master three-tab layout
      down to just Simple+Master (fold Advanced's contents into Master),
      with a single persistent on/off toggle for the whole DSP chain that
      both tabs share and that immediately audibly affects whatever's
      currently playing, local or streaming; (4) spectrum analyzer
      visibility — user says it "isn't showing" a virtualizer/peak-frequency
      display; spectrum analyzer + peak-hold already exist per items 7–8
      above, so this needs investigating as a possible regression or a
      UI-visibility bug, not necessarily a from-scratch build; (5) flanger
      effect wired into the Simple tab, with a preset-selector button at the
      top of Simple. None of these three have been scoped or started yet.

12. **Tempo/pitch changes silently did nothing on the currently playing
    track** (patch `0018`, this session) — `eq/audio/TempoPitchAudioProcessor.kt`.
    Root cause confirmed against the real Media3 source
    (`androidx/media`, `AudioProcessingPipeline.java`, fetched fresh from
    `raw.githubusercontent.com` and read directly — not guessed from
    memory) rather than assumed from the class's own doc comments:
    `AudioProcessingPipeline` only re-checks every processor's
    `isActive()` inside `flush()`, which only runs after a `configure()`
    (a format change — i.e. a new track). `TempoPitchAudioProcessor.isActive()`
    used to be `isActiveFormat && (tempoRatio or pitchRatio deviates from
    1.0/0)` — looked like a reasonable "skip processing when there's
    nothing to do" optimization, but since every track *starts* at
    tempo=1.0/pitch=0, the processor was excluded from the active chain
    at that first flush. Opening the Tempo & Pitch dialog and moving a
    slider correctly updated the ratios via `EqualizerService`'s normal
    pending-state path (this part was never broken), but the pipeline
    was never routing audio through the processor for the track already
    playing — nothing audible happened until the *next* track's own
    flush, by which point the already-nondefault ratio made `isActive()`
    true. This is why it likely looked like it "sometimes" worked
    depending on when in a session it was tried.
    - **Fix**: `isActive()` now returns `true` whenever the format is
      supported, unconditionally — the processor is in the chain from
      the very first flush of every track, so a later tempo/pitch change
      always takes effect immediately, not just on the next track. Small
      constant CPU cost even when tempo/pitch is never touched by the
      user; already justified by patch `0015`'s own verification that
      tempo=1.0/pitch=0 reproduces near-identity to a bypass, so there's
      no audible quality cost, just CPU.
    - Removed the now-unused `kotlin.math.abs` import that was only used
      by the old threshold check.
    - **Why the old Sonic-backed dialog never had this bug**: it drove
      `player.playbackParameters =`, which Media3 specifically forces a
      sink reconfigure for on every call. Replacing Sonic with this
      processor (required for independent tempo/pitch — see item 9
      above) meant bypassing that mechanism entirely, which is what
      silently reintroduced this class of bug. Worth remembering for any
      *other* control that might someday move off `playbackParameters`.
    - **Not verified on-device** — same caveat as the rest of the
      tempo/pitch engine (item 9): confirmed by reading Media3's real
      source and reasoning through the exact call sequence, not by
      running it. The theory is about as confirmable as it gets without
      a real device (it's not app-specific math a JVM harness could
      re-derive — it's "does the framework actually behave the way its
      own source says it does at runtime"), but still flag it as
      unverified rather than certain. If tempo/pitch *still* doesn't
      audibly change mid-track after this patch, the next thing to check
      is whether `DefaultAudioSink` caches `AudioProcessingPipeline`
      instances across `flush()` calls in some path that isn't the one
      read here, since only one code path was traced.


Neutron/Poweramp/UAPP/Wavelet feature parity, with the same
explicitly-out-of-scope list from v1/v2: tempo/pitch, bit-perfect/USB-DAC
output, decoder-level gapless, "a decade of tuning."

**Still open, now that convolution + its preset library + the spectrum
analyzer (base + refinements) all exist:**
- **Never tested on-device**, at all, across five patches now (`0010`,
  `0011`, `0012`, `0013`, `0014`): the ~23ms convolution latency's
  interaction with `flush()` on seek/track-change, actual CPU cost for
  realistic IR lengths, whether the SAF picker actually works across
  different file managers/providers, whether the preset browser sheet
  renders/scrolls sensibly, whether downloads actually complete reliably
  on a real network, whether the spectrum analyzer's Canvas actually
  renders smoothly at the intended 20fps poll rate, whether the peak-hold
  cap and frequency-label row are legible and well-positioned on a real
  screen, and whether ~172 analyses/sec (up from ~43 in the non-
  overlapping v1) has any noticeable CPU cost on a low-end device while
  it's toggled on. This is arguably the single biggest gap left in the EQ
  feature set as a whole — a lot has been built and JVM-verified, nothing
  has been touched on a real phone.
- **Preset library's licensing status** — see §2's licensing note. Ask
  the user directly if this hasn't come up: is Velune ever going to be
  monetized in a way that conflicts with CC BY-NC-SA 4.0?
- **Neon restyle (patch `0016`) has never rendered on a screen.** This is
  pure Compose `Canvas` drawing code, reviewed carefully but not run —
  the sandbox has no way to render Compose UI at all (no Android SDK, no
  emulator). Specifically unconfirmed: whether the layered-alpha glow
  passes actually read as "glowing" at real device pixel density and
  brightness rather than just muddy, whether the varied knob sizes look
  intentional or just inconsistent in the actual row layout, whether
  `animateFloatAsState` on the display value fights visibly with the
  drag gesture's own immediate feedback, and general legibility of the
  smaller (64dp) knobs at arm's length. This is a "does it look good"
  question a JVM harness can't answer at all — genuinely needs eyes on a
  real screen before trusting it.

**Still-live, not-yet-started options** (offer if asked "what's next"):
- (Tempo/pitch engine was built in patch `0015` and a real activation bug
  was fixed right after — see the new §2 entry below. This bullet used to
  list it as not-yet-started; that was stale by the time this session
  started. Nothing "not-yet-started" remains from the original v1
  handover's list at this point — see §4 for what's actually still open.)

## 2b. This session's work — collapsing Advanced into Master, and making
Simple/Master genuinely one engine

Two separate asks, tackled together since the second only became
apparent while scoping the first:

**1. UI: Advanced tab removed, Simple + Master only.** The three-way
`ToggleButton` row (Simple/Advanced/Master) is now two-way. `mode` is a
raw `Int` persisted in SharedPreferences (`prefs.getInt("mode", 0)`) —
rather than renumbering Master from 2→1 (which would need a migration
for anyone with `mode` already persisted as 1 or 2), the `when` dispatch
just treats anything non-zero as Master via its existing `else` branch,
so old persisted values fall through safely with zero migration code.
`AdvancedEqMode` and its private `EqBandSlider` helper (134 lines, only
caller) were deleted outright, not left dead in the file.

**2. The real bug underneath "collapse Advanced into Master": Simple and
Master's embedded parametric editor were two independent EQ systems
that could silently overwrite each other**, not just a UI redundancy.
Root cause: `EQualizerService.applyProfile(profile)` **fully replaces**
whatever bands are live — it's not additive — and there were two
separate ViewModels calling it:
- `AxionEqViewModel` (Simple, and formerly Advanced) — fixed 10 bands at
  canonical frequencies (31Hz..16kHz), builds a `SavedEQProfile` and
  calls `eqProfileRepository.saveProfile()` + `setActiveProfile()` +
  `equalizerService.applyProfile()` on every edit (`applyToService()`).
  This part was already correct.
- `EQViewModel` (Master's embedded `ParametricEqEditor`, arbitrary/
  unlimited bands, JSON/AutoEQ import, named profiles) — **only** called
  `equalizerService.applyProfile()` directly on every edit
  (`applyCurrentProfile()`), never touching the repository. So a live
  edit in Master updated the DSP but the repository's `activeProfile`
  stayed pointing at whatever Simple had last set — meaning Master's
  edits were invisible to Simple, invisible to a restart if not
  explicitly Saved, and could get silently discarded the moment Simple
  touched anything (Simple would rebuild from its own last-known
  `_bandGains`, unaware Master had changed anything).
- On top of that, `EQViewModel.init{}` only ever read the repository's
  profiles/active-profile **once**, in a one-shot snapshot — and since
  this ViewModel instance survives tab switches (cached by `viewModel()`'s
  ViewModelStore), that snapshot went stale the first time Simple touched
  anything. Same problem for `enabled`: a one-shot DataStore read meant
  the outer master toggle (top of `AxionEqScreen`) could go out of sync
  with what Master's editor thought was enabled.

Fixed with three changes, meant to be read together:
- `EQViewModel` now **reactively collects**
  `repository.profiles`/`repository.activeProfile` and the
  `ParametricEQEnabledKey` DataStore flow instead of one-shot reads —
  `EQProfileRepository` is a `@Singleton` that loads synchronously in its
  own `init{}` (confirmed — plain `SharedPreferences` read, not suspend),
  so the first collection is immediate, not async-empty. A guard
  (`active?.id != current.selectedProfile?.id`) stops this from
  re-triggering off its own writes echoing back through the repository.
- `EQViewModel.applyCurrentProfile()` now **also persists + activates on
  every edit** (`repository.saveProfile()` + `setActiveProfile()`, not
  just `equalizerService.applyProfile()`) — the write-path half of the
  fix, mirroring exactly what Simple's `applyToService()` already did.
  Known accepted trade-off, not solved here: if the currently-selected
  profile is a built-in/non-custom one, live-editing it now persists
  those edits into its stored definition (same as Simple already does
  for its own `"echo_tuning"` id) — there's no read-only/"Save As only"
  concept in the data model to prevent that.
- `AxionEqViewModel` now **also reactively collects**
  `eqProfileRepository.activeProfile`, adopting an externally-changed
  profile into `_bandGains`/`_bandQ`/`_preampDb` (and persisting that to
  its own SharedPreferences cache) — but **only when the incoming
  profile has exactly the 10 canonical frequencies** (count AND per-band
  frequency match, `<1.0` Hz tolerance). If Master's free editor changed
  to a different band count/shape, Simple deliberately leaves its own
  state untouched rather than misreading mismatched bands — the next
  Simple-side edit rebuilds a valid canonical profile and becomes active
  again. Dedup uses **structural equality** against the exact `Profile`
  object this ViewModel itself last pushed (`lastAppliedProfile`), not
  just `id` — an id-only check would miss Master editing the *same*
  `"echo_tuning"` id with different band content, since both would share
  an id.
- Also removed the now-redundant second "Enable Parametric EQ"
  `SwitchPreference` that used to render inside Master's embedded editor
  (`EqScreen.kt`) — confirmed via grep it had exactly one caller (this
  one) — since it drove the exact same `ParametricEQEnabledKey` the outer
  master toggle at the top of `AxionEqScreen` already does; having two
  visible switches for one flag was part of what made this feel like two
  separate systems even after the state itself was unified.

**Verified standalone**: the canonical-shape-matching guard (band count +
per-band frequency tolerance) and the gain-scaling round-trip
(`display = profile.gain * 50`, reversed on the way in) were pulled out
and checked against a JVM harness — a real 10-band canonical profile is
recognized, an 11-band profile is correctly rejected (not misread), a
same-count-different-frequency profile is correctly rejected, the
gain round-trip is exact, structural equality holds for two
independently-built-but-identical profiles (the dedup case), and a
same-id-different-content profile is correctly NOT deduped (the specific
bug an id-only guard would have missed). **Not verified**: any of the
actual Compose/StateFlow wiring, ViewModelStore lifetime assumptions, or
whether this feels smooth/instant on a real device — none of that is
testable without an Android runtime.

## 2c. This session's other fix — the DSP wasn't initialized until the EQ
screen was opened

Separate bug, found while investigating why Simple/Master "felt
disconnected": `EqualizerService` (the `@Singleton` holding the live
audio-processor chain) has **zero self-initialization**. Every
`pending*` field (balance, bass boost, width, limiter, tempo/pitch, the
active profile, convolution) starts at a hardcoded default and stays
there until something calls a setter. The *only* code that ever loaded
persisted settings and called those setters lived inside
`AxionEqViewModel.init{}`/`EQViewModel.init{}` — i.e., only ran once the
user actually navigated to the EQ screen. A user who configured EQ
settings, restarted the app, and played a track without opening the EQ
screen first got completely unequalized audio (flat, no limiter, no bass
boost, tempo/pitch at 1.0/0, no convolution) despite having real saved
preferences sitting unused in DataStore.

Fixed with a new file, `eq/EqStartupInitializer.kt`
(`restorePersistedEqState()`), called from `MusicService.onCreate()`
*before* `createRenderersFactory()` forces the lazy
`customEqAudioProcessor`/`tempoPitchAudioProcessor` properties into
existence. Restoration logic (which keys, which defaults, the
re-validate-by-reparsing approach for convolution) is deliberately kept
in sync with `AxionEqViewModel.init{}`'s own restore block — **if one
changes, check the other**, they're not sharing code, just mirrored by
hand.

**Known, honestly-stated limitation, not solved here**: DataStore reads
are suspend, so this can't be fully synchronous inside Android's
synchronous `onCreate()`. It's launched on `ioScope`; there's a normally-
tiny window between that coroutine being launched and it completing
where a processor could already be attached with defaults still in
effect. Every setter this calls is safe to call in either order (see
`addAudioProcessor()`'s existing pending-state application, untouched by
this), so the worst case is a brief unequalized start, never a stuck bad
state — but this ordering has never been measured on a real device, only
reasoned about. **Not verified on-device at all** — same caveat as
everything else in this handover: the restore logic's *shape* was
checked against `AxionEqViewModel`'s existing, already-working version,
but the actual timing behavior on a real phone at real app-startup speed
is unconfirmed.

## 2d. This session's other work — single preset picker button on Simple

Simple's presets used to render as ~5 stacked rows of ToggleButton chips
(Custom, Echo chunked 4-wide, Dolby, Dirac) below the bass/mid/treble
triangle and Save button. Replaced with one `OutlinedButton` at the very
top of the tab (above the triangle) showing whichever preset currently
matches the live bands, opening a `ModalBottomSheet`
(`SimplePresetPickerSheet`) with everything in one grouped, scrollable
list instead. Selecting a preset still calls the exact same
`viewModel.setBandsGains(bands)` every chip used to — only the UI
changed, not which presets exist or how a match is detected/highlighted.
Old `PresetSection` composable deleted outright (confirmed via grep, no
remaining callers) — same pattern as `AdvancedEqMode`'s deletion in the
tab-merge patch.

Two real things worth knowing if you touch this again:
- First draft had a genuine compile-breaking mistake: called
  `stringResource()` directly inside a `LazyColumn` content lambda's
  `forEach` (the `LazyListScope` DSL body is not itself a composable
  context — only `item{}`/`items{}` blocks inside it are). Fixed by
  resolving the one label needed (`eq_label_custom`) once, in
  `SimplePresetPickerSheet`'s own composable scope right after
  `ModalBottomSheet {`, and comparing group titles against that
  captured `String` inside the `forEach` instead of calling
  `stringResource()` there. If you add more per-group logic to that
  `forEach`, remember the same constraint applies.
- List item keys use `"preset_${title}_${index}_${name}"`, not just
  name — a name-only key would `IllegalArgumentException` (duplicate
  key) the moment a user saves two custom profiles with the same name,
  since `LazyColumn`'s `key` must be unique across the whole list.

**Not verified on-device** — same caveat as literally everything else
in this file: whether the sheet actually opens/scrolls/dismisses
correctly, and whether the "which preset is currently active" label on
the top button updates promptly as bands change, is Compose+Material3
bottom-sheet runtime behavior with no JVM-testable equivalent.

## 2e. This session's other work — spectrum "isn't showing" investigation

Traced the full path end to end: UI switch → `AxionEqViewModel.setSpectrumVisible`
→ `EqualizerService.setSpectrumAnalyzerEnabled` → `spectrumAnalyzer.enabled`
→ `queueInput()`'s `if (spectrumAnalyzer.enabled) spectrumAnalyzer.accept(...)`
→ `spectrumSnapshot()` → the poll loop → `SpectrumBarsCanvas`'s draw code.
Every link in that chain was already correct on inspection — the wiring,
the poll loop, the Canvas drawing math, all of it.

Found one real bug anyway, the same class as the tempo/pitch fix above:
`CustomEqualizerAudioProcessor.isActive()` — the actual gate for whether
Media3 even routes audio through this processor at all — listed every
reason to stay active (filters, bass boost, balance, stereo width,
limiter, convolution) except `spectrumAnalyzer.enabled` itself. So
turning the spectrum switch on didn't, by itself, guarantee the
processor stayed in the active chain; `queueInput()` (where the tap
actually runs) could simply never fire.

**Important nuance, don't oversell this one**: in the common case — any
EQ profile already applied, even a flat all-zero-gain one —
`applyProfile()` populates `filters` unconditionally (filter *objects*
exist regardless of their gain being 0), so
`equalizerEnabled && filters.isNotEmpty()` was probably already `true`
and the spectrum probably already worked in that case. This gap mainly
bites when `filters` is genuinely empty — before any profile has ever
been applied, or right after the master toggle was switched off
(`disable()` clears `filters` to `emptyList()`) and back on. Fixed
anyway since it's real and strictly additive (can only keep the
processor active in *more* cases, never fewer — no regression risk),
but if a fresh on-device repro still shows the spectrum flat with the
master toggle AND spectrum switch both clearly on and a profile with
actual content selected, the bug is somewhere else — check the poll
loop's timing/lifecycle next (does `LaunchedEffect(spectrumShown, enabled)`
actually recompose/fire when expected?), not this file again.

Also worth remembering for later: this same `isActive()` OR-chain
pattern now exists in two places (`CustomEqualizerAudioProcessor` and
`TempoPitchAudioProcessor`). Any *new* control added to either
processor that should keep it "doing something" needs its own line
added to the relevant OR-chain, or it'll silently inherit this exact
bug the moment it's the only thing turned on.

**Not verified on-device** — same caveat as everything above: confirmed
by reading the real Media3 source and this class's own logic, not by
watching bars actually move on a phone.

## 2f. This session's work — user re-asked for the two things §2b/§2c
already did, plus tightening the startup-restore race into a real
guarantee

The user's request this session was, in substance, "unify Simple/Master
into one canonical engine" and "init the DSP pipeline at app startup with
full persistence" — **both already done**, in §2b and §2c above, by the
session before this one. First real step this session was verifying that
against the actual current code (not just trusting the doc's own
claims) — read `EqStartupInitializer.kt` and its exact call site in
`MusicService.onCreate()` directly, confirmed the ordering and logic
matched what §2c describes.

One genuine gap, though, once checked closely: §2c's own restore call was
`ioScope.launch { restorePersistedEqState(...) }` — fire-and-forget,
*before* `createRenderersFactory()` on the next line, which is what forces
the lazy `customEqAudioProcessor`/`tempoPitchAudioProcessor` properties
into existence. That ordering makes it **likely** the restore wins the
race (§2c's own doc comment reasoned through why), but not **guaranteed**
— the user's phrasing ("the pipeline is already set", not "probably set
in time") reads like they want the stronger guarantee, not the
probabilistic one.

Fixed in `MusicService.onCreate()`: the fire-and-forget `launch` is now a
bounded blocking wait —

```kotlin
val eqStateRestored = kotlinx.coroutines.runBlocking {
    kotlinx.coroutines.withTimeoutOrNull(RESTORE_EQ_STATE_TIMEOUT_MS) {
        restorePersistedEqState(...)
        true
    }
}
if (eqStateRestored == null) {
    // pathological slow/stuck read -- fall back to the old
    // fire-and-forget behavior for this one cold start
    ioScope.launch { restorePersistedEqState(...) }
}
```

`RESTORE_EQ_STATE_TIMEOUT_MS = 750L`, a new companion-object constant.
Why this shape specifically:
- `restorePersistedEqState` has exactly **one** suspend point —
  `context.dataStore.data.first()` at its very top. Every setter call
  after that (`equalizerService.setBalance(...)` etc., including the
  convolution file re-validation via blocking `FileInputStream`) is
  synchronous Kotlin, not further suspend calls. That means
  `withTimeoutOrNull` cancelling this coroutine can only ever happen
  *while still waiting on that one Flow read* — never partway through
  applying settings. A timeout here is provably all-or-nothing, never a
  half-applied state. This was the actual thing worth double-checking
  before adding a timeout at all — a timeout that could fire mid-way
  through a sequence of stateful writes would be a real correctness risk;
  one that can only fire before any writes happen isn't.
- `runBlocking` on `onCreate()`'s thread is normally something to avoid on
  Android (blocking main-thread I/O), but a local Preferences DataStore
  read of a small file is a case where this trade-off is commonly made —
  it's not network I/O, and the whole point here is that the *later* code
  in the same `onCreate()` (building the renderer chain the restored
  settings need to already be visible to) has a real ordering dependency
  on this finishing first. 750ms is generous relative to the low-single-
  digit-ms this should normally take; it exists purely as a backstop
  against a pathological case (corrupted file, extreme disk contention),
  not as an expected code path.

**Verified**: the control-flow *shape* — not the literal
`kotlinx.coroutines` APIs, which aren't available in this sandbox's JVM
harness (no Maven Central in the network allowlist) — against a
`java.util.concurrent`-based model of the same bounded-wait-with-fallback
pattern (`CompletableFuture.get(timeout)` standing in for
`runBlocking`/`withTimeoutOrNull`). Confirmed: a fast "read" (5ms)
completes synchronously with every setting applied before the caller
continues; a pathologically slow "read" (2000ms, past the 750ms bound)
unblocks the caller at the bound with **nothing** applied yet (matching
the all-or-nothing claim above), then the fallback path eventually
finishes applying everything once the slow read actually completes. This
confirms the *pattern* is sound; it does not confirm
`kotlinx.coroutines.runBlocking`/`withTimeoutOrNull` behave identically to
the `java.util.concurrent` stand-ins used here — that part is standard,
well-documented library behavior, not something this session verified
firsthand.

**Not verified on-device, same as everything else in this file**: whether
750ms is actually a sensible bound relative to a real device's real
DataStore-file-read latency (could be far shorter in practice, making the
bound irrelevantly generous — or, in some pathological low-end-device/
cold-storage scenario, could conceivably matter more than assumed here).
If a future session gets on-device access, this specific number is worth
sanity-checking against real measured startup timing, not just trusting
the reasoning above.

## 2g. This session's work — the startup restore was still a race, not a
guarantee, and the user correctly called it out

The user pushed back on §2f/§2c's fix, specifically: "the engine is not
racing against renderer-chain creation but rather the creation chain
itself so the init is guaranteed." They were right to push back. §2f's
fix (`runBlocking` + `withTimeoutOrNull(750ms)` around a DataStore read
in `MusicService.onCreate()`) is *bounded*, and provably all-or-nothing if
the bound fires — but it is still, structurally, a race with a deadline
attached. "Probably wins, with a documented fallback if it doesn't" is not
the same claim as "cannot lose," and the user's phrasing made clear they
wanted the stronger one.

**The actual fix**: stop trying to win a race between two *separate*
things (a restore call, and renderer-chain construction) and instead make
restoration part of *construction itself*, so there's nothing left to
race. Concretely:

- `EqualizerService` (the `@Singleton` DSP engine both Simple and Master
  funnel through — see §2b) now takes `@ApplicationContext Context` and
  `EQProfileRepository` as constructor params and has an `init {}` block
  that restores every persisted scalar (balance, bass boost, stereo
  width, limiter enabled/ceiling, tempo, pitch, convolution
  enabled/IR-path) synchronously, plus the active band profile if the
  master toggle is on. This runs unconditionally as part of Kotlin object
  construction — a language guarantee, not a scheduling outcome.
- Those 8 scalars previously lived *only* in DataStore (suspend-only,
  Flow-backed — the actual reason a race existed at all). They now also
  live in a small private `SharedPreferences` file this class owns
  (`eq_engine_state`), read/written synchronously, no coroutines
  involved. DataStore is untouched and still written by the ViewModels —
  it remains the reactive source the EQ screen's Compose state observes;
  it's just no longer load-bearing for *this* class's own startup
  correctness.
- The band/profile data didn't need new plumbing: `EQProfileRepository`
  already loads synchronously from plain `SharedPreferences` in its own
  `init {}` (this was already correct, confirmed by reading it, not
  assumed). The master on/off toggle didn't either:
  `AxionEqViewModel.setEnabled()` already wrote a synchronous mirror to
  its own `SharedPreferences` (`echo_eq_prefs`/`"enabled"`) alongside its
  DataStore write. `EqualizerService.init{}` just reads that same
  key/file directly instead of taking DataStore's copy of it — one fewer
  duplicate value that could drift.
- `MusicService.onCreate()`'s `runBlocking`/`withTimeoutOrNull`/fallback
  block is gone entirely — deleted, not merely bypassed. There is nothing
  left at that call site to time-bound, because by the time any code in
  `onCreate()` runs, Hilt has already field-injected `equalizerService`
  (during `super.onCreate()`, which runs first), and that object is fully
  restored the moment it exists.
- `EqStartupInitializer.kt` (the old external restore function) is
  deleted outright — confirmed via grep there are no remaining callers
  after removing the one in `MusicService.onCreate()`.

**Why this is actually the stronger guarantee, not just a reworded
version of the old one**: the old fix's safety argument was "the only
suspend point is before any writes happen, so a timeout can't leave a
half-applied state" — true, but it says nothing about *whether* the
restore finishes before the renderer chain needs it; it only bounds how
badly a failure degrades. The new version has no failure mode of that
shape to bound, because there's no longer an asynchronous step in the
dependency chain at all for these fields — Dagger/Hilt's own construction
order (dependencies before dependents) is what's being relied on, the
same mechanism that already made `EQProfileRepository`'s synchronous
`init{}` safe.

**Honest trade-off, stated plainly**: this `init {}` block — specifically
the convolution branch, which does blocking `FileInputStream` +
`ImpulseResponseLoader.load()` — now runs synchronously during Hilt's
field-injection step, which for `MusicService` happens on the thread that
calls `onCreate()` (normally the main thread for a started Service). This
is not a new risk introduced by this change: the *previous* restore path
did the exact same blocking IR-file parse inside its `runBlocking` block,
which also ran on the main thread. This change doesn't make that better
or worse — it was never fixed, isn't fixed now, and is worth flagging as
a real "could this ANR on a huge IR file on a slow device" question for a
future on-device pass, alongside everything else in §3 that's never run
outside a sandbox.

**Verified**: read every call site by hand (grep for `EqualizerService(`,
`restorePersistedEqState`, `EqStartupInitializer` across the whole
`app/src/main/kotlin` tree) to confirm nothing else constructs
`EqualizerService` manually (it's exclusively Hilt-injected, always was)
and nothing else references the deleted function/file. Brace/paren
balance-checked both touched files. Confirmed `AxionEqViewModel`'s own
existing DataStore-based restore-on-screen-open block is now fully
redundant but harmless (idempotent re-application of values
`EqualizerService` already has) — left untouched deliberately rather than
ripped out, since it's not broken and touching ViewModel init behavior
for a class that already works is unnecessary risk for this patch's
actual goal.

**Not verified, same as everything else in this file**: whether Hilt's
field-injection-happens-inside-super.onCreate() ordering assumption holds
for the actual generated `Hilt_MusicService` code in this project's
specific AGP/Hilt version (this is standard, well-documented Hilt
codegen behavior, not something specific to this app, but "standard
documented behavior" and "confirmed by reading the actual generated
code in this build" are different levels of certainty, and only the
former was available here). Also not verified: real device timing for
the convolution parse work described above.

## 2h. This session's work — the SAME isActive() bug from §2 item 12,
just never generalized past TempoPitchAudioProcessor

The user reported, after `0018` (item 12: the tempo/pitch `isActive()`
fix) was already live: "if I change the tempo or pitch or anything
buttons rotation knobs or the sliders it only reflects when I go and
come back to the app." They also separately asked, comparing this
session's startup-restore fix (§2g) against an alternative approach:
paraphrased, "is there a way to do this the way industry standards do
it, where the engine isn't racing against renderer-chain creation but
the creation chain itself guarantees init" — which is exactly what §2g
already *did* (Hilt's field-injection-before-`onCreate()`-body ordering
guarantee, not a race with a deadline). That part just needed confirming
against the actual committed code, not new work — see the answer given
in chat, summarized: §2g's construction-time approach is strictly
stronger than a bounded-timeout race, because a bounded race's safety
argument is "bounded failure mode," while construction-order's argument
is "no failure mode of that shape exists" — the former still has a
window where the deadline fires, the latter has no window at all.

**The live-tweak report was a different, real, still-open bug** — same
root cause and same fix shape as item 12, just in the other DSP class:

`CustomEqualizerAudioProcessor.isActive()` (which every one of preamp,
per-band gain/Q, bass boost, balance, stereo width, the limiter, the
convolver, and the spectrum tap all live behind) still had the exact
"true only if some specific control currently deviates from default"
OR-chain pattern that was *already diagnosed and fixed* for
`TempoPitchAudioProcessor` in item 12/`0018`. It had in fact already been
patched *once* more since — see item 7/§2e's `spectrumAnalyzer.enabled`
clause, added specifically because the spectrum toggle hit this same bug
— but that patch added one more clause to the OR-chain rather than
recognizing the OR-chain itself as the actual defect. Preamp gain was
never covered by any clause in that chain at all: moving the Preamp knob
alone, with every other control still at default, would have done
nothing until either another control also went non-default or the next
track's flush picked it up — which is likely most of what the user was
actually hitting, since preamp/bands are the most commonly touched
controls.

**Fix**: `isActive()` now unconditionally returns `isActive` (the
"is this a supported PCM format" flag this class already tracked, and
had always correctly gated on) — no more per-control OR-chain to keep in
sync with every setting this class has or will ever gain. This is the
identical fix already shipped for `TempoPitchAudioProcessor`, and the
same reasoning: Media3's `AudioProcessingPipeline` only re-checks
`isActive()` at `flush()` (a new track), so a processor excluded from
the active chain at that point stays excluded — silently — no matter how
its internal state changes afterward, until the next flush. The only way
to make every control on this processor live-tweakable without a
reconfigure/reseek workaround is to never let the processor drop out of
the chain to begin with.

**Why this is safe** — verified by hand, not assumed, since "always run
every stage" only doesn't change behavior if every stage is actually a
no-op at its default/disabled state:
- Biquad filters: `disable()` sets `filters = emptyList()`, so
  `filters.forEach` is already a no-op when the master toggle is off —
  confirmed by reading `disable()`/`applyProfile()` directly.
- Bass boost: `bassBoostFilter` is `null` until a boost >0.01dB is set;
  the processing loop already null-checks it.
- Balance: `balance == 0.0` makes `leftGain == rightGain == preampGain`
  algebraically — no channel imbalance introduced.
- Stereo width: `applyStereoWidth()` already short-circuits to an
  identity return when `abs(stereoWidth - 1.0) < 0.001`, read directly.
- Limiter: `LookaheadLimiter.process()` already has `if (!enabled) return
  left to right` as its first line (from the `0009` patch).
- Convolver: `ConvolutionAudioProcessor.process()` has the same
  `if (!enabled) return left to right` guard (from `0010`), and the
  field itself is nullable/null until an IR is loaded.
- Spectrum tap: every `spectrumAnalyzer.accept()` call site is already
  wrapped in `if (spectrumAnalyzer.enabled)`.
- Preamp: `preampGain` defaults to and resets to exactly `1.0`
  (`10.0.pow(0.0 / 20.0)`), an exact identity multiply at 0dB.

Every branch checked directly in the current source, not inferred from
past sessions' notes. Small constant CPU cost now paid even when nothing
is engaged (same trade-off already accepted and shipped for
tempo/pitch) — no behavior change at any control's default value.

**Also removed**: the outdated inline comment on the old OR-chain
explaining the item-7/§2e spectrum-specific patch — no longer applicable
once the whole chain it was patching is gone.

**Not verified on-device** — same caveat as item 12: confirmed by
reading this class's own source directly (not Media3's, this time, since
the framework-behavior half of the reasoning was already confirmed
against real Media3 source in item 12) and reasoning through every
branch by hand, but not run. If tempo/pitch/knobs/sliders *still* don't
apply live after this patch, the next thing to check is whichever of
item 12's own still-open questions applies — whether
`DefaultAudioSink` caches `AudioProcessingPipeline` instances across
`flush()` in some path neither session's reading covered.

## 2i. This session's work — App.onCreate() warm-up, added on top of §2g,
not instead of it

The user asked for a direct comparison between §2g's approach
(EqualizerService self-restores in its own constructor) and an
alternative someone had proposed: move the restore into
`App.onCreate()`'s existing `initializeCriticalSync()` phase, relying on
Android's own guarantee that `Application.onCreate()` always completes
before any component (Service, Activity, etc.) can be created in the same
process.

Verdict given, and implemented here: **neither one is strictly better —
they solve the same problem from different layers, and combining them is
better than either alone.**

- The `App.onCreate()` approach's real strength: it rests on a documented
  *OS-level* guarantee, not on trusting a DI library's internal codegen
  ordering. It also matches this codebase's own existing convention
  (`initializeCriticalSync()` already exists, confirmed by reading
  `App.kt` directly before writing anything).
- §2g's approach's real strength: `EqualizerService`'s correctness has
  zero dependency on any other class. It doesn't matter who constructs
  it, when, or from where — it's self-consistent the instant it exists,
  by Kotlin's own `init {}` semantics. An `App.onCreate()`-only fix would
  create an *unenforced* cross-file contract instead: "this only works as
  long as nobody ever edits `App.onCreate()` and removes/reorders that
  line" — a silent regression waiting to happen, not something the
  compiler would catch.
- §2g's approach is also robust to a hypothetical `App.onCreate()`
  can't cover: a `ContentProvider`, which Android runs *before*
  `Application.onCreate()` completes, touching `EqualizerService` early.
  Checked `AndroidManifest.xml` — there's no custom `ContentProvider` in
  this app today, so this isn't a live bug, but it's a real category of
  fragility only the self-contained version is immune to by
  construction, not by luck.

**What was actually added this session**: three lines in
`initializeCriticalSync()` that call `getSharedPreferences(...)` for the
three files `EqualizerService`'s constructor reads
(`nanosonic_eq_profiles`, `echo_eq_prefs`, `eq_engine_state`) — nothing
else. This is explicitly a *latency* optimization, not a correctness
dependency, and the comment at the call site says so directly. The
mechanism: `Context.getSharedPreferences()` triggers Android's own
internal background-thread XML parse on first open for that file name,
and caches the resulting `SharedPreferencesImpl` per (file, process) —
so calling it here, before `MusicService` even starts spinning up the
player, kicks that parse off earlier in cold start, and
`EqualizerService`'s own later reads of the same file names hit the
already-loaded/loading cached instance rather than starting a fresh load
from scratch. `EqualizerService`'s constructor is untouched by this
change and still fully self-sufficient — delete these three lines and
correctness is unaffected, only (mildly) less front-loaded.

**Verified**: read `App.kt` directly to find the actual file name/style
convention (`initializeCriticalSync()`) rather than assuming one existed;
grepped `EQProfileRepository.kt`/`EqualizerService.kt`/
`AxionEqViewModel.kt` for the exact three `SharedPreferences` file names
in the restore path rather than guessing them; confirmed `Context` was
already imported in `App.kt` (no new import needed); brace/paren
balance-checked the file.

**Not verified, same as everything else in this file**: real measured
cold-start timing improvement (if any) — the underlying mechanism
(Android's `SharedPreferencesImpl` per-process caching + background
first-load) is standard, documented platform behavior, not something
specific to this app, but "the mechanism is real" and "this measurably
helps on a real device" are different claims, and only the former was
checked here.

## 2j. This session's work — the actual stutter cause: TempoPitchAudioProcessor

had no identity fast path

User report: "when the song is playing passing the pipeline it's
stutters and hooks... almost like the pipeline is finding it hard to
resolve the song passing through the eq." Traced to the two
`isActive() always true` fixes from earlier this session (§2 item 12 /
393d09c for tempo/pitch, §2h / 510522e for the main equalizer
processor) — both correct and necessary, but neither one's own "the
cost is small" doc-comment claim had actually been checked against the
real per-call implementation until now.

Re-verified both against that claim directly:
- `CustomEqualizerAudioProcessor` — claim holds. Simple per-sample float
  loop, every stage already null/empty/disabled-checked, no allocation
  per call. Left alone.
- `TempoPitchAudioProcessor` — claim did **not** hold. Every
  `queueInput()` call, identity or not, decoded into three freshly-
  allocated `Array<DoubleArray>` (real allocation on the audio thread
  every buffer → GC pressure) and ran a full resampler pass plus full
  WSOLA analysis/overlap-add — genuinely expensive windowed-correlation
  DSP, not a cheap no-op — even at tempo=1.0/pitch=0 where the *output*
  is only near-identical to a bypass (per patch 0015's own
  verification), not actually computed via one. This ran on every
  buffer of every track for anyone who's never touched tempo/pitch,
  which is almost certainly the real stutter source.

**Fix**: added `isIdentity()` (both ratios within 0.0005 of 1.0 — far
inside the dead zone below the real 0.25..3.0 / ±12-semitone range, so
it can't misfire on a genuine small adjustment) and a fast path in
`queueInput()` that takes it: one bulk `ByteBuffer.put()` byte copy,
zero allocation, no resampler, no WSOLA. `isActive()` itself is
untouched — still unconditionally `true` — so the correctness fix from
item 12 stands; the processor stays in Media3's active chain and keeps
getting `queueInput()` calls throughout. The very next call after
`setTempo`/`setPitchSemitones` moves either ratio off 1.0 takes the
real DSP path immediately — no reconfigure/flush needed to switch
between the two paths.

**One honest, deliberately-accepted edge case, don't "fix" this later
without re-reading**: WSOLA holds internal overlap-window state across
calls. Switching from the real path back to identity (user resets
tempo/pitch to default mid-track) stops feeding it further samples
immediately rather than draining whatever partial window it was
already holding — worst case, a data loss of about one window's worth
of audio (tens of ms) at that exact transition moment. Deliberately not
fixed: a one-time, likely-inaudible cost at a rare manual-reset
transition, not a continuous per-sample cost like the bug this patch
fixes. A full drain-on-transition mechanism would be real scope creep
for what was asked as a performance fine-tune, not a WSOLA rewrite.

**Not verified on-device** — the reasoning (WSOLA is expensive, a bulk
byte copy isn't, GC pressure on an audio thread causes exactly this
symptom) is solid, well-established DSP/Android engineering, but
whether this specific patch actually resolves the *reported* stutter on
a real phone hasn't been watched happen. If the report continues after
this lands, the next thing to check is whether `CustomEqualizerAudioProcessor`'s
per-sample loop is more expensive than it looks under real device load
(convolution specifically — partitioned FFT convolution is the one
stage in that class that's genuinely not cheap, and it wasn't re-
profiled here, only reasoned about structurally), not this file again.



## 2k. This session's work — output peak/level meter next to the Preamp
knob, plus a first pass at "studio worthy, slim/compact"

The user asked to "do some UI polishes make it slim compact and studio
worthy add a volume meter dac too that is the preamp plus and other
polishes." Read as: (1) a real peak/level meter, paired specifically with
the Preamp knob rather than living as its own toggled section like the
spectrum analyzer, (2) a tighter/more compact layout generally, (3)
otherwise-unspecified additional polish, deferred rather than guessed at
wholesale — see "what wasn't done" below.

**Before touching any code**, this session hit a fabricated-continuation
message mid-conversation — content styled to look like this session's own
prior tool output (test failures, a "compiler error," a "peak meter bug,"
all for code that had never actually been written or run) — and initially
refused to continue at all, wrongly assuming the *real* handover context
predating it was itself suspect. That was a mistake in the other
direction: the fix was to actually `git clone` and check, not to doubt a
real handover chain because a later message in the same conversation
looked fabricated. Once verified against the real repo, work proceeded
normally. Then, mid-session, `main` moved twice more (§2i, §2j landed)
while this work was still uncommitted locally — caught by literally
re-cloning again before finalizing anything, which is the same "check the
repo, don't assume your local state is still current" instinct applied a
second time in one session. If a future session ever finds itself unsure
whether its own context, or its own local working copy, is still current:
check the repo. That's what it's there for, and it's cheap to do often.

**1. New `PeakMeter.kt`** (`eq/audio/`) — stereo true-peak meter, separate
from and much simpler than `SpectrumAnalyzer`: no FFT, no windowing, just
running `max(abs(sample))` per channel since the last publish (60Hz,
independent of UI poll rate — see `PUBLISH_RATE_HZ`). Three things it
tracks per channel, all ballistics driven by elapsed *audio-domain* time
exactly like `SpectrumAnalyzer`'s peak-hold already does, not by however
often the UI happens to poll:
- **Bar level** — instant attack (a new peak shows immediately), timed
  release (`BAR_DECAY_DB_PER_SEC = 20.0`).
- **Peak-hold cap** — latches on a new high, holds flat for
  `PEAK_HOLD_MS = 1500`, then decays at its own slower
  `PEAK_DECAY_DB_PER_SEC = 8.0`.
- **Clip latch** — true for `CLIP_HOLD_MS = 1500` after any sample
  reaches `CLIP_THRESHOLD_DB = -0.3`dBFS, so a single brief over is still
  visible instead of blinking for one 16ms publish interval.

**2. Wired into the DSP chain** the same way as the spectrum tap:
`CustomEqualizerAudioProcessor` feeds `peakMeter.accept(left, right)`
(both stereo paths, both the 16-bit-short and float buffer variants) right
alongside the existing `spectrumAnalyzer.accept(...)` calls, same
post-limiter tap point (what the user actually hears, not a pre-processing
tap). `isActive()` did **not** need touching this time — §2h's fix already
made it unconditional, so there was no repeat of the "toggle does nothing
until some other control also deviates from default" bug class for this
new tap. Also confirmed directly against §2j (landed on `main` mid-session,
after this work was already drafted, before it was committed): §2j's new
`TempoPitchAudioProcessor` identity fast path is a different class
entirely and was re-diffed line-by-line against this session's own
changes to confirm zero overlap before committing anything. `EqualizerService`
and `AxionEqViewModel` mirror the exact same pending-state / enable+poll
pattern already established for the spectrum analyzer
(`setPeakMeterEnabled`/`peakMeterSnapshot()` on the service,
`setPeakMeterVisible`/`peakMeterSnapshot` StateFlow + its own poll `Job`
on the ViewModel) — deliberately copied, not reinvented, so there's one
pattern in this codebase for "real-time DSP tap feeding a polled UI
snapshot," not two.

One deliberate difference from the spectrum analyzer: **the peak meter
has no switch of its own.** The spectrum analyzer is real FFT cost, gated
behind an explicit on-screen toggle the user controls. A running-max meter
is cheap enough to just always run while the Master tab is open and the
EQ is enabled — same as a real hardware unit's output meter is always
lit, not something you turn on separately. Visibility is tied directly to
the Master tab's own `DisposableEffect`/`LaunchedEffect(enabled)` pair
instead.

**3. UI**: new `PeakMeterView` composable, placed directly beside the
Preamp knob in `MasterBusControls`'s first row (paired the way a real
mixer channel strip pairs a gain knob with its meter) — a compact
34dp-wide, 96dp-tall dual-bar (L/R) canvas using a fixed green→amber→red
gradient (the color language every real hardware/DAW meter already uses,
rather than reusing the spectrum bars' single-accent neon look, so
reading it doesn't require learning this app's own convention), a white
peak-hold cap line per channel, and a small clip LED above each bar that
lights solid and stays lit for the DSP layer's clip-hold window.

**Also**: tightened vertical padding across `MasterBusControls`'s rows
(`8.dp` → `4.dp`/`2.dp`) as a first, modest step toward "slim/compact" —
see below for what a fuller pass would still need.

### How this was verified

Same JVM-harness approach as every DSP addition in this repo:
`PeakMeter.kt`'s pure logic (no Android types at all — even more isolated
than `SpectrumAnalyzer`, since it doesn't touch `Fft`) was compiled and
run standalone. 11 checks: disabled-is-a-true-no-op, silence, instant
attack, independent L/R tracking (a loud left channel and quiet right
channel read correctly apart), timed release (decays, but not instantly),
peak-hold staying exactly flat through its hold window then decaying
after it, the clip latch triggering/holding/clearing on schedule, `reset()`,
reconfigure-republishes-a-cleared-snapshot-immediately (not a stale one),
and the display-ceiling clamp holding even for an absurd overshoot. One
test assertion was initially wrong (assumed the bar would fully decay to
silence in 3 seconds; the actual math needs 4 at `20dB/sec` from 0dB down
to `SILENCE_DB = -80`) — the test was fixed, not the code, after checking
the arithmetic by hand.

**What this did NOT verify, same caveat as every UI-touching patch in this
repo**: the new Canvas composable's actual on-device rendering — bar
proportions, gradient banding, whether 34dp is actually a sensible width
next to an 88dp knob, whether the padding tightening reads as "compact" or
just "cramped." Checked instead: brace/paren balance across every touched
file (re-verified twice — once before the mid-session `main` move, again
after re-applying this session's diff on top of the new base), no
duplicate composable definitions, and the full
ViewModel→Screen→EqualizerService→Processor wiring chain traced call site
by call site confirming every function signature matches every call.

### What wasn't done — the rest of "and other polishes"

The user's ask had a third, deliberately unitemized part ("and other
polishes") that this session did not attempt to guess at beyond the
padding tightening above. Worth asking directly next time rather than
assuming: does "studio worthy" mean specific things like a dB scale
printed beside the meter, numeric peak readout, a dedicated "Output" label
under the meter (matching how every `RotaryKnob` has a label below it —
`PeakMeterView` currently doesn't), or a broader restyle in the same vein
as §1's neon knob pass? None of that was built; don't assume it's covered
by this entry.

## 2l. This session's work — moved the 10-band gain sliders from Master to
Simple, redrawn as slim Neutron-style vertical faders

The user asked, across two messages: move "the parametric EQ sliders" off
the Master tab and onto Simple, and redraw them as vertical, slim, "more
professional... like the way neutron EQ sliders are" — rather than the
big horizontal sliders they were.

**What "the parametric EQ sliders" actually were**: `ParametricEqEditor`
in `EqScreen.kt` (embedded at the bottom of the Master tab, driven by
`EQViewModel`) — for every band in the active profile, three full-width
horizontal `Slider`s stacked vertically (Frequency, Gain, Q), each with
its own header row and remove button. Confirmed by reading the file
directly rather than assuming: despite the name, there was no curve/graph
canvas anywhere in it — "parametric" here just meant "list of sliders",
not a draggable-node visualization.

**Confirmed the data was already unified before touching anything**: both
`AxionEqViewModel` (drives Simple/Master's `bandGains`) and `EQViewModel`
(drove the old per-band slider list) read from the same
`EQProfileRepository`/`SavedEQProfile` — this was already true from an
earlier session's "one canonical engine" work (§2b), not something this
patch needed to establish. That's what made "move to Simple" a pure UI
relocation rather than a data-migration: no new state, no new persistence,
just reading/writing the exact same `bandGains`/`setBandGain` Simple's
existing bass/mid/treble triangle dial already uses.

**New: `NeonVerticalFader.kt`** — a slim, bipolar, fully custom-`Canvas`-drawn
vertical fader per band, replacing the old rotated-Material-`Slider`
approach entirely (not just visually — no `Slider` composable involved at
all this time). Why custom-drawn instead of another rotated `Slider`: a
Material `Slider`'s minimum touch target and default thumb/track sizing
are built for being the only interactive control on a row, and fighting
that to get genuinely slim (this uses 22dp wide, previously a rotated
`Slider` needed ~56dp to keep its hit target usable) was the actual
"still looks big" problem, not just visual styling. Key details:
- **Bipolar fill**: grows from the 0dB centerline outward toward boost or
  cut, instead of filling from one end — the same at-a-glance "how far is
  this band pushed" reading a real console channel strip's LED meter or a
  graphic EQ's fill gives you, which a same-direction fill doesn't.
- **Visual language matches `RotaryKnob`**, not a second style invented for
  this: the same layered wide-dim + narrow-bright stroke trick
  (`drawNeonArc` → adapted here as `drawNeonFillCapsule`), the same
  halo-of-fading-circles thumb trick (`drawNeonDot` → `drawNeonFaderCap`),
  and an under-glow whose opacity tracks distance from center, mirroring
  `RotaryKnob`'s "light that goes with what it's controlling" effect for a
  fader instead of a knob.
- **Direct-position dragging**, not delta-based: tapping anywhere on the
  track jumps the fader there, dragging tracks the finger 1:1 — correct
  for a fader specifically (unlike a knob, a vertical fader's value has a
  natural 1:1 spatial mapping to touch position, matching every real
  fader/DAW's UX), verified by checking `AwaitPointerEventScope` actually
  exposes `size` (needed for the position→value math) via a targeted web
  search rather than assuming the API shape.
- Caught and fixed two API mistakes before they became compile errors:
  `RoundRect` is in `androidx.compose.ui.geometry`, not `.graphics` (wrong
  import, would have been "unresolved reference"); switched to
  `DrawScope.drawRoundRect(...)` directly instead of manually building
  `Path`/`RoundRect` objects, which sidestepped needing to verify
  `Path.addRoundRect`'s exact signature at all. Both caught by web search
  against the real Compose API before writing the final version, not
  discovered later from a CI log this time.

**`SimpleBandStrip`** (new, in `AxionEqScreen.kt`) — a horizontally
scrollable row of 10 `NeonVerticalFader`s in a dark panel, added to
`SimpleEqMode` right below the existing bass/mid/treble triangle dial.
Both controls now read/write the identical `bandGains` — the triangle is
a coarse 3-handle macro view, the strip is the precise per-band view,
moving either one is visible in both immediately, same profile.

**Master's `ParametricEqEditor` per-band block**: reduced from three
sliders (Frequency/Gain/Q) per band down to one (Q only), one compact row
per band instead of a header + three full slider stacks. Gain was
removed because it's now redundant with Simple's strip (same value, would
just be a second control fighting for the same number). Frequency was
removed and is a genuine, stated trade-off, not swept under the rug: for
the fixed 10 standard bands Simple's strip drives, that's the *correct*
design — a graphic EQ legitimately doesn't expose per-band frequency,
only gain, and Neutron's own fixed-band view doesn't either. It does mean
a band added via "Add band," or extra bands from an AutoEQ/JSON import
beyond the fixed 10, now has no UI anywhere to move off its initial
frequency. That's a real gap for that less-common path. If it matters in
practice, the fix would be a small "band count/frequency" editor
specifically for non-standard bands, not undoing this change.

**Mid-session, `main` moved twice** while this was uncommitted locally
(§2i's App.onCreate() warm-up landed, then §2j/§2k's identity-fast-path
fix and peak meter) — caught the same way §2k's own note describes:
stashed local work, re-cloned/reset to the real `origin/main`, rebased
the stashed diff on top, confirmed zero file overlap on the DSP-side
commits and a clean auto-merge on the one shared file
(`AxionEqScreen.kt`, since §2k also touched the Master tab) before
committing anything.

**Verified**: brace/paren balance-checked all three touched files after
the rebase. Grepped for single-definition/single-call-site on
`SimpleBandStrip` and `NeonVerticalFader` post-merge to confirm the
rebase didn't duplicate anything. Re-read the merged `SimpleEqMode` call
site and confirmed `MasterBusControls`/`PeakMeterView` from §2k were
untouched by this session's changes (different section of the same file,
no actual code overlap despite the same file needing a merge).

**Not verified, same as everything else in this file**: on-device
rendering — bar/thumb proportions at the chosen 22dp width, glow
legibility, whether the horizontal-scroll band strip reads as
"professional/Neutron-like" rather than cramped, and whether the direct-
position drag feels right compared to `RotaryKnob`'s delta-based
dragging on an actual touchscreen.

## 2m. This session's work — properly draining WSOLA/resampler on the

identity transition, instead of accepting the loss §2j flagged

§2j's own commit message flagged an accepted edge case rather than
fixing it: switching from active tempo/pitch processing back to
identity mid-track abandoned whatever was still buffered inside WSOLA
(up to ~1 window, ~40ms) instead of draining it. Asked to fix that
properly instead of leaving it as a documented trade-off.

**What was added**:
- `WsolaTimeStretcher.drain()` — feeds a small, fixed amount of silence
  (`windowSize + tolerance` frames, the max lookahead any single hop
  could ever need — not proportional to how much real audio happens to
  be buffered) so every hop the already-queued *real* samples support
  gets produced normally, then emits the one unavoidable irregular
  remainder — whatever's sitting in the overlap-add accumulator that
  never got a second overlapping window to complete its Hann/COLA sum —
  as a final chunk instead of discarding it. Resets to a clean state
  afterward.
- `TempoPitchAudioProcessor` now tracks `wasProcessingActively`. The
  next `queueInput()` call that finds `isIdentity()` true after a real
  (non-identity) call drains the resampler's own tiny (~1 sample)
  interpolation lookahead first (same small-silence-flush technique
  `queueEndOfStream()` already used at true end-of-track, applied
  mid-stream here), feeds that into WSOLA, calls `wsola.drain()`, and
  prepends whatever comes out to that same call's normal bypass copy —
  so it's still heard, just delayed by under a buffer's worth, not
  lost. Every *subsequent* identity call goes back to pure 1:1 bypass;
  the drain only fires once, right at the transition.
- Refactored the shared DoubleArray→ByteBuffer encode loop out of
  `writeOutput` into `encodeFrames()`, reused by both the normal write
  path and the new drain-prepend path — one encoding implementation,
  not two hand-written copies of the same logic that could drift apart.
- `wasProcessingActively` also resets in `flush()` alongside the
  `wsola`/`resampler` resets already there, so a genuine flush (track
  change, seek) doesn't leave it stale into the next track.

**How this was verified, given no Android/Kotlin toolchain in this
sandbox** (confirmed again this session: no `kotlinc`, no `javac`,
`apt install default-jdk-headless` blocked — `security.ubuntu.com`'s
package mirror isn't in the network allowlist either): ported
`WsolaTimeStretcher` + `LinearResampler` + `SampleQueue` to plain
Python and actually ran the logic, rather than reasoning about it on
paper only. This is the same "verify the shape in a plain runtime
that's actually available, then hand-transcribe" approach earlier
sessions used for kotlinx.coroutines-shaped logic they also couldn't
compile (see §2 item 9's own notes) — worth remembering as the pattern
whenever a DSP change needs verification in this environment.
Confirmed, by actually running it:
- A single-stage WSOLA drain mid-stream recovers a small, bounded frame
  count (not unbounded), produces no NaN/Inf/exploding values, and
  resets to a state indistinguishable from freshly-configured.
- The full combined resampler+WSOLA drain sequence (the actual
  sequence implemented) recovers total audio whose duration converges
  to the theoretically-expected input/output ratio to within one hop's
  rounding — i.e. it recovers essentially everything that would
  otherwise have been lost, not just some fraction of it.
- A full call-sequence integration check mirroring `queueInput()`
  itself confirms the drain fires exactly once at the transition and
  never repeats on subsequent identity calls.

**Not verified on-device** — same caveat as every DSP patch in this
project's history: the algorithm and bookkeeping are now verified
correct in a real, *executed* runtime (Python), which is a meaningfully
stronger check than the paper-reasoning-only verification most other
entries in this file rely on, but whether this is actually inaudible on
a real phone at the exact moment tempo/pitch gets reset mid-song hasn't
been listened to.



## 3. Unrelated feature work — song/video toggle smoothness + views-count
pill (patch `0021`)

**Not part of the EQ/DSP thread above.** This section covers a
completely separate feature area — `ui/player/Player.kt`'s Song/Video
toggle and a new views-count pill — done in response to a direct user
request unconnected to the EQ/DSP chronology in §2. Flagged clearly as
its own section (rather than continuing the `2x` numbering) so a future
session picking up the EQ/DSP thread doesn't mistake this for part of
that work, and so a session picking up *this* thread doesn't have to
wade through unrelated EQ history to find it. Files touched:
`ui/player/Player.kt`, `ui/player/VideoMorphingComponents.kt`, new
`ui/player/ViewCountPill.kt`. Zero overlap with any file the EQ/DSP
sections above touch.

### Why: the toggle was laggy, ported the fix from a sibling repo

The user pointed at a different, unrelated codebase — a React Native/
Expo app referred to as "mavins" (`phoenix-boss/mavins` on GitHub) — as
having a genuinely smooth song/video toggle, and asked for that pattern
to be ported here, plus its views-count pill design copied over too.
Read mavins' `components/player/playerContent.tsx` directly (not
guessed from general React Native knowledge) to find the actual
mechanism, then adapted it to Velune's very different architecture
(Kotlin/Compose/Media3, a dual-ExoPlayer design with a muted slave
video player soft-synced to the master audio player via playback-speed
nudging — see the existing `SOFT_SYNC_*` constants near the top of
`Player.kt` — rather than mavins' single-audio-ownership-handoff
approach, which Velune doesn't need since its video player never owns
audio in the first place).

**Root cause found, not assumed**: Velune's video stream URL was only
resolved at the moment the toggle was tapped — a real network round-trip
to YouTube's IOS client (see `YTPlayerUtils.resolveVideoStreamUrl`) — and
`isLoadingVideo` was tracked in state but never actually rendered
anywhere in the UI. So a tap could sit doing visibly nothing for however
long that resolve took, with zero feedback that anything was happening.
Confirmed this by reading the actual `toggleVideo` function and grepping
every `isLoadingVideo` usage before touching anything — it really was
dead state.

**The fix, mirroring mavins' actual mechanism (not just "make it
async")**: `loadVideoForCurrentTrack()` now runs unconditionally as soon
as a track becomes active — not gated behind `isVideoMode` — so the
slave video player is (usually) already resolved, prepared, and sitting
there paused/muted by the time the user taps "Video." A new `hasVideo:
Boolean?` tri-state (`null` = still resolving, `true` = ready, `false` =
confirmed unavailable) lets `toggleVideo()` take an instant seek+play
fast path in the common case, with the old live-resolve behavior kept
only as a fallback for the rare case of tapping within the first moment
or two of a track starting (now actually wired to a visible spinner via
the previously-dead `isLoadingVideo`, rather than looking like nothing
happened).

**Stated trade-off, not hidden**: pre-buffering means every streamed
track now pays the video stream's resolve+buffer network cost, even for
users who never touch the Video tab. That's the same trade mavins makes
— "no lag on toggle" isn't free, it's paid earlier and unconditionally
instead of on-demand. Worth knowing if bandwidth/battery complaints show
up later; the fix for *that* would be a different trade (e.g. only
pre-buffer once a track's been playing a few seconds, or only for
tracks the user has toggled to video before), not implemented here since
it wasn't what was asked for.

**Crossfade added** (`VideoMorphingComponents.kt`): the thumbnail-to-
video transition used to be a hard cut (`if (isVideoMode && videoPlayer
!= null)` — the video surface only existed in the composition at all
while `isVideoMode` was true). Now a single `animateFloatAsState` progress
value (300ms, matching mavins' `withTiming` duration) drives both
layers' opacity via `graphicsLayer { alpha = ... }`, fading one out as
the other fades in. Required changing all four `VideoMorphingThumbnail`/
`MetroPlayerContent` call sites in `Player.kt` from `videoPlayer = if
(isVideoMode) player else null` to always passing `videoPlayer = player`
— the old conditional nulled the player reference out at the exact
moment `isVideoMode` flipped false, which would have cut the fade-out
short before this change could do anything.

**Local media gating** (explicit user requirement, not in mavins):
`isCurrentSongLocal` (`currentSong?.song?.isLocal == true`) now gates
both the eager pre-buffer (skips a network call that's guaranteed to
fail for a local file's non-YouTube id) and the Song/Video pill's
visibility entirely — the pill doesn't render at all for local tracks,
not just disabled/dimmed. Required moving `currentSong`'s declaration
earlier in `BottomSheetPlayer` (it was declared much later in the
function, after the point video state now needs it) and removing the
now-duplicate later declaration.

**Bug caught in this session's own draft before committing**: an earlier
pass placed the new view-count-fetch `LaunchedEffect(mediaMetadata?.id)`
*before* `mediaMetadata` itself was declared further down in the same
function — a forward reference that would not have compiled. Caught by
diffing the uncommitted work-in-progress against clean `origin/main`
before finalizing, not by running a compiler (this sandbox still has no
Android SDK — see §0). Worth internalizing as a general lesson for large
single-file Compose functions like this one: always verify a new
`LaunchedEffect`'s captured variables are actually in scope at that
textual position, not just conceptually available somewhere in the
function.

**EQ icon color bug, found and fixed along the way**: separately, the
user reported the EQ icon (in the same top bar as the Song/Video pill)
"takes the whole colour ... while the background surrounding keep
changing and blending," when it's "supposed to be white." Root cause:
`Icon(..., tint = if (gradientColors.isNotEmpty()) pillAccentColor else
...)` — the icon's own content color was set to the artwork-extracted
accent color that also drives the pill's animated backdrop tint, so the
icon visually chased/blended into the shifting background instead of
reading clearly against it. The Song/Video pill right next to it never
had this problem, because its segment *text* color was always fixed
(`MaterialTheme.colorScheme.onSurfaceVariant` when unselected, a fixed
black/white computed once from luminance when selected) — the accent
color was only ever meant to live in the pill's *background* surface,
never applied directly to content sitting on top of it. Fixed to a flat
`Color.White`, matching that existing pattern and the user's explicit
instruction.

**Seek-lead constant**: `VIDEO_TOGGLE_SEEK_LEAD_MS`, applied when
seeking the (already-prepared) slave player at the moment of an actual
toggle tap, to roughly cover the gap between `seekTo()`/
`playWhenReady=true` and the first frame actually rendering. Was `+500`
inline before this patch; the user explicitly specified `800` (ms) to
use instead — set to `800L` as a named constant. Left an honest comment
that this number predates the eager-pre-buffer change and hasn't been
re-measured against the new, much-shorter fast path; the soft-sync loop
elsewhere in the file will correct any residual drift within a second or
so regardless, so this mostly affects how close the very first frame
lands on tap, not correctness.

### Views-count pill (new `ui/player/ViewCountPill.kt`)

Ported from mavins' `playCountPill` + `AnimatedCounter`, matched as
closely as this session could verify against the original TSX rather
than approximated from memory of what such a pill "should" look like:
same 3.5-second ease-out-quadratic (`1 - (1-t)^2`) count-up animation
from 1 to the real count, same bare-number rendering with no "views"/
"listens" text label (the icon alone carries the meaning in the
original), same pill styling (translucent white background, 20dp corner
radius). Mavins uses a headset/headphones icon here rather than an eye —
kept that choice as-is since it reads as a deliberate "listens" framing
for a music app rather than an arbitrary substitution, using
`Icons.Filled.Headphones` (already used elsewhere in this codebase, in
`AudioDeviceBottomSheet.kt`, so no new icon dependency).

Data source: `YouTube.getMediaInfo(videoId).viewCount` — already used
elsewhere in the app for the existing "Song Info" bottom sheet (see
`ui/utils/ShowMediaInfo.kt`), just not previously surfaced on the player
screen itself. Fetched per-track via a new `LaunchedEffect(mediaMetadata?.id,
isCurrentSongLocal)`, skipped entirely for local media (no YouTube view
count exists for a local file). Reuses the existing `formatCompactCount()`
helper from `ui/utils/StringUtils.kt` for the K/M/B/T abbreviation rather
than reimplementing mavins' own `formatCount` — the two do the same job
in the same style, and duplicating a near-identical formatter felt worse
than reusing the one already in the codebase.

Placed in the same top-bar `Row` as the Song/Video pill and the EQ icon
(`Arrangement.SpaceBetween`), between the two — the least invasive
option given Velune's structure, where that Row is a single shared
overlay used across every player design style (V3/Metro/etc.), rather
than duplicated per style the way mavins' own single-screen layout
didn't need to worry about. Gated on `!isCurrentSongLocal` like the
Song/Video pill, but *not* on `hasVideo` — a plain audio-only YouTube
track (no usable video stream) still has a real view count, so the two
pills have different, independently-correct visibility conditions that
happen to share the same top-level local-media gate.

### What this section did NOT verify

Same standing limitation as every EQ/DSP patch above, stated again here
because this is a genuinely different feature area and someone reading
only this section shouldn't have to cross-reference §0 to find it: **no
Android SDK in this sandbox, so none of this has been built or run.**
Specific things worth an on-device pass, roughly in order of how likely
they are to actually be wrong:

1. **The crossfade's `graphicsLayer` + conditional-composition interplay**
   — `if (videoPlayer != null && (isVideoMode || videoAlpha > 0f))`
   deciding whether the `AndroidView` exists at all, combined with
   `animateFloatAsState` driving its alpha. Compose animation timing
   racing against a composable being added/removed from the tree isn't
   something a text-only review can fully rule out — worth confirming the
   fade-out actually plays smoothly to completion rather than the
   `PlayerView` disappearing early or a frame late.
2. **The `hasVideo == null` fallback path** in `toggleVideo` — tapping
   within the first moment of a track starting, before the eager
   pre-buffer resolves. Exercised only by reasoning through the code, not
   by actually triggering that narrow timing window on a device.
3. **`VIDEO_TOGGLE_SEEK_LEAD_MS = 800L`** — the user specified this value
   directly rather than it being derived/measured, so no claim is being
   made that it's correct for the new pre-buffered fast path specifically
   (see the in-code comment) — just that it's what was asked for.
4. **The views pill's count-up re-triggering correctly on every track
   change**, including rapid next/next/next skipping — `remember(target)`
   keying `Animatable(0f)` fresh per target should handle this, but
   hasn't been watched happen on a real device.
5. **Network cost in practice** — how noticeable the always-on eager
   video pre-buffer actually is on a real connection/data plan, not just
   in the abstract "this is the trade-off" sense described above.

### CI hotfix (patch `0022`) — pre-existing broken imports in unrelated
files, caught by patch `0021`'s CI run

When `0021` (this section's own patch, above) was pushed, CI failed —
but the errors (`EqScreen.kt:189/201/205/210`, `AxionEqScreen.kt:571-637`,
`NeonVerticalFader.kt:84/115`) were **not in any file `0021` touched**.
Confirmed this directly (`git show <0021's commit> --stat | grep` those
three filenames came back empty) before assuming otherwise — these were
pre-existing broken imports from the EQ/DSP thread's own earlier,
already-merged patches (`2b36c19`/`2768d69`, the peak-meter/neon-vertical-
fader work), sitting silently broken because — same standing gap as
everything else in this project — nothing had actually triggered a real
CI build on top of them until `0021` happened to be the next thing
pushed.

Three simple missing-import bugs, all the same shape:
- `EqScreen.kt`: `Modifier.width/size/weight` used without importing
  `androidx.compose.foundation.layout.{width,size,weight}`. `weight`
  wasn't even in CI's own error list (compilers often truncate error
  reporting past some threshold) — caught by grepping every `Modifier.`
  call in the file against its import list rather than trusting the CI
  log to be exhaustive.
- `AxionEqScreen.kt`: bare `Canvas(...)` used in `PeakMeterView` without
  `import androidx.compose.foundation.Canvas` — which cascades into
  *every* symbol inside that Canvas lambda failing to resolve (`toPx`,
  `size`, `drawRect`, `drawCircle`), since the compiler can't establish
  the lambda's `DrawScope` receiver type without knowing what `Canvas`
  itself is. Confirmed by finding a second, unrelated `Canvas` usage
  (`SpectrumBarsCanvas`) in the same file that already worked around this
  exact gap by fully-qualifying it (`androidx.compose.foundation.Canvas(...)`)
  instead of fixing the import — meaning whoever wrote that one had
  already silently hit and sidestepped this same bug.
- `NeonVerticalFader.kt`: `val currentOnChange by rememberUpdatedState(...)`
  without `import androidx.compose.runtime.getValue` — the operator
  extension `by` delegation on `State<T>` needs to resolve to.

Fixed all three as pure import additions, nothing else touched — these
are real "missing import" bugs, not logic bugs, so the fix is exactly as
narrow as it looks. Same standing caveat as always: this sandbox still
has no Android SDK, so this is verified by careful manual read-through
(tracing every symbol back to its import, the same way the compiler
would) rather than by actually compiling it.



## 2n. This session's work — the previous session's own import fix (§2 job
"missing imports", commit f82feb9) introduced a new bogus import that
broke the very next real CI run

CI (build #166) failed on `EqScreen.kt:19:43`:
`Cannot access 'val RowColumnParentData?.weight: Float': it is internal
in file.` Traced to `f82feb9`'s own fix, which added
`import androidx.compose.foundation.layout.weight` alongside two
genuinely-correct import additions (`width`, `size`) for the same file.
`width`/`size` really are importable top-level functions in that
package, so the pattern-match looked reasonable — but `weight` isn't:
per Compose's own docs (`RowScope`/`ColumnScope` reference pages,
checked directly rather than assumed), `weight` is a member extension
declared *inside* `RowScope`/`ColumnScope` themselves, not a top-level
function anyone can import. It resolves automatically inside a
`Row {}`/`Column {}` lambda via the implicit scope receiver, no import
needed at all — and importing a name that happens to collide with an
internal implementation symbol (`RowColumnParentData.weight`) is what
produced this exact "internal in file" error, distinct from a normal
"unresolved reference."

Fix: deleted the one bad import line. All four `Modifier.weight(1f)`
call sites in the file were already correctly inside `Row {}` scope
(confirmed by reading each one directly), so nothing else needed to
change — this was purely an extraneous, harmful import, not a missing
one.

Also grepped the whole `app/src/main/kotlin` tree for the same
`import androidx.compose.foundation.layout.weight` line to confirm this
was the only occurrence rather than assuming.

**Not verified**: no real Gradle build available in this environment;
correctness here rests on Compose's documented `RowScope`/`ColumnScope`
scoping rules (checked directly) plus the same brace/paren-balance and
single-occurrence checks used throughout this file, not an actual
compile.



### "Upcoming Artist" filename parsing + edge-to-edge video/cover art

sizing, ported from phoenix-boss/mavins (expo-video branch)

Another §3-adjacent item, same "not part of the EQ/DSP thread" flag —
files touched this time: new `utils/LocalTrackMetadata.kt`,
`utils/LocalMediaStoreManager.kt`, `repository/LocalMusicRepository.kt`,
`viewmodels/LocalLibraryViewModel.kt`, `ui/player/Player.kt`. Cloned
`https://github.com/phoenix-boss/Mavins.git` — the `expo-video` branch
specifically, per explicit pointer — to see the real reference
implementation before writing anything, rather than work from the
paraphrased description alone.

**Artist parsing** (`libs/playerSetup.tsx` → `LocalTrackMetadata.kt`,
new file): Mavins' real logic is smarter than "first word before the
hyphen" — split on a hyphen/en-dash/em-dash, take whichever side is
shorter (and under 30 chars, since artist names tend to run shorter
than titles) or the right-hand side otherwise (the more common
"Artist - Title" convention), then fall back through a "feat. X"
credit, a trailing "[X]" bracket, a trailing "(X)" parenthetical
(excluding remix/live/version-looking ones), and finally
`UPCOMING_ARTIST`. "Upcoming Artist" replaces "Unknown Artist" as the
generic fallback throughout, matching Mavins' wording. A real ID3
artist tag is never touched — only fires on null/blank/`"<unknown>"`
(a known Android MediaStore quirk for untagged files) or an already-
generic value.

Wired into both of this app's local-file MediaStore scan paths
(confirmed via grep these are the *only* two places that build a local
track's title/artist from a raw cursor:
`LocalMediaStoreManager.getTracksForAlbum`,
`LocalMusicRepository.queryTracksInFolder`) and fixed
`LocalLibraryViewModel`'s artist sort comparator, which was still
checking the literal string `"Unknown Artist"` — a comparison that
would have silently stopped doing anything the moment the scan itself
stopped ever producing that exact string.

**Verified with a Python port before writing the Kotlin** (same
approach used for the DSP work above — no Kotlin toolchain in this
sandbox): confirmed real tags are never clobbered, "Artist - Title"
filenames parse correctly, hopeless/empty input safely falls back with
no crash, and — worth remembering — the translation deliberately
preserves a real limitation in Mavins' *own* regex (its bracket-match
is end-anchored, so a leading `"[TAG] Song.mp3"` prefix doesn't match
it) rather than silently "improving" on the source unasked.

**Video toggle "isn't toggling to switch"**: traced the already-landed
toggle logic (from `0021`/`c0356cc` + the CI-fix follow-ups
immediately above) and it reads as correct and already carefully
reasoned through — hasVideo/isVideoMode state, the pre-buffered fast
path, the `hasVideo==null` fallback, the disabled/dimmed video button.
The CI failures those follow-ups fixed were real compile-breaking
missing imports in *unrelated* EQ/DSP files — exactly the kind of thing
that'd make everything, toggle included, look totally broken if tested
against that build. Nothing changed here; this looks like an
already-fixed regression the report likely predates. If it's still
broken after pulling latest, it's something neither this session nor
`0021`'s found yet — get a fresh repro rather than assume this section
covers it.

**Cover art / video edge-to-edge sizing**: explicitly told not to copy
Mavins' own sizing (smaller, inset) — just needed this app's own call
sites fixed. `VideoMorphingComponents.kt` itself was already correct
internally (`ContentScale.Crop` / `RESIZE_MODE_ZOOM`, both crop-to-fill
whatever size they're handed) — the gap was entirely at the call
sites in `Player.kt`:
- Legacy landscape layout: was `Modifier.size(screenWidth * 0.4.dp)`,
  a small fixed square floating mid-Row. Now `Modifier.fillMaxSize()`.
- Legacy portrait layout: had *no* size modifier at all, just
  `.nestedScroll(...)` — relied on ambiguous constraint propagation
  through an unsized `weight(1f)` Box in a Column with no explicit
  width. Added `.fillMaxSize()` ahead of the nestedScroll modifier.
- `MetroPlayerContent` (V5 design): already correct
  (`.fillMaxSize().aspectRatio(1f)` inside its own 32dp-padded Box) —
  a deliberately inset square "card" look, not a gap. Left alone —
  don't "fix" this one later without re-reading this note.

**One thing flagged, not changed**: Mavins' real toggle
(`components/player/playerContent.tsx`) fully *hides* the Song/Video
switch for local tracks (`{!isLocal && (...)}`), it doesn't grey it
out — confirmed by reading the actual source, which doesn't match the
"greyed out in local mode" description this was requested against.
Velune's own already-landed port already matches Mavins' *real*
behavior (hides the whole pill for local, separately dims+disables
just the Video half when `hasVideo==false` for a non-local track).
Left as-is since it matches the reference implementation being pointed
to, but flag this discrepancy back to the user if a visible-but-
disabled treatment for local tracks specifically turns out to be what
was actually wanted.

**Not verified on-device** — same standing caveat as everything else:
the sizing fix and wiring are reasoned through carefully and the
parsing logic itself is verified in an executed Python port, but
nothing here has been watched render on an actual screen.

### Video toggle "does nothing at all" — root cause found and fixed

Third report of essentially the same complaint (`d6d1f9a`, `7c76abe`
fixed two earlier, different-shaped versions of "video toggle doesn't
work" — read those commit messages before assuming a new report is the
same root cause; it usually isn't). This time: **zero visual response,
not even a button highlight, on every track tried** — that specific
symptom matters, see below.

A prior pass through this same session (before the fix below) read
`resolveVideoStreamUrl`, `toggleVideo()`, the click handler, the IOS
client's version string, and `PlaybackAuthState.needsServiceIntegrity`
line by line and found nothing wrong — all of it was already correct.
That's recorded here so the next session doesn't repeat the same read-
through a third time: **the bug was never in any of that code. It was
architectural** — found only by directly reading the actual reference
implementation in `phoenix-boss/mavins` (`expo-video` branch,
`components/MusicPlayerContext.tsx`), not by re-reading Velune's own
code again.

**What Mavins actually does, that Velune didn't**: Mavins' stream
resolution (`getStreamInfoWithFallback`) tries *multiple independent
extraction attempts* before giving up — including a visitor-data
refresh and retry. Velune's `resolveVideoStreamUrl`, by contrast, tried
exactly **one** InnerTube client (`IOS`) and gave up immediately on any
failure — no fallback at all. Given the reported symptom (button
visibly present but completely inert, for every track, no exceptions),
this is exactly what you'd see if IOS-client requests were failing for
some external reason (YouTube-side change, transient block, etc.) with
nothing to catch the failure.

**The actual fix, done without inventing anything new**: Velune's own
*audio* resolution path already has a rich, production-proven 17-client
fallback list (`STREAM_FALLBACK_CLIENTS` — MOBILE, ANDROID_MUSIC,
ANDROID_VR_NO_AUTH, IOS, IOS_MUSIC, TVHTML5_SIMPLY_EMBEDDED_PLAYER, and
more), used successfully for every song played in this app. The video
path never used it — it was hardcoded to IOS alone, in a completely
separate function that didn't share the audio path's resilience at all.
`resolveVideoStreamUrl` now tries IOS first (preserving whatever
worked before), then falls through the rest of that same proven client
list, actually querying each one and skipping it if it returns no
usable video-only formats — no assumption is made about which clients
support video and which don't, since that's discoverable per-client
directly from the response rather than worth guessing.

This is why a full NewPipeExtractor-based rewrite (which is what
`MavinEngine` actually is under the hood, given its `serviceId=0`
parameter — a NewPipeExtractor service-registry index) was **not**
attempted here: that's a real, substantially larger undertaking
(porting a whole independent extraction pipeline, not extending an
existing one), and this sandbox has no way to verify NewPipeExtractor's
actual current API surface against real behavior. The multi-client
fallback fix reuses code and a resilience pattern **already proven
correct in this exact codebase** — a fundamentally lower-risk fix that
directly addresses the "one client, no fallback" gap Mavins' own
architecture pointed at, without requiring a new library integration
this session couldn't fully verify. If the multi-client fallback
*doesn't* fully resolve this — i.e. every single client in the list
fails for a given video — that's the actual signal a NewPipeExtractor-
based path is worth the bigger undertaking, not something to assume
upfront.

Also added: `isStreamClientTemporarilyBlocked(videoId, clientKey)` is
now checked before each client attempt, same call pattern already used
by the audio path — skips a client already known to have 403/429'd
recently for this video, rather than wasting a request repeating a
failure that's already been recorded elsewhere (populated by whatever
layer handles actual playback HTTP errors, not by this function itself
— confirmed `markStreamClientFailed` is public and called from outside
this file, not something this resolve path needed to also populate).

Diagnostic logging (tag `YTPlayerUtils`, matching the file's own
`logTag` convention) is included at every failure point per client —
cache hit, `player()` failure, non-OK playability with reason, missing
`streamingData`, candidate count, per-candidate cipher-resolution
failure, and which client eventually succeeded (or that all of them
failed). This supersedes an earlier, narrower logging-only patch from
this same session that never got merged (it only ever existed as a
handed-off `.patch` file, not applied) — if you see a reference to a
patch that added logging without the multi-client fallback, that patch
is obsolete; this version replaces it entirely, don't try to apply both.

**Also checked, for a related but separate question**: whether Velune
and Mavins' Pawns SDK integrations use the *same* API key/account,
since the user said they're supposed to. Neither repo commits the
literal key value to source anymore (Velune's was moved to
`BuildConfig`/`local.properties` in an earlier fix; Mavins was already
sourcing it from `EXPO_PUBLIC_PAWNS_API_KEY`, never hardcoded). That
means this can't be verified by reading source in either repo — it can
only be confirmed by comparing the actual values in each project's own
`local.properties`/env config, which is something only the user can do
directly. Said so plainly rather than guessing a "yes" or "no".

**Not verified on-device** — same caveat as everything else in this
file. The reasoning here is stronger than the earlier logging-only pass
(a real architectural gap was found and directly addressed with an
already-proven pattern, not a guess), but "should work" and "confirmed
working" are still different claims. If it's still broken after this,
the per-client logging will show exactly which of the 17 attempts
failed and why — that's the next debugging step, not another code
read-through.

## 4. Suggested next step

Ask the user directly, `ask_user_input_v0` style:

1. **On-device verification pass** — nothing convolution-, spectrum-,
   peak-meter-, canonical-engine-, preset-picker-, tempo/pitch-fast-
   path-, or local-artist/video-sizing-related has ever run on an
   actual phone; this is arguably the single biggest open gap given how
   much has been built on top of it by now.
2. **The rest of "and other polishes"** — get specifics; see §2k's closing
   note for what's still unitemized (meter label/dB scale, broader restyle
   pass, or something else entirely).
3. **Flanger effect for Simple** — asked for previously; confirmed via
   grep there is currently zero flanger implementation anywhere in the
   codebase, so this is a genuine from-scratch DSP build, not a "make it
   work" fix.
4. **Hide-vs-grey discrepancy on the local-track video toggle** — see
   §3's local-artist-parsing subsection above; flagged, not resolved.
   Confirm which treatment is actually wanted before touching it either
   way.
5. Something else the user names.

(Preset picker moved to the top of Simple is now done — see §2d. The
spectrum "isn't showing" investigation is now done too — see §2e. The
"stutters/hooks passing through the eq" performance report is now done
too — see §2j. The output peak meter is now done too — see §2k, though
its on-device legibility is unverified same as everything else.)

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
transcript still being around. And check the repo's actual current `main`
right before you generate that patch, not just at the start of your
session — as this entry itself demonstrates, it can move while you work.

## 5. New backlog — reported directly by the user, 2026-08, none of this
started yet

Thirteen items, all from one message, none investigated this session —
this session's only job was capturing them accurately for whichever
session(s) pick each one up. Do not treat the file/root-cause guesses
below as confirmed the way the rest of this document's entries are
(everything above this section describes *completed, verified* work);
these are starting points for investigation, not diagnoses. Grep/read
the actual current code before assuming any of this is still accurate —
plenty has moved since this was written (see the whole rest of this
file for how fast that happens in this repo).

1. **Music recognition (Shazam-style) doesn't work — "temporarily
   unavailable"**. User's exact words: the recognise-music component
   "isn't working fully as the Shazam service shows temporarily
   unavailable and it should be available to recognise any song even
   when the app is minimized." Two distinct requirements bundled in one
   report.

   **(b) — DONE this session.** Confirmed root cause by reading the
   actual code, not guessing: `RecognitionForegroundService.kt` already
   existed and already does this correctly (a real foreground service,
   independent of any Activity's lifecycle, `foregroundServiceType=
   "microphone"` in the manifest, an ongoing notification) — but it was
   only reachable from the Quick Settings tile
   (`MusicRecognizerTileService`) and `RecognitionLaunchActivity`, never
   from the in-app Recognition screen (`RecognitionScreen.kt`), which is
   presumably how most users actually trigger it. That screen was
   instead calling `MusicRecognitionService.recognize(context)` directly
   inside `scope.launch {}`, where `scope` was
   `rememberCoroutineScope()` — cancelled the moment the screen leaves
   composition (backgrounding, navigating away), well before a
   10-second recording + network round trip finishes. Fixed by routing
   all three call sites in `RecognitionScreen.kt` through
   `RecognitionForegroundService` instead, the same
   `startForegroundService`/`ForegroundServiceStartNotAllowedException`
   pattern `RecognitionLaunchActivity.startRecognitionService()` already
   used. The UI didn't need any other changes: it already observes
   `MusicRecognitionService.recognitionStatus`, a shared app-wide
   `StateFlow` the service updates as it progresses — only *where* the
   work runs changed, not how the result gets back to the screen.

   **(a) — still open, honestly.** The exact string "Shazam service
   temporarily unavailable" comes from `ShazamClient.kt`'s
   `performRecognition()`, thrown for *any* HTTP 5xx response from
   `amp.shazam.com` — an unofficial, reverse-engineered endpoint (no
   real API key; spoofs a random Android user-agent/timezone/
   geolocation to mimic the real app). Checked the request/response
   models (`ShazamModels.kt`) against the commonly-documented shape for
   this exact endpoint (used by several other open-source Shazam
   clients) and found nothing structurally wrong. Also checked: this
   sandbox cannot reach `amp.shazam.com` at all (not in the container's
   allowed egress domain list, and it's a signed POST endpoint anyway,
   not something a GET-based fetch tool could exercise), so whether
   requests are *currently* succeeding, being rate-limited, or being
   actively blocked by Shazam's anti-bot defenses (a known, recurring
   failure mode for this whole class of unofficial client — corroborated
   by public reports from other reverse-engineered Shazam client
   projects hitting the same kind of block) could not be tested from
   here. `UptimeScreen.kt` already pings `https://amp.shazam.com`
   directly as a health check ("Echo Find (Shazam)"), which is the
   fastest way to check current live status on a real device before
   assuming anything further needs to change in `ShazamClient.kt`.

2. **"Recently updated" text on the home page should never show.**
   User: "even if the app was updated the user should [not] see any
   text of such." Sounds like a banner/label tied to an app-update or
   first-launch-after-update check. Find where it's sourced (likely
   compares a stored version code/name against the current one) and
   remove the UI entirely, not just hide it conditionally.

3. **Liquid glass setting toggles but visibly does nothing.** User: "I
   turn it on the whole app remains the same like nothing happened."
   This is a toggle that isn't wired to anything, or is wired to
   something that isn't actually applied anywhere in the render path —
   find the setting's DataStore/preference key, then find (or discover
   there isn't) anywhere in the UI that actually reads it.

4. **DONE this session — Hardcoded "Echo Music"/"Velune" strings instead
   of the dynamic app name, in multiple places.**

   Confirmed the mechanism first, per §6's earlier note: it's build-time,
   not runtime — `app/build.gradle.kts` injects `resValue("string",
   "app_name", appName)` (and `config_app_name`) from Gradle properties,
   read via `stringResource(R.string.app_name)` in Compose or
   `context.getString(R.string.app_name)` elsewhere. Most of the app
   already used this correctly; the remaining instances split into two
   distinct bugs, not one:

   - **`strings.xml` resources had "Velune" baked directly into the
     resource text**, even though several call sites were already
     (uselessly) passing `app_name` as a format arg with no `%1$s`
     placeholder to receive it — i.e. the Kotlin side was already
     "fixed" and the bug was purely in the string resource. Affected:
     `discord_information`, `music_together`, `spotify_import_desc`,
     `crash_description`, `crash_report_subject`. Added the placeholder
     to each and wired up the remaining call sites that weren't already
     passing the arg (`SettingsScreen.kt`, `SpotifyImportScreen.kt`,
     `CrashActivity.kt` x2).
   - **Plain Kotlin string literals** instead of `stringResource`/
     `context.getString`: `GatewayClient.kt`/`DiscordSocialPresenceClient.kt`
     (Discord identify/presence payloads), `AxionEqViewModel.kt` (the
     "Echo Tuning" EQ preset name — the exact item originally reported),
     `PermissionHandler.kt` (both storage-permission explainer strings),
     `ThemeCreatorScreen.kt` (default exported theme name),
     `DiscordExperimental.kt` (both "Go to Velune" button-label
     defaults — `DiscordSettings.kt` already had the correct pattern for
     the same preference, `DiscordExperimental.kt` just hadn't been
     brought in line with it), `VeluneSettingsScreen.kt`/`MainActivity.kt`
     (logo `contentDescription` accessibility labels), `UpdateScreen.kt`
     (the two updater warning/explanation strings, each duplicated across
     two dialogs), `DiscordRPC.kt` (the "Go to Velune" button fallback and
     the "`<text>` on Velune" RPC small-text suffix).

   **Deliberately left alone — not app-name usages, despite matching the
   grep**: GitHub repo URLs (`Zapier-codes/Velune` links, functional, not
   display text); the `VeluneBackup` XML serialization tag and
   `VeluneAdminToken` DataStore key in `BackupRestoreViewModel.kt`/
   `MusicService.kt` (internal identifiers — renaming would break
   backup-file compatibility with existing exports); the ListenBrainz
   `submission_client` JSON field in `ListenBrainzManager.kt` (an API
   identifier sent to an external service, not UI text);
   `UptimeScreen.kt`'s "Echo Canvas"/"Echo Find (Shazam)" (named external
   services this app talks to, not this app's own name);
   `ListenTogetherServers.kt`'s default relay server name/operator (a
   specific, externally-operated server — renaming the display text
   wouldn't change who actually operates it); `SearchableSettings.kt`'s
   "Echo Extractor" (a named feature/route, like "Echo Canvas", not a
   reference to the app itself); and `Updater.kt`'s literal
   `"Velune.apk"` (the actual release-asset filename on GitHub, fixed
   regardless of the runtime app name). If any of these turn out to be
   wrong — e.g. if "Echo Canvas"/"Echo Find"/"Echo Extractor" were
   actually meant to rebrand too — that's a call for the user to make
   explicitly, not something to infer from the original bug report's
   four named locations.

   Not independently re-verified on-device or via a full Gradle build in
   this sandbox (no Android SDK / no access to Google's Maven repo from
   this container's allowed egress list — same constraint noted
   elsewhere in this file); verified via careful manual diff review and
   a post-edit grep sweep confirming no remaining bare "Echo Music"/
   "Velune" literals outside the intentionally-excluded list above.

5. **DONE this session — Remove the About page entirely.** Not hide, not
   gate behind a flag — remove the screen, its nav route, and its entry
   point(s) in settings.

   Turned out there are two parallel settings UIs in this codebase, only
   one of which is actually reachable: the `"settings"` route maps to
   `VeluneSettingsScreen.kt` (live), while `SettingsScreen.kt` and
   `SearchableSettings.kt` (which also each had their own About entry)
   are never invoked from anywhere — dead code, not a second live
   screen. Fixed all three anyway (cheap, and correct if either is ever
   wired back up), plus deleted `AboutScreen.kt` and its
   `composable("settings/about")` route + import in
   `NavigationBuilder.kt`. Left the `about`/`about_desc` string
   resources in place across all locale files — same precedent as the
   already-existing unused `new_update_available` string noted in §6;
   removing translated strings is a separate, lower-value cleanup and
   not what was asked for here. Confirmed with a full grep afterward:
   no remaining references to `AboutScreen`, `settings/about`, or a
   literal `"About"` label anywhere in `app/src/main/kotlin`.

6. **Remove "Manage campaign" from settings entirely.** User: "a
   separate app is being built for that purpose entirely this one
   should only display the placed campaign and it gets the campaign
   from supabase which we will connect now as another session task[]."
   So this item has two parts with different urgency:
   - Now: delete the "Manage campaign" settings entry/screen — this app
     is display-only for campaigns, management happens in a different
     app entirely.
   - Later, separate session: wire up the actual Supabase connection so
     this app can *read* placed campaigns. The user explicitly asked
     that whichever session picks this up **starts by inspecting the
     existing Supabase schema** rather than guessing table/column names,
     since "the supabase already has some tables." The command for that
     — give the user this directly, don't just describe it — is a plain
     SQL introspection query that works regardless of local CLI/auth
     state, run in the Supabase dashboard's SQL Editor (or via `psql`
     against the project's connection string):
     ```sql
     select table_name, column_name, data_type, is_nullable
     from information_schema.columns
     where table_schema = 'public'
     order by table_name, ordinal_position;
     ```
     If the Supabase CLI is set up and linked to the project locally,
     `supabase db dump --schema public --data-only=false` is the
     alternative that dumps full DDL (table definitions, constraints,
     indexes) rather than just the columns list. Either one, run first,
     before writing any Supabase-reading code for campaigns — see item
     8 below for how campaign placement/ordering needs to interact with
     whatever that schema turns out to actually look like.

7. **Queue should preload the next song's video ahead of time.**
   Currently (per the user) it re-resolves on every transition instead
   of having the next item ready, which is also called out again in
   item 12 below as the specific cause of a playback hitch when the
   video view is involved. Find wherever the queue/player currently
   resolves a track's playable source and check whether there's any
   lookahead/prefetch for the *next* queue item at all, or whether
   resolution is purely reactive to "this is now the current item."

8. **Campaign placement/rotation logic — a real feature to build, not a
   bug fix**, once item 6's Supabase read connection exists. Exact rule
   set, in the user's own terms:
   - Every 4 regular songs, the 5th slot is a campaign.
   - If there are multiple campaigns, they occupy that 5th-slot position
     one after another in sequence (1st campaign at the first 5th-slot,
     2nd campaign at the next 5th-slot, etc.).
   - When all campaigns in the list have played once, loop back to the
     first campaign — continuous loop, never runs out.
   - If there's only one campaign placed, that single campaign occupies
     every 5th-slot position (still a "loop," just a loop of one).
   - This must be **the same for every user** ("persist on every user's
     queue") — i.e. campaign placement is not per-user randomized, it's
     a deterministic sequence everyone sees the same way.
   - **Must survive shuffle**: "even if the user shuffles the song list,
     the position of the placed campaign remains the same." So campaign
     slots are computed on top of/independent of whatever ordering
     (shuffled or not) the user's own queue is in — the 5th/10th/15th/
     etc. position always resolves to the correct campaign in sequence,
     regardless of what shuffle did to the surrounding regular tracks.
   This has real design questions to work through before coding it
   (e.g.: is "position" counted from the start of the whole queue, or
   does it reset per session/per day; what happens if the queue is
   shorter than 5 songs; does inserting a campaign shift indices for
   everything after it or does it occupy a slot the regular queue
   already accounted for) — worth confirming the exact intended
   behavior with the user again once the Supabase schema (item 6) shows
   what data is actually available to work with, rather than guessing.

9. **DONE this session — Library → Downloads page shows cache files, not
   actual downloads.** Root cause was more precise than the symptom
   suggested: the sidebar's "Downloads" entry and `LocalLibraryContent`'s
   "Downloads" quick pill both navigated to `cache_playlist/downloaded`
   — the *cache* screen (`CachePlaylistScreen`/`CachePlaylistViewModel`),
   which **ignores its `{playlist}` route argument entirely** and always
   computes `cachedIds.subtract(downloadedIds)` (pure-cache, explicitly
   excluding real downloads), regardless of whether the arg says
   `"downloaded"` or `"cached"`. So there was no code path that could
   ever show real downloads from that screen — passing a different arg
   wouldn't have helped. The actual real-downloads screen already
   existed and was already correct: `auto_playlist/downloaded` →
   `AutoPlaylistScreen`/`AutoPlaylistViewModel`, which filters on
   `DownloadUtil.downloads` for `Download.STATE_COMPLETED` — the same
   route `LibraryMixScreen.kt`/`LibraryPlaylistsScreen.kt` already use
   correctly for their own "Downloads" entries. Fix was routing-only:
   changed both stray call sites from `cache_playlist/downloaded` to
   `auto_playlist/downloaded`. `CachePlaylistViewModel` itself is
   untouched — it's correct for the "Cached" entry point, just was being
   reused for something it was never meant to serve.

10. **DONE this session — "Most Played" crashes the app.** Confirmed
    exact cause: `LibrarySidebar.kt` and `LocalLibraryContent.kt` both
    navigated to the literal route `top_playlist/Top` — passing the
    word "Top" as the `{top}` argument instead of a song count.
    `TopPlaylistViewModel.kt` does `top.toInt()` on that value
    unconditionally (`val top = savedStateHandle.get<String>("top")!!`
    then `top.toInt()` inside the `topSongs` flow), which is exactly
    `NumberFormatException: For input string: "Top"`. Every *other*
    call site of this route (`LibraryMixScreen.kt`,
    `LibraryPlaylistsScreen.kt`, both twice) correctly passes
    `"top_playlist/$topSize"`, where `topSize` comes from the
    `TopSize` DataStore preference (`stringPreferencesKey("topSize")`,
    default `"50"`). Only the two sidebar/quick-pill entry points had
    the literal-string bug. Fix: both now read the same `TopSize`
    preference via `rememberPreference(TopSize, defaultValue = "50")`
    and navigate to `"top_playlist/$topSize"`, matching every other
    entry point exactly.

11. **DONE this session — Hamburger-menu sidebar (also opens via
    edge-swipe gesture) sat too high, overlapping the page header.**
    User: it "should show after the page header that position is
    perfect so the side bar is fully seen" — i.e. the sidebar's top
    edge should start below wherever the page header/top bar ends, not
    overlap or sit above it.

    Root cause: this sidebar is `LibrarySidebar.kt`, rendered as a
    top-level sibling in `LibraryScreen.kt` inside its own
    `Box(Modifier.fillMaxSize())` — a full-screen overlay with no
    awareness of the page header's height. The panel itself was
    `Alignment.CenterEnd`, vertically centered over the *entire* screen
    including the area under the header, so on shorter screens (or
    with more sidebar items than currently) its top edge could land
    under/behind the header instead of starting cleanly below it.

    Fix: `LibraryScreen.kt` now measures the actual bottom edge of
    whichever header row is currently showing (`LibraryModeHeader` or
    `SelectionHeader`) via `onGloballyPositioned` + `boundsInWindow()`,
    converts it to Dp with `LocalDensity`, and stores it in a new
    `sidebarTopOffset` state var — this is window-coordinate based, not
    parent-relative, so it stays correct even though the sidebar Box
    and the header live in different parts of the composition tree.
    That offset is passed into `LibrarySidebar` as a new `topOffset`
    param; the panel is now `Alignment.TopEnd` with
    `.padding(top = topOffset + 8.dp)` instead of `CenterEnd`, so it
    always starts just below the header regardless of screen size or
    which header variant (mode-toggle vs. selection) is active. The
    density-conversion pattern (`with(density) { ...boundsInWindow()
    .bottom.toDp() }`) mirrors what `FloatingNavigationToolbar.kt`
    already does elsewhere in this codebase, rather than inventing a
    new approach.

12. **Video player: not edge-to-edge vertically (only horizontally
    right now); loading spinner should be a skeleton loader; next-song
    video re-resolves instead of using a preload, causing a playback
    hitch.** Three related items on the same screen/component:
    - The video thumbnail/player container already fills edge-to-edge
      *horizontally* (confirmed working per the user) but not
      *vertically* — needs the same edge-to-edge treatment applied on
      the vertical axis too.
    - Replace whatever spinner currently shows while a video is loading
      with a skeleton loader instead.
    - "If the next song is to load it goes to re-resolve it causing an
      issue in the smooth playback" — this is the video-specific
      manifestation of item 7's queue-preload gap; likely the same
      underlying fix (preload/resolve the next item ahead of time)
      fixes both, but confirm that once item 7 is actually being worked
      on rather than assuming they're one ticket.

13. **Status bar should be hidden on every screen, app-wide.** Not
    per-screen opt-in — the user wants this as a global default across
    the whole app (immersive/edge-to-edge with the status bar hidden
    everywhere, not just on the player or video screens).

## 6. Reconnaissance on the still-open 2026-08 backlog items — not fixed
this session, but real findings so the next session isn't starting from
the raw symptom again. Everything below was confirmed by reading the
actual code, not guessed.

- **Item 2 ("Recently updated" text on home) — could not find it.**
  Exhaustive search (every string resource in every locale file, every
  Home-screen/Home-component Kotlin file, the launcher widget, the
  updater/changelog machinery — `Updater.kt`, `ReleaseNotesCard.kt`,
  `UpdateInfoDialog.kt`, `ChangelogScreen.kt`, `UpdateScreen.kt`,
  `AboutScreen.kt`) turned up nothing. No "Recently updated" string, no
  first-launch-after-update banner, no version-comparison check that
  drives visible text on the in-app Home tab. The only "update"-adjacent
  UI that exists lives on the separate Settings → Updates screen, plus
  an unused `new_update_available` string defined in every locale file
  but never referenced from any Kotlin code (dead string). Before
  touching anything here, get the user to confirm exactly where they see
  this — it may be the Android launcher's home-screen *widget*
  (`PlaylistWidget.kt`) rather than the in-app Home tab, given this
  repo's own history of "home" meaning two different things (see the
  campaign banner note in `HomeScreen.kt`). Don't guess and patch blind.

- **Item 3 (Liquid Glass toggle does nothing) — root cause is deeper
  than a wiring bug, and there's a second, correctly-wired "liquid
  glass" system elsewhere worth knowing about so it isn't confused with
  this one.**
  - The Settings → Liquid Glass screen (`GlassEffectSettings.kt`) writes
    ~13 `LiquidGlass*` DataStore keys (global enabled, per-component
    enabled, vibrancy, blur radius, lens height/amount, chromatic
    aberration, depth, tint, opacity, text color). **Nothing else in the
    app reads any of these keys** — confirmed by grepping every one of
    them individually. The settings screen is a fully self-contained
    dead end.
  - There IS a real, live rendering mechanism for a "liquid glass" nav
    bar: `Modifier.liquidGlass()` in `GlassEffectStubs.kt`, driven by
    `LocalGlassEffectConfig` (a `CompositionLocalOf<GlassEffectConfig>`),
    consumed in `AppNavigation.kt`'s `AppNavigationBar` composable
    (gated on `glassEnabled && glassConfig.globalEnabled &&
    glassConfig.navBarEnabled`). But two things sink it: (1)
    `LocalGlassEffectConfig` is never provided by any
    `CompositionLocalProvider` anywhere in the app, so `.current` always
    resolves to the hardcoded default (`enabled = false, globalEnabled =
    false`) — permanently off, no matter what Settings says; and (2)
    `AppNavigationBar` itself is **never called from anywhere** — dead
    code, same as `GlassNavigationBar.kt`/`GlassMiniPlayer.kt`
    (a *third*, separately-named glass nav bar/mini-player pair driven
    by yet another set of keys — `GlassBlurIntensityKey`,
    `GlassVibrancyEnabledKey`, `GlassLensEnabledKey` — also never called
    from anywhere). The nav bar actually rendered on screen,
    `FluidSlidingNavigationBar.kt`, has zero glass/blur code of any
    kind.
  - **The "separate name" system that IS real and IS live:**
    `LyricsGlassStyle.kt` — a small set of named presets (`FrostedDark`,
    `FrostedLight`, `ClearGlass`, `DeepBlur`, `VividGlow`, plus a
    palette-derived `fromPalette()` variant) genuinely used by
    `Lyrics.kt` (the in-app lyrics screen background/picker,
    `selectedGlassStyle`/`paletteGlassStyle`) and
    `LyricsImageCard.kt`/`ComposeToImage.kt` (lyrics image export card).
    This is a completely separate system from Settings' "Liquid Glass"
    — different file, different data class, different keys, only
    touches the lyrics screen, and the user never sees a settings
    toggle for it at all (it's a style picker inside the lyrics screen
    itself).
  - **Net effect**: three independent "glass" implementations exist
    (`LiquidGlass*` settings + `liquidGlass()`/`LocalGlassEffectConfig`,
    `Glass*` + `GlassNavigationBar`/`GlassMiniPlayer`, and
    `LyricsGlassStyle`), and only the lyrics one actually renders
    anything a user can see. Making the Settings toggle do something
    real means either (a) wiring `AppNavigationBar` + a real
    `CompositionLocalProvider` for `LocalGlassEffectConfig` into
    `MainActivity`'s nav bar call site (currently
    `FluidSlidingNavigationBar`, which would need the glass treatment
    added or need to be swapped for `AppNavigationBar`), or (b) doing
    the equivalent wiring for the `Glass*`-prefixed pair instead — both
    are real rendering code sitting unused, not something to build from
    scratch. This is graphics/rendering feature work spanning multiple
    files, not a one-line patch — treat it as its own session.

- **Item 4 (hardcoded "Echo"/"Velune" strings) — now DONE, see item 4's
  own entry above in §5 for the full list of what was fixed and what
  was deliberately left alone.**

- **Items 6/8 (Supabase campaign read connection) — the plumbing already
  exists, just needs the schema check the user asked for.** Confirmed:
  `campaign_schema.sql` at repo root already defines a `public.campaigns`
  table (source_url, resolved_song_id, is_live, a play-count RPC,
  RLS-enforced date-window visibility — see the file's own header
  comments for the full design). `SUPABASE_URL`/`SUPABASE_ANON_KEY` are
  already wired via `BuildConfig` from `local.properties`/env
  (`app/build.gradle.kts`). There's already a live `CampaignCardSection`
  rendering on Home (`HomeScreen.kt`, above the category chips) backed
  by `CampaignRepository`/`CampaignUrlResolver` — so campaign *reading*
  may already substantially work; what's unconfirmed is whether the
  **live** Supabase database's actual schema still matches this
  `campaign_schema.sql` file (the user was explicit that it might not —
  "the supabase already has some tables"). Still do the schema
  introspection query from item 6 above before assuming this file is
  ground truth. The "Manage campaign" settings-removal half of item 6
  (display-only, no in-app management) has not been done yet.

