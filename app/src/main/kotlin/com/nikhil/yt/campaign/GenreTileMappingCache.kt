/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Task 59 Part 2b-b, Round 13 (handover.md, Mavins-web repo) — the
 * cache-lifecycle half of the sub-part Round 12 flagged as "genuinely
 * the next sub-part, not a small follow-up." Split further, per this
 * project's own mandatory task-splitting rule: this round builds ONLY
 * this cache, self-contained and independently verifiable; wiring it
 * into `MusicService.kt`'s still-stubbed `campaignSlotProvider = { null }`
 * is deliberately left for the next round, not attempted here.
 *
 * Wraps [CampaignRepository.fetchGenreTileMapping] with the periodic-
 * refresh caching behavior that function's own KDoc already asks
 * callers to provide ("Callers are expected to fetch this periodically
 * and cache the result client-side... rather than call it fresh on
 * every single genre-tile tap") — this is that caller, the first one
 * built.
 *
 * `object`, not `class` — matches this package's neighboring
 * [DiscordAssetRegistrar] singleton-cache convention (`com.nikhil.yt.discord`),
 * for the same reason: the whole point of a cache is amortizing
 * repeated fetches across every genre-tile tap for the lifetime of the
 * app process, which only works if every caller shares the same
 * instance rather than each queue construction getting its own
 * freshly-empty one.
 */
object GenreTileMappingCache {
    private const val TAG = "GenreTileMappingCache"

    // 15 minutes — a judgment call, not a confirmed product requirement;
    // no specific cadence was specified anywhere in this task's prior
    // rounds. Long enough that a user browsing several genre tiles in
    // one session doesn't refetch the whole table repeatedly; short
    // enough that a newly-reviewed mapping (an admin resolving an
    // unreviewed tile) reaches a running app session within one
    // reasonable sitting, not just on next app restart. Revisit this
    // number directly if either assumption turns out wrong in practice.
    private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    private val repository = CampaignRepository()
    private val mutex = Mutex()

    private var cachedMapping: Map<String, String?> = emptyMap()

    // 0L means "never fetched" -- deliberately NOT inferred from
    // `cachedMapping.isEmpty()`. The live `campaign_genre_tile_mapping`
    // table is expected to start (and may remain, for a while) genuinely
    // empty per Round 12's own note #4 ("still zero rows until real
    // production traffic or a manual seed populates it -- expected, not
    // a bug"). If staleness were judged by map emptiness instead of this
    // separate timestamp, an empty live table would make [ensureFresh]
    // refetch on every single call forever, defeating the entire point
    // of periodic caching for exactly the state this feature is
    // expected to actually launch in.
    private var lastFetchedAtMs: Long = 0L

    /**
     * Resolves a genre-tile title (e.g. the exact string shown on a
     * YouTube Music mood/genre tile) to a confirmed genre id for
     * campaign targeting, or `null`.
     *
     * Three distinct cases, per [CampaignRepository.fetchGenreTileMapping]'s
     * own documented map shape:
     * 1. **Tile known, mapped to a real genre id** — returns that id.
     * 2. **Tile known, explicitly reviewed as NOT a genre** (a mood/
     *    charts/etc. tile an admin has confirmed isn't genre-target-able)
     *    — the cached value is a real `null` for a present key; returns
     *    `null`, no ingest call (it's already been reviewed, nothing to
     *    flag for review again).
     * 3. **Tile not in the map at all** — unreviewed/unknown. Fires
     *    [CampaignRepository.ingestGenreTile] (fire-and-forget, per that
     *    function's own contract) so an admin can review it later, and
     *    returns `null` for *this* call regardless — there is no
     *    confirmed mapping yet, so nothing to inject this time either
     *    way. This is the "ingest at lookup-miss time, not at tap time"
     *    behavior Round 12 flagged as the resolved design question: a
     *    tile only gets reported when a real campaign-injection slot
     *    actually needs it, not on every browse regardless of whether a
     *    campaign slot is ever reached.
     *
     * @param tileTitle The exact genre-tile title string, expected to be
     *   `Queue.genre` (see [com.nikhil.yt.playback.queues.Queue]'s own
     *   `genre` property, added Round 12) once a real caller in
     *   `MusicService.kt` is wired up to pass it — not yet, as of this
     *   round.
     */
    suspend fun resolveGenreId(tileTitle: String): String? {
        ensureFresh()

        if (!cachedMapping.containsKey(tileTitle)) {
            repository.ingestGenreTile(tileTitle)
            return null
        }

        return cachedMapping[tileTitle]
    }

    private suspend fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (lastFetchedAtMs != 0L && now - lastFetchedAtMs < REFRESH_INTERVAL_MS) return

        mutex.withLock {
            // Re-check inside the lock -- another coroutine may have
            // just finished a refresh while this one was waiting.
            val nowInLock = System.currentTimeMillis()
            if (lastFetchedAtMs != 0L && nowInLock - lastFetchedAtMs < REFRESH_INTERVAL_MS) return@withLock

            val fetched = repository.fetchGenreTileMapping()
            cachedMapping = fetched
            lastFetchedAtMs = nowInLock
            Timber.tag(TAG).d("Refreshed genre tile mapping cache: ${fetched.size} reviewed entries")
        }
    }
}
