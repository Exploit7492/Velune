package com.nikhil.yt.campaign

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which [CampaignCard] (if any) the currently-loaded playback came
 * from, so the full player screen can show the certified badge and
 * "Promoted" label for it (see Player.kt's call site).
 *
 * A plain app-scoped singleton rather than threading a "current campaign"
 * value through a ViewModel: the setter (CampaignCardSection, on tap) and
 * the reader (Player.kt) live in genuinely different parts of the
 * composition with no natural shared ViewModel between them, and this is
 * exactly the kind of small, single-purpose piece of shared state a
 * `CompositionLocal`/singleton `StateFlow` is for — not big enough to
 * justify new DI plumbing.
 *
 * Self-clearing: [Player.kt] calls [clearIfNot] on every media item
 * change, passing the id of whatever's now actually playing. If that
 * doesn't match the tracked campaign's songId — the user skipped away,
 * picked something else, the queue advanced — the badge disappears. It
 * never "sticks" to a track that isn't the one the campaign was actually
 * pointing at.
 *
 * [isRewardEligible] — Task 66 Part a-ii-b-ii (handover.md, Mavins-web
 * repo): carries whether the currently-tracked campaign was launched via
 * the reward=true deep link from mavins-web's own /earn page, as opposed
 * to a normal in-app tap on the home banner. Defaults to `false` and is
 * only ever set `true` by [MainActivity]'s deep-link handler — the
 * existing [CampaignCardSection]/`HomeScreen.kt` tap path is unchanged,
 * calls [setActive] with no second argument, same as before this field
 * existed. Reading this flag to actually gate qualifying-play recording
 * (only count a play toward listener earnings if it came via the reward
 * intent) is deliberately NOT done here — that's ii-b-ii-b, still open,
 * needs a change at `MusicService.kt`'s own recording call site, a
 * different part of this same sub-part.
 */
object CampaignPlaybackTracker {

    private val _current = MutableStateFlow<CampaignCard?>(null)
    val current = _current.asStateFlow()

    private val _isRewardEligible = MutableStateFlow(false)
    val isRewardEligible = _isRewardEligible.asStateFlow()

    /** Called right before starting playback for a tapped campaign.
     *  [rewardEligible] defaults to `false` (a normal in-app tap) —
     *  only the reward=true deep-link handler passes `true`. */
    fun setActive(campaign: CampaignCard, rewardEligible: Boolean = false) {
        _current.value = campaign
        _isRewardEligible.value = rewardEligible
    }

    /**
     * Called on every media item change with whatever song id is now
     * actually playing. Clears the tracked campaign (and its reward
     * flag) if it no longer matches — see class doc.
     */
    fun clearIfNot(currentlyPlayingSongId: String?) {
        val tracked = _current.value ?: return
        if (tracked.songId != currentlyPlayingSongId) {
            _current.value = null
            _isRewardEligible.value = false
        }
    }
}
