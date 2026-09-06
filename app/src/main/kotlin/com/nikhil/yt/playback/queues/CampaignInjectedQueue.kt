/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.playback.queues

import androidx.media3.common.MediaItem
import com.nikhil.yt.models.MediaMetadata

/**
 * A [Queue] decorator that injects campaign songs into every 5th position
 * of any underlying queue (ListQueue, YouTubeQueue, LocalAlbumRadio, etc.).
 *
 * ## The Rule
 * Every 5th position (indices 4, 9, 14, 19, ...) is a campaign slot.
 * Base items are grouped into chunks of 4; one campaign is inserted after
 * each chunk.
 *
 * ## Task 59 Part 2a — per-slot fetch, not a pre-fetched batch
 * (handover.md, Mavins-web repo, Task 59 "Round 5"/Part 2a) — the
 * previous design fetched a batch of up to 10 campaigns once, in
 * [getInitialStatus()], and rotated through that fixed local list via
 * a one-time shuffle for every slot in the whole queue. This changes
 * that: [campaignSlotProvider] is now called **fresh, once per slot**,
 * at the moment that slot is actually reached during real queue
 * construction. This is not a style change — the RPC this now calls
 * through (`get_next_campaign_for_queue_slot`, migration 023, via
 * [com.nikhil.yt.campaign.CampaignRepository.fetchNextCampaignForQueueSlot])
 * does an atomic "pick the least-recently-served eligible campaign and
 * mark it served, in one transaction" per call — that is the actual
 * mechanism that guarantees every eligible campaign gets a turn before
 * any repeats, GLOBALLY across every listener's queue, not just within
 * one queue instance. Pre-fetching a batch up front would mark all of
 * those campaigns "just served" immediately, even though most of them
 * might not reach an actually-played slot for a while (or ever, if the
 * queue ends early) — silently corrupting the fairness bookkeeping for
 * every *other* listener's queue being built concurrently. Calling
 * fresh per slot is what makes the global fairness guarantee real
 * rather than approximate.
 *
 * [campaignSlotProvider] returning `null` — this class's own default —
 * means "no injection at all" for that slot; every slot boundary is
 * simply skipped, and the base items pass through unchanged. That's a
 * per-slot outcome only (no eligible campaign existed at that exact
 * moment), never a per-queue-type one: HANDOVER_CAMPAIGN.md §33 removed
 * the fail-closed "only a genre-tile-originated queue gets a real
 * provider, every other queue gets `{ null }`" rule this class's own
 * doc previously described here. `MusicService.kt`'s one call site now
 * wires a real provider onto every queue unconditionally — search
 * results, playlists, albums, radio, liked songs, all of it — so a
 * `null` slot result from here on means "the RPC had nothing eligible
 * right now," not "this queue type was never wired for injection."
 *
 * ## Index Adjustment
 * When the user taps a song at index N in the original queue, that song
 * may shift right due to campaigns injected before it. Tracked directly
 * during the same splice pass now (see [inject]'s own doc comment for
 * why a formula-based approach, used before Part 2a, silently breaks
 * once any individual slot can independently return no campaign).
 *
 * ## nextPage() Handling
 * For paginated queues (YouTubeQueue), injection continues seamlessly into
 * newly fetched chunks. Small chunks (< 4 items) may not reach the next
 * injection boundary; the pattern resumes correctly in the next chunk.
 *
 * @param baseQueue The underlying queue (radio, playlist, album, etc.)
 * @param campaignSlotProvider Suspend lambda, called once per injection
 *   slot, returning one campaign [MediaItem] to insert there, or `null`
 *   to skip that slot (no eligible campaign right now, or injection is
 *   disabled for this queue entirely — see class doc above).
 */
