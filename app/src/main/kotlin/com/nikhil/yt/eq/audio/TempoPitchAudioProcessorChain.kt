package com.nikhil.yt.eq.audio

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

/**
 * Replaces Media3's `DefaultAudioProcessorChain` so that [TempoPitchAudioProcessor]
 * -- not the built-in `SonicAudioProcessor` -- is the thing `DefaultAudioSink`
 * asks about *media duration* while tempo/pitch is active.
 *
 * This matters for more than just "which class resamples the audio": Media3
 * tracks playback position by asking the chain's [getMediaDuration] to scale
 * however much output audio has actually been played back into the
 * corresponding amount of *source* media time (`DefaultAudioProcessorChain`'s
 * own implementation just forwards this to its internal `SonicAudioProcessor`).
 * Point that at the wrong place -- or don't implement it at all -- and the
 * seek bar/position reporting drifts out of sync with what's actually
 * playing as soon as tempo changes duration. Routing it to
 * [TempoPitchAudioProcessor.mediaDurationForPlayoutDuration] instead is what
 * keeps that correct now that Sonic isn't the one doing the resampling.
 *
 * [SilenceSkippingAudioProcessor] is kept exactly as `DefaultAudioProcessorChain`
 * would have wired it -- it backs the app's existing "Skip silence" setting
 * (`player.skipSilenceEnabled`, see `MusicService`), which has nothing to do
 * with this feature and must keep working unchanged.
 *
 * [applyPlaybackParameters] is intentionally a passthrough: `PlaybackParameters`
 * used to be how the old Sonic-backed tempo/pitch dialog worked, but nothing
 * in the app sets it anymore (tempo/pitch is now driven directly through
 * [TempoPitchAudioProcessor.setTempo]/[TempoPitchAudioProcessor.setPitchSemitones],
 * the same "pending state" pattern `EqualizerService` uses for every other
 * control). If something else in the app ever calls
 * `player.playbackParameters =` again, this accepts it at face value rather
 * than silently ignoring it, but note it won't actually change the audio.
 */
@UnstableApi
class TempoPitchAudioProcessorChain(
    private val tempoPitchProcessor: TempoPitchAudioProcessor,
    userDefinedAudioProcessors: Array<AudioProcessor>,
    private val silenceSkippingAudioProcessor: SilenceSkippingAudioProcessor = SilenceSkippingAudioProcessor(),
) : DefaultAudioSink.AudioProcessorChain {

    private val audioProcessors: Array<AudioProcessor> =
        userDefinedAudioProcessors + arrayOf(silenceSkippingAudioProcessor, tempoPitchProcessor)

    override fun getAudioProcessors(): Array<AudioProcessor> = audioProcessors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters = playbackParameters

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
        return skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long =
        tempoPitchProcessor.mediaDurationForPlayoutDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = silenceSkippingAudioProcessor.skippedFrames
}
