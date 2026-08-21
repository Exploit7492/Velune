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
 */
object CampaignPlaybackTracker {

    private val _current = MutableStateFlow<CampaignCard?>(null)
    val current = _current.asStateFlow()

    /** Called right before starting playback for a tapped campaign. */
    fun setActive(campaign: CampaignCard) {
        _current.value = campaign
    }

    /**
     * Called on every media item change with whatever song id is now
     * actually playing. Clears the tracked campaign if it no longer
     * matches — see class doc.
     */
    fun clearIfNot(currentlyPlayingSongId: String?) {
        val tracked = _current.value ?: return
        if (tracked.songId != currentlyPlayingSongId) {
            _current.value = null
        }
    }
}