class CampaignInjectedQueue(
    private val baseQueue: Queue,
    private val campaignSlotProvider: suspend () -> MediaItem? = { null },
) : Queue {

    /** How many base items have been consumed across all pages. */
    private var baseItemsConsumed: Int = 0

    override val preloadItem: MediaMetadata? = baseQueue.preloadItem

    override suspend fun getInitialStatus(): Queue.Status {
        baseItemsConsumed = 0
        val baseStatus = baseQueue.getInitialStatus()
        baseItemsConsumed = baseStatus.items.size

        val injectionResult = inject(
            baseItems = baseStatus.items,
            offset = 0,
            targetOriginalIndex = baseStatus.mediaItemIndex,
        )

        val newIndex = if (injectionResult.adjustedTargetIndex >= 0) {
            injectionResult.adjustedTargetIndex
        } else {
            baseStatus.mediaItemIndex
        }

        return Queue.Status(
            title = baseStatus.title,
            items = injectionResult.items,
            mediaItemIndex = newIndex,
            position = baseStatus.position,
        )
    }

    override fun hasNextPage(): Boolean = baseQueue.hasNextPage()

    override suspend fun nextPage(): List<MediaItem> {
        val chunk = baseQueue.nextPage()
        val injectionResult = inject(baseItems = chunk, offset = baseItemsConsumed)
        baseItemsConsumed += chunk.size
        return injectionResult.items
    }

    /** Result of one [inject] pass — see that function's own doc comment. */
    private data class InjectionResult(
        val items: List<MediaItem>,
        val adjustedTargetIndex: Int,
    )

    /**
     * Injects campaigns into a chunk of base items, one
     * [campaignSlotProvider] call per 5th-position slot — not a
     * pre-fetched batch rotated locally (see class doc for why).
     *
     * @param baseItems The items from the underlying queue.
     * @param offset How many base items were consumed before this chunk
     *   — needed because the "every 4th" rule applies across chunk
     *   boundaries, not restarting at 0 for every [nextPage] call.
     * @param targetOriginalIndex A specific original (pre-injection)
     *   index within *this* chunk to track through the splice, or -1
     *   (the default, and always what [nextPage] passes) to skip
     *   tracking — [getInitialStatus] uses this to find where the song
     *   the user actually tapped landed after campaigns were spliced in
     *   around it. A real index is never negative, so -1 is always a
     *   safe sentinel for "not applicable" without ambiguity.
     *
     *   Deliberately computed by tracking position during THIS SAME
     *   pass, not via a separate `originalIndex + originalIndex/4`
     *   -style formula afterward (the previous design's approach) —
     *   verified by simulation (this project's established substitute
     *   for a live Kotlin compile/run — this sandbox has no Android
     *   SDK/Google Maven access, same limitation every prior Velune
     *   task in this project has hit) that such a formula silently
     *   undercounts as soon as any slot's [campaignSlotProvider] call
     *   returns `null` instead of a real item: e.g. base items 0-9,
     *   target original index 5, first injection slot (after index 3)
     *   returns null (skipped) — the formula says adjusted index 6
     *   (wrong, points at the wrong song), while tracking the real
     *   splice position during the pass correctly gives 5 (right song).
     *   This matters concretely: a wrong adjusted index means the
     *   player jumps to and starts playing the wrong song. This
     *   couldn't happen under the previous design (injection was
     *   all-or-nothing for every slot in a queue, never mixed) — it's
     *   a real correctness requirement introduced by moving to
     *   per-slot calls that can each independently succeed or return
     *   nothing, not a hypothetical edge case.
     */
    private suspend fun inject(
        baseItems: List<MediaItem>,
        offset: Int,
        targetOriginalIndex: Int = -1,
    ): InjectionResult {
        val result = mutableListOf<MediaItem>()
        var adjustedTargetIndex = -1

        for (i in baseItems.indices) {
            val globalBaseIndex = offset + i
            if (globalBaseIndex == targetOriginalIndex) {
                adjustedTargetIndex = result.size
            }
            result.add(baseItems[i])

            // After every 4th base item (global indices 3, 7, 11, ...),
            // try to inject one campaign. A null result (no eligible
            // campaign this moment, or injection disabled for this
            // queue) simply skips this slot — never blocks or errors.
            if ((globalBaseIndex + 1) % 4 == 0) {
                campaignSlotProvider()?.let { result.add(it) }
            }
        }

        return InjectionResult(items = result, adjustedTargetIndex = adjustedTargetIndex)
    }
}
