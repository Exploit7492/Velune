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
 * ## Modes
 * - **No campaigns**: Passes through unchanged.
 * - **Single campaign**: That campaign appears at every 5th position.
 * - **Multiple campaigns (max 10)**: Campaigns rotate in shuffled order
 *   across 5th positions. A new shuffle is generated for each queue instance
 *   so the user never hears the same sequence twice across different
 *   playlists/albums.
 *
 * ## Index Adjustment
 * When the user taps a song at index N in the original queue, that song
 * shifts right by floor(N / 4) positions due to injected campaigns before
 * it. [adjustIndex] computes the new index for the player.
 *
 * ## nextPage() Handling
 * For paginated queues (YouTubeQueue), injection continues seamlessly into
 * newly fetched chunks. Small chunks (< 4 items) may not reach the next
 * injection boundary; the pattern resumes correctly in the next chunk.
 *
 * ## Lazy Loading
 * Campaigns are fetched asynchronously in [getInitialStatus()] via the
 * provided [campaignProvider] lambda. This allows [MusicService.playQueue()]
 * to construct the wrapper synchronously while the actual campaign fetch
 * (which may involve network calls) happens inside the existing IO coroutine
 * that already calls [getInitialStatus()].
 *
 * @param baseQueue The underlying queue (radio, playlist, album, etc.)
 * @param campaignProvider Suspend lambda that returns campaign MediaItems.
 *   Returns empty list = pass-through mode.
 */
class CampaignInjectedQueue(
    private val baseQueue: Queue,
    private val campaignProvider: suspend () -> List<MediaItem> = { emptyList() },
) : Queue {

    /** Resolved campaign items, populated on first [getInitialStatus()] call. */
    private var campaignMediaItems: List<MediaItem> = emptyList()

    /** How many base items have been consumed across all pages. */
    private var baseItemsConsumed: Int = 0

    /** Shuffled order of campaign indices. Regenerated per queue instance. */
    private var campaignOrder: List<Int> = emptyList()

    override val preloadItem: MediaMetadata? = baseQueue.preloadItem

    override suspend fun getInitialStatus(): Queue.Status {
        // Fetch campaigns lazily inside the IO coroutine
        campaignMediaItems = campaignProvider()
        baseItemsConsumed = 0

        val baseStatus = baseQueue.getInitialStatus()
        baseItemsConsumed = baseStatus.items.size

        // Shuffle campaign order for this queue instance so the user
        // doesn't hear the same sequence when switching to a new playlist.
        campaignOrder = if (campaignMediaItems.size > 1) {
            campaignMediaItems.indices.shuffled()
        } else {
            campaignMediaItems.indices.toList()
        }

        val injectedItems = inject(baseStatus.items, offset = 0)
        val newIndex = adjustIndex(baseStatus.mediaItemIndex)

        return Queue.Status(
            title = baseStatus.title,
            items = injectedItems,
            mediaItemIndex = newIndex,
            position = baseStatus.position,
        )
    }

    override fun hasNextPage(): Boolean = baseQueue.hasNextPage()

    override suspend fun nextPage(): List<MediaItem> {
        val chunk = baseQueue.nextPage()
        val result = inject(chunk, offset = baseItemsConsumed)
        baseItemsConsumed += chunk.size
        return result
    }

    /**
     * Injects campaigns into a chunk of base items.
     *
     * @param baseItems The items from the underlying queue.
     * @param offset How many base items were consumed before this chunk.
     * @return The chunk with campaigns interleaved every 5th position.
     */
    private fun inject(baseItems: List<MediaItem>, offset: Int): List<MediaItem> {
        if (campaignMediaItems.isEmpty()) return baseItems

        val result = mutableListOf<MediaItem>()
        var campaignIdx = offset / 4

        for (i in baseItems.indices) {
            result.add(baseItems[i])

            val globalBaseIndex = offset + i
            // After every 4th base item (global indices 3, 7, 11, ...),
            // inject one campaign.
            if ((globalBaseIndex + 1) % 4 == 0) {
                val campaignMediaItem = campaignMediaItems[
                    campaignOrder[campaignIdx % campaignOrder.size]
                ]
                result.add(campaignMediaItem)
                campaignIdx++
            }
        }
        return result
    }

    /**
     * Adjusts the original start index to account for campaigns injected
     * before it.
     *
     * For every complete group of 4 base items before the original index,
     * one campaign was injected, shifting the target right by 1.
     *
     * Example: original index 5 (6th song). One group of 4 precedes it
     * (indices 0–3), so 1 campaign was injected at position 4. The 6th
     * song shifts to position 6. Formula: 5 + floor(5/4) = 5 + 1 = 6. ✓
     */
    private fun adjustIndex(originalIndex: Int): Int {
        if (originalIndex <= 0) return originalIndex
        val injectionsBefore = originalIndex / 4
        return originalIndex + injectionsBefore
    }
}
