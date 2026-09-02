package com.nikhil.yt.campaign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Task 59 Part 3b-a (handover.md) — the state/timer/lifecycle engine
 * behind the home banner's single-card carousel. Deliberately split
 * from the actual UI rebuild (Part 3b-b, not built yet) the same way
 * Round 13/14 split `GenreTileMappingCache`'s own cache logic from
 * `MusicService.kt`'s consumption of it — this file is fully
 * self-contained and was verified with a throwaway Python simulation
 * (15 scenarios, all passed, discarded — not committed) before being
 * written as real Kotlin, same convention every prior Velune part in
 * this task has used given no compiler is available in this sandbox.
 *
 * Three rules this state machine exists to enforce, per Round 4's own
 * spec and Round 3's already-resolved "replace, not augment" decision:
 * 1. **Single card, one at a time** — no `LazyRow` of every live
 *    campaign, unlike the surface this replaces.
 * 2. **A 30-second auto-advance timer**, cycling through a shuffled
 *    order.
 * 3. **Reshuffle specifically on app-background-then-resume** — not on
 *    every recomposition, not periodically, and NOT on the very first
 *    `ON_RESUME` a Compose lifecycle fires when a screen first appears
 *    (that's not "returning from the background," it's just arriving).
 *    Getting this distinction wrong was the one real bug the
 *    simulation script was specifically written to catch before this
 *    got written as Kotlin — `wasStopped` below only becomes `true`
 *    from a real `ON_STOP`, so an initial `ON_RESUME` with no prior
 *    `ON_STOP` correctly does nothing.
 *
 * `mappedGenreId`-style "never reveal the true count" rule (Round 3):
 * this file exposes `current: CampaignCard?`, one card at a time —
 * never the underlying list or its size, so a 3b-b consumer has no
 * way to accidentally leak the live campaign count even by accident.
 */

private const val CARD_DURATION_MS = 30_000L

interface CampaignCarouselState {
    /** The single campaign to render right now, or `null` if there are
     * no live campaigns at all (3b-b should render nothing in that
     * case, same as `CampaignCardSection`'s current empty-list
     * behavior — not a new failure mode). */
    val current: CampaignCard?
}

private class CampaignCarouselStateImpl(
    initialCampaigns: List<CampaignCard>,
) : CampaignCarouselState {
    var order: List<CampaignCard> by mutableStateOf(initialCampaigns.shuffled())
    var index: Int by mutableIntStateOf(0)
    var wasStopped: Boolean = false

    override val current: CampaignCard?
        get() = order.getOrNull(index)

    fun advance() {
        if (order.isEmpty()) return
        index = (index + 1) % order.size
    }

    fun onStop() {
        wasStopped = true
    }

    fun onResume() {
        // Only reshuffle if a real ON_STOP happened since the last
        // reshuffle — see this file's own header comment for why this
        // guard is the entire point of this function existing.
        if (!wasStopped) return
        order = order.shuffled()
        index = 0
        wasStopped = false
    }

    fun updateCampaigns(campaigns: List<CampaignCard>) {
        // A data refresh mid-session (e.g. a future periodic re-fetch)
        // is not separately specified by Round 4's own spec — simplest
        // safe default, matching the simulation's own documented
        // assumption: swap the source list in place, don't force a
        // reshuffle. The next real background-then-resume picks up the
        // new set naturally. Keep `index` in bounds regardless, so a
        // shrinking list can't index out of range.
        val byId = campaigns.associateBy { it.id }
        order = order.mapNotNull { byId[it.id] } + campaigns.filter { c -> order.none { it.id == c.id } }
        if (order.isNotEmpty()) index = index.coerceIn(0, order.size - 1)
    }
}

/**
 * Composable entry point — owns the 30-second timer and the
 * lifecycle observer internally, so a 3b-b caller only needs to read
 * `.current` and doesn't manage either concern itself.
 *
 * `campaigns` changing (a new list reference) updates the underlying
 * source data via [CampaignCarouselStateImpl.updateCampaigns] without
 * losing timer/shuffle state — same reasoning as the simulation's own
 * "data refresh mid-session" scenario.
 */
@Composable
fun rememberCampaignCarouselState(campaigns: List<CampaignCard>): CampaignCarouselState {
    val state = remember { CampaignCarouselStateImpl(campaigns) }
    val latestCampaigns by rememberUpdatedState(campaigns)

    LaunchedEffect(campaigns) {
        state.updateCampaigns(latestCampaigns)
    }

    // 30-second auto-advance. Restarts implicitly whenever `state`
    // itself is unchanged (this LaunchedEffect is keyed on Unit, so it
    // runs exactly once per composition of this state holder, looping
    // internally) — a reshuffle (onResume) resets `index` to 0 but
    // deliberately does NOT reset this timer's own phase; the next
    // tick simply advances from the fresh shuffled order at whatever
    // point the 30s cycle is already at. Round 4's spec doesn't
    // require the timer itself to restart on reshuffle, only that the
    // shuffled order does — conflating the two would be adding a rule
    // that was never actually specified.
    LaunchedEffect(Unit) {
        while (true) {
            delay(CARD_DURATION_MS)
            state.advance()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> state.onStop()
                Lifecycle.Event.ON_RESUME -> state.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}
