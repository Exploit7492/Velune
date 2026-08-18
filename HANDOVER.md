# Velune EQ/DSP Handover (v10)

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

As of this handover (v9), the **real GitHub `main`** was last confirmed
at:

```
36d5518 fix(eq): spectrum analyzer could stay outside the active DSP chain even with its switch on
```

**Note on the stale pointer this replaced**: the previous version of this
file was titled "v8" but this section still said "(v7)" and pointed at
`ee25ce6`/patch `0016` — four real commits behind (`aed244d`, `2adc975`,
`393d09c`, `861719d`, `d420a83`, `36d5518` — six, actually) by the time it
was read. Whoever wrote §2b–§2e updated the content but not this pointer,
likely because that session committed straight to a local clone without
going through the `git am`-patch handoff (no separate session needed to
know "which number is next"). If you're a session working from a pasted
copy of this file rather than a fresh clone, **always verify against a
real `git log`, don't trust this pointer blindly** — this is exactly the
failure mode that bit the last session.

Also note: `aed244d`, `2adc975`, and `ee25ce6` landed via normal GitHub
PRs (`(#144)`, `(#146)`) rather than this session's `git am`-patch
workflow — don't confuse that PR-number commit style with the numbered
`.patch` file sequence. `393d09c`, `861719d`, and `d420a83` appear to have
been committed directly too (author `Pops <pops@velune.dev>`, no `.patch`
file evidence) — the numbered-patch discipline isn't being followed with
perfect consistency session to session; don't assume it always will be.

One more patch was built after that commit in the v9 session — `0018`,
described in §2f below — but **you have no way to know from here whether
the user has applied it yet.** Check the log after cloning:

- If `main` still tops out at `36d5518` → `0018` not applied yet. Ask if
  the user still has the file, or regenerate it from §2f below if needed.
- If `main` already has a commit titled `fix(eq): block startup EQ
  restore (bounded) instead of racing the renderer chain` → applied,
  build on top of that history directly. This file is updated in that
  same patch, so if that commit landed, this version of the file is
  already on `main` too.

Number your next patch `0019`.

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

## 4. Suggested next step


Ask the user directly, `ask_user_input_v0` style:

1. **On-device verification pass** — nothing convolution-, spectrum-,
   canonical-engine-, or preset-picker-related has ever run on an actual
   phone; this is arguably overdue given how much has been built on top
   of it.
2. **Flanger effect for Simple** — asked for; confirmed via grep there is
   currently zero flanger implementation anywhere in the codebase, so
   this is a genuine from-scratch DSP build, not a "make it work" fix.
3. Something else the user names.

(Preset picker moved to the top of Simple is now done — see §2d. The
spectrum "isn't showing" investigation is now done too — see §2e — a
real isActive() gap was found and fixed, but get an on-device repro
before assuming it's the *whole* story if reports continue.)

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
